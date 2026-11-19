from datetime import timedelta

from django.contrib.auth import get_user_model
from django.test import TestCase
from django.urls import reverse
from django.utils import timezone
from rest_framework.test import APIClient

from api.models import TelemetryEvent


class TelemetryCollectionTests(TestCase):
    def setUp(self):
        self.client = APIClient()
        self.url = "/api/v1/telemetry/events/"

    def event(self, **overrides):
        return {
            "event_name": "route_generation",
            "outcome": "succeeded",
            "duration_ms": 2400,
            "app_version": "1.0",
            **overrides,
        }

    def test_accepts_allowlisted_anonymous_events_without_user_linkage(self):
        response = self.client.post(self.url, {"events": [self.event()]}, format="json")

        self.assertEqual(response.status_code, 202)
        self.assertEqual(response.data["accepted"], 1)
        stored = TelemetryEvent.objects.get()
        self.assertEqual((stored.event_name, stored.outcome, stored.duration_ms), ("route_generation", "succeeded", 2400))
        self.assertFalse(any(field.name in {"user", "device_id", "latitude", "longitude"} for field in stored._meta.fields))

    def test_rejects_identifiers_free_text_and_unknown_fields(self):
        response = self.client.post(
            self.url,
            {"events": [self.event(email="runner@example.com", query="강남역", latitude=37.5)]},
            format="json",
        )

        self.assertEqual(response.status_code, 400)
        self.assertFalse(TelemetryEvent.objects.exists())

    def test_rejects_unrecognized_outcomes_and_event_combinations(self):
        response = self.client.post(self.url, {"events": [self.event(outcome="completed")]}, format="json")

        self.assertEqual(response.status_code, 400)
        self.assertFalse(TelemetryEvent.objects.exists())

    def test_rejects_oversized_batches_and_durations(self):
        oversized = self.client.post(
            self.url,
            {"events": [self.event() for _ in range(26)]},
            format="json",
        )
        too_slow = self.client.post(
            self.url,
            {"events": [self.event(duration_ms=900_000)]},
            format="json",
        )

        self.assertEqual(oversized.status_code, 400)
        self.assertEqual(too_slow.status_code, 400)
        self.assertFalse(TelemetryEvent.objects.exists())

    def test_rejects_failure_category_on_success(self):
        response = self.client.post(
            self.url,
            {"events": [self.event(failure_category="server")]},
            format="json",
        )

        self.assertEqual(response.status_code, 400)
        self.assertFalse(TelemetryEvent.objects.exists())


class TelemetryDashboardTests(TestCase):
    def test_dashboard_requires_staff_access(self):
        response = self.client.get("/telemetry/dashboard/")

        self.assertIn(response.status_code, (302, 403))

    def test_dashboard_shows_counts_and_uses_reported_result_denominator(self):
        staff = get_user_model().objects.create_user(username="telemetry-admin", password="test-pass", is_staff=True)
        self.client.force_login(staff)
        now = timezone.now()
        TelemetryEvent.objects.create(event_name="route_generation", outcome="started", app_version="1.0")
        TelemetryEvent.objects.create(event_name="route_generation", outcome="succeeded", duration_ms=2000, app_version="1.0")
        TelemetryEvent.objects.create(event_name="route_generation", outcome="failed", duration_ms=4000, failure_category="server", app_version="1.0")
        TelemetryEvent.objects.create(event_name="app_crash", outcome="crashed", failure_category="unknown", app_version="1.0")
        old_event = TelemetryEvent.objects.create(event_name="route_generation", outcome="succeeded", app_version="0.9")
        TelemetryEvent.objects.filter(pk=old_event.pk).update(received_at=now - timedelta(days=100))

        response = self.client.get("/telemetry/dashboard/?days=7")

        self.assertEqual(response.status_code, 200)
        self.assertContains(response, "Reported app crashes")
        self.assertContains(response, "50.0%")
        self.assertContains(response, "Operation success rate")
        self.assertContains(response, "≥ 90%")
        self.assertContains(response, "≤ 30 s")
        self.assertContains(response, "Server: 1")
        self.assertContains(response, "Reported crashes")
        self.assertNotContains(response, "0.9")
