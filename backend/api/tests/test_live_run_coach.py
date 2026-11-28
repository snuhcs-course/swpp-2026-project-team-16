from unittest import mock

from django.test import SimpleTestCase
from rest_framework.test import APITestCase

from api.route_algorithm.llm import LLMError
from api.services.live_run_coach import (
    build_live_coach_prompt,
    generate_live_coaching,
    validate_live_coach_request,
)


VALID_CONTEXT = {
    "completed_km": 2,
    "distance_m": 2010,
    "elapsed_s": 740,
    "split_paces_s": [360, 380],
    "plan": {
        "distance_m": 5000,
        "pace_min_s": 360,
        "pace_max_s": 390,
        "pace_segments": [
            {"start_m": 0, "end_m": 2000, "pace_min_s": 360, "pace_max_s": 380},
            {"start_m": 2000, "end_m": 5000, "pace_min_s": 370, "pace_max_s": 390},
        ],
    },
    "next_target_pace_min_s": 370,
    "next_target_pace_max_s": 390,
    "upcoming_hills": [{"distance_ahead_m": 180, "length_m": 300}],
}


class LiveCoachValidationTests(SimpleTestCase):
    def test_validates_and_keeps_only_coaching_facts(self):
        context, error = validate_live_coach_request(VALID_CONTEXT)

        self.assertIsNone(error)
        self.assertEqual(context["split_paces_s"], [360, 380])
        self.assertEqual(context["plan"]["pace_segments"][1]["start_m"], 2000)
        self.assertNotIn("summary", context["plan"])

    def test_rejects_more_splits_than_completed_kilometers(self):
        context, error = validate_live_coach_request({**VALID_CONTEXT, "split_paces_s": [360, 370, 380]})

        self.assertIsNone(context)
        self.assertIn("split_paces_s", error)

    def test_rejects_invalid_plan_and_hill_values(self):
        bad_plan = {**VALID_CONTEXT, "plan": {**VALID_CONTEXT["plan"], "pace_min_s": 950}}
        bad_hill = {**VALID_CONTEXT, "upcoming_hills": [{"distance_ahead_m": -1, "length_m": 300}]}

        self.assertIsNotNone(validate_live_coach_request(bad_plan)[1])
        self.assertIsNotNone(validate_live_coach_request(bad_hill)[1])

    def test_prompt_includes_completed_splits_next_target_and_hill_without_coordinates(self):
        prompt = build_live_coach_prompt(VALID_CONTEXT, "ko")

        self.assertIn("360, 380", prompt["user"])
        self.assertIn("6:10-6:30", prompt["user"])
        self.assertIn("180 m, 300 m", prompt["user"])
        self.assertNotIn("latitude", prompt["user"])
        self.assertIn("Korean", prompt["system"])

    def test_runner_goal_and_condition_reach_the_coach(self):
        plan = {**VALID_CONTEXT["plan"], "condition": "low", "focus": "finish", "coach_note": "완주가 목표"}
        context, error = validate_live_coach_request({**VALID_CONTEXT, "plan": plan})

        self.assertIsNone(error)
        prompt = build_live_coach_prompt(context, "ko")
        self.assertIn("runner's focus: finishing the distance matters more than time", prompt["user"])
        self.assertIn("runner's condition before the run: not good", prompt["user"])
        self.assertIn("runner's note: 완주가 목표", prompt["user"])
        self.assertIn("never suggest running faster than the target", prompt["system"])

    def test_unknown_runner_context_is_dropped(self):
        plan = {**VALID_CONTEXT["plan"], "condition": "bad", "focus": "finish", "coach_note": 5}
        context, error = validate_live_coach_request({**VALID_CONTEXT, "plan": plan})

        self.assertIsNone(error)
        self.assertEqual((context["plan"]["condition"], context["plan"]["focus"], context["plan"]["coach_note"]), (None, "finish", ""))

    def test_cleans_llm_reply_to_one_or_two_plain_sentences(self):
        message = generate_live_coaching(
            VALID_CONTEXT,
            "ko",
            llm=lambda prompt: "첫 문장입니다. 다음 구간은 편하게 이어가세요. 세 번째 문장은 제거합니다.",
        )

        self.assertEqual(message, "첫 문장입니다. 다음 구간은 편하게 이어가세요.")

    def test_provider_failure_is_not_reported_as_ai_coaching(self):
        def failing_llm(prompt):
            raise LLMError("offline")

        with self.assertRaises(LLMError):
            generate_live_coaching(VALID_CONTEXT, "ko", llm=failing_llm)


class LiveCoachApiTests(APITestCase):
    url = "/api/v1/runs/live-coaching/"

    @mock.patch("api.services.live_run_coach.generate_text", return_value="다음 구간도 차분하게 이어가세요.")
    def test_returns_voice_message_from_the_coach_llm(self, provider):
        response = self.client.post(self.url, VALID_CONTEXT, format="json", HTTP_ACCEPT_LANGUAGE="ko")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["data"]["message"], "다음 구간도 차분하게 이어가세요.")
        provider.assert_called_once()
        prompt = provider.call_args.args[0]
        self.assertIn("180 m, 300 m", prompt["user"])

    def test_rejects_bad_live_coaching_payload(self):
        response = self.client.post(self.url, {**VALID_CONTEXT, "completed_km": 0}, format="json")

        self.assertEqual(response.status_code, 400)

    @mock.patch("api.services.live_run_coach.generate_text", side_effect=LLMError("offline"))
    def test_provider_failure_returns_service_unavailable(self, provider):
        response = self.client.post(self.url, VALID_CONTEXT, format="json")

        self.assertEqual(response.status_code, 503)
        self.assertNotIn("offline", str(response.data))
        provider.assert_called_once()
