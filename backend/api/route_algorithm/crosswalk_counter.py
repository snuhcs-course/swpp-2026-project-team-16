"""Count signalized crosswalk encounters along a generated route.

The route graph is an OSM street-centerline graph, so its traffic-signal tags
are incomplete for counting pedestrian crossings. This counter uses Seoul's
published crosswalk inventory and matches its signalized crossing points to
the generated route in a projected metric coordinate system (EPSG:5186).
"""

import csv
import math
import threading
from dataclasses import dataclass
from pathlib import Path

from pyproj import Transformer


DATA_PATH = Path(__file__).resolve().parent / "data" / "seoul_signalized_crosswalks.csv"
MATCH_RADIUS_METERS = 15.0
EVENT_MERGE_DISTANCE_METERS = 30.0
GRID_CELL_METERS = 100.0

_WGS84_TO_SEOUL = Transformer.from_crs("EPSG:4326", "EPSG:5186", always_xy=True)
_default_counter = None
_default_counter_lock = threading.Lock()


@dataclass(frozen=True)
class SignalizedCrosswalk:
    crosswalk_id: str
    intersection_id: str
    easting_m: float
    northing_m: float


def project_route(coordinates: list[tuple[float, float]]) -> list[tuple[float, float]]:
    """Convert route coordinates from GeoJSON [longitude, latitude] to EPSG:5186."""
    if len(coordinates) < 2:
        return []
    eastings, northings = _WGS84_TO_SEOUL.transform(
        [point[0] for point in coordinates],
        [point[1] for point in coordinates],
    )
    return list(zip(eastings, northings))


class SignalizedCrosswalkCounter:
    def __init__(
        self,
        records: list[SignalizedCrosswalk],
        match_radius_m: float = MATCH_RADIUS_METERS,
        event_merge_distance_m: float = EVENT_MERGE_DISTANCE_METERS,
        grid_cell_m: float = GRID_CELL_METERS,
    ):
        if match_radius_m < 0 or event_merge_distance_m < 0 or grid_cell_m <= 0:
            raise ValueError("matching distances must be non-negative and grid_cell_m must be positive")
        self.records = tuple(records)
        self.match_radius_m = match_radius_m
        self.event_merge_distance_m = event_merge_distance_m
        self.grid_cell_m = grid_cell_m
        self._grid: dict[tuple[int, int], list[SignalizedCrosswalk]] = {}
        for record in self.records:
            cell = self._cell(record.easting_m, record.northing_m)
            self._grid.setdefault(cell, []).append(record)

    @classmethod
    def from_csv(cls, path: Path = DATA_PATH) -> "SignalizedCrosswalkCounter":
        if not path.exists():
            raise FileNotFoundError(
                f"Seoul signalized-crosswalk data is missing: {path}"
            )

        records = []
        skipped_rows = 0

        with path.open(encoding="utf-8-sig", newline="") as file:
            for line_number, row in enumerate(csv.DictReader(file), start=2):
                crosswalk_id = (row.get("crosswalk_id") or "").strip()
                intersection_id = (row.get("intersection_id") or "").strip()
                easting = (row.get("easting_m") or "").strip()
                northing = (row.get("northing_m") or "").strip()

                if not crosswalk_id or not easting or not northing:
                    skipped_rows += 1
                    continue

                try:
                    easting_m = float(easting)
                    northing_m = float(northing)
                except ValueError as error:
                    raise ValueError(
                        f"Invalid coordinates in crosswalk CSV at line {line_number}."
                    ) from error

                records.append(
                    SignalizedCrosswalk(
                        crosswalk_id=crosswalk_id,
                        intersection_id=intersection_id,
                        easting_m=easting_m,
                        northing_m=northing_m,
                    )
                )

        if skipped_rows:
            print(
                f"[crosswalk_counter] Skipped {skipped_rows} rows "
                "with missing IDs or coordinates."
            )

        if not records:
            raise ValueError(
                f"Seoul signalized-crosswalk data is empty: {path}"
            )

        return cls(records)

    def _cell(self, x: float, y: float) -> tuple[int, int]:
        return math.floor(x / self.grid_cell_m), math.floor(y / self.grid_cell_m)

    def _nearby_records(self, route: list[tuple[float, float]]):
        radius = self.match_radius_m
        min_x = min(point[0] for point in route) - radius
        max_x = max(point[0] for point in route) + radius
        min_y = min(point[1] for point in route) - radius
        max_y = max(point[1] for point in route) + radius
        min_cell_x, min_cell_y = self._cell(min_x, min_y)
        max_cell_x, max_cell_y = self._cell(max_x, max_y)
        for cell_x in range(min_cell_x, max_cell_x + 1):
            for cell_y in range(min_cell_y, max_cell_y + 1):
                yield from self._grid.get((cell_x, cell_y), ())

    def _route_positions_near_point(self, point, route):
        """Return each route visit within radius, merging hits on adjacent segments."""
        cumulative = 0.0
        positions = []
        radius_squared = self.match_radius_m * self.match_radius_m

        for start, end in zip(route, route[1:]):
            dx = end[0] - start[0]
            dy = end[1] - start[1]
            length_squared = dx * dx + dy * dy
            if length_squared == 0:
                continue
            length = math.sqrt(length_squared)
            fraction = (
                ((point[0] - start[0]) * dx + (point[1] - start[1]) * dy)
                / length_squared
            )
            fraction = min(1.0, max(0.0, fraction))
            nearest_x = start[0] + fraction * dx
            nearest_y = start[1] + fraction * dy
            distance_squared = (point[0] - nearest_x) ** 2 + (point[1] - nearest_y) ** 2
            if distance_squared <= radius_squared:
                positions.append(cumulative + fraction * length)
            cumulative += length

        if not positions:
            return []

        positions.sort()
        merged = [positions[0]]
        for position in positions[1:]:
            if position - merged[-1] > self.event_merge_distance_m:
                merged.append(position)
        return merged

    def count_projected_route(self, route: list[tuple[float, float]]) -> int:
        """Count signalized crossing events for a route already in EPSG:5186.

        Nearby crosswalk records at the same intersection are treated as one
        event when the route reaches them at nearly the same point. Re-visiting
        that intersection later in the route is counted again.
        """
        if len(route) < 2 or not self.records:
            return 0

        events: dict[str, list[float]] = {}
        for record in self._nearby_records(route):
            positions = self._route_positions_near_point(
                (record.easting_m, record.northing_m),
                route,
            )
            if positions:
                key = record.intersection_id or record.crosswalk_id
                events.setdefault(key, []).extend(positions)

        count = 0
        for positions in events.values():
            positions.sort()
            previous = None
            for position in positions:
                if previous is None or position - previous > self.event_merge_distance_m:
                    count += 1
                    previous = position
        return count

    def count(self, coordinates: list[tuple[float, float]]) -> int:
        return self.count_projected_route(project_route(coordinates))


def count_signalized_crossings(coordinates: list[tuple[float, float]]) -> int:
    """Count pedestrian-signal crossing events along a GeoJSON route line."""
    global _default_counter
    if len(coordinates) < 2:
        return 0
    if _default_counter is None:
        with _default_counter_lock:
            if _default_counter is None:
                _default_counter = SignalizedCrosswalkCounter.from_csv()
    return _default_counter.count(coordinates)
