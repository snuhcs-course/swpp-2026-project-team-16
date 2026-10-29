from unittest import mock

import requests
from django.test import SimpleTestCase, TestCase

from api.models import ElevationCache
from api.route_algorithm.elevation import (
    ElevationError,
    climb_profile,
    climb_profiles,
    fetch_elevations,
    sample_route,
)
from api.route_algorithm.scoring import ScoreWeights, score_route
from api.tests.fakes import SNU


def north(meters):
    return (SNU[0], SNU[1] + meters / 111_000)


class SampleRouteTests(SimpleTestCase):
    def test_samples_every_50m_including_both_ends(self):
        samples, distances = sample_route([north(0), north(200)])

        self.assertEqual(len(samples), 5)
        self.assertEqual(samples[0], north(0))
        self.assertAlmostEqual(samples[-1][1], north(200)[1])
        self.assertAlmostEqual(distances[-1], 200, delta=1)

    def test_long_routes_are_capped(self):
        samples, _ = sample_route([north(0), north(50_000)])
        self.assertEqual(len(samples), 201)


class ClimbProfileTests(SimpleTestCase):
    def test_flat_route_has_no_climb(self):
        profile = climb_profile([0, 50, 100, 150], [10, 10, 10, 10])
        self.assertEqual((profile.total_gain_m, profile.uphill_segments), (0, []))

    def test_reports_gain_and_uphill_start(self):
        distances = [i * 50 for i in range(21)]
        elevations = [0] * 10 + [i * 4 for i in range(1, 7)] + [24] * 5

        profile = climb_profile(distances, elevations)

        self.assertEqual(profile.total_gain_m, 24)
        self.assertEqual(len(profile.uphill_segments), 1)
        uphill = profile.uphill_segments[0]
        self.assertAlmostEqual(uphill.start_km, 0.5, delta=0.1)
        self.assertEqual(uphill.elevation_gain_m, 24)

    def test_ignores_small_bumps(self):
        profile = climb_profile([0, 50, 100, 150, 200], [0, 2, 0, 2, 0])
        self.assertEqual(profile.uphill_segments, [])

    def test_keeps_the_three_biggest_uphills_in_route_order(self):
        elevations, distances = [], []
        for height in [10, 30, 20, 40]:
            base = len(elevations)
            elevations += [0, 0, height / 2, height, height, 0, 0]
            distances += [(base + i) * 50 for i in range(7)]

        segments = climb_profile(distances, elevations).uphill_segments

        self.assertEqual([s.start_km for s in segments], [0.3, 0.7, 1.1])


class FetchElevationsTests(TestCase):
    def response(self, elevations):
        response = mock.Mock()
        response.json.return_value = {"elevation": elevations}
        response.raise_for_status.return_value = None
        return response

    @mock.patch("api.route_algorithm.elevation.requests.get")
    def test_splits_requests_into_batches_of_100(self, get):
        get.side_effect = lambda url, params, timeout: self.response(
            [float(i) for i in range(len(params["latitude"].split(",")))]
        )

        elevations = fetch_elevations([north(i) for i in range(250)])

        self.assertEqual(get.call_count, 3)
        self.assertEqual(len(elevations), 250)
        self.assertEqual(elevations[100], 0.0)

    @mock.patch("api.route_algorithm.elevation.requests.get", side_effect=requests.Timeout("slow"))
    def test_network_errors_raise_elevation_error(self, get):
        with self.assertRaises(ElevationError):
            fetch_elevations([north(0)])

    @mock.patch("api.route_algorithm.elevation.requests.get")
    def test_mismatched_response_raises_elevation_error(self, get):
        get.return_value = self.response([1.0])
        with self.assertRaises(ElevationError):
            fetch_elevations([north(0), north(50)])

    @mock.patch("api.route_algorithm.elevation.fetch_elevations", side_effect=ElevationError("down"))
    def test_climb_profiles_returns_none_when_lookup_fails(self, fetch):
        self.assertIsNone(climb_profiles([[north(0), north(100)]]))


class ScoreRouteTests(SimpleTestCase):
    def test_combines_climb_signals_and_distance_error(self):
        weights = ScoreWeights(per_climb_m=1, per_traffic_light=10, per_distance_error_percent=5)
        self.assertEqual(score_route(25, 3, 5100, 5000, weights), 25 + 30 + 10)

    def test_unknown_climb_counts_as_zero(self):
        self.assertEqual(score_route(None, 0, 5000, 5000), 0)


class ElevationCacheTests(TestCase):
    def test_fetches_and_stores_on_cache_miss_then_reuses_cached_values(self):
        points = [(126.952, 37.46), (126.953, 37.461)]

        with mock.patch(
            "api.route_algorithm.elevation._fetch_batch",
            side_effect=lambda batch: [120.0 + index for index, _ in enumerate(batch)],
        ) as provider:
            first = fetch_elevations(points)
            second = fetch_elevations(list(reversed(points)))

        self.assertEqual(first, [120.0, 121.0])
        self.assertEqual(second, [121.0, 120.0])
        provider.assert_called_once()
        self.assertEqual(ElevationCache.objects.count(), 2)

    def test_fetches_only_missing_unique_coordinates(self):
        cached_point = (126.952, 37.46)
        ElevationCache.objects.create(
            coordinate_key="37.460000,126.952000",
            elevation_m=88.0,
        )
        almost_same_point = (126.9520001, 37.4600001)
        missing_point = (126.953, 37.46)

        with mock.patch(
            "api.route_algorithm.elevation._fetch_batch",
            return_value=[99.0],
        ) as provider:
            result = fetch_elevations([cached_point, almost_same_point, missing_point, cached_point])

        self.assertEqual(result, [88.0, 88.0, 99.0, 88.0])
        provider.assert_called_once_with([missing_point])
        self.assertEqual(ElevationCache.objects.count(), 2)

    @mock.patch(
        "api.route_algorithm.elevation._fetch_batch",
        side_effect=ElevationError("provider unavailable"),
    )
    def test_does_not_cache_when_provider_fails(self, provider):
        with self.assertRaises(ElevationError):
            fetch_elevations([(126.952, 37.46)])

        self.assertFalse(ElevationCache.objects.exists())
