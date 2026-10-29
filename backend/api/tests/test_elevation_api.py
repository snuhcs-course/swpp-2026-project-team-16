from unittest import mock

from rest_framework.test import APITestCase

from api.models import ElevationCache
from api.route_algorithm.elevation import ElevationError


URL = "/api/v1/elevations/"


class ElevationApiTests(APITestCase):
    def test_returns_elevations_and_persists_them_for_reuse(self):
        body = {
            "coordinates": [
                {"latitude": 37.46, "longitude": 126.952},
                {"latitude": 37.461, "longitude": 126.953},
            ]
        }

        with mock.patch(
            "api.route_algorithm.elevation._fetch_batch",
            return_value=[101.0, 102.0],
        ) as provider:
            response = self.client.post(URL, body, format="json")
            repeated_response = self.client.post(URL, body, format="json")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["data"]["elevations"], [101.0, 102.0])
        self.assertEqual(repeated_response.data["data"]["elevations"], [101.0, 102.0])
        provider.assert_called_once()
        self.assertEqual(ElevationCache.objects.count(), 2)

    def test_rejects_invalid_coordinate(self):
        response = self.client.post(
            URL,
            {"coordinates": [{"latitude": 91, "longitude": 126.952}]},
            format="json",
        )

        self.assertEqual(response.status_code, 400)
        self.assertIn("latitude", response.data["message"])

    def test_returns_service_unavailable_when_provider_fails(self):
        with mock.patch(
            "api.route_algorithm.elevation._fetch_batch",
            side_effect=ElevationError("offline"),
        ):
            response = self.client.post(
                URL,
                {"coordinates": [{"latitude": 37.46, "longitude": 126.952}]},
                format="json",
            )

        self.assertEqual(response.status_code, 503)
        self.assertFalse(ElevationCache.objects.exists())
