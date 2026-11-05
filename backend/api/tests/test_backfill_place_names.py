from io import StringIO
from unittest import mock

from django.core.management import call_command
from django.test import TestCase

from api.models import Route


LINE = [{"type": "LineString", "coordinates": [[127.0, 37.0], [127.01, 37.01]]}]


def route(**names):
    return Route.objects.create(distance=3000, briefing="Flat", route=LINE, **names)


@mock.patch(
    "api.management.commands.backfill_place_names.translate_names",
    side_effect=lambda names: [f"{name} (en)" if name else "" for name in names],
)
class BackfillPlaceNamesTests(TestCase):
    def test_fills_english_names_for_korean_routes(self, translate):
        korean = route(start_name="코엑스", end_name="강남역")
        english = route(start_name="COEX")
        done = route(start_name="서울역", start_name_en="Seoul Station")

        call_command("backfill_place_names", stdout=StringIO())

        korean.refresh_from_db()
        english.refresh_from_db()
        done.refresh_from_db()
        self.assertEqual((korean.start_name_en, korean.end_name_en), ("코엑스 (en)", "강남역 (en)"))
        self.assertEqual(english.start_name_en, "")
        self.assertEqual(done.start_name_en, "Seoul Station")

    def test_leaves_names_when_translation_fails(self, translate):
        translate.side_effect = lambda names: list(names)
        korean = route(start_name="코엑스")

        call_command("backfill_place_names", stdout=StringIO())

        korean.refresh_from_db()
        self.assertEqual(korean.start_name_en, "")
