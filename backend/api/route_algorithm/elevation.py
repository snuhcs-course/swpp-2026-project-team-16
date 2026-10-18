import math
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass

import requests

from api.route_algorithm.briefing import UphillSegment

OPEN_METEO_URL = "https://api.open-meteo.com/v1/elevation"
BATCH_SIZE = 100
REQUEST_TIMEOUT_SECONDS = 10
MAX_PARALLEL_REQUESTS = 4

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
    return [float(e) for e in elevations]


def fetch_elevations(points: list[tuple[float, float]]) -> list[float]:
    batches = [points[i:i + BATCH_SIZE] for i in range(0, len(points), BATCH_SIZE)]
    if not batches:
        return []
    with ThreadPoolExecutor(max_workers=min(MAX_PARALLEL_REQUESTS, len(batches))) as pool:
        results = list(pool.map(_fetch_batch, batches))
    return [e for batch in results for e in batch]


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
