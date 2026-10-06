from unittest import mock

from rest_framework.test import APITestCase

from api.route_algorithm.llm import LLMError


URL = "/api/v1/routes/generate/"


def body(**extra):
    return {
        "starting_point": {"type": "Point", "coordinates": [126.952, 37.46]},
        "distance": 5000,
        **extra,
    }


@mock.patch("api.route_algorithm.briefing.generate_text", side_effect=LLMError("offline"))
class GenerateRouteLanguageTests(APITestCase):
    def test_korean_briefing(self, llm):
        response = self.client.post(URL, body(language="ko"), format="json")
        self.assertEqual(response.status_code, 200)
        self.assertIn("코스입니다", response.data["data"]["route"]["briefing"])
        self.assertIn("한국어", llm.call_args.args[0]["system"])

    def test_english_briefing(self, llm):
        response = self.client.post(URL, body(language="en"), format="json")
        self.assertEqual(response.status_code, 200)
        self.assertIn("traffic lights", response.data["data"]["route"]["briefing"])
        self.assertIn("in English", llm.call_args.args[0]["system"])

    def test_defaults_to_korean(self, llm):
        response = self.client.post(URL, body(), format="json")
        self.assertEqual(response.status_code, 200)
        self.assertIn("코스입니다", response.data["data"]["route"]["briefing"])

    def test_rejects_unsupported_language(self, llm):
        response = self.client.post(URL, body(language="ja"), format="json")
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.data["message"], "language must be one of: ko, en")
        llm.assert_not_called()


class GenerateRouteLLMTests(APITestCase):
    @mock.patch("api.route_algorithm.briefing.generate_text", return_value="LLM briefing text.")
    def test_uses_llm_briefing(self, llm):
        response = self.client.post(URL, body(language="en"), format="json")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["data"]["route"]["briefing"], "LLM briefing text.")
