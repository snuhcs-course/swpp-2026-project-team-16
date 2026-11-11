import io
import json
from unittest import mock

from django.core.cache import cache
from django.test import SimpleTestCase
from rest_framework.test import APITestCase

from api.services.weather import WeatherError, build_briefing, fetch_raw, grade, PM10_LIMITS, PM25_LIMITS, weather_tip_key


def raw(code=1, temp=9.0, apparent=7.4, wind=0.7, probabilities=(0, 10), pm10=64.5, pm25=49.1, air=True):
    return {
        "forecast": {
            "current": {
                "time": "2026-11-11T10:45",
                "temperature_2m": temp,
                "apparent_temperature": apparent,
                "relative_humidity_2m": 77,
                "weather_code": code,
                "wind_speed_10m": wind,
            },
            "hourly": {"precipitation_probability": list(probabilities)},
        },
        "air": {"current": {"pm10": pm10, "pm2_5": pm25}} if air else None,
    }


class BriefingTests(SimpleTestCase):
    def test_builds_a_korean_briefing_with_the_worse_dust_grade(self):
        data = build_briefing(raw(), "ko")

        self.assertEqual(data["condition_label"], "구름 조금")
        self.assertEqual(data["weather_tip"], "달리기 좋은 날씨예요.")
        self.assertEqual(data["precipitation_probability"], 10)
        self.assertEqual((data["pm10"], data["pm2_5"]), (64, 49))
        self.assertEqual(data["air_grade"], "bad")
        self.assertEqual(data["air_label"], "나쁨")

    def test_air_quality_is_optional(self):
        data = build_briefing(raw(air=False), "en")

        self.assertIsNone(data["air_grade"])
        self.assertIsNone(data["air_tip"])
        self.assertEqual(data["condition_label"], "Partly cloudy")

    def test_missing_forecast_is_an_error(self):
        with self.assertRaises(WeatherError):
            build_briefing({"forecast": {}, "air": None}, "ko")

    def test_korean_dust_grades(self):
        self.assertEqual([grade(v, PM10_LIMITS) for v in (30, 31, 80, 81, 151)], ["good", "moderate", "moderate", "bad", "very_bad"])
        self.assertEqual([grade(v, PM25_LIMITS) for v in (15, 16, 35, 36, 76)], ["good", "moderate", "moderate", "bad", "very_bad"])

    def test_weather_tip_priority(self):
        self.assertEqual(weather_tip_key("thunder", 35, 90, 12), "thunder")
        self.assertEqual(weather_tip_key("clear", 35, 70, 12), "wet")
        self.assertEqual(weather_tip_key("rain", 10, 0, 0), "wet")
        self.assertEqual(weather_tip_key("clear", 31, 0, 12), "hot")
        self.assertEqual(weather_tip_key("clear", -3, 0, 12), "cold")
        self.assertEqual(weather_tip_key("clear", 15, 0, 9), "windy")
        self.assertEqual(weather_tip_key("clear", 15, 0, 2), "nice")


class FetchTests(SimpleTestCase):
    def setUp(self):
        cache.clear()

    def opener(self, calls, fail_air=False):
        def open_url(request, timeout):
            calls.append(request.full_url)
            if "air-quality" in request.full_url:
                if fail_air:
                    raise TimeoutError()
                return io.BytesIO(json.dumps(raw()["air"]).encode())
            return io.BytesIO(json.dumps(raw()["forecast"]).encode())
        return open_url

    def test_caches_nearby_requests(self):
        calls = []
        fetch_raw(37.5665, 126.978, self.opener(calls))
        fetch_raw(37.5670, 126.979, self.opener(calls))

        self.assertEqual(len(calls), 2)

    def test_keeps_the_forecast_when_air_quality_fails(self):
        data = fetch_raw(35.1, 129.0, self.opener([], fail_air=True))

        self.assertIsNone(data["air"])
        self.assertIn("current", data["forecast"])


class WeatherApiTests(APITestCase):
    @mock.patch("api.views.weather.fetch_raw", return_value=raw())
    def test_returns_the_briefing_without_login(self, _):
        response = self.client.get("/api/v1/weather/?lat=37.5&lng=127.0&language=en")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["data"]["air_label"], "Unhealthy")

    def test_rejects_bad_coordinates(self):
        for query in ("", "?lat=91&lng=0", "?lat=a&lng=0"):
            self.assertEqual(self.client.get(f"/api/v1/weather/{query}").status_code, 400)

    @mock.patch("api.views.weather.fetch_raw", side_effect=WeatherError("down"))
    def test_upstream_failure_is_503(self, _):
        self.assertEqual(self.client.get("/api/v1/weather/?lat=37.5&lng=127.0").status_code, 503)
