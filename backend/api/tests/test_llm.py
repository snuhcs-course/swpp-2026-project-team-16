import io
import json
import os
import urllib.error
from unittest import mock

from django.test import SimpleTestCase

from api.route_algorithm.llm import GEMINI_URL, LLMError, generate_text


PROMPT = {"system": "system text", "user": "user text"}


def ok_response(text):
    body = {"steps": [{"type": "model_output", "content": [{"type": "text", "text": text}]}]}
    return io.BytesIO(json.dumps(body).encode("utf-8"))


def http_error(code):
    return urllib.error.HTTPError(GEMINI_URL, code, "error", {}, io.BytesIO(b"{}"))


class FakeOpener:
    def __init__(self, *results):
        self.results = list(results)
        self.requests = []

    def __call__(self, request, timeout):
        self.requests.append(request)
        result = self.results.pop(0)
        if isinstance(result, Exception):
            raise result
        return result


class GenerateTextTests(SimpleTestCase):
    def call(self, opener, **kwargs):
        return generate_text(PROMPT, api_key="test-key", opener=opener, sleep=lambda _: None, **kwargs)

    def test_returns_text_and_sends_prompt(self):
        opener = FakeOpener(ok_response("  Easy run today.  "))
        self.assertEqual(self.call(opener), "Easy run today.")
        request = opener.requests[0]
        self.assertEqual(request.get_header("X-goog-api-key"), "test-key")
        sent = json.loads(request.data)
        self.assertEqual(sent["input"], "system text\n\nuser text")

    def test_retries_once_on_overload(self):
        opener = FakeOpener(http_error(503), ok_response("Second try."))
        self.assertEqual(self.call(opener), "Second try.")
        self.assertEqual(len(opener.requests), 2)

    def test_gives_up_after_retries(self):
        opener = FakeOpener(http_error(503), http_error(503))
        with self.assertRaises(LLMError):
            self.call(opener)

    def test_does_not_retry_client_errors(self):
        opener = FakeOpener(http_error(404))
        with self.assertRaises(LLMError):
            self.call(opener)
        self.assertEqual(len(opener.requests), 1)

    def test_timeout_raises_after_retries(self):
        opener = FakeOpener(TimeoutError(), urllib.error.URLError("down"))
        with self.assertRaises(LLMError):
            self.call(opener)

    def test_empty_text_raises(self):
        with self.assertRaises(LLMError):
            self.call(FakeOpener(ok_response("   ")))

    def test_missing_api_key_raises(self):
        with mock.patch.dict(os.environ, {"GOOGLE_API_KEY": ""}), self.assertRaises(LLMError):
            generate_text(PROMPT, opener=FakeOpener())
