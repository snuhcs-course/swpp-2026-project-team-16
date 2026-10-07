from unittest import mock

import requests
from rest_framework.test import APITestCase

from api.route_algorithm.algorithm import create_route
from api.route_algorithm.llm import LLMError
from api.tests.fakes import SNU, grid_graph


URL = "/api/v1/routes/generate/"
END = [SNU[0] + 0.009, SNU[1]]


def body(**extra):
    return {
        "starting_point": {"type": "Point", "coordinates": list(SNU)},
        "distance": 5000,
        **extra,
    }


class FakeMapTestCase(APITestCase):
    def setUp(self):
        patcher = mock.patch(
            "api.route_algorithm.algorithm.ox.graph.graph_from_point",
            return_value=grid_graph(),
        )
        self.download = patcher.start()
        self.addCleanup(patcher.stop)


@mock.patch("api.route_algorithm.briefing.generate_text", side_effect=LLMError("offline"))
class GenerateRouteLanguageTests(FakeMapTestCase):
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


class GenerateRouteLLMTests(FakeMapTestCase):
    @mock.patch("api.route_algorithm.briefing.generate_text", return_value="LLM briefing text.")
    def test_uses_llm_briefing(self, llm):
        response = self.client.post(URL, body(language="en"), format="json")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["data"]["route"]["briefing"], "LLM briefing text.")


@mock.patch("api.route_algorithm.briefing.generate_text", side_effect=LLMError("offline"))
class GenerateRouteEndPointTests(FakeMapTestCase):
    def post(self, **extra):
        with mock.patch("api.views.generate_route.create_route", wraps=create_route) as spy:
            response = self.client.post(URL, body(**extra), format="json")
        return response, spy

    def test_omitted_end_point_makes_a_loop(self, llm):
        response, spy = self.post()
        self.assertEqual(response.status_code, 200)
        self.assertIsNone(spy.call_args.kwargs["end_point"])
        coordinates = response.data["data"]["route"]["route"][0]["coordinates"]
        self.assertEqual(coordinates[0], coordinates[-1])

    def test_accepts_end_point(self, llm):
        response, spy = self.post(end_point={"type": "Point", "coordinates": END})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(spy.call_args.kwargs["end_point"].coordinates, tuple(END))

    def test_rejects_invalid_end_point(self, llm):
        response, _ = self.post(end_point={"type": "LineString", "coordinates": [126.9, 37.4]})
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.data["message"], "end_point must have type Point")

        response, _ = self.post(end_point={"type": "Point", "coordinates": [200, 37.4]})
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.data["message"], "end_point longitude must be between -180 and 180")

    def test_starting_point_is_still_required(self, llm):
        response = self.client.post(URL, {"distance": 5000}, format="json")
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.data["message"], "starting_point must be a GeoJSON Point")


@mock.patch("api.route_algorithm.briefing.generate_text", side_effect=LLMError("offline"))
class GenerateRouteErrorTests(FakeMapTestCase):
    def test_algorithm_error_returns_400(self, llm):
        response = self.client.post(URL, body(end_point={"type": "Point", "coordinates": list(SNU)}), format="json")
        self.assertEqual(response.status_code, 400)
        self.assertEqual(
            response.data["message"],
            "Start and end are the same point. Use 'loop' or 'out_and_back'.",
        )

    def test_map_download_failure_returns_503(self, llm):
        self.download.side_effect = requests.ConnectionError("offline")
        response = self.client.post(URL, body(), format="json")
        self.assertEqual(response.status_code, 503)
        self.assertEqual(response.data["status"], "error")
