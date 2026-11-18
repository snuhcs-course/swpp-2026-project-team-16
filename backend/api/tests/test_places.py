import io
import os
import urllib.error
import urllib.parse
from pathlib import Path
from unittest import mock

import json

from django.test import SimpleTestCase
from rest_framework.test import APITestCase

from api.services.google_places import (
    GOOGLE_PLACES_NEARBY_URL,
    GOOGLE_PLACES_SEARCH_URL,
    reverse_geocode_in_english,
    search_places_in_english,
)
from api.services.tmap import (
    TMAP_POI_URL,
    TMAP_REVERSE_GEOCODING_URL,
    Place,
    PlaceSearchError,
    reverse_geocode,
    search_places,
)


SAMPLE = (Path(__file__).parent / "data" / "tmap_poi_samgakji.json").read_bytes()
REVERSE_SAMPLE = (Path(__file__).parent / "data" / "tmap_reverse_city_hall.json").read_bytes()
URL = "/api/v1/places/search/"
REVERSE_URL = "/api/v1/places/reverse/"


class FakeOpener:
    def __init__(self, result):
        self.result = result
        self.request = None

    def __call__(self, request, timeout):
        self.request = request
        if isinstance(self.result, Exception):
            raise self.result
        return io.BytesIO(self.result)


class SearchPlacesTests(SimpleTestCase):
    def search(self, result, keyword="삼각지"):
        opener = FakeOpener(result)
        return search_places(keyword, api_key="test-key", opener=opener), opener

    def test_parses_tmap_sample(self):
        places, opener = self.search(SAMPLE)
        self.assertEqual(len(places), 3)
        self.assertEqual(
            places[0],
            Place(
                name="삼각지(전쟁기념관)역[수도권4호선]",
                address="서울 용산구 한강대로 180",
                longitude=126.97291133,
                latitude=37.53443005,
            ),
        )
        query = urllib.parse.parse_qs(urllib.parse.urlparse(opener.request.full_url).query)
        self.assertEqual(query["searchKeyword"], ["삼각지"])
        self.assertEqual(opener.request.get_header("Appkey"), "test-key")
        self.assertTrue(opener.request.full_url.startswith(TMAP_POI_URL))

    def test_no_result_returns_empty_list(self):
        places, _ = self.search(b"")
        self.assertEqual(places, [])

    def test_request_failure_raises(self):
        error = urllib.error.HTTPError(TMAP_POI_URL, 500, "error", {}, io.BytesIO(b""))
        with self.assertRaises(PlaceSearchError):
            self.search(error)
        with self.assertRaises(PlaceSearchError):
            self.search(TimeoutError())

    def test_invalid_response_raises(self):
        with self.assertRaises(PlaceSearchError):
            self.search(b'{"unexpected": true}')

    def test_skips_places_without_coordinates(self):
        body = b'{"searchPoiInfo": {"pois": {"poi": [{"name": "no coords"}]}}}'
        places, _ = self.search(body)
        self.assertEqual(places, [])

    def test_missing_api_key_raises(self):
        with mock.patch.dict(os.environ, {"TMAP_APP_KEY": ""}), self.assertRaises(PlaceSearchError):
            search_places("삼각지", opener=FakeOpener(SAMPLE))

    def test_place_to_dict_uses_geojson_point(self):
        place = Place(name="A", address="B", longitude=126.9, latitude=37.5)
        self.assertEqual(
            place.to_dict(),
            {"name": "A", "address": "B", "point": {"type": "Point", "coordinates": [126.9, 37.5]}},
        )


class SearchPlacesApiTests(APITestCase):
    @mock.patch("api.views.places.search_places")
    def test_returns_places(self, search):
        search.return_value = [Place(name="삼각지역", address="서울 용산구", longitude=126.97, latitude=37.53)]
        response = self.client.get(URL, {"q": " 삼각지 "})
        self.assertEqual(response.status_code, 200)
        search.assert_called_once_with("삼각지")
        self.assertEqual(response.data["data"][0]["point"]["coordinates"], [126.97, 37.53])

    @mock.patch("api.views.places.search_places")
    def test_requires_query(self, search):
        response = self.client.get(URL, {"q": "  "})
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.data["message"], "q is required")
        search.assert_not_called()

    @mock.patch("api.views.places.search_places")
    def test_rejects_long_query(self, search):
        response = self.client.get(URL, {"q": "a" * 51})
        self.assertEqual(response.status_code, 400)
        search.assert_not_called()

    @mock.patch("api.views.places.search_places", side_effect=PlaceSearchError("down"))
    def test_search_failure_returns_503(self, search):
        response = self.client.get(URL, {"q": "삼각지"})
        self.assertEqual(response.status_code, 503)


def google_result(*places):
    return json.dumps({"places": [
        {
            "id": place_id,
            "displayName": {"text": name},
            "formattedAddress": address,
            "location": {"latitude": 37.46, "longitude": 126.95},
        }
        for place_id, name, address in places
    ]}).encode("utf-8")


class GoogleOpener:
    def __init__(self, by_language):
        self.by_language = by_language
        self.requests = []

    def __call__(self, request, timeout):
        self.requests.append(request)
        result = self.by_language[json.loads(request.data)["languageCode"]]
        if isinstance(result, Exception):
            raise result
        return io.BytesIO(result)


ENGLISH = google_result(
    ("snu", "Seoul National University", "1 Gwanak-ro, Gwanak-gu, Seoul, South Korea"),
    ("gate", "SNU Main Gate", "Gwanak-ro, Gwanak-gu, Seoul, South Korea"),
)
KOREAN = google_result(("snu", "서울대학교", "대한민국 서울특별시 관악구 관악로 1"))


class GooglePlacesTests(SimpleTestCase):
    def search(self, by_language):
        opener = GoogleOpener(by_language)
        return search_places_in_english("서울대", api_key="test-key", opener=opener), opener

    def test_pairs_english_results_with_korean_names_by_place_id(self):
        results, opener = self.search({"en": ENGLISH, "ko": KOREAN})

        self.assertEqual(results, [
            (
                Place("Seoul National University", "1 Gwanak-ro, Gwanak-gu, Seoul", 126.95, 37.46),
                Place("서울대학교", "서울특별시 관악구 관악로 1", 126.95, 37.46),
            ),
            (Place("SNU Main Gate", "Gwanak-ro, Gwanak-gu, Seoul", 126.95, 37.46), None),
        ])
        request = opener.requests[0]
        self.assertEqual(request.full_url, GOOGLE_PLACES_SEARCH_URL)
        self.assertEqual(request.get_header("X-goog-api-key"), "test-key")
        body = json.loads(request.data)
        self.assertEqual((body["textQuery"], body["regionCode"], body["pageSize"]), ("서울대", "KR", 5))

    def test_korean_failure_keeps_english_results(self):
        results, _ = self.search({"en": ENGLISH, "ko": TimeoutError()})

        self.assertEqual([korean for _, korean in results], [None, None])
        self.assertEqual(results[0][0].name, "Seoul National University")

    def test_english_failure_raises(self):
        with self.assertRaises(PlaceSearchError):
            self.search({"en": TimeoutError(), "ko": KOREAN})
        with self.assertRaises(PlaceSearchError):
            self.search({"en": b"not json", "ko": KOREAN})

    def test_skips_places_without_name_or_location(self):
        body = json.dumps({"places": [{"id": "a", "displayName": {"text": "No location"}}, {"id": "b", "location": {"latitude": 1, "longitude": 2}}]}).encode()

        results, _ = self.search({"en": body, "ko": b"{}"})

        self.assertEqual(results, [])

    def test_missing_api_key_raises(self):
        with mock.patch.dict(os.environ, {"GOOGLE_PLACES_API_KEY": ""}), self.assertRaises(PlaceSearchError):
            search_places_in_english("서울대", opener=GoogleOpener({"en": ENGLISH, "ko": KOREAN}))


class GoogleReverseGeocodeTests(SimpleTestCase):
    def reverse(self, by_language):
        opener = GoogleOpener(by_language)
        return reverse_geocode_in_english(37.4601, 126.9502, api_key="test-key", opener=opener), opener

    def test_uses_the_nearest_place_at_the_given_point(self):
        result, opener = self.reverse({"en": ENGLISH, "ko": KOREAN})

        self.assertEqual(result, (
            Place("Seoul National University", "1 Gwanak-ro, Gwanak-gu, Seoul", 126.9502, 37.4601),
            Place("서울대학교", "서울특별시 관악구 관악로 1", 126.9502, 37.4601),
        ))
        self.assertEqual(opener.requests[0].full_url, GOOGLE_PLACES_NEARBY_URL)
        body = json.loads(opener.requests[0].data)
        self.assertEqual((body["rankPreference"], body["maxResultCount"]), ("DISTANCE", 1))
        self.assertEqual(body["locationRestriction"]["circle"]["center"], {"latitude": 37.4601, "longitude": 126.9502})

    def test_nothing_nearby_returns_none(self):
        result, _ = self.reverse({"en": b"{}", "ko": b"{}"})

        self.assertIsNone(result)

    def test_korean_failure_keeps_the_english_place(self):
        result, _ = self.reverse({"en": ENGLISH, "ko": TimeoutError()})

        self.assertEqual(result[0].name, "Seoul National University")
        self.assertIsNone(result[1])


SNU_EN = Place("Seoul National University", "1 Gwanak-ro, Gwanak-gu, Seoul", 126.95, 37.46)
SNU_KO = Place("서울대학교", "서울특별시 관악구 관악로 1", 126.95, 37.46)


@mock.patch("api.views.places.google_places.is_configured", return_value=True)
class SearchPlacesInEnglishApiTests(APITestCase):
    @mock.patch("api.views.places.translate_places")
    @mock.patch("api.views.places.search_places")
    @mock.patch("api.views.places.google_places.search_places_in_english", return_value=[(SNU_EN, SNU_KO), (SNU_EN, None)])
    def test_english_uses_google_places_without_translation(self, google, tmap, translate, configured):
        response = self.client.get(URL, {"q": "서울대", "language": "en"})

        self.assertEqual(response.status_code, 200)
        first, second = response.data["data"]
        self.assertEqual((first["name"], first["name_ko"], first["address_ko"]), ("Seoul National University", "서울대학교", "서울특별시 관악구 관악로 1"))
        self.assertIsNone(second["name_ko"])
        tmap.assert_not_called()
        translate.assert_not_called()

    @mock.patch("api.views.places.translate_places", side_effect=lambda places: places)
    @mock.patch("api.views.places.search_places", return_value=[SNU_KO])
    @mock.patch("api.views.places.google_places.search_places_in_english", side_effect=PlaceSearchError("down"))
    def test_english_falls_back_to_tmap_when_google_fails(self, google, tmap, translate, configured):
        response = self.client.get(URL, {"q": "서울대", "language": "en"})

        self.assertEqual(response.status_code, 200)
        tmap.assert_called_once_with("서울대")
        translate.assert_called_once_with([SNU_KO])

    @mock.patch("api.views.places.search_places", return_value=[SNU_KO])
    @mock.patch("api.views.places.google_places.search_places_in_english")
    def test_korean_keeps_using_tmap(self, google, tmap, configured):
        response = self.client.get(URL, {"q": "서울대", "language": "ko"})

        self.assertEqual(response.data["data"][0]["name"], "서울대학교")
        google.assert_not_called()


class ReverseGeocodeTests(SimpleTestCase):
    def reverse(self, result):
        opener = FakeOpener(result)
        return reverse_geocode(37.5665, 126.978, api_key="test-key", opener=opener), opener

    def test_uses_building_name_and_road_address(self):
        place, opener = self.reverse(REVERSE_SAMPLE)
        self.assertEqual(
            place,
            Place(name="서울특별시청", address="서울특별시 중구 세종대로 110", longitude=126.978, latitude=37.5665),
        )
        query = urllib.parse.parse_qs(urllib.parse.urlparse(opener.request.full_url).query)
        self.assertEqual((query["lat"], query["lon"]), (["37.5665"], ["126.978"]))
        self.assertTrue(opener.request.full_url.startswith(TMAP_REVERSE_GEOCODING_URL))

    def test_falls_back_to_road_then_lot_address(self):
        road = '{"addressInfo": {"city_do": "서울특별시", "roadName": "삼청로", "buildingIndex": "37"}}'.encode()
        place, _ = self.reverse(road)
        self.assertEqual(place.name, "삼청로 37")

        lot = '{"addressInfo": {"legalDong": "신림동", "bunji": "산56-1"}}'.encode()
        place, _ = self.reverse(lot)
        self.assertEqual((place.name, place.address), ("신림동 산56-1", "신림동 산56-1"))

    def test_empty_address_returns_none(self):
        place, _ = self.reverse(b'{"addressInfo": {}}')
        self.assertIsNone(place)

    def test_unsupported_area_raises(self):
        error = urllib.error.HTTPError(TMAP_REVERSE_GEOCODING_URL, 400, "error", {}, io.BytesIO(b""))
        with self.assertRaises(PlaceSearchError):
            self.reverse(error)
        with self.assertRaises(PlaceSearchError):
            self.reverse(b"not json")


class ReverseGeocodeApiTests(APITestCase):
    place = Place(name="서울특별시청", address="서울특별시 중구 세종대로 110", longitude=126.978, latitude=37.5665)

    @mock.patch("api.views.places.reverse_geocode")
    def test_returns_place_in_korean(self, reverse):
        reverse.return_value = self.place
        response = self.client.get(REVERSE_URL, {"lat": "37.5665", "lng": "126.978", "language": "ko"})
        self.assertEqual(response.status_code, 200)
        reverse.assert_called_once_with(37.5665, 126.978)
        self.assertEqual(response.data["data"]["name"], "서울특별시청")

    @mock.patch("api.views.places.google_places.is_configured", return_value=False)
    @mock.patch("api.views.places.translate_places")
    @mock.patch("api.views.places.reverse_geocode")
    def test_translates_place_in_english_without_google_places(self, reverse, translate, configured):
        reverse.return_value = self.place
        translate.return_value = [Place(name="Seoul City Hall", address="110 Sejong-daero", longitude=126.978, latitude=37.5665)]
        response = self.client.get(REVERSE_URL, {"lat": "37.5665", "lng": "126.978", "language": "en"})
        self.assertEqual(response.data["data"]["name"], "Seoul City Hall")

    @mock.patch("api.views.places.reverse_geocode")
    def test_rejects_invalid_input(self, reverse):
        cases = [{}, {"lat": "abc", "lng": "126.9"}, {"lat": "95", "lng": "126.9"}, {"lat": "37.5", "lng": "126.9", "language": "fr"}]
        for params in cases:
            with self.subTest(params=params):
                self.assertEqual(self.client.get(REVERSE_URL, params).status_code, 400)
        reverse.assert_not_called()

    @mock.patch("api.views.places.reverse_geocode", return_value=None)
    def test_no_address_returns_404(self, reverse):
        self.assertEqual(self.client.get(REVERSE_URL, {"lat": "37.5", "lng": "126.9"}).status_code, 404)

    @mock.patch("api.views.places.reverse_geocode", side_effect=PlaceSearchError("down"))
    def test_failure_returns_503(self, reverse):
        self.assertEqual(self.client.get(REVERSE_URL, {"lat": "37.5", "lng": "126.9"}).status_code, 503)

    @mock.patch("api.views.places.google_places.is_configured", return_value=True)
    @mock.patch("api.views.places.reverse_geocode")
    @mock.patch("api.views.places.google_places.reverse_geocode_in_english", return_value=(SNU_EN, SNU_KO))
    def test_english_reverse_uses_google_places(self, google, tmap, configured):
        response = self.client.get(REVERSE_URL, {"lat": "37.46", "lng": "126.95", "language": "en"})

        self.assertEqual(response.status_code, 200)
        self.assertEqual((response.data["data"]["name"], response.data["data"]["name_ko"]), ("Seoul National University", "서울대학교"))
        google.assert_called_once_with(37.46, 126.95)
        tmap.assert_not_called()

    @mock.patch("api.views.places.google_places.is_configured", return_value=True)
    @mock.patch("api.views.places.translate_places", side_effect=lambda places: places)
    @mock.patch("api.views.places.reverse_geocode", return_value=SNU_KO)
    @mock.patch("api.views.places.google_places.reverse_geocode_in_english", return_value=None)
    def test_english_reverse_falls_back_when_nothing_is_nearby(self, google, tmap, translate, configured):
        response = self.client.get(REVERSE_URL, {"lat": "37.46", "lng": "126.95", "language": "en"})

        self.assertEqual(response.data["data"]["name"], "서울대학교")
        translate.assert_called_once_with([SNU_KO])
