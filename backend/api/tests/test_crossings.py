import json
from pathlib import Path

from django.test import SimpleTestCase

from api.route_algorithm.crossings import (
    count_crossings,
    count_keywords,
    extract_guidance_texts,
)


SAMPLE_PATH = Path(__file__).parent / "data" / "tmap_pedestrian_snu_to_nakseongdae.json"


def point(description, turn_type=11):
    return {
        "type": "Feature",
        "geometry": {"type": "Point", "coordinates": [126.95, 37.46]},
        "properties": {"description": description, "turnType": turn_type},
    }


def line(description):
    return {
        "type": "Feature",
        "geometry": {"type": "LineString", "coordinates": [[126.95, 37.46], [126.96, 37.47]]},
        "properties": {"description": description},
    }


class CountCrossingsSampleTests(SimpleTestCase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.response = json.loads(SAMPLE_PATH.read_text(encoding="utf-8"))

    def test_counts_crosswalks_in_tmap_sample(self):
        counts = count_crossings(self.response)
        self.assertEqual(counts.crosswalks, 3)
        self.assertEqual(counts.crosswalk_turns, 3)
        self.assertEqual(counts.traffic_lights, 0)

    def test_extracts_only_point_descriptions(self):
        texts = extract_guidance_texts(self.response)
        self.assertEqual(len(texts), 14)
        self.assertIn("낙성대빌딩에서 횡단보도 후 38m 이동", texts)


class CountCrossingsTests(SimpleTestCase):
    def test_counts_keywords_in_guidance_text(self):
        response = {
            "features": [
                point("신호등 앞 횡단보도 후 20m 이동", turn_type=211),
                point("좌측 횡단보도 후 10m 이동", turn_type=212),
                point("신호등에서 우회전 후 50m 이동", turn_type=13),
                line("보행자도로, 30m"),
            ]
        }
        counts = count_crossings(response)
        self.assertEqual(
            counts.to_dict(),
            {"crosswalks": 2, "traffic_lights": 2, "crosswalk_turns": 2},
        )

    def test_ignores_line_string_descriptions(self):
        response = {"features": [line("횡단보도, 38m")]}
        self.assertEqual(count_crossings(response).crosswalks, 0)

    def test_handles_empty_or_missing_fields(self):
        self.assertEqual(count_crossings({}).to_dict(), {"crosswalks": 0, "traffic_lights": 0, "crosswalk_turns": 0})
        response = {"features": [{"geometry": {"type": "Point"}, "properties": {"turnType": 200}}]}
        self.assertEqual(extract_guidance_texts(response), [])

    def test_count_keywords_counts_every_occurrence(self):
        self.assertEqual(count_keywords(["횡단보도 건너 횡단보도"], ("횡단보도",)), 2)
