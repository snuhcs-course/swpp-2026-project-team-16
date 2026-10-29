from django.test import SimpleTestCase

from api.route_algorithm.crosswalk_counter import (
    SignalizedCrosswalk,
    SignalizedCrosswalkCounter,
)


class SignalizedCrosswalkCounterTests(SimpleTestCase):
    def test_counts_a_signalized_crossing_near_the_route(self):
        counter = SignalizedCrosswalkCounter([
            SignalizedCrosswalk("cw-1", "junction-1", 50, 4),
        ])

        self.assertEqual(counter.count_projected_route([(0, 0), (100, 0)]), 1)

    def test_ignores_crosswalks_outside_the_route_corridor(self):
        counter = SignalizedCrosswalkCounter([
            SignalizedCrosswalk("cw-near", "junction-1", 50, 4),
            SignalizedCrosswalk("cw-far", "junction-2", 50, 40),
        ])

        self.assertEqual(counter.count_projected_route([(0, 0), (100, 0)]), 1)

    def test_merges_multiple_crosswalk_records_for_one_intersection_visit(self):
        counter = SignalizedCrosswalkCounter([
            SignalizedCrosswalk("cw-1", "junction-1", 48, 2),
            SignalizedCrosswalk("cw-2", "junction-1", 54, -2),
        ])

        self.assertEqual(counter.count_projected_route([(0, 0), (100, 0)]), 1)

    def test_counts_the_same_intersection_again_when_route_returns_later(self):
        counter = SignalizedCrosswalkCounter([
            SignalizedCrosswalk("cw-1", "junction-1", 50, 0),
        ])

        route = [(0, 0), (100, 0), (0, 0)]
        self.assertEqual(counter.count_projected_route(route), 2)

    def test_counts_separate_signalized_intersections(self):
        counter = SignalizedCrosswalkCounter([
            SignalizedCrosswalk("cw-1", "junction-1", 25, 0),
            SignalizedCrosswalk("cw-2", "junction-2", 75, 0),
        ])

        self.assertEqual(counter.count_projected_route([(0, 0), (100, 0)]), 2)

    def test_empty_or_single_point_route_has_no_crossings(self):
        counter = SignalizedCrosswalkCounter([
            SignalizedCrosswalk("cw-1", "junction-1", 50, 0),
        ])

        self.assertEqual(counter.count_projected_route([]), 0)
        self.assertEqual(counter.count_projected_route([(50, 0)]), 0)
