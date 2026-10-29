import math
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass

import requests
from django.db import transaction

from api.models import ElevationCache
from api.route_algorithm.briefing import UphillSegment

OPEN_METEO_URL = "https://api.open-meteo.com/v1/elevation"
BATCH_SIZE = 100
REQUEST_TIMEOUT_SECONDS = 10
MAX_PARALLEL_REQUESTS = 4
COORDINATE_DECIMAL_PLACES = 6
DB_LOOKUP_BATCH_SIZE = 500

SAMPLE_SPACING_M = 50
MAX_SAMPLES_PER_ROUTE = 200
SMOOTHING_WINDOW = 3
MIN_UPHILL_GAIN_M = 5
MIN_UPHILL_GRADE = 0.03
MAX_UPHILLS = 3

M_PER_DEG = 111_000


class ElevationError(Exception):
    pass


@dataclass(frozen=True)
class ClimbProfile:
    total_gain_m: int
    uphill_segments: list[UphillSegment]


def _fetch_batch(points):
    try:
        response = requests.get(
            OPEN_METEO_URL,
            params={
                "latitude": ",".join(f"{lat:.6f}" for _, lat in points),
                "longitude": ",".join(f"{lon:.6f}" for lon, _ in points),
            },
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
        elevations = response.json()["elevation"]
    except (requests.RequestException, ValueError, KeyError) as error:
        raise ElevationError(str(error)) from error

    if len(elevations) != len(points) or any(e is None for e in elevations):
        raise ElevationError("Elevation response did not match the request.")
    try:
        values = [float(elevation) for elevation in elevations]
    except (TypeError, ValueError) as error:
        raise ElevationError("Elevation response contained an invalid value.") from error
    if any(not math.isfinite(value) for value in values):
        raise ElevationError("Elevation response contained an invalid value.")
    return values


def _coordinate_key(point: tuple[float, float]) -> str:
    lon, lat = point
    return f"{lat:.{COORDINATE_DECIMAL_PLACES}f},{lon:.{COORDINATE_DECIMAL_PLACES}f}"


def _cached_elevations(keys: list[str]) -> dict[str, float]:
    cached = {}
    for offset in range(0, len(keys), DB_LOOKUP_BATCH_SIZE):
        batch = keys[offset:offset + DB_LOOKUP_BATCH_SIZE]
        cached.update(
            ElevationCache.objects.filter(coordinate_key__in=batch)
            .values_list("coordinate_key", "elevation_m")
        )
    return cached


def _fetch_missing_elevations(points: list[tuple[float, float]]) -> list[float]:
    batches = [points[i:i + BATCH_SIZE] for i in range(0, len(points), BATCH_SIZE)]
    if not batches:
        return []
    with ThreadPoolExecutor(max_workers=min(MAX_PARALLEL_REQUESTS, len(batches))) as pool:
        results = list(pool.map(_fetch_batch, batches))
    return [elevation for batch in results for elevation in batch]


def fetch_elevations(points: list[tuple[float, float]]) -> list[float]:
    if not points:
        return []

    if any(
        not math.isfinite(lon)
        or not math.isfinite(lat)
        or not -180 <= lon <= 180
        or not -90 <= lat <= 90
        for lon, lat in points
    ):
        raise ElevationError("Coordinates must be finite latitude/longitude values.")

    # Match the precision sent to Open-Meteo so equivalent requests share a cache row.
    normalized_points = [
        (float(f"{lon:.{COORDINATE_DECIMAL_PLACES}f}"), float(f"{lat:.{COORDINATE_DECIMAL_PLACES}f}"))
        for lon, lat in points
    ]
    keys = [_coordinate_key(point) for point in normalized_points]
    unique_keys = list(dict.fromkeys(keys))
    cached = _cached_elevations(unique_keys)
    missing_keys = [key for key in unique_keys if key not in cached]

    if missing_keys:
        points_by_key = dict(zip(keys, normalized_points))
        missing_points = [points_by_key[key] for key in missing_keys]
        missing_elevations = _fetch_missing_elevations(missing_points)
        cached.update(zip(missing_keys, missing_elevations))

        rows = [
            ElevationCache(coordinate_key=key, elevation_m=elevation)
            for key, elevation in zip(missing_keys, missing_elevations)
        ]
        # Concurrent route requests can fill the same coordinate; the existing row wins.
        with transaction.atomic():
            ElevationCache.objects.bulk_create(rows, ignore_conflicts=True)

        # Read back rows so a concurrent insert is handled consistently.
        cached.update(_cached_elevations(missing_keys))

    return [cached[key] for key in keys]


def _distance_m(a, b):
    (lon1, lat1), (lon2, lat2) = a, b
    kx = M_PER_DEG * math.cos(math.radians((lat1 + lat2) / 2))
    return math.hypot((lon2 - lon1) * kx, (lat2 - lat1) * M_PER_DEG)


def sample_route(coordinates: list[tuple[float, float]]) -> tuple[list[tuple[float, float]], list[float]]:
    if len(coordinates) < 2:
        return list(coordinates), [0.0] * len(coordinates)

    cumulative = [0.0]
    for a, b in zip(coordinates, coordinates[1:]):
        cumulative.append(cumulative[-1] + _distance_m(a, b))
    total = cumulative[-1]
    if total == 0:
        return [coordinates[0]], [0.0]

    count = max(1, min(MAX_SAMPLES_PER_ROUTE, round(total / SAMPLE_SPACING_M)))
    targets = [total * i / count for i in range(count + 1)]

    samples, segment = [], 0
    for target in targets:
        while segment < len(cumulative) - 2 and cumulative[segment + 1] < target:
            segment += 1
        start, end = cumulative[segment], cumulative[segment + 1]
        t = 0.0 if end == start else (target - start) / (end - start)
        (lon1, lat1), (lon2, lat2) = coordinates[segment], coordinates[segment + 1]
        samples.append((lon1 + (lon2 - lon1) * t, lat1 + (lat2 - lat1) * t))
    return samples, targets


def _smooth(values):
    half = SMOOTHING_WINDOW // 2
    return [
        sum(values[max(0, i - half):i + half + 1]) / len(values[max(0, i - half):i + half + 1])
        for i in range(len(values))
    ]


def climb_profile(distances: list[float], elevations: list[float]) -> ClimbProfile:
    if len(elevations) < 2:
        return ClimbProfile(total_gain_m=0, uphill_segments=[])

    smoothed = _smooth(elevations)
    total_gain = sum(max(b - a, 0.0) for a, b in zip(smoothed, smoothed[1:]))

    uphills, start = [], None
    for i in range(1, len(smoothed) + 1):
        rising = i < len(smoothed) and smoothed[i] > smoothed[i - 1]
        if rising and start is None:
            start = i - 1
        elif not rising and start is not None:
            end = i - 1
            gain = smoothed[end] - smoothed[start]
            length = distances[end] - distances[start]
            if gain >= MIN_UPHILL_GAIN_M and length > 0 and gain / length >= MIN_UPHILL_GRADE:
                uphills.append(UphillSegment(
                    start_km=round(distances[start] / 1000, 1),
                    length_m=round(length),
                    elevation_gain_m=round(gain),
                ))
            start = None

    biggest = sorted(uphills, key=lambda s: s.elevation_gain_m, reverse=True)[:MAX_UPHILLS]
    return ClimbProfile(
        total_gain_m=round(total_gain),
        uphill_segments=sorted(biggest, key=lambda s: s.start_km),
    )


def climb_profiles(routes: list[list[tuple[float, float]]]) -> list[ClimbProfile] | None:
    sampled = [sample_route(coordinates) for coordinates in routes]
    points = [p for samples, _ in sampled for p in samples]
    try:
        elevations = fetch_elevations(points)
    except ElevationError:
        return None

    profiles, offset = [], 0
    for samples, distances in sampled:
        profiles.append(climb_profile(distances, elevations[offset:offset + len(samples)]))
        offset += len(samples)
    return profiles
