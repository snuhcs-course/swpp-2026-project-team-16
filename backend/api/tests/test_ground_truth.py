import json
import tempfile
from pathlib import Path
from unittest import mock

from django.test import SimpleTestCase

from api.evaluation import ground_truth as gt
from api.route_algorithm.elevation import ClimbProfile
from api.tests.fakes import SNU, grid_graph

THRESHOLDS = {
    "traffic_lights": {"within": 2, "min_pass_rate": 0.75},
    "elevation_gain_m": {"within_m": 10, "within_ratio": 0.3, "min_pass_rate": 0.75},
}


def case(case_id, lights=None, gain=None):
    return {
        "id": case_id,
        "name": case_id,
        "start": list(SNU),
        "distance_m": 3000,
        "truth": {"traffic_lights": lights, "elevation_gain_m": gain},
    }


class SignalCounterTests(SimpleTestCase):
    def test_counts_route_points_on_signal_nodes(self):
        graph = grid_graph()
        nodes = list(graph.nodes)
        graph.nodes[nodes[1]]["traffic_signal"] = True
        graph.nodes[nodes[3]]["traffic_signal"] = True
        route = [(graph.nodes[n]["x"], graph.nodes[n]["y"]) for n in nodes[:5] + [nodes[1]]]

        self.assertEqual(gt.SignalCounter(graph).count(route), 3)


class EvaluateTests(SimpleTestCase):
    def test_within_uses_the_larger_of_absolute_and_relative_tolerance(self):
        self.assertTrue(gt.within(55, 50, 10, 0.3))
        self.assertTrue(gt.within(130, 100, 10, 0.3))
        self.assertFalse(gt.within(140, 100, 10, 0.3))
        self.assertIsNone(gt.within(5, None, 2))
        self.assertIsNone(gt.within(None, 5, 2))

    def test_pass_rate_and_error_only_count_cases_with_truth(self):
        cases = [case("a", lights=3, gain=40), case("b", lights=0, gain=10), case("c")]
        systems = [
            {"traffic_lights": 4, "elevation_gain_m": 45},
            {"traffic_lights": 5, "elevation_gain_m": 35},
            {"traffic_lights": 1, "elevation_gain_m": None},
        ]

        rows, metrics = gt.evaluate(cases, systems, THRESHOLDS)

        self.assertEqual([r["lights_ok"] for r in rows], [True, False, None])
        self.assertEqual([r["gain_ok"] for r in rows], [True, False, None])
        self.assertEqual(metrics["traffic_lights"], {"cases": 2, "pass_rate": 0.5, "mean_abs_error": 3.0})
        self.assertEqual(metrics["elevation_gain_m"], {"cases": 2, "pass_rate": 0.5, "mean_abs_error": 15.0})

    def test_no_truth_yet(self):
        _, metrics = gt.evaluate([case("a")], [{"traffic_lights": 1, "elevation_gain_m": 5}], THRESHOLDS)
        self.assertEqual(metrics["traffic_lights"]["cases"], 0)
        self.assertIsNone(metrics["traffic_lights"]["pass_rate"])


class SystemValuesTests(SimpleTestCase):
    def test_combines_distance_signals_and_climb(self):
        route = [(126.950, 37.46), (126.951, 37.46)]
        counter = mock.Mock(count=mock.Mock(return_value=2))
        with mock.patch.object(gt, "climb_profiles", return_value=[ClimbProfile(total_gain_m=12, uphill_segments=[])]):
            values = gt.system_values([route], counter)
        self.assertEqual(values, [{"distance_m": 88, "traffic_lights": 2, "elevation_gain_m": 12}])

    def test_unknown_elevation_when_lookup_fails(self):
        counter = mock.Mock(count=mock.Mock(return_value=0))
        with mock.patch.object(gt, "climb_profiles", return_value=None):
            values = gt.system_values([[(126.95, 37.46), (126.951, 37.46)]], counter)
        self.assertIsNone(values[0]["elevation_gain_m"])


class RouteFileTests(SimpleTestCase):
    def test_saved_route_loads_back_with_markers(self):
        with tempfile.TemporaryDirectory() as directory, mock.patch.object(gt, "ROUTES_DIR", Path(directory)):
            item = {**case("p2p"), "end": [126.96, 37.47]}
            gt.save_route(item, [(126.95, 37.46), (126.96, 37.47)])

            self.assertEqual(gt.load_route("p2p"), [(126.95, 37.46), (126.96, 37.47)])
            features = json.loads(gt.route_path("p2p").read_text())["features"]
            self.assertEqual([f["properties"].get("name") for f in features[1:]], ["start", "end"])


class CommittedCasesTests(SimpleTestCase):
    def test_every_case_has_a_route_file_and_truth_fields(self):
        data = gt.load_cases()
        self.assertGreaterEqual(len(data["cases"]), 8)
        for item in data["cases"]:
            self.assertTrue(gt.route_path(item["id"]).exists(), item["id"])
            self.assertLessEqual({"traffic_lights", "elevation_gain_m", "elevation_source", "checked_by"}, set(item["truth"]))
