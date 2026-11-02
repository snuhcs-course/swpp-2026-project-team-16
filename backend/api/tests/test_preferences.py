from django.test import SimpleTestCase

from api.route_algorithm.llm import LLMError
from api.route_algorithm.preferences import (
    Multipliers,
    build_preference_prompt,
    multipliers_from_options,
    parse_multipliers,
    resolve_multipliers,
    to_weights,
    validate_options,
)
from api.route_algorithm.scoring import DEFAULT_WEIGHTS, score_route


class PreferenceOptionTests(SimpleTestCase):
    def test_no_options_keep_the_default_weights(self):
        self.assertEqual(to_weights(multipliers_from_options([])), DEFAULT_WEIGHTS)

    def test_options_raise_their_factor(self):
        multipliers = multipliers_from_options(["flat", "few_signals", "exact_distance"])
        self.assertEqual(multipliers, Multipliers(climb=3.0, traffic_lights=3.0, distance=3.0))

    def test_hills_prefers_climbing(self):
        weights = to_weights(multipliers_from_options(["hills"]))
        flat_cost = score_route(0, 0, 3000, 3000, weights)
        hilly_cost = score_route(40, 0, 3000, 3000, weights)
        self.assertLess(hilly_cost, flat_cost)

    def test_validate_options(self):
        self.assertIsNone(validate_options([]))
        self.assertIsNone(validate_options(["flat", "few_signals"]))
        self.assertIsNotNone(validate_options(["flat", "hills"]))
        self.assertIsNotNone(validate_options(["unknown"]))
        self.assertIsNotNone(validate_options([1]))
        self.assertIsNotNone(validate_options({"flat": True}))


class PreferenceTextTests(SimpleTestCase):
    def test_parses_json_and_clamps_values(self):
        multipliers = parse_multipliers('```json\n{"climb": -5, "traffic_lights": 2.5, "distance": 10}\n```')
        self.assertEqual(multipliers, Multipliers(climb=-2.0, traffic_lights=2.5, distance=3.0))

    def test_missing_factors_stay_neutral(self):
        self.assertEqual(parse_multipliers('{"traffic_lights": 0}'), Multipliers(traffic_lights=0.0))

    def test_rejects_replies_without_numbers(self):
        for reply in ["sure!", '{"climb": "high"}', '{"climb": true}', "{climb: 2}"]:
            with self.assertRaises(ValueError):
                parse_multipliers(reply)

    def test_prompt_includes_text_and_selected_options(self):
        prompt = build_preference_prompt("신호등 없는 길", ["flat"])
        self.assertIn("Selected options: flat", prompt["user"])
        self.assertIn("신호등 없는 길", prompt["user"])
        self.assertIn("JSON", prompt["system"])

    def test_uses_the_llm_only_when_there_is_text(self):
        calls = []

        def llm(prompt):
            calls.append(prompt)
            return '{"climb": 2, "traffic_lights": 1, "distance": 1}'

        self.assertEqual(resolve_multipliers(["few_signals"], "", llm), Multipliers(traffic_lights=3.0))
        self.assertEqual(calls, [])
        self.assertEqual(resolve_multipliers(["few_signals"], "언덕 싫어", llm), Multipliers(climb=2.0))
        self.assertEqual(len(calls), 1)

    def test_falls_back_to_options_when_the_llm_fails(self):
        def offline(prompt):
            raise LLMError("offline")

        self.assertEqual(resolve_multipliers(["flat"], "평지", offline), Multipliers(climb=3.0))
        self.assertEqual(resolve_multipliers(["flat"], "평지", lambda prompt: "no idea"), Multipliers(climb=3.0))
