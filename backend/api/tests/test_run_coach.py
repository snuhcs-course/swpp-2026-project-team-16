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

    def test_prompt_does_not_infer_a_run_type_from_pace_or_distance(self):
        prompt = build_goal_prompt("3km at 6:00/km", RunningStyle(), "en")
        self.assertIn("Do not infer that a run is easy based only on distance or pace", prompt["system"])

    def test_pace_ranges_can_include_the_two_minute_slider_endpoint(self):
        plan, error = validate_plan({**RECOVERY_PLAN, "pace_min_s": 120, "pace_max_s": 600})
        self.assertIsNone(error)
        self.assertEqual((plan["pace_min_s"], plan["pace_max_s"]), (120, 600))

    def test_selected_pace_range_is_kept_and_explicit_sections_get_a_30_second_band(self):
        reply = json.dumps({
            "run_type": "tempo",
            "distance_km": 1,
            "explicit_pace_s": 360,
            "pace_segments": [
                {"start_km": 0, "end_km": 2, "pace_s": 360},
                {"start_km": 2, "end_km": 3, "pace_s": 330},
            ],
            "route_preferences": [],
            "intensity": "꾸준히",
            "summary": "마지막 1km에서 페이스를 올려요.",
        })
        plan = plan_from_goal(
            "6:00 페이스로 달리다가 마지막 1km는 5:30으로 올리고 싶어",
            RunningStyle(),
            3000,
            "ko",
            llm=lambda _: reply,
            pace_min_s=350,
            pace_max_s=390,
            alert_on_slow=True,
        )

        self.assertEqual(plan["distance_m"], 3000)
        self.assertEqual((plan["pace_min_s"], plan["pace_max_s"]), (350, 390))
        self.assertTrue(plan["alert_on_slow"])
        self.assertFalse(plan["alert_on_fast"])
        self.assertEqual(plan["pace_segments"], [
            {"start_m": 0, "end_m": 2000, "pace_min_s": 330, "pace_max_s": 390},
            {"start_m": 2000, "end_m": 3000, "pace_min_s": 300, "pace_max_s": 360},
        ])

    def test_selected_distance_is_total_and_overrides_section_distance_from_llm(self):
        prompt = build_goal_prompt("마지막 1km는 빠르게", RunningStyle(), "ko", 3000)
        self.assertIn("Selected total distance: 3 km", prompt["user"])
        self.assertIn("parts of this total", prompt["user"])

        reply = '{"run_type":"tempo","distance_km":1,"explicit_pace_s":null,"route_preferences":[],"intensity":"빠르게","summary":"마지막 구간을 빠르게 달려요."}'
        plan = plan_from_goal("마지막 1km는 빠르게", RunningStyle(), 3000, "ko", llm=lambda _: reply)
        self.assertEqual(plan["distance_m"], 3000)

    def test_validate_plan(self):
        self.assertEqual(validate_plan(None), (None, None))
        self.assertEqual(validate_plan(RECOVERY_PLAN)[0]["run_type"], "recovery")
        self.assertIsNotNone(validate_plan({**RECOVERY_PLAN, "run_type": "sprint"})[1])
        self.assertIsNotNone(validate_plan({**RECOVERY_PLAN, "pace_min_s": 500})[1])

    def test_validate_plan_keeps_alerts_and_segments_and_rejects_overlaps(self):
        plan = {
            **RECOVERY_PLAN,
            "alert_on_slow": True,
            "alert_on_fast": False,
            "pace_segments": [
                {"start_m": 0, "end_m": 2000, "pace_min_s": 330, "pace_max_s": 390},
                {"start_m": 2000, "end_m": 3000, "pace_min_s": 300, "pace_max_s": 360},
            ],
        }
        valid, error = validate_plan(plan)
        self.assertIsNone(error)
        self.assertTrue(valid["alert_on_slow"])
        self.assertEqual(valid["pace_segments"], plan["pace_segments"])
        self.assertIsNotNone(validate_plan({
            **plan,
            "pace_segments": [plan["pace_segments"][0], {**plan["pace_segments"][1], "start_m": 1500}],
        })[1])


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

    def test_custom_pace_sections_override_the_overall_range_for_report_splits(self):
        plan = {
            **RECOVERY_PLAN,
            "pace_segments": [
                {"start_m": 0, "end_m": 2000, "pace_min_s": 330, "pace_max_s": 390},
                {"start_m": 2000, "end_m": 3000, "pace_min_s": 300, "pace_max_s": 360},
            ],
        }

        facts = report_facts(plan, [375, 375, 375, 375, 375], 5000, 1875)

        self.assertEqual([row["status"] for row in facts["splits"]], [
            "in_range", "in_range", "slow", "in_range", "in_range",
        ])
        self.assertEqual(facts["splits"][2]["diff_s"], 15)

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
    def test_guest_plan_keeps_the_selected_total_distance(self, _):
        response = self.client.post("/api/v1/runs/plan/", {"text": "천천히 4km", "distance_m": 5000}, format="json")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["data"]["run_type"], "recovery")
        self.assertEqual(response.data["data"]["distance_m"], 5000)
        self.assertEqual(self.client.post("/api/v1/runs/plan/", {"text": " "}, format="json").status_code, 400)

    @mock.patch("api.services.run_coach.generate_text", side_effect=LLMError("down"))
    def test_plan_api_keeps_selected_pace_range_and_alert_settings_on_llm_fallback(self, _):
        response = self.client.post("/api/v1/runs/plan/", {
            "text": "회복 러닝",
            "distance_m": 3000,
            "pace_min_s": 350,
            "pace_max_s": 390,
            "alert_on_slow": True,
            "alert_on_fast": False,
        }, format="json")
        self.assertEqual(response.status_code, 200)
        self.assertEqual((response.data["data"]["pace_min_s"], response.data["data"]["pace_max_s"]), (350, 390))
        self.assertTrue(response.data["data"]["alert_on_slow"])
        self.assertFalse(response.data["data"]["alert_on_fast"])

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
