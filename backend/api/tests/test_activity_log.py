from unittest import mock

from django.contrib.auth import get_user_model
from rest_framework.authtoken.models import Token
from rest_framework.test import APITestCase

from api.models import ActivityLog, Post
from api.tests.test_free_run import straight_path


class FakeRoute:
    distance = 2070
    briefing = "Briefing"

    def to_dict(self):
        line = {"type": "LineString", "coordinates": [[126.978, 37.5755], [126.978, 37.5665]]}
        return {"distance": self.distance, "briefing": self.briefing, "route": [line]}


def actions(user=None):
    logs = ActivityLog.objects.order_by("id")
    if user is not None:
        logs = logs.filter(user=user)
    return list(logs.values_list("action", flat=True))


class ActivityLogTests(APITestCase):
    def setUp(self):
        self.user = get_user_model().objects.create_user(username="runner", password="pass-1234")
        self.token = Token.objects.create(user=self.user)

    def log_in(self):
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {self.token.key}")

    def test_sign_up_and_log_in_are_logged(self):
        self.client.post(
            "/api/v1/auth/register/",
            {"username": "new", "email": "new@example.com", "password": "pass-1234"},
            format="json",
        )
        self.client.post("/api/v1/auth/login/", {"username": "new", "password": "pass-1234"}, format="json")

        new_user = get_user_model().objects.get(username="new")
        self.assertEqual(actions(new_user), ["signed_up", "logged_in"])

    @mock.patch("api.views.generate_route.create_routes", return_value=[FakeRoute()])
    def test_route_generation_records_where_and_how_far(self, create_routes):
        self.log_in()

        self.client.post(
            "/api/v1/routes/generate/",
            {
                "starting_point": {"type": "Point", "coordinates": [126.978, 37.5755]},
                "end_point": {"type": "Point", "coordinates": [126.978, 37.5665]},
                "distance": 2000,
                "starting_point_name": "City Hall",
                "language": "en",
            },
            format="json",
        )

        log = ActivityLog.objects.get(action="route_generated")
        self.assertEqual(log.user, self.user)
        self.assertEqual(log.details["start"], [126.978, 37.5755])
        self.assertEqual(log.details["end"], [126.978, 37.5665])
        self.assertEqual(log.details["start_name"], "City Hall")
        self.assertEqual(log.details["requested_distance_m"], 2000)
        self.assertEqual(log.details["options"][0]["distance_m"], 2070)

    @mock.patch("api.views.generate_route.create_routes", return_value=[FakeRoute()])
    def test_guest_route_generation_is_logged_without_a_user(self, create_routes):
        self.client.post(
            "/api/v1/routes/generate/",
            {"starting_point": {"type": "Point", "coordinates": [126.978, 37.5755]}, "distance": 2000},
            format="json",
        )

        log = ActivityLog.objects.get(action="route_generated")
        self.assertIsNone(log.user)
        self.assertIsNone(log.details["end"])

    def test_run_records_distance_time_and_speed(self):
        self.log_in()

        self.client.post(
            "/api/v1/runs/",
            {"path": straight_path(1000), "distance_m": 1000, "duration_s": 300},
            format="json",
        )

        log = ActivityLog.objects.get(action="run_recorded")
        self.assertEqual(log.details["distance_m"], 1000)
        self.assertEqual(log.details["duration_s"], 300)
        self.assertEqual(log.details["average_pace_s_per_km"], 300)
        self.assertEqual(log.details["average_speed_kmh"], 12.0)
        self.assertTrue(log.details["free_run"])
        self.assertAlmostEqual(log.details["start"][1], 37.5 + 200 / 111_320, places=4)

    def test_posts_comments_and_follows_are_logged(self):
        other = get_user_model().objects.create_user(username="friend", password="pass-1234")
        self.log_in()

        self.client.post("/api/v1/board/posts/", {"title": "Knee pain", "body": "Any tips?"}, format="json")
        post = Post.objects.get()
        self.client.post(f"/api/v1/board/posts/{post.id}/comments/", {"body": "Rest a week"}, format="json")
        self.client.post(f"/api/v1/users/{other.username}/follow/")
        self.client.delete(f"/api/v1/users/{other.username}/follow/")

        self.assertEqual(actions(self.user), ["post_created", "comment_created", "followed", "unfollowed"])
        self.assertEqual(ActivityLog.objects.get(action="post_created").details["body"], "Any tips?")
        self.assertEqual(ActivityLog.objects.get(action="comment_created").details["body"], "Rest a week")
        self.assertEqual(ActivityLog.objects.get(action="followed").details["username"], "friend")

    def test_failed_requests_are_not_logged(self):
        self.log_in()

        self.client.post("/api/v1/board/posts/", {"title": "", "body": ""}, format="json")

        self.assertEqual(actions(), [])

    def test_logs_are_deleted_with_the_account(self):
        ActivityLog.objects.create(user=self.user, action="logged_in")

        self.user.delete()

        self.assertFalse(ActivityLog.objects.exists())


class ActivityDashboardTests(APITestCase):
    def test_requires_staff(self):
        user = get_user_model().objects.create_user(username="runner", password="pass-1234")
        self.client.force_login(user)

        response = self.client.get("/activity/")

        self.assertIn(response.status_code, (302, 403))

    def test_shows_a_users_timeline(self):
        staff = get_user_model().objects.create_user(username="admin", password="pass-1234", is_staff=True)
        runner = get_user_model().objects.create_user(username="runner", password="pass-1234")
        other = get_user_model().objects.create_user(username="other", password="pass-1234")
        ActivityLog.objects.create(
            user=runner,
            action="run_recorded",
            details={"distance_m": 1020, "duration_s": 316, "average_pace_s_per_km": 310, "average_speed_kmh": 11.62, "free_run": True},
        )
        ActivityLog.objects.create(user=other, action="post_created", details={"title": "Hidden title"})
        self.client.force_login(staff)

        response = self.client.get("/activity/?user=runner")

        self.assertEqual(response.status_code, 200)
        self.assertContains(response, "1.02 km in 5:16 · 5:10 /km · 11.62 km/h · free run")
        self.assertNotContains(response, "Hidden title")
