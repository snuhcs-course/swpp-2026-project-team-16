from django.contrib.auth import get_user_model
from rest_framework.authtoken.models import Token
from rest_framework.test import APITestCase

from api.models import Route, RouteReview, RouteRun, SharedRoute, TemporaryRoute, UserSavedRoute


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

    def test_can_run_a_route_again_after_it_is_unshared(self):
        route = self.route(created_by=self.other)
        shared = SharedRoute.objects.create(route=route, shared_by=self.other, title="Loop")
        self.log_in(self.runner)
        self.assertEqual(self.record_run(route_id=route.id).status_code, 201)
        shared.delete()

        response = self.record_run(route_id=route.id)

        self.assertEqual(response.status_code, 201)
        self.assertEqual(RouteRun.objects.filter(user=self.runner, route=route).count(), 2)

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


class RunHistoryTests(CommunityTestBase):
    def test_requires_authentication(self):
        self.assertEqual(self.client.get(RUNS_URL).status_code, 401)

    def test_lists_only_my_runs_newest_first(self):
        mine = self.route(created_by=self.runner)
        shared_route = self.route(created_by=self.other)
        shared = SharedRoute.objects.create(route=shared_route, shared_by=self.other, title="Loop")
        UserSavedRoute.objects.create(user=self.runner, route=mine)
        self.log_in(self.other)
        self.record_run(route_id=shared_route.id)
        self.log_in(self.runner)
        self.record_run(route_id=mine.id, distance_m=2900, duration_s=1100)
        self.record_run(route_id=shared_route.id)

        response = self.client.get(RUNS_URL)

        self.assertEqual(response.status_code, 200)
        runs = response.data["data"]
        self.assertEqual([run["route_id"] for run in runs], [shared_route.id, mine.id])
        self.assertEqual(runs[0]["shared_route_id"], shared.id)
        self.assertEqual(runs[0]["shared_title"], "Loop")
        self.assertFalse(runs[0]["is_saved"])
        self.assertEqual(runs[1]["distance_m"], 2900)
        self.assertEqual(runs[1]["duration_s"], 1100)
        self.assertEqual(runs[1]["route_distance"], 3000)
        self.assertEqual(runs[1]["start_name"], "Start")
        self.assertIsNone(runs[1]["shared_route_id"])
        self.assertTrue(runs[1]["is_saved"])
        self.assertEqual(runs[1]["route"], LINE)

    def test_empty_history(self):
        self.log_in(self.runner)

        self.assertEqual(self.client.get(RUNS_URL).data["data"], [])


class SharedRouteTests(CommunityTestBase):
    def setUp(self):
        super().setUp()
        self.my_route = self.route(created_by=self.runner)

    def test_cannot_share_someone_elses_route_without_running_or_saving(self):
        self.log_in(self.other)

        response = self.share(self.my_route)

        self.assertEqual(response.status_code, 403)
        self.assertFalse(SharedRoute.objects.exists())

    def test_can_share_my_route_before_running(self):
        self.log_in(self.runner)

        response = self.share(self.my_route)

        self.assertEqual(response.status_code, 201)
        self.assertEqual(response.data["data"]["run_count"], 0)

    def test_can_share_a_saved_route(self):
        UserSavedRoute.objects.create(user=self.other, route=self.my_route)
        self.log_in(self.other)

        self.assertEqual(self.share(self.my_route).status_code, 201)

    def test_share_a_generated_route_reuses_one_route(self):
        temporary = TemporaryRoute.objects.create(user=None, distance=3000, briefing="Flat", route=LINE)
        self.log_in(self.runner)

        response = self.client.post(
            SHARED_URL, {"temporary_route_id": temporary.id, "title": "Fresh loop"}, format="json"
        )
        run = self.record_run(temporary_route_id=temporary.id)

        self.assertEqual(response.status_code, 201)
        route_id = response.data["data"]["route_id"]
        self.assertEqual(run.data["data"]["route_id"], route_id)
        self.assertEqual(run.data["data"]["shared_route_id"], response.data["data"]["id"])
        self.assertEqual(Route.objects.filter(created_by=self.runner).count(), 2)

    def test_share_requires_exactly_one_route_reference(self):
        temporary = TemporaryRoute.objects.create(user=None, distance=3000, briefing="Flat", route=LINE)
        self.log_in(self.runner)

        for body in ({"title": "Loop"}, {"title": "Loop", "route_id": self.my_route.id, "temporary_route_id": temporary.id}):
            with self.subTest(body=body):
                self.assertEqual(self.client.post(SHARED_URL, body, format="json").status_code, 400)
        missing = self.client.post(SHARED_URL, {"title": "Loop", "temporary_route_id": 9999}, format="json")
        self.assertEqual(missing.status_code, 404)

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


class ReviewTests(CommunityTestBase):
    def setUp(self):
        super().setUp()
        User = get_user_model()
        self.third = User.objects.create_user(username="third", email="third@test.com", password="password123")
        self.shared = SharedRoute.objects.create(route=self.route(created_by=self.other), shared_by=self.other, title="Loop")
        self.reviews_url = f"{SHARED_URL}{self.shared.id}/reviews/"

    def review(self, rating=5, comment="Great"):
        return self.client.post(self.reviews_url, {"rating": rating, "comment": comment}, format="json")

    def ran(self, user):
        RouteRun.objects.create(user=user, route=self.shared.route, distance_m=3000, duration_s=1000)

    def test_requires_authentication(self):
        self.assertEqual(self.review().status_code, 401)

    def test_must_run_or_save_before_reviewing(self):
        self.log_in(self.runner)
        self.assertEqual(self.review().status_code, 403)

        UserSavedRoute.objects.create(user=self.runner, route=self.shared.route)
        self.assertEqual(self.review().status_code, 201)

    def test_sharer_cannot_review_own_route(self):
        self.ran(self.other)
        self.log_in(self.other)

        self.assertEqual(self.review().status_code, 403)

    def test_one_review_per_person(self):
        self.ran(self.runner)
        self.log_in(self.runner)

        first = self.review(comment="  Nice and flat  ")

        self.assertEqual(first.status_code, 201)
        self.assertEqual((first.data["data"]["comment"], first.data["data"]["author"]), ("Nice and flat", "runner"))
        self.assertEqual(self.review().status_code, 409)

    def test_rejects_bad_rating_or_comment(self):
        self.ran(self.runner)
        self.log_in(self.runner)
        cases = [(0, ""), (6, ""), ("5", ""), (True, ""), (4.5, ""), (5, "x" * 501)]
        for rating, comment in cases:
            with self.subTest(rating=rating, comment=comment[:5]):
                self.assertEqual(self.review(rating, comment).status_code, 400)

    def test_summary_shows_average_counts_and_permissions(self):
        self.ran(self.runner)
        self.ran(self.third)
        RouteReview.objects.create(shared_route=self.shared, user=self.runner, rating=5)
        RouteReview.objects.create(shared_route=self.shared, user=self.third, rating=4)
        self.log_in(self.runner)

        listed = self.client.get(SHARED_URL).data["data"][0]
        detail = self.client.get(f"{SHARED_URL}{self.shared.id}/").data["data"]

        self.assertEqual((listed["review_count"], listed["rating_average"], listed["run_count"]), (2, 4.5, 2))
        self.assertEqual(detail["rating_counts"], {"1": 0, "2": 0, "3": 0, "4": 1, "5": 1})
        self.assertTrue(detail["can_review"])
        self.assertIsNotNone(detail["my_review_id"])

    def test_route_without_reviews_has_no_average(self):
        listed = self.client.get(SHARED_URL).data["data"][0]
        detail = self.client.get(f"{SHARED_URL}{self.shared.id}/").data["data"]

        self.assertEqual((listed["review_count"], listed["rating_average"]), (0, None))
        self.assertFalse(detail["can_review"])
        self.assertIsNone(detail["my_review_id"])

    def test_sort_by_rating_and_review_count(self):
        second = SharedRoute.objects.create(route=self.route(created_by=self.other), shared_by=self.other, title="Hills")
        RouteReview.objects.create(shared_route=self.shared, user=self.runner, rating=3)
        RouteReview.objects.create(shared_route=self.shared, user=self.third, rating=3)
        RouteReview.objects.create(shared_route=second, user=self.runner, rating=5)

        def titles(sort):
            return [item["title"] for item in self.client.get(SHARED_URL, {"sort": sort}).data["data"]]

        self.assertEqual(titles("rating"), ["Hills", "Loop"])
        self.assertEqual(titles("reviews"), ["Loop", "Hills"])
        self.assertEqual(titles("latest"), ["Hills", "Loop"])
        self.assertEqual(self.client.get(SHARED_URL, {"sort": "random"}).status_code, 400)

    def test_list_reviews_is_public(self):
        RouteReview.objects.create(shared_route=self.shared, user=self.runner, rating=4, comment="Good")

        response = self.client.get(self.reviews_url)

        self.assertEqual(response.status_code, 200)
        review = response.data["data"][0]
        self.assertEqual((review["rating"], review["comment"], review["author"]), (4, "Good", "runner"))
        self.assertFalse(review["is_mine"])
        self.assertFalse(review["edited"])

    def test_only_the_author_can_edit_or_delete(self):
        review = RouteReview.objects.create(shared_route=self.shared, user=self.runner, rating=4)
        url = f"/api/v1/community/reviews/{review.id}/"

        self.log_in(self.third)
        self.assertEqual(self.client.patch(url, {"rating": 1}, format="json").status_code, 404)
        self.assertEqual(self.client.delete(url).status_code, 404)

        self.log_in(self.runner)
        updated = self.client.patch(url, {"rating": 2, "comment": "Changed my mind"}, format="json")
        self.assertEqual((updated.data["data"]["rating"], updated.data["data"]["comment"]), (2, "Changed my mind"))
        self.assertEqual(self.client.delete(url).status_code, 200)
        self.assertFalse(RouteReview.objects.exists())
