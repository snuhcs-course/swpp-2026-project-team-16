from unittest import mock

from django.test import SimpleTestCase

from api.route_algorithm.algorithm import create_route
from api.route_algorithm.classes import GeoJSONPoint
from api.route_algorithm.llm import LLMError
from api.tests.fakes import SNU, grid_graph


@mock.patch("api.route_algorithm.briefing.generate_text", side_effect=LLMError("offline"))
@mock.patch("api.route_algorithm.algorithm.ox.graph.graph_from_point")
class CreateRouteTests(SimpleTestCase):
    def setUp(self):
        self.graph = grid_graph()
        self.start = GeoJSONPoint(coordinates=SNU)

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

    def test_point_to_point_rejects_same_point(self, download, llm):
        with self.assertRaises(ValueError):
            create_route(self.start, 2000, "ko", end_point=GeoJSONPoint(coordinates=SNU))
        download.assert_not_called()

    def test_unknown_mode_raises(self, download, llm):
        with self.assertRaises(ValueError):
            create_route(self.start, 2000, "ko", mode="zigzag")
