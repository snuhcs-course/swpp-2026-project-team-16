from django.contrib.auth import get_user_model
from rest_framework.authtoken.models import Token
from rest_framework.test import APITestCase

from api.models import Route, RouteRun, SharedRoute, TemporaryRoute, UserSavedRoute


RUNS_URL = "/api/v1/runs/"
SHARED_URL = "/api/v1/community/routes/"
SAVED_URL = "/api/v1/routes/saved/"
LINE = [{"type": "LineString", "coordinates": [[127.0, 37.0], [127.01, 37.01]]}]


class CommunityTestBase(APITestCase):
    def setUp(self):
        User = get_user_model()
        self.runner = User.objects.create_user(username="runner", email="runner@test.com", password="password123")
        self.other = User.objects.create_user(username="other", email="other@test.com", password="password123")

    def log_in(self, user):
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {Token.objects.get_or_create(user=user)[0].key}")

    def route(self, created_by=None):
        return Route.objects.create(
            created_by=created_by, distance=3000, briefing="Flat route", route=LINE, start_name="Start", end_name=""
        )

    def record_run(self, **body):
        return self.client.post(RUNS_URL, {"distance_m": 3010, "duration_s": 1200, **body}, format="json")

    def share(self, route, title="Morning loop", description="Nice and flat"):
        return self.client.post(
            SHARED_URL, {"route_id": route.id, "title": title, "description": description}, format="json"
        )


class RouteRunTests(CommunityTestBase):
    def test_requires_authentication(self):
        self.assertEqual(self.record_run(route_id=1).status_code, 401)

    def test_running_a_generated_route_creates_one_route_and_reuses_it(self):
        temporary = TemporaryRoute.objects.create(user=None, distance=3000, briefing="Flat", route=LINE)
        self.log_in(self.runner)

        first = self.record_run(temporary_route_id=temporary.id)
        second = self.record_run(temporary_route_id=temporary.id)
        saved = self.client.post(SAVED_URL, {"temporary_route_id": temporary.id}, format="json")

        self.assertEqual(first.status_code, 201)
        route_id = first.data["data"]["route_id"]
        self.assertEqual(second.data["data"]["route_id"], route_id)
        self.assertEqual(saved.data["route_id"], route_id)
        self.assertEqual(Route.objects.count(), 1)
        self.assertEqual(RouteRun.objects.filter(user=self.runner, route_id=route_id).count(), 2)
        self.assertTrue(UserSavedRoute.objects.filter(user=self.runner, route_id=route_id).exists())

    def test_running_a_shared_route_reports_the_shared_route(self):
        route = self.route(created_by=self.other)
        shared = SharedRoute.objects.create(route=route, shared_by=self.other, title="Loop")
        self.log_in(self.runner)

        response = self.record_run(route_id=route.id)

        self.assertEqual(response.status_code, 201)
        self.assertEqual(response.data["data"]["shared_route_id"], shared.id)

    def test_cannot_run_someone_elses_private_route(self):
        route = self.route(created_by=self.other)
        self.log_in(self.runner)

        self.assertEqual(self.record_run(route_id=route.id).status_code, 404)

    def test_rejects_invalid_input(self):
        route = self.route(created_by=self.runner)
        self.log_in(self.runner)
        cases = [
            {"route_id": route.id, "temporary_route_id": 1},
            {"route_id": route.id, "distance_m": -1},
            {"route_id": route.id, "duration_s": "abc"},
            {"route_id": route.id, "distance_m": True},
        ]
        for body in cases:
            with self.subTest(body=body):
                self.assertEqual(self.record_run(**body).status_code, 400)


class SharedRouteTests(CommunityTestBase):
    def setUp(self):
        super().setUp()
        self.my_route = self.route(created_by=self.runner)

    def test_must_run_before_sharing(self):
        self.log_in(self.runner)

        response = self.share(self.my_route)

        self.assertEqual(response.status_code, 403)
        self.assertFalse(SharedRoute.objects.exists())

    def test_share_after_running(self):
        self.log_in(self.runner)
        self.record_run(route_id=self.my_route.id)

        response = self.share(self.my_route, title="  Morning loop  ")

        self.assertEqual(response.status_code, 201)
        data = response.data["data"]
        self.assertEqual((data["title"], data["shared_by"], data["run_count"]), ("Morning loop", "runner", 1))
        self.assertTrue(data["is_mine"])
        self.assertEqual(data["route"], LINE)

    def test_route_can_only_be_shared_once(self):
        self.log_in(self.runner)
        self.record_run(route_id=self.my_route.id)
        self.share(self.my_route)

        self.assertEqual(self.share(self.my_route).status_code, 409)

    def test_rejects_bad_title_or_description(self):
        self.log_in(self.runner)
        self.record_run(route_id=self.my_route.id)
        cases = [("  ", ""), ("x" * 61, ""), ("Loop", "x" * 301)]
        for title, description in cases:
            with self.subTest(title=title[:5], description=description[:5]):
                self.assertEqual(self.share(self.my_route, title, description).status_code, 400)

    def test_list_and_detail_are_public(self):
        shared = SharedRoute.objects.create(route=self.my_route, shared_by=self.runner, title="Loop")
        RouteRun.objects.create(user=self.other, route=self.my_route, distance_m=3000, duration_s=1000)

        listed = self.client.get(SHARED_URL)
        detail = self.client.get(f"{SHARED_URL}{shared.id}/")

        self.assertEqual(listed.status_code, 200)
        self.assertEqual(listed.data["data"][0]["run_count"], 1)
        self.assertNotIn("route", listed.data["data"][0])
        self.assertFalse(listed.data["data"][0]["is_mine"])
        self.assertEqual(detail.data["data"]["route"], LINE)
        self.assertEqual(self.client.get(f"{SHARED_URL}999/").status_code, 404)

    def test_only_the_sharer_can_remove(self):
        shared = SharedRoute.objects.create(route=self.my_route, shared_by=self.runner, title="Loop")

        self.log_in(self.other)
        self.assertEqual(self.client.delete(f"{SHARED_URL}{shared.id}/").status_code, 403)

        self.log_in(self.runner)
        self.assertEqual(self.client.delete(f"{SHARED_URL}{shared.id}/").status_code, 200)
        self.assertFalse(SharedRoute.objects.exists())
        self.assertTrue(Route.objects.filter(id=self.my_route.id).exists())
