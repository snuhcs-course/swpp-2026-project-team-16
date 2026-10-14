import json
from unittest import mock

from django.test import SimpleTestCase
from rest_framework.test import APITestCase

from api.route_algorithm.llm import LLMError
from api.services.place_translation import clear_cache, translate_places
from api.services.tmap import Place


GANGNAM = Place(name="강남역[수도권2호선]", address="서울 강남구 강남대로 396", longitude=127.02, latitude=37.49)
COEX = Place(name="코엑스", address="서울 강남구 영동대로 513", longitude=127.05, latitude=37.51)


class FakeLLM:
    def __init__(self, *responses):
        self.responses = list(responses)
        self.calls = []

    def __call__(self, prompt, **kwargs):
        self.calls.append((prompt, kwargs))
        response = self.responses.pop(0)
        if isinstance(response, Exception):
            raise response
        return response


def translations(*pairs):
    return json.dumps([{"name": name, "address": address} for name, address in pairs])


class TranslatePlacesTests(SimpleTestCase):
    def setUp(self):
        clear_cache()
        self.addCleanup(clear_cache)

    def test_translates_names_and_addresses(self):
        llm = FakeLLM(translations(
            ("Gangnam Station (Line 2)", "396 Gangnam-daero, Gangnam-gu, Seoul"),
            ("COEX", "513 Yeongdong-daero, Gangnam-gu, Seoul"),
        ))
        result = translate_places([GANGNAM, COEX], llm=llm)
        self.assertEqual(result[0].name, "Gangnam Station (Line 2)")
        self.assertEqual(result[1].address, "513 Yeongdong-daero, Gangnam-gu, Seoul")
        self.assertEqual((result[0].longitude, result[0].latitude), (127.02, 37.49))
        prompt, kwargs = llm.calls[0]
        self.assertEqual(json.loads(prompt["user"])[0], {"name": GANGNAM.name, "address": GANGNAM.address})
        self.assertEqual(kwargs["retries"], 0)

    def test_uses_cache_for_known_places(self):
        llm = FakeLLM(
            translations(("Gangnam Station (Line 2)", "396 Gangnam-daero")),
            translations(("COEX", "513 Yeongdong-daero")),
        )
        translate_places([GANGNAM], llm=llm)
        result = translate_places([GANGNAM, COEX], llm=llm)
        self.assertEqual([place.name for place in result], ["Gangnam Station (Line 2)", "COEX"])
        self.assertEqual(json.loads(llm.calls[1][0]["user"]), [{"name": COEX.name, "address": COEX.address}])

    def test_strips_markdown_code_fence(self):
        llm = FakeLLM("```json\n" + translations(("COEX", "513 Yeongdong-daero")) + "\n```")
        self.assertEqual(translate_places([COEX], llm=llm)[0].name, "COEX")

    def test_keeps_korean_when_llm_fails(self):
        self.assertEqual(translate_places([GANGNAM], llm=FakeLLM(LLMError("overloaded"))), [GANGNAM])

    def test_keeps_korean_when_response_is_invalid(self):
        self.assertEqual(translate_places([GANGNAM, COEX], llm=FakeLLM(translations(("only one", "x")))), [GANGNAM, COEX])
        self.assertEqual(translate_places([GANGNAM], llm=FakeLLM("not json")), [GANGNAM])


class SearchPlacesLanguageApiTests(APITestCase):
    URL = "/api/v1/places/search/"

    @mock.patch("api.views.places.translate_places", side_effect=lambda places: places)
    @mock.patch("api.views.places.search_places", return_value=[GANGNAM])
    def test_english_translates_results(self, search, translate):
        response = self.client.get(self.URL, {"q": "Gangnam", "language": "en"})
        self.assertEqual(response.status_code, 200)
        translate.assert_called_once_with([GANGNAM])

    @mock.patch("api.views.places.translate_places")
    @mock.patch("api.views.places.search_places", return_value=[GANGNAM])
    def test_korean_is_default_and_not_translated(self, search, translate):
        response = self.client.get(self.URL, {"q": "강남"})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["data"][0]["name"], GANGNAM.name)
        translate.assert_not_called()

    @mock.patch("api.views.places.search_places")
    def test_rejects_unsupported_language(self, search):
        response = self.client.get(self.URL, {"q": "강남", "language": "ja"})
        self.assertEqual(response.status_code, 400)
        search.assert_not_called()
