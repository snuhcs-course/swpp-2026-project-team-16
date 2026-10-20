from unittest import mock

from django.test import SimpleTestCase

from api.route_algorithm.algorithm import create_route, create_routes
from api.route_algorithm.classes import GeoJSONPoint
from api.route_algorithm.elevation import ElevationError
from api.route_algorithm.llm import LLMError
from api.tests.fakes import SNU, fake_elevations, grid_graph


@mock.patch("api.route_algorithm.briefing.generate_text", side_effect=LLMError("offline"))
@mock.patch("api.route_algorithm.algorithm.ox.graph.graph_from_point")
class CreateRouteTests(SimpleTestCase):
    def setUp(self):
        self.graph = grid_graph()
        self.start = GeoJSONPoint(coordinates=SNU)
        patcher = mock.patch("api.route_algorithm.street_graph.local_graph", return_value=None)
        patcher.start()
        self.addCleanup(patcher.stop)
        patcher = mock.patch("api.route_algorithm.elevation.fetch_elevations", side_effect=fake_elevations)
        self.elevation = patcher.start()
        self.addCleanup(patcher.stop)

    def test_loop_returns_to_start_near_target_distance(self, download, llm):
        download.return_value = self.graph
        route = create_route(self.start, 3000, "ko")
        coordinates = route.route[0].coordinates
        self.assertEqual(coordinates[0], coordinates[-1])
        self.assertLessEqual(abs(route.distance - 3000), 3000 * 0.5)
        self.assertIn(f"{route.distance / 1000:.2f}".rstrip("0").rstrip("."), route.briefing)

    def test_point_to_point_ends_near_end_point(self, download, llm):
        download.return_value = self.graph
        end = GeoJSONPoint(coordinates=(SNU[0] + 0.009, SNU[1]))
        route = create_route(self.start, 2000, "en", end_point=end)
        last_lon, last_lat = route.route[0].coordinates[-1]
        self.assertAlmostEqual(last_lon, end.coordinates[0], delta=0.001)
        self.assertAlmostEqual(last_lat, end.coordinates[1], delta=0.001)
        self.assertLessEqual(abs(route.distance - 2000), 2000 * 0.5)

    def test_short_distance_falls_back_to_shortest_path(self, download, llm):
        download.return_value = self.graph
        end = GeoJSONPoint(coordinates=(SNU[0] + 0.009, SNU[1]))
        route = create_route(self.start, 500, "ko", end_point=end)
        self.assertTrue(route.is_shortest_path)
        self.assertGreater(route.distance, 500)
        self.assertTrue(route.to_dict()["is_shortest_path"])

    def test_regular_routes_are_not_shortest_path(self, download, llm):
        download.return_value = self.graph
        end = GeoJSONPoint(coordinates=(SNU[0] + 0.009, SNU[1]))
        self.assertFalse(create_route(self.start, 2000, "ko", end_point=end).is_shortest_path)
        self.assertFalse(create_route(self.start, 3000, "ko").is_shortest_path)

    def test_point_to_point_rejects_same_point(self, download, llm):
        with self.assertRaises(ValueError):
            create_route(self.start, 2000, "ko", end_point=GeoJSONPoint(coordinates=SNU))
        download.assert_not_called()

    def test_unknown_mode_raises(self, download, llm):
        with self.assertRaises(ValueError):
            create_route(self.start, 2000, "ko", mode="zigzag")


@mock.patch("api.route_algorithm.briefing.generate_text", side_effect=LLMError("offline"))
@mock.patch("api.route_algorithm.algorithm.ox.graph.graph_from_point")
class CreateRoutesTests(SimpleTestCase):
    def setUp(self):
        self.graph = grid_graph()
        self.start = GeoJSONPoint(coordinates=SNU)
        patcher = mock.patch("api.route_algorithm.street_graph.local_graph", return_value=None)
        patcher.start()
        self.addCleanup(patcher.stop)

    def create(self, elevations=fake_elevations):
        with mock.patch("api.route_algorithm.elevation.fetch_elevations", side_effect=elevations):
            return create_routes(self.start, 3000, "ko")

    def test_returns_up_to_three_distinct_routes_ranked_by_score(self, download, llm):
        download.return_value = self.graph
        routes = self.create()

        self.assertEqual(len(routes), 3)
        self.assertEqual(len({tuple(r.route[0].coordinates) for r in routes}), len(routes))
        self.assertEqual([r.score for r in routes], sorted(r.score for r in routes))

    def test_briefing_uses_measured_climb_and_signals(self, download, llm):
        for node in self.graph.nodes:
            self.graph.nodes[node]["highway"] = "traffic_signals"
        download.return_value = self.graph
        route = self.create()[0]

        self.assertIsNotNone(route.elevation_gain_m)
        self.assertGreater(route.elevation_gain_m, 0)
        self.assertGreater(route.traffic_light_count, 0)
        self.assertIn(f"누적 상승 {route.elevation_gain_m}m", route.briefing)
        self.assertIn(f"신호등 {route.traffic_light_count}개", route.briefing)

    def test_ranks_by_score_not_only_distance(self, download, llm):
        download.return_value = self.graph
        with mock.patch(
            "api.route_algorithm.algorithm.score_route",
            side_effect=lambda gain, signals, length, target: -length,
        ):
            routes = self.create()

        self.assertEqual([r.distance for r in routes], sorted((r.distance for r in routes), reverse=True))
        self.assertNotEqual(routes[0].distance, 3000)

    def test_works_without_elevation(self, download, llm):
        download.return_value = self.graph
        routes = self.create(elevations=ElevationError("down"))

        self.assertTrue(routes)
        self.assertIsNone(routes[0].elevation_gain_m)
        self.assertNotIn("누적 상승", routes[0].briefing)

    def test_counts_only_signal_nodes_on_generated_route(self, download, llm):
        signal_coordinates = set()

        # 그래프 노드 일부를 신호등으로 표시
        for node, data in self.graph.nodes(data=True):
            if node % 3 == 0:
                data["highway"] = "traffic_signals"
                signal_coordinates.add((data["x"], data["y"]))

        download.return_value = self.graph
        route = self.create()[0]
        route_coordinates = route.route[0].coordinates

        # 반환된 좌표 중 신호등 개수 직접 counting
        expected_count = sum(
            tuple(coordinate) in signal_coordinates
            for coordinate in route_coordinates
        )

        self.assertGreater(expected_count, 0)
        self.assertEqual(route.traffic_light_count, expected_count)
