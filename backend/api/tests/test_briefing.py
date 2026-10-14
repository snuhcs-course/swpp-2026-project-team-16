from django.test import SimpleTestCase

from api.route_algorithm.briefing import (
    MAX_BRIEFING_LENGTH,
    SAMPLE_FEATURES,
    RouteFeatures,
    build_briefing_prompt,
    fallback_briefing,
    generate_briefing,
)
from api.route_algorithm.llm import LLMError


def failing_llm(prompt):
    raise LLMError("overloaded")


class GenerateBriefingTests(SimpleTestCase):
    def test_returns_llm_text_for_language(self):
        prompts = []

        def llm(prompt):
            prompts.append(prompt)
            return " 좋은 러닝 되세요. "

        self.assertEqual(generate_briefing(SAMPLE_FEATURES, "ko", llm=llm), "좋은 러닝 되세요.")
        self.assertIn("한국어", prompts[0]["system"])

    def test_falls_back_when_llm_fails(self):
        self.assertEqual(
            generate_briefing(SAMPLE_FEATURES, "ko", llm=failing_llm),
            "총 5km, 누적 상승 45m, 신호등 3개가 있는 코스입니다. 2km 지점의 오르막 전에 페이스를 조절하세요.",
        )
        self.assertEqual(
            generate_briefing(SAMPLE_FEATURES, "en", llm=failing_llm),
            "This 5km route has 45m of climbing and 3 traffic lights. Ease your pace before the uphill at 2km.",
        )

    def test_falls_back_when_llm_text_is_too_long(self):
        too_long = "a" * (MAX_BRIEFING_LENGTH + 1)
        self.assertEqual(
            generate_briefing(SAMPLE_FEATURES, "en", llm=lambda prompt: too_long),
            fallback_briefing(SAMPLE_FEATURES, "en"),
        )

    def test_fallback_without_uphill(self):
        features = RouteFeatures(distance_m=1500, total_elevation_gain_m=5, traffic_light_count=1)
        self.assertEqual(
            fallback_briefing(features, "ko"),
            "총 1.5km, 누적 상승 5m, 신호등 1개가 있는 코스입니다.",
        )


class BuildBriefingPromptTests(SimpleTestCase):
    def test_korean_prompt_contains_sample_data(self):
        prompt = build_briefing_prompt(SAMPLE_FEATURES, "ko")
        self.assertIn("한국어", prompt["system"])
        self.assertIn("총 거리: 5km", prompt["user"])
        self.assertIn("총 상승 고도: 45m", prompt["user"])
        self.assertIn("신호등: 3개", prompt["user"])
        self.assertIn("2km 지점부터 300m 구간 (+20m)", prompt["user"])

    def test_english_prompt_contains_sample_data(self):
        prompt = build_briefing_prompt(SAMPLE_FEATURES, "en")
        self.assertIn("in English", prompt["system"])
        self.assertIn("Total distance: 5km", prompt["user"])
        self.assertIn("Total elevation gain: 45m", prompt["user"])
        self.assertIn("Traffic lights: 3", prompt["user"])
        self.assertIn("300m starting at 2km (+20m)", prompt["user"])

    def test_route_without_uphills(self):
        features = RouteFeatures(distance_m=3250, total_elevation_gain_m=0, traffic_light_count=0)
        self.assertIn("오르막: 없음", build_briefing_prompt(features, "ko")["user"])
        self.assertIn("Uphills: none", build_briefing_prompt(features, "en")["user"])
        self.assertIn("3.25km", build_briefing_prompt(features, "en")["user"])

    def test_crosswalks_line_only_when_known(self):
        self.assertNotIn("횡단보도", build_briefing_prompt(SAMPLE_FEATURES, "ko")["user"])
        features = RouteFeatures(distance_m=5000, total_elevation_gain_m=45, traffic_light_count=3, crosswalk_count=2)
        self.assertIn("횡단보도: 2개", build_briefing_prompt(features, "ko")["user"])
        self.assertIn("Crosswalks: 2", build_briefing_prompt(features, "en")["user"])

    def test_rejects_unsupported_language(self):
        with self.assertRaises(ValueError):
            build_briefing_prompt(SAMPLE_FEATURES, "ja")
