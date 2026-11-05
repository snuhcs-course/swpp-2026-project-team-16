import re

from django.core.management.base import BaseCommand
from django.db.models import Q

from api.models import Route, TemporaryRoute
from api.services.place_translation import translate_names

HANGUL = re.compile(r"[가-힣]")
BATCH_SIZE = 20


def _needs_english(name, name_en):
    return bool(name) and not name_en and bool(HANGUL.search(name))


class Command(BaseCommand):
    help = "Fill in English start and end names for routes saved with only Korean names."

    def handle(self, *args, **options):
        total = 0
        for model in (Route, TemporaryRoute):
            rows = [
                row for row in model.objects.filter(
                    Q(start_name_en="", start_name__gt="") | Q(end_name_en="", end_name__gt="")
                )
                if _needs_english(row.start_name, row.start_name_en) or _needs_english(row.end_name, row.end_name_en)
            ]
            for start in range(0, len(rows), BATCH_SIZE):
                total += self._translate(model, rows[start:start + BATCH_SIZE])
        self.stdout.write(self.style.SUCCESS(f"Translated names for {total} routes."))

    def _translate(self, model, rows):
        names = [name for row in rows for name in (row.start_name, row.end_name)]
        translated = translate_names(names)
        updated = []
        for index, row in enumerate(rows):
            start_en, end_en = translated[2 * index], translated[2 * index + 1]
            if _needs_english(row.start_name, row.start_name_en) and start_en != row.start_name:
                row.start_name_en = start_en[:100]
            if _needs_english(row.end_name, row.end_name_en) and end_en != row.end_name:
                row.end_name_en = end_en[:100]
            updated.append(row)
        model.objects.bulk_update(updated, ["start_name_en", "end_name_en"])
        return sum(1 for row in updated if row.start_name_en or row.end_name_en)
