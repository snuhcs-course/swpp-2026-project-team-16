from datetime import timedelta
import re

from django.contrib.admin.views.decorators import staff_member_required
from django.db.models import Avg, Count, Q
from django.shortcuts import render
from django.utils import timezone
from rest_framework import serializers, status
from rest_framework.decorators import api_view, authentication_classes, permission_classes, throttle_classes
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.throttling import AnonRateThrottle

from api.models import TelemetryEvent


MAX_BATCH_SIZE = 25
MAX_BATCH_BYTES = 16_384
MAX_DURATION_MS = 600_000
EVENT_OUTCOMES = {
    "route_generation": {"started", "succeeded", "failed"},
    "place_search": {"started", "succeeded", "failed"},
    "authentication": {"started", "succeeded", "failed"},
    "pace_plan": {"started", "succeeded", "failed"},
    "run_session": {"started", "completed", "failed"},
    "run_report": {"started", "succeeded", "failed"},
    "app_crash": {"crashed"},
}
SUCCESS_TARGETS = {
    "route_generation": 90,
    "place_search": 85,
    "pace_plan": 90,
    "run_report": 85,
}


class TelemetryThrottle(AnonRateThrottle):
    scope = "telemetry"


class TelemetryEventInputSerializer(serializers.Serializer):
    event_name = serializers.ChoiceField(choices=[name for name, _ in TelemetryEvent.EVENT_NAMES])
    outcome = serializers.ChoiceField(choices=[name for name, _ in TelemetryEvent.OUTCOMES])
    duration_ms = serializers.IntegerField(min_value=0, max_value=MAX_DURATION_MS, required=False, allow_null=True)
    failure_category = serializers.ChoiceField(
        choices=[name for name, _ in TelemetryEvent.FAILURE_CATEGORIES], required=False, allow_blank=True
    )
    app_version = serializers.RegexField(re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,31}$"), max_length=32)

    def to_internal_value(self, data):
        allowed = {"event_name", "outcome", "duration_ms", "failure_category", "app_version"}
        if isinstance(data, dict) and set(data) - allowed:
            raise serializers.ValidationError("Only the documented telemetry fields are accepted.")
        return super().to_internal_value(data)

    def validate(self, attrs):
        if attrs["outcome"] not in EVENT_OUTCOMES[attrs["event_name"]]:
            raise serializers.ValidationError({"outcome": "This outcome is not valid for the event."})
        if attrs["outcome"] not in {"failed", "crashed"} and attrs.get("failure_category"):
            raise serializers.ValidationError({"failure_category": "Only failed or crashed events can include a category."})
        return attrs


class TelemetryBatchInputSerializer(serializers.Serializer):
    events = TelemetryEventInputSerializer(many=True, allow_empty=False, max_length=MAX_BATCH_SIZE)

    def to_internal_value(self, data):
        if isinstance(data, dict) and set(data) != {"events"}:
            raise serializers.ValidationError("The request may contain only an events array.")
        return super().to_internal_value(data)


@api_view(["POST"])
@authentication_classes([])
@permission_classes([AllowAny])
@throttle_classes([TelemetryThrottle])
def collect_telemetry(request):
    """Accept only enumerated, non-identifying app events; never associate them with a user."""
    if len(request.body) > MAX_BATCH_BYTES:
        return Response(
            {"status": "error", "message": "Telemetry batch is too large."},
            status=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
        )
    serializer = TelemetryBatchInputSerializer(data=request.data)
    if not serializer.is_valid():
        return Response(
            {"status": "error", "message": "Invalid telemetry batch.", "errors": serializer.errors},
            status=status.HTTP_400_BAD_REQUEST,
        )

    rows = [TelemetryEvent(**event) for event in serializer.validated_data["events"]]
    TelemetryEvent.objects.bulk_create(rows)
    return Response({"accepted": len(rows)}, status=status.HTTP_202_ACCEPTED)


@staff_member_required
def telemetry_dashboard(request):
    try:
        days = int(request.GET.get("days", "7"))
    except (TypeError, ValueError):
        days = 7
    days = min(max(days, 1), 90)
    cutoff = timezone.now() - timedelta(days=days)
    events = TelemetryEvent.objects.filter(received_at__gte=cutoff)
    counts = {
        (row["event_name"], row["outcome"]): row["count"]
        for row in events.values("event_name", "outcome").annotate(count=Count("id"))
    }
    avg_durations = {
        row["event_name"]: row["average"]
        for row in events.filter(outcome__in=["succeeded", "failed"])
        .values("event_name")
        .annotate(average=Avg("duration_ms"))
    }
    failure_counts = {
        (row["event_name"], row["failure_category"]): row["count"]
        for row in events.exclude(failure_category="").values("event_name", "failure_category").annotate(count=Count("id"))
    }
    app_versions = events.values("app_version").annotate(
        total=Count("id"),
        crashes=Count("id", filter=Q(outcome="crashed")),
    ).order_by("app_version")

    rows = []
    for event_name, label in TelemetryEvent.EVENT_NAMES:
        started = counts.get((event_name, "started"), 0)
        succeeded = counts.get((event_name, "succeeded"), 0)
        failed = counts.get((event_name, "failed"), 0)
        completed = counts.get((event_name, "completed"), 0)
        crashed = counts.get((event_name, "crashed"), 0)
        reasons = [
            f"{label}: {count}"
            for category, label in TelemetryEvent.FAILURE_CATEGORIES
            if (count := failure_counts.get((event_name, category), 0))
        ]
        denominator = succeeded + failed
        success_percent = round(100 * succeeded / denominator, 1) if denominator else None
        completion_percent = round(100 * completed / started, 1) if event_name == "run_session" and started else None
        rows.append({
            "name": label,
            "started": started,
            "succeeded": succeeded,
            "failed": failed,
            "completed": completed,
            "crashed": crashed,
            "failure_reasons": ", ".join(reasons) if reasons else "—",
            "reported_results": denominator,
            "success_percent": success_percent,
            "completion_percent": completion_percent,
            "success_label": f"{success_percent}%" if success_percent is not None else "—",
            "completion_label": f"{completion_percent}% completed" if completion_percent is not None else "—",
            "success_target": f"≥ {SUCCESS_TARGETS[event_name]}%" if event_name in SUCCESS_TARGETS else "—",
            "latency_target": {"route_generation": "≤ 30 s", "place_search": "≤ 3 s", "authentication": "≤ 5 s", "pace_plan": "≤ 10 s", "run_report": "≤ 10 s"}.get(event_name, "—"),
            "average_duration_ms": round(avg_durations[event_name]) if avg_durations.get(event_name) is not None else None,
        })

    return render(request, "api/telemetry_dashboard.html", {
        "days": days,
        "rows": rows,
        "total_events": events.count(),
        "crash_count": counts.get(("app_crash", "crashed"), 0),
        "app_versions": app_versions,
        "since": cutoff,
    })
