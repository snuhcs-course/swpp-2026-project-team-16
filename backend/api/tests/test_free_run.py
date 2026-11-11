from django.contrib.auth import get_user_model
from django.test import SimpleTestCase
from rest_framework.authtoken.models import Token
from rest_framework.test import APITestCase

from api.models import Route, RouteRun, SharedRoute
from api.services.free_route import free_route_line, path_length_m, simplify, trim_ends, validate_path


RUNS_URL = "/api/v1/runs/"


def straight_path(meters, step_m=10, lat=37.5):
    count = int(meters / step_m) + 1
    step_deg = step_m / 111_320.0
    return [[127.0, lat + i * step_deg] for i in range(count)]


class FreeRouteGeometryTests(SimpleTestCase):
    def test_simplify_drops_points_on_a_straight_line(self):
        points = straight_path(1000)

        self.assertEqual(simplify(points), [points[0], points[-1]])

    def test_simplify_keeps_corners(self):
        corner = [[127.0, 37.5], [127.0, 37.501], [127.001, 37.501]]

        self.assertEqual(len(simplify(corner)), 3)

    def test_trims_two_hundred_meters_at_each_end(self):
        points = straight_path(2000)

        trimmed = trim_ends(points)

        self.assertAlmostEqual(path_length_m(trimmed), 1600, delta=5)

    def test_short_runs_are_not_trimmed(self):
        points = straight_path(600)

        self.assertEqual(trim_ends(points), points)

    def test_free_route_line_reports_its_length(self):
        line, length = free_route_line(straight_path(3000))

        self.assertAlmostEqual(length, 2600, delta=5)
        self.assertEqual(len(line), 2)

    def test_validate_path(self):
        self.assertIsNotNone(validate_path([[127.0, 37.5]])[1])
        self.assertIsNotNone(validate_path([[127.0, 37.5], [200.0, 37.5]])[1])
        self.assertIsNotNone(validate_path([[127.0, 37.5], ["a", 37.5]])[1])
        self.assertIsNotNone(validate_path("nope")[1])
        self.assertIsNone(validate_path([[127.0, 37.5], [127.0, 37.6]])[1])


class FreeRunApiTests(APITestCase):
    def setUp(self):
        self.user = get_user_model().objects.create_user(username="runner", email="r@test.com", password="password123")
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {Token.objects.get_or_create(user=self.user)[0].key}")

    def test_records_a_free_run_as_a_trimmed_route(self):
        response = self.client.post(RUNS_URL, {
            "path": straight_path(3000),
            "distance_m": 3050,
            "duration_s": 1100,
            "splits": [360, 370, 365],
            "start_name": "Han river",
        }, format="json")

        self.assertEqual(response.status_code, 201)
        route = Route.objects.get()
        self.assertEqual(route.source, "free")
        self.assertEqual(route.created_by, self.user)
        self.assertEqual(route.start_name, "Han river")
        self.assertAlmostEqual(route.distance, 2600, delta=5)
        run = RouteRun.objects.get()
        self.assertEqual(run.distance_m, 3050)
        self.assertEqual(response.data["data"]["route_id"], route.id)

        listed = self.client.get(RUNS_URL).data["data"][0]
        self.assertEqual(listed["source"], "free")

    def test_free_route_can_be_saved_and_shared(self):
        route_id = self.client.post(RUNS_URL, {"path": straight_path(2000), "distance_m": 2000, "duration_s": 700}, format="json").data["data"]["route_id"]

        saved = self.client.post("/api/v1/routes/saved/", {"route_id": route_id}, format="json")
        shared = self.client.post("/api/v1/community/routes/", {"route_id": route_id, "title": "My river run"}, format="json")

        self.assertIn(saved.status_code, (200, 201))
        self.assertEqual(shared.status_code, 201)
        self.assertTrue(SharedRoute.objects.filter(route_id=route_id).exists())

    def test_needs_exactly_one_route_source(self):
        both = self.client.post(RUNS_URL, {"path": straight_path(500), "route_id": 1, "distance_m": 1, "duration_s": 1}, format="json")
        none = self.client.post(RUNS_URL, {"distance_m": 1, "duration_s": 1}, format="json")
        bad = self.client.post(RUNS_URL, {"path": [[127.0, 37.5]], "distance_m": 1, "duration_s": 1}, format="json")

        self.assertEqual(both.status_code, 400)
        self.assertEqual(none.status_code, 400)
        self.assertEqual(bad.status_code, 400)
