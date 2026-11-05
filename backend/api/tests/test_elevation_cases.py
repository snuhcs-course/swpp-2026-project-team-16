from unittest import mock

from django.test import SimpleTestCase

from api.route_algorithm.algorithm import count_steps, create_routes
from api.route_algorithm.briefing import RouteFeatures, build_briefing_prompt, fallback_briefing
from api.route_algorithm.classes import GeoJSONPoint
from api.route_algorithm.elevation import ClimbProfile, climb_profile, climb_profiles, climbs_late, sample_route
from api.route_algorithm.llm import LLMError
from api.route_algorithm.scoring import NOTE_NO_HILLS, NOTE_SLOPED_START, climb_amounts
from api.tests.fakes import SNU, fake_elevations, grid_graph


def profile(gain=0, loss=None, sustained=0):
    return ClimbProfile(
        total_gain_m=gain,
        uphill_segments=[],
        total_loss_m=gain if loss is None else loss,
        sustained_gain_m=sustained,
    )


class ClimbProfileExtrasTests(SimpleTestCase):
    def test_counts_descent_and_sustained_climbs(self):
        elevations = [0, 0, 0, 10, 20, 30, 30, 30, 20, 10, 10, 10]
        distances = [i * 50 for i in range(len(elevations))]

        result = climb_profile(distances, elevations)

        self.assertEqual(result.total_gain_m, 30)
        self.assertEqual(result.total_loss_m, 20)
        self.assertEqual(result.sustained_gain_m, 30)

    def test_short_spikes_in_the_data_are_ignored(self):
        elevations = [16, 19, 15, 21, 33, 37, 37, 12, 12, 14, 12, 12]
        result = climb_profile([i * 50 for i in range(len(elevations))], elevations)

        self.assertLess(result.total_gain_m, 10)
        self.assertEqual(result.sustained_gain_m, 0)

    def test_steep_but_real_climbs_are_kept(self):
        elevations = [0, 0, 0, 10, 20, 30, 40, 40, 40, 40]
        result = climb_profile([i * 50 for i in range(len(elevations))], elevations)

        self.assertEqual(result.total_gain_m, 40)

    def test_noise_is_not_a_sustained_climb(self):
        result = climb_profile([0, 50, 100, 150, 200], [0, 2, 0, 2, 0])
        self.assertEqual(result.sustained_gain_m, 0)

    def test_detects_a_loop_that_saves_the_steep_climb_for_last(self):
        gentle_down_steep_up = [30, 27, 24, 21, 18, 15, 12, 9, 6, 3, 0, 15, 30, 30]
        self.assertTrue(climbs_late(gentle_down_steep_up))
        self.assertFalse(climbs_late(gentle_down_steep_up[::-1]))
        self.assertFalse(climbs_late([0, 1, 0, 1, 0, 1, 0, 1]))

    def test_reversible_routes_move_the_climb_earlier(self):
        route = [(SNU[0], SNU[1]), (SNU[0], SNU[1] + 1000 / 111_000)]
        samples, _ = sample_route(route)
        count = len(samples)
        descent = round(count * 0.75)
        elevations = [30 - 30 * i / descent for i in range(descent)] + [
            30 * (i + 1) / (count - descent) for i in range(count - descent)
        ]

        with mock.patch("api.route_algorithm.elevation.fetch_elevations", return_value=elevations):
            fixed = climb_profiles([route], reversible=True)[0]
            kept = climb_profiles([route])[0]

        self.assertTrue(fixed.reversed)
        self.assertFalse(kept.reversed)
        self.assertLess(fixed.uphill_segments[0].start_km, kept.uphill_segments[0].start_km)


class ClimbAmountTests(SimpleTestCase):
    def test_without_elevation_climb_is_ignored(self):
        self.assertEqual(climb_amounts([None, None], [0, 0], [3000, 3000], 3.0), ([0.0, 0.0], None))

    def test_compares_against_the_flattest_candidate(self):
        amounts, note = climb_amounts([profile(20), profile(50)], [0, 0], [3000, 3000], 1.0)
        self.assertEqual(amounts, [0, 30])
        self.assertIsNone(note)

    def test_descent_counts_for_point_to_point(self):
        amounts, _ = climb_amounts([profile(10, loss=40), profile(10, loss=10)], [0, 0], [3000, 3000], 3.0)
        self.assertEqual(amounts, [15, 0])

    def test_notes_a_sloped_start_when_flat_is_wanted(self):
        candidates = [profile(40), profile(45)]

        self.assertEqual(climb_amounts(candidates, [0, 0], [3000, 3000], 3.0)[1], NOTE_SLOPED_START)
        self.assertIsNone(climb_amounts(candidates, [0, 0], [3000, 3000], 1.0)[1])
        self.assertIsNone(climb_amounts([profile(5), profile(45)], [0, 0], [3000, 3000], 3.0)[1])

    def test_ignores_hills_below_the_noise_floor(self):
        amounts, note = climb_amounts([profile(8, sustained=0), profile(6, sustained=5)], [0, 0], [3000, 3000], -1.0)
        self.assertEqual(amounts, [0.0, 0.0])
        self.assertEqual(note, NOTE_NO_HILLS)

    def test_caps_the_hill_reward(self):
        amounts, note = climb_amounts([profile(300, sustained=300), profile(60, sustained=60)], [0, 0], [3000, 3000], -1.0)
        self.assertEqual(amounts, [120.0, 60])
        self.assertIsNone(note)

    def test_stairs_count_as_climbing(self):
        amounts, note = climb_amounts([profile(), profile()], [5, 0], [3000, 3000], -1.0)
        self.assertEqual(amounts, [20, 0])
        self.assertIsNone(note)


class TerrainNoteBriefingTests(SimpleTestCase):
    def features(self, note):
        return RouteFeatures(distance_m=3000, total_elevation_gain_m=40, traffic_light_count=1, terrain_note=note)

    def test_prompt_includes_the_note(self):
        self.assertIn("경사지", build_briefing_prompt(self.features(NOTE_SLOPED_START), "ko")["user"])
        self.assertIn("hardly any hills", build_briefing_prompt(self.features(NOTE_NO_HILLS), "en")["user"])
        self.assertNotIn("안내", build_briefing_prompt(self.features(None), "ko")["user"])

    def test_fallback_includes_the_note(self):
        self.assertIn("오르막이 거의 없어", fallback_briefing(self.features(NOTE_NO_HILLS), "ko"))
        self.assertIn("fully flat route", fallback_briefing(self.features(NOTE_SLOPED_START), "en"))


class CountStepsTests(SimpleTestCase):
    def test_counts_edges_tagged_as_steps(self):
        graph = grid_graph(size=3).to_undirected()
        for _, _, data in graph.edges(data=True):
            data["highway"] = "footway"
        graph[0][1][0]["highway"] = "steps"
        graph[1][2][0]["highway"] = ["steps", "footway"]

        self.assertEqual(count_steps(graph, [0, 1, 2, 5]), 2)


@mock.patch("api.route_algorithm.briefing.generate_text", side_effect=LLMError("offline"))
@mock.patch("api.route_algorithm.algorithm.ox.graph.graph_from_point", return_value=grid_graph())
class CreateRoutesTerrainTests(SimpleTestCase):
    def setUp(self):
        for target, value in (
            ("api.route_algorithm.street_graph.local_graph", None),
            ("api.route_algorithm.algorithm.count_signalized_crossings", 0),
        ):
            patcher = mock.patch(target, return_value=value)
            patcher.start()
            self.addCleanup(patcher.stop)

    def create(self, elevations, preferences):
        with mock.patch("api.route_algorithm.elevation.fetch_elevations", side_effect=elevations):
            return create_routes(GeoJSONPoint(coordinates=SNU), 3000, "ko", preferences=preferences)

    def test_flat_area_with_hills_wanted_says_so(self, download, llm):
        routes = self.create(lambda points: [10.0] * len(points), ["hills"])
        self.assertIn("오르막이 거의 없어", routes[0].briefing)

    def test_steep_start_with_flat_wanted_says_so(self, download, llm):
        routes = self.create(lambda points: fake_elevations(points, grade=0.2), ["flat"])
        self.assertIn("경사지", routes[0].briefing)

    def test_loops_climb_first(self, download, llm):
        routes = self.create(fake_elevations, [])
        for route in routes:
            samples, _ = sample_route(route.route[0].coordinates)
            self.assertFalse(climbs_late(fake_elevations(samples)))
