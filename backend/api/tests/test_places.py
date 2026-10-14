import io
import os
import urllib.error
import urllib.parse
from pathlib import Path
from unittest import mock

from django.test import SimpleTestCase
from rest_framework.test import APITestCase

from api.services.tmap import TMAP_POI_URL, Place, PlaceSearchError, search_places


SAMPLE = (Path(__file__).parent / "data" / "tmap_poi_samgakji.json").read_bytes()
URL = "/api/v1/places/search/"


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
