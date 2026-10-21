import json
from pathlib import Path

from api.route_algorithm import street_graph
from api.route_algorithm.algorithm import create_routes
from api.route_algorithm.classes import GeoJSONPoint
from api.route_algorithm.elevation import _distance_m, climb_profiles

DATA_DIR = Path(__file__).resolve().parent / "ground_truth"
CASES_PATH = DATA_DIR / "cases.json"
ROUTES_DIR = DATA_DIR / "routes"
COORDINATE_DIGITS = 7


def load_cases(path=CASES_PATH):
    with open(path, encoding="utf-8") as file:
        return json.load(file)


def save_cases(data, path=CASES_PATH):
    with open(path, "w", encoding="utf-8") as file:
        json.dump(data, file, ensure_ascii=False, indent=2)
        file.write("\n")


def route_path(case_id):
    return ROUTES_DIR / f"{case_id}.geojson"


def load_route(case_id):
    with open(route_path(case_id), encoding="utf-8") as file:
        feature = next(f for f in json.load(file)["features"] if f["geometry"]["type"] == "LineString")
    return [tuple(point) for point in feature["geometry"]["coordinates"]]


def save_route(case, coordinates):
    ROUTES_DIR.mkdir(parents=True, exist_ok=True)
    features = [
        {
            "type": "Feature",
            "properties": {"id": case["id"], "name": case["name"], "stroke": "#d62728", "stroke-width": 4},
            "geometry": {"type": "LineString", "coordinates": [list(point) for point in coordinates]},
        },
        {
            "type": "Feature",
            "properties": {"name": "start", "marker-color": "#2ca02c"},
            "geometry": {"type": "Point", "coordinates": case["start"]},
        },
    ]
    if case.get("end"):
        features.append({
            "type": "Feature",
            "properties": {"name": "end", "marker-color": "#1f77b4"},
            "geometry": {"type": "Point", "coordinates": case["end"]},
        })
    with open(route_path(case["id"]), "w", encoding="utf-8") as file:
        json.dump({"type": "FeatureCollection", "features": features}, file, ensure_ascii=False, indent=1)
        file.write("\n")


def generate_route(case):
    end = GeoJSONPoint(coordinates=tuple(case["end"])) if case.get("end") else None
    best = create_routes(GeoJSONPoint(coordinates=tuple(case["start"])), case["distance_m"], "ko", end_point=end)[0]
    return [tuple(point) for point in best.route[0].coordinates]


def _key(point):
    return round(point[0], COORDINATE_DIGITS), round(point[1], COORDINATE_DIGITS)


class SignalCounter:
    def __init__(self, graph):
        self.signals = {
            _key((data["x"], data["y"]))
            for _, data in graph.nodes(data=True)
            if data.get("traffic_signal")
        }

    def count(self, coordinates):
        return sum(1 for point in coordinates if _key(point) in self.signals)


def local_signal_counter():
    local = street_graph._load_local()
    if local is None:
        raise RuntimeError("Run `python manage.py build_street_graph` first.")
    return SignalCounter(local.graph)


def route_length_m(coordinates):
    return sum(_distance_m(a, b) for a, b in zip(coordinates, coordinates[1:]))


def system_values(routes, signal_counter):
    profiles = climb_profiles(routes) or [None] * len(routes)
    return [
        {
            "distance_m": round(route_length_m(coordinates)),
            "traffic_lights": signal_counter.count(coordinates),
            "elevation_gain_m": profile.total_gain_m if profile else None,
        }
        for coordinates, profile in zip(routes, profiles)
    ]


def within(system, truth, absolute, relative=0.0):
    if system is None or truth is None:
        return None
    return abs(system - truth) <= max(absolute, relative * truth)


def evaluate(cases, systems, thresholds):
    rows = []
    for case, system in zip(cases, systems):
        truth = case["truth"]
        rows.append({
            "id": case["id"],
            "system_lights": system["traffic_lights"],
            "truth_lights": truth.get("traffic_lights"),
            "lights_ok": within(system["traffic_lights"], truth.get("traffic_lights"), thresholds["traffic_lights"]["within"]),
            "system_gain": system["elevation_gain_m"],
            "truth_gain": truth.get("elevation_gain_m"),
            "gain_ok": within(
                system["elevation_gain_m"],
                truth.get("elevation_gain_m"),
                thresholds["elevation_gain_m"]["within_m"],
                thresholds["elevation_gain_m"]["within_ratio"],
            ),
        })

    def summary(ok_key, system_key, truth_key):
        judged = [r for r in rows if r[ok_key] is not None]
        if not judged:
            return {"cases": 0, "pass_rate": None, "mean_abs_error": None}
        return {
            "cases": len(judged),
            "pass_rate": sum(r[ok_key] for r in judged) / len(judged),
            "mean_abs_error": sum(abs(r[system_key] - r[truth_key]) for r in judged) / len(judged),
        }

    return rows, {
        "traffic_lights": summary("lights_ok", "system_lights", "truth_lights"),
        "elevation_gain_m": summary("gain_ok", "system_gain", "truth_gain"),
    }
