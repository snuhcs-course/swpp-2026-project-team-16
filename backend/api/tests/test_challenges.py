from datetime import date, datetime, timedelta, timezone as dt_timezone
from unittest import mock
from zoneinfo import ZoneInfo

from django.contrib.auth import get_user_model
from django.test import SimpleTestCase
from rest_framework.authtoken.models import Token
from rest_framework.test import APITestCase

from api.models import Route, RouteRun, SharedRoute
from api.services.challenges import streaks, validate_goals, week_start


LINE = [{"type": "LineString", "coordinates": [[127.0, 37.5], [127.01, 37.51]]}]
SEOUL = ZoneInfo("Asia/Seoul")
NOW = datetime(2026, 11, 11, 3, 0, tzinfo=dt_timezone.utc)


class HelperTests(SimpleTestCase):
    def test_weeks_start_on_sunday(self):
        self.assertEqual(week_start(date(2026, 11, 11)), date(2026, 11, 8))
        self.assertEqual(week_start(date(2026, 11, 8)), date(2026, 11, 8))

    def test_streaks(self):
        days = [date(2026, 11, d) for d in (1, 2, 3, 6, 9, 10)]
        self.assertEqual(streaks(days, date(2026, 11, 10)), (2, 3))
        self.assertEqual(streaks(days, date(2026, 11, 11)), (2, 3))
        self.assertEqual(streaks(days, date(2026, 11, 12)), (0, 3))
        self.assertEqual(streaks([], date(2026, 11, 12)), (0, 0))

    def test_validate_goals(self):
        self.assertEqual(validate_goals({"weekly_goal_m": 20000}), ({"weekly_goal_m": 20000}, None))
        for bad in ({"weekly_goal_m": -1}, {"monthly_goal_m": True}, {"weekly_goal_m": "5"}, []):
            self.assertIsNotNone(validate_goals(bad)[1])


@mock.patch("api.views.challenges.timezone.now", return_value=NOW)
class ChallengeApiTests(APITestCase):
    def setUp(self):
        self.user = get_user_model().objects.create_user(username="runner", email="r@test.com", password="password123")
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {Token.objects.get_or_create(user=self.user)[0].key}")

    def run_at(self, local, meters, seconds, splits=(), source="generated"):
        route = Route.objects.create(created_by=self.user, distance=meters, briefing="b", route=LINE, source=source)
        run = RouteRun.objects.create(user=self.user, route=route, distance_m=meters, duration_s=seconds, splits=list(splits))
        RouteRun.objects.filter(id=run.id).update(finished_at=local.replace(tzinfo=SEOUL))
        return run

    def get(self, language="en"):
        return self.client.get("/api/v1/challenges/", {"tz": "Asia/Seoul", "language": language}).data["data"]

    def test_requires_login(self, _):
        self.client.credentials()
        self.assertEqual(self.client.get("/api/v1/challenges/").status_code, 401)

    def test_goals_progress_uses_the_local_week_and_month(self, _):
        self.client.put("/api/v1/challenges/goals/", {"weekly_goal_m": 10000, "monthly_goal_m": 40000}, format="json")
        self.run_at(datetime(2026, 11, 8, 6, 30), 5000, 1800)
        self.run_at(datetime(2026, 11, 7, 23, 30), 3000, 1000)
        self.run_at(datetime(2026, 10, 31, 8, 0), 4000, 1400)

        goals = self.get()["goals"]

        self.assertEqual((goals["weekly_goal_m"], goals["monthly_goal_m"]), (10000, 40000))
        self.assertEqual(goals["week_m"], 5000)
        self.assertEqual(goals["month_m"], 8000)
        self.assertEqual(goals["week_start"], "2026-11-08")

    def test_streak_and_personal_bests(self, _):
        self.run_at(datetime(2026, 11, 9, 7, 0), 5200, 1800, splits=[350, 340, 345, 352, 348])
        self.run_at(datetime(2026, 11, 10, 7, 0), 10100, 3700, splits=[360] * 10)
        self.run_at(datetime(2026, 11, 11, 7, 0), 3000, 900, splits=[300, 300, 300])

        data = self.get()

        self.assertEqual(data["streak"], {"current": 3, "best": 3})
        bests = data["personal_bests"]
        self.assertEqual(bests["longest_run_m"], {"value": 10100, "date": "2026-11-10"})
        self.assertEqual(bests["fastest_pace_s"]["value"], 300)
        self.assertEqual(bests["fastest_1k_s"]["value"], 300)
        self.assertEqual(bests["fastest_5k_s"], {"value": 1735, "date": "2026-11-09"})
        self.assertEqual(bests["best_week_m"]["value"], 18300)

    def test_badges_are_earned_with_dates_and_progress(self, _):
        self.client.put("/api/v1/challenges/goals/", {"weekly_goal_m": 6000}, format="json")
        first = self.run_at(datetime(2026, 11, 3, 6, 0), 5000, 1700)
        self.run_at(datetime(2026, 11, 4, 22, 0), 2000, 700, source="free")
        SharedRoute.objects.create(route=first.route, shared_by=self.user, title="Morning")

        badges = {badge["id"]: badge for badge in self.get("ko")["badges"]}

        self.assertEqual(badges["first_run"]["earned_at"], "2026-11-03")
        self.assertEqual(badges["first_run"]["title"], "첫 러닝")
        self.assertTrue(badges["single_5k"]["earned"])
        self.assertFalse(badges["single_10k"]["earned"])
        self.assertEqual((badges["total_10k"]["progress"], badges["total_10k"]["target"]), (7000, 10000))
        self.assertTrue(badges["early_bird"]["earned"])
        self.assertTrue(badges["night_owl"]["earned"])
        self.assertTrue(badges["free_runner"]["earned"])
        self.assertTrue(badges["sharer"]["earned"])
        self.assertEqual(badges["goal_crusher"]["earned_at"], "2026-11-04")
        self.assertEqual(badges["streak_3"]["progress"], 2)

    def test_empty_history(self, _):
        data = self.get()

        self.assertEqual(data["personal_bests"], {})
        self.assertEqual(data["totals"], {"runs": 0, "distance_m": 0})
        self.assertTrue(all(not badge["earned"] for badge in data["badges"]))

    def test_bad_goals_are_rejected(self, _):
        response = self.client.put("/api/v1/challenges/goals/", {"weekly_goal_m": -5}, format="json")
        self.assertEqual(response.status_code, 400)
