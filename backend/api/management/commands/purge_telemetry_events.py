from datetime import timedelta

from django.core.management.base import BaseCommand, CommandError
from django.utils import timezone

from api.models import TelemetryEvent


class Command(BaseCommand):
    help = "Delete consent-based telemetry events older than the configured retention period."

    def add_arguments(self, parser):
        parser.add_argument("--days", type=int, default=90)

    def handle(self, *args, **options):
        days = options["days"]
        if days < 1 or days > 365:
            raise CommandError("--days must be between 1 and 365")
        cutoff = timezone.now() - timedelta(days=days)
        deleted, _ = TelemetryEvent.objects.filter(received_at__lt=cutoff).delete()
        self.stdout.write(self.style.SUCCESS(f"Deleted {deleted} old telemetry record(s)."))
