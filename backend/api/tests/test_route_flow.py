from unittest import mock, skipUnless

from django.contrib.auth import get_user_model
from rest_framework.authtoken.models import Token
from rest_framework.test import APITestCase

from api.route_algorithm import street_graph
from api.route_algorithm.elevation import ElevationError
from api.route_algorithm.llm import LLMError
from api.tests.fakes import fake_elevations

GENERATE_URL = "/api/v1/routes/generate/"
SAVED_URL = "/api/v1/routes/saved/"
CITY_HALL = [126.9784, 37.5668]


@skipUnless(street_graph.GRAPH_PATH.exists(), "Run `python manage.py build_street_graph` first.")
@mock.patch("api.route_algorithm.briefing.generate_text", side_effect=LLMError("offline"))
@mock.patch("api.route_algorithm.street_graph.download_graph", side_effect=AssertionError("Overpass used"))
@mock.patch(
    "api.route_algorithm.elevation.fetch_elevations",
    side_effect=lambda points: fake_elevations(points, grade=0.02),
)
class RouteFlowTests(APITestCase):
    def setUp(self):
        user = get_user_model().objects.create_user(username="runner", email="r@test.com", password="password123")
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {Token.objects.create(user=user).key}")

    def generate(self, **extra):
        body = {
            "starting_point": {"type": "Point", "coordinates": CITY_HALL},
            "starting_point_name": "서울시청",
            "distance": 3000,
            "language": "ko",
            **extra,
        }
        return self.client.post(GENERATE_URL, body, format="json")

    def test_loop_from_start_point_to_saved_route(self, elevation, download, llm):
        response = self.generate()

        self.assertEqual(response.status_code, 200)
        options = response.data["data"]["options"]
        self.assertTrue(1 <= len(options) <= 3)
        self.assertEqual([o["route"]["score"] for o in options], sorted(o["route"]["score"] for o in options))

        for option in options:
            route = option["route"]
            coordinates = route["route"][0]["coordinates"]
            self.assertEqual(coordinates[0], coordinates[-1])
            self.assertAlmostEqual(coordinates[0][0], CITY_HALL[0], delta=0.002)
            self.assertAlmostEqual(coordinates[0][1], CITY_HALL[1], delta=0.002)
            self.assertLessEqual(abs(route["distance"] - 3000), 3000 * 0.15)
            self.assertIsNotNone(route["elevation_gain_m"])
            self.assertGreaterEqual(route["traffic_light_count"], 0)
            self.assertIn(f"누적 상승 {route['elevation_gain_m']}m", route["briefing"])
            self.assertIn(f"신호등 {route['traffic_light_count']}개", route["briefing"])

        saved = self.client.post(SAVED_URL, {"temporary_route_id": options[-1]["temporary_route_id"]}, format="json")
        self.assertEqual(saved.status_code, 201)
        listed = self.client.get(SAVED_URL).data
        self.assertEqual([r["start_name"] for r in listed], ["서울시청"])
        self.assertEqual(listed[0]["distance"], options[-1]["route"]["distance"])
        download.assert_not_called()

    def test_point_to_point_ends_at_the_end_point(self, elevation, download, llm):
        end = [126.9900, 37.5700]
        response = self.generate(end_point={"type": "Point", "coordinates": end}, distance=2500)

        self.assertEqual(response.status_code, 200)
        coordinates = response.data["data"]["route"]["route"][0]["coordinates"]
        self.assertAlmostEqual(coordinates[-1][0], end[0], delta=0.002)
        self.assertAlmostEqual(coordinates[-1][1], end[1], delta=0.002)
        download.assert_not_called()

    def test_routes_without_elevation_still_work(self, elevation, download, llm):
        elevation.side_effect = ElevationError("down")

        response = self.generate()

        self.assertEqual(response.status_code, 200)
        route = response.data["data"]["route"]
        self.assertIsNone(route["elevation_gain_m"])
        self.assertNotIn("누적 상승", route["briefing"])
