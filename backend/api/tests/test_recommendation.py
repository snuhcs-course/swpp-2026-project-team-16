from datetime import timedelta

from django.contrib.auth import get_user_model
from django.utils import timezone
from rest_framework.authtoken.models import Token
from rest_framework.test import APITestCase

from api.models import Route, RouteReview, RouteRun, SharedRoute


SHARED_URL = "/api/v1/community/routes/"
SEOUL = (37.5665, 126.9780)
BUSAN = (35.1796, 129.0756)


def line(lat, lng):
    return [{"type": "LineString", "coordinates": [[lng, lat], [lng + 0.01, lat + 0.01]]}]


class RecommendationTestBase(APITestCase):
    def setUp(self):
        User = get_user_model()
        self.me = User.objects.create_user(username="me", email="me@test.com", password="password123")
        self.author = User.objects.create_user(username="author", email="a@test.com", password="password123")
        self.others = [
            User.objects.create_user(username=f"r{i}", email=f"r{i}@test.com", password="password123") for i in range(4)
        ]

    def log_in(self):
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {Token.objects.get_or_create(user=self.me)[0].key}")

    def share(self, title, distance, place, user=None):
        route = Route.objects.create(created_by=user or self.author, distance=distance, briefing="b", route=line(*place))
        return SharedRoute.objects.create(route=route, shared_by=user or self.author, title=title)

    def titles(self, **params):
        return [item["title"] for item in self.client.get(SHARED_URL, {"sort": "recommended", **params}).data["data"]]


class RecommendationTests(RecommendationTestBase):

    def test_nearby_routes_come_first_for_a_guest_with_location(self):
        self.share("Busan loop", 5000, BUSAN)
        self.share("Seoul loop", 5000, SEOUL)

        data = self.client.get(SHARED_URL, {"sort": "recommended", "lat": SEOUL[0], "lng": SEOUL[1], "language": "en"}).data["data"]

        self.assertEqual([item["title"] for item in data], ["Seoul loop", "Busan loop"])
        self.assertLess(data[0]["distance_from_me_m"], 2000)
        self.assertIn("from you", data[0]["recommend_reason"])

    def test_well_rated_routes_win_without_location(self):
        good = self.share("Loved", 5000, SEOUL)
        bad = self.share("Disliked", 5000, SEOUL)
        for user in self.others:
            RouteReview.objects.create(shared_route=good, user=user, rating=5)
            RouteReview.objects.create(shared_route=bad, user=user, rating=1)

        self.assertEqual(self.titles(), ["Loved", "Disliked"])

    def test_post_run_ratings_teach_the_preferred_distance(self):
        self.log_in()
        short_route = Route.objects.create(created_by=self.me, distance=3000, briefing="b", route=line(*SEOUL))
        long_route = Route.objects.create(created_by=self.me, distance=15000, briefing="b", route=line(*SEOUL))
        loved = RouteRun.objects.create(user=self.me, route=short_route, distance_m=3000, duration_s=1000)
        hated = RouteRun.objects.create(user=self.me, route=long_route, distance_m=15000, duration_s=5000)
        self.client.patch(f"/api/v1/runs/{loved.id}/", {"rating": 5}, format="json")
        self.client.patch(f"/api/v1/runs/{hated.id}/", {"rating": 1}, format="json")
        self.share("Short and sweet", 3200, SEOUL)
        self.share("Half marathon", 15000, SEOUL)

        data = self.client.get(SHARED_URL, {"sort": "recommended", "language": "ko"}).data["data"]

        self.assertEqual([item["title"] for item in data], ["Short and sweet", "Half marathon"])

    def test_home_area_is_learned_from_past_runs(self):
        self.log_in()
        home_route = Route.objects.create(created_by=self.me, distance=5000, briefing="b", route=line(*BUSAN))
        RouteRun.objects.create(user=self.me, route=home_route, distance_m=5000, duration_s=1800)
        self.share("Seoul loop", 5000, SEOUL)
        self.share("Busan loop", 5000, BUSAN)

        data = self.client.get(SHARED_URL, {"sort": "recommended", "language": "ko"}).data["data"]

        self.assertEqual(data[0]["title"], "Busan loop")
        self.assertTrue(data[0]["recommend_reason"].startswith("자주 달리는 곳에서"))

    def test_routes_already_run_or_shared_by_me_drop_a_little(self):
        self.log_in()
        ran = self.share("Ran it", 5000, SEOUL)
        mine = self.share("Mine", 5000, SEOUL, user=self.me)
        fresh = self.share("Fresh", 5000, SEOUL)
        RouteRun.objects.create(user=self.me, route=ran.route, distance_m=5000, duration_s=1800)
        RouteRun.objects.create(user=self.others[0], route=fresh.route, distance_m=5000, duration_s=1800)
        RouteRun.objects.create(user=self.others[1], route=mine.route, distance_m=5000, duration_s=1800)

        self.assertEqual(self.titles(), ["Fresh", "Ran it", "Mine"])

    def test_rejects_bad_coordinates(self):
        self.assertEqual(self.client.get(SHARED_URL, {"sort": "recommended", "lat": "x", "lng": "1"}).status_code, 400)


class PopularSortTests(RecommendationTestBase):
    def test_recent_runs_and_reviews_make_a_route_popular(self):
        quiet = self.share("Quiet", 5000, SEOUL)
        busy = self.share("Busy", 5000, SEOUL)
        old = timezone.now() - timedelta(days=60)
        for user in self.others:
            RouteRun.objects.create(user=user, route=busy.route, distance_m=5000, duration_s=1800)
            run = RouteRun.objects.create(user=user, route=quiet.route, distance_m=5000, duration_s=1800)
            RouteRun.objects.filter(id=run.id).update(finished_at=old)

        titles = [item["title"] for item in self.client.get(SHARED_URL, {"sort": "popular"}).data["data"]]

        self.assertEqual(titles, ["Busy", "Quiet"])


class RunRatingTests(RecommendationTestBase):
    def test_only_the_runner_can_rate_with_one_to_five(self):
        route = Route.objects.create(created_by=self.me, distance=3000, briefing="b", route=line(*SEOUL))
        run = RouteRun.objects.create(user=self.me, route=route, distance_m=3000, duration_s=1000)
        url = f"/api/v1/runs/{run.id}/"

        self.assertEqual(self.client.patch(url, {"rating": 4}, format="json").status_code, 401)
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {Token.objects.get_or_create(user=self.author)[0].key}")
        self.assertEqual(self.client.patch(url, {"rating": 4}, format="json").status_code, 404)
        self.log_in()
        self.assertEqual(self.client.patch(url, {"rating": 6}, format="json").status_code, 400)
        self.assertEqual(self.client.patch(url, {"rating": True}, format="json").status_code, 400)
        self.assertEqual(self.client.patch(url, {"rating": 4}, format="json").data["data"]["rating"], 4)
