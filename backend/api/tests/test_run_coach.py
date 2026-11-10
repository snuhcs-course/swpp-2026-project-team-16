import json
from unittest import mock

from django.contrib.auth import get_user_model
from django.test import SimpleTestCase
from rest_framework.authtoken.models import Token
from rest_framework.test import APITestCase

from api.models import Route, RouteRun
from api.route_algorithm.llm import LLMError
from api.services.run_coach import (
    RunningStyle,
    build_goal_prompt,
    fallback_comment,
    generate_comment,
    pace_range,
    plan_from_goal,
    recommend_next_run,
    report_facts,
    split_paces,
    validate_plan,
    validate_running_style,
)


LINE = [{"type": "LineString", "coordinates": [[127.0, 37.0], [127.01, 37.01]]}]
RECOVERY_PLAN = {
    "run_type": "recovery",
    "distance_m": 5000,
    "pace_min_s": 375,
    "pace_max_s": 405,
    "intensity": "",
    "route_preferences": ["flat"],
    "summary": "",
}


def failing_llm(prompt):
    raise LLMError("down")


class RunningStyleTests(SimpleTestCase):
    def test_accepts_a_full_style(self):
        style, error = validate_running_style({
            "purpose": "fitness",
            "run_types": ["easy", "recovery", "easy"],
            "comfortable_pace_s": 375,
            "weekly_runs": 3,
            "note": "  no hills ",
        })
        self.assertIsNone(error)
        self.assertEqual(style.run_types, ["easy", "recovery"])
        self.assertEqual(style.note, "no hills")

    def test_rejects_invalid_values(self):
        for data in [
            {"purpose": "fun"},
            {"run_types": ["jog"]},
            {"comfortable_pace_s": 60},
            {"comfortable_pace_s": True},
            {"weekly_runs": 9},
            {"note": "x" * 201},
            [],
        ]:
            self.assertIsNotNone(validate_running_style(data)[1], data)


class PlanTests(SimpleTestCase):
    def test_pace_range_comes_from_the_comfortable_pace(self):
        style = RunningStyle(comfortable_pace_s=375)
        self.assertEqual(pace_range("recovery", style), (395, 425))
        self.assertEqual(pace_range("tempo", style), (315, 345))
        self.assertEqual(pace_range("easy", RunningStyle()), (380, 410))

    def test_an_explicit_pace_wins(self):
        self.assertEqual(pace_range("easy", RunningStyle(comfortable_pace_s=375), 360), (350, 370))

    def test_llm_reply_becomes_a_plan_with_computed_pace(self):
        reply = json.dumps({
            "run_type": "recovery",
            "distance_km": 5,
            "explicit_pace_s": None,
            "route_preferences": [],
            "intensity": "대화 가능한 정도",
            "summary": "어제 인터벌을 해서 회복 러닝으로 잡았어요.",
        })
        plan = plan_from_goal("어제 인터벌 해서 다리가 무거워요. 천천히 5km", RunningStyle(comfortable_pace_s=375), llm=lambda prompt: reply)
        self.assertEqual(plan["run_type"], "recovery")
        self.assertEqual(plan["distance_m"], 5000)
        self.assertEqual((plan["pace_min_s"], plan["pace_max_s"]), (395, 425))
        self.assertEqual(plan["route_preferences"], ["flat"])
        self.assertEqual(plan["intensity"], "대화 가능한 정도")

    def test_falls_back_to_keywords_when_the_llm_fails(self):
        plan = plan_from_goal("오늘은 템포로 7km 달릴래요", RunningStyle(), llm=failing_llm)
        self.assertEqual(plan["run_type"], "tempo")
        self.assertEqual(plan["distance_m"], 7000)

    def test_falls_back_when_the_llm_returns_an_unknown_type(self):
        plan = plan_from_goal("천천히 뛸래요", RunningStyle(), 3000, llm=lambda prompt: '{"run_type": "sprint"}')
        self.assertEqual(plan["run_type"], "recovery")
        self.assertEqual(plan["distance_m"], 3000)

    def test_prompt_includes_the_profile_and_request(self):
        prompt = build_goal_prompt("천천히 5km", RunningStyle(comfortable_pace_s=375, note="언덕 싫어요"), "ko")
        self.assertIn("6:15/km", prompt["user"])
        self.assertIn("언덕 싫어요", prompt["user"])
        self.assertIn("Korean", prompt["system"])

    def test_validate_plan(self):
        self.assertEqual(validate_plan(None), (None, None))
        self.assertEqual(validate_plan(RECOVERY_PLAN)[0]["run_type"], "recovery")
        self.assertIsNotNone(validate_plan({**RECOVERY_PLAN, "run_type": "sprint"})[1])
        self.assertIsNotNone(validate_plan({**RECOVERY_PLAN, "pace_min_s": 500})[1])


class ReportTests(SimpleTestCase):
    def test_partial_last_split_is_normalized(self):
        rows = split_paces([360, 370], 2500, 360 + 370 + 200)
        self.assertEqual([row["pace_s"] for row in rows], [360, 370, 400])
        self.assertEqual(rows[-1]["distance_m"], 500)

    def test_short_leftover_is_dropped(self):
        self.assertEqual(len(split_paces([360], 1100, 400)), 1)

    def test_compares_each_split_with_the_plan(self):
        facts = report_facts(RECOVERY_PLAN, [392, 381, 366, 358, 371], 5000, 1868, 1, 40)
        self.assertEqual([row["status"] for row in facts["splits"]], ["in_range", "in_range", "fast", "fast", "fast"])
        self.assertEqual([row["diff_s"] for row in facts["splits"]], [0, 0, 9, 17, 4])
        self.assertEqual(facts["in_range_count"], 2)
        self.assertEqual(facts["average_pace_s"], 374)

    def test_comment_falls_back_with_real_numbers(self):
        facts = report_facts(RECOVERY_PLAN, [392, 381, 366, 358, 371], 5000, 1868)
        self.assertEqual(generate_comment(RECOVERY_PLAN, facts, "ko", llm=failing_llm), fallback_comment(RECOVERY_PLAN, facts, "ko"))
        self.assertIn("5개 구간 중 2개", fallback_comment(RECOVERY_PLAN, facts, "ko"))

    def test_too_long_comment_falls_back(self):
        facts = report_facts(None, [], 3000, 1200)
        comment = generate_comment(None, facts, "en", llm=lambda prompt: "x" * 400)
        self.assertIn("3.00 km", comment)


class RecommendationTests(SimpleTestCase):
    def test_uses_the_llm_choice_and_computes_the_pace(self):
        reply = '{"run_type": "easy", "distance_km": 6, "rest_days": 2, "reason": "이틀 쉬고 이지런"}'
        facts = report_facts(RECOVERY_PLAN, [392, 381, 366, 358, 371], 5000, 1868)
        result = recommend_next_run(RunningStyle(comfortable_pace_s=375), RECOVERY_PLAN, facts, [], "ko", llm=lambda prompt: reply)
        self.assertEqual(result["rest_days"], 2)
        self.assertEqual(result["plan"]["run_type"], "easy")
        self.assertEqual(result["plan"]["distance_m"], 6000)
        self.assertEqual((result["plan"]["pace_min_s"], result["plan"]["pace_max_s"]), (365, 395))

    def test_falls_back_to_an_easier_run_after_a_hard_one(self):
        tempo = {**RECOVERY_PLAN, "run_type": "tempo", "pace_min_s": 300, "pace_max_s": 330}
        facts = report_facts(tempo, [310, 320, 315], 3000, 945)
        result = recommend_next_run(RunningStyle(), tempo, facts, [], "en", llm=failing_llm)
        self.assertEqual(result["plan"]["run_type"], "recovery")
        self.assertLessEqual(result["plan"]["distance_m"], 5000)

    def test_rejects_an_out_of_range_llm_reply(self):
        reply = '{"run_type": "easy", "distance_km": 100, "rest_days": 1, "reason": "go"}'
        facts = report_facts(RECOVERY_PLAN, [392, 381, 366, 358, 371], 5000, 1868)
        result = recommend_next_run(RunningStyle(), RECOVERY_PLAN, facts, [], "ko", llm=lambda prompt: reply)
        self.assertEqual(result["plan"]["distance_m"], 6000)


class RunCoachApiTests(APITestCase):
    def setUp(self):
        self.user = get_user_model().objects.create_user(username="runner", email="r@test.com", password="password123")
        self.route = Route.objects.create(created_by=self.user, distance=5000, briefing="Flat", route=LINE)

    def log_in(self):
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {Token.objects.get_or_create(user=self.user)[0].key}")

    def test_running_style_round_trip(self):
        self.assertEqual(self.client.get("/api/v1/users/my_profile/running_style/").status_code, 401)
        self.log_in()
        response = self.client.put(
            "/api/v1/users/my_profile/running_style/",
            {"purpose": "fitness", "run_types": ["easy"], "comfortable_pace_s": 375, "weekly_runs": 3, "note": ""},
            format="json",
        )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(self.client.get("/api/v1/users/my_profile/running_style/").data["data"]["comfortable_pace_s"], 375)
        bad = self.client.put("/api/v1/users/my_profile/running_style/", {"weekly_runs": 0}, format="json")
        self.assertEqual(bad.status_code, 400)

    @mock.patch("api.services.run_coach.generate_text", side_effect=LLMError("down"))
    def test_plan_works_for_guests(self, _):
        response = self.client.post("/api/v1/runs/plan/", {"text": "천천히 4km", "distance_m": 5000}, format="json")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["data"]["run_type"], "recovery")
        self.assertEqual(response.data["data"]["distance_m"], 4000)
        self.assertEqual(self.client.post("/api/v1/runs/plan/", {"text": " "}, format="json").status_code, 400)

    def test_run_is_recorded_with_plan_and_splits(self):
        self.log_in()
        response = self.client.post("/api/v1/runs/", {
            "route_id": self.route.id,
            "distance_m": 5000,
            "duration_s": 1868,
            "plan": RECOVERY_PLAN,
            "splits": [392, 381, 366, 358, 371],
            "off_route_count": 1,
            "off_route_seconds": 40,
        }, format="json")
        self.assertEqual(response.status_code, 201)
        run = RouteRun.objects.get()
        self.assertEqual(run.plan["run_type"], "recovery")
        self.assertEqual(run.splits, [392, 381, 366, 358, 371])
        self.assertEqual(run.off_route_count, 1)

    def test_rejects_invalid_splits(self):
        self.log_in()
        response = self.client.post("/api/v1/runs/", {
            "route_id": self.route.id, "distance_m": 5000, "duration_s": 1868, "splits": [300, -1],
        }, format="json")
        self.assertEqual(response.status_code, 400)

    @mock.patch("api.services.run_coach.generate_text", side_effect=LLMError("down"))
    def test_report_is_generated_once_and_cached(self, generate):
        run = RouteRun.objects.create(
            user=self.user, route=self.route, distance_m=5000, duration_s=1868,
            plan=RECOVERY_PLAN, splits=[392, 381, 366, 358, 371],
        )
        self.log_in()
        first = self.client.get(f"/api/v1/runs/{run.id}/report/?language=ko")
        calls = generate.call_count
        second = self.client.get(f"/api/v1/runs/{run.id}/report/?language=ko")
        self.assertEqual(first.status_code, 200)
        self.assertEqual(first.data["data"]["in_range_count"], 2)
        self.assertIn("comment", first.data["data"])
        self.assertEqual(first.data["data"]["recommendation"]["plan"]["run_type"], "easy")
        self.assertEqual(generate.call_count, calls)
        self.assertEqual(second.data["data"], first.data["data"])

    def test_report_of_someone_elses_run_is_hidden(self):
        other = get_user_model().objects.create_user(username="other", email="o@test.com", password="password123")
        run = RouteRun.objects.create(user=other, route=self.route, distance_m=5000, duration_s=1800)
        self.log_in()
        self.assertEqual(self.client.get(f"/api/v1/runs/{run.id}/report/").status_code, 404)
