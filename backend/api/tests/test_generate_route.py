from rest_framework.test import APITestCase


URL = "/api/v1/routes/generate/"


def body(**extra):
    return {
        "starting_point": {"type": "Point", "coordinates": [126.952, 37.46]},
        "distance": 5000,
        **extra,
    }


class GenerateRouteLanguageTests(APITestCase):
    def test_korean_briefing(self):
        response = self.client.post(URL, body(language="ko"), format="json")
        self.assertEqual(response.status_code, 200)
        self.assertIn("모의 경로", response.data["data"]["route"]["briefing"])

    def test_english_briefing(self):
        response = self.client.post(URL, body(language="en"), format="json")
        self.assertEqual(response.status_code, 200)
        self.assertIn("Mock route", response.data["data"]["route"]["briefing"])

    def test_defaults_to_korean(self):
        response = self.client.post(URL, body(), format="json")
        self.assertEqual(response.status_code, 200)
        self.assertIn("모의 경로", response.data["data"]["route"]["briefing"])

    def test_rejects_unsupported_language(self):
        response = self.client.post(URL, body(language="ja"), format="json")
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.data["message"], "language must be one of: ko, en")
