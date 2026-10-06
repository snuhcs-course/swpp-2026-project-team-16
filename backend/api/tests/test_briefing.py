from django.test import SimpleTestCase

from api.route_algorithm.briefing import (
    SAMPLE_FEATURES,
    RouteFeatures,
    build_briefing_prompt,
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
