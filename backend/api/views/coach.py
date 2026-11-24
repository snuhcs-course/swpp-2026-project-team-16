from datetime import timedelta

from django.utils import timezone
from rest_framework import status
from rest_framework.decorators import api_view, throttle_classes
from rest_framework.response import Response
from rest_framework.throttling import ScopedRateThrottle

from api.models import RouteRun
from api.services.language import request_language
from api.services.run_coach import (
    MAX_DISTANCE_M,
    MAX_GOAL_TEXT_LENGTH,
    MAX_PACE_S,
    MIN_PACE_S,
    RunningStyle,
    generate_comment,
    plan_from_goal,
    recommend_next_run,
    report_facts,
    validate_running_style,
)
from api.services.live_run_coach import generate_live_coaching, validate_live_coach_request
from api.route_algorithm.llm import LLMError
from api.views.profile import get_profile


RECENT_RUN_DAYS = 14
MAX_RECENT_RUNS = 10


class LiveRunCoachThrottle(ScopedRateThrottle):
    scope = "live_coach"


def _error(message, code):
    return Response({"status": "error", "message": message}, status=code)


def _auth_required():
    return _error("Authentication required.", status.HTTP_401_UNAUTHORIZED)


def user_style(user):
    if not user.is_authenticated:
        return RunningStyle()
    return RunningStyle.from_dict(get_profile(user).running_style)


@api_view(["GET", "PUT"])
def running_style(request):
    if not request.user.is_authenticated:
        return _auth_required()
    profile = get_profile(request.user)
    if request.method == "PUT":
        style, error = validate_running_style(request.data)
        if error:
            return _error(error, status.HTTP_400_BAD_REQUEST)
        profile.running_style = style.to_dict()
        profile.save(update_fields=["running_style"])
    return Response(
        {"status": "success", "data": RunningStyle.from_dict(profile.running_style).to_dict()},
        status=status.HTTP_200_OK,
    )


@api_view(["POST"])
def run_plan(request):
    text = request.data.get("text")
    if not isinstance(text, str) or not text.strip():
        return _error("Tell us how you want to run today.", status.HTTP_400_BAD_REQUEST)
    if len(text.strip()) > MAX_GOAL_TEXT_LENGTH:
        return _error(f"The goal must be {MAX_GOAL_TEXT_LENGTH} characters or fewer.", status.HTTP_400_BAD_REQUEST)
    distance_m = request.data.get("distance_m")
    if isinstance(distance_m, bool) or not isinstance(distance_m, int) or not 0 < distance_m <= MAX_DISTANCE_M:
        distance_m = None
    pace_min_s = request.data.get("pace_min_s")
    pace_max_s = request.data.get("pace_max_s")
    if (pace_min_s is None) != (pace_max_s is None):
        return _error("Provide both pace_min_s and pace_max_s.", status.HTTP_400_BAD_REQUEST)
    if pace_min_s is not None and (
        isinstance(pace_min_s, bool) or not isinstance(pace_min_s, int)
        or isinstance(pace_max_s, bool) or not isinstance(pace_max_s, int)
        or not MIN_PACE_S <= pace_min_s <= pace_max_s <= MAX_PACE_S
    ):
        return _error("The target pace range is invalid.", status.HTTP_400_BAD_REQUEST)
    alert_on_slow = request.data.get("alert_on_slow", False)
    alert_on_fast = request.data.get("alert_on_fast", False)
    if not isinstance(alert_on_slow, bool) or not isinstance(alert_on_fast, bool):
        return _error("Pace alert settings must be booleans.", status.HTTP_400_BAD_REQUEST)
    plan = plan_from_goal(
        text.strip(),
        user_style(request.user),
        distance_m,
        request_language(request),
        pace_min_s=pace_min_s,
        pace_max_s=pace_max_s,
        alert_on_slow=alert_on_slow,
        alert_on_fast=alert_on_fast,
    )
    return Response({"status": "success", "data": plan}, status=status.HTTP_200_OK)


@api_view(["POST"])
@throttle_classes([LiveRunCoachThrottle])
def live_run_coaching(request):
    context, error = validate_live_coach_request(request.data)
    if error:
        return _error(error, status.HTTP_400_BAD_REQUEST)
    try:
        message = generate_live_coaching(context, request_language(request))
    except LLMError:
        return _error("Live coaching is temporarily unavailable.", status.HTTP_503_SERVICE_UNAVAILABLE)
    return Response({"status": "success", "data": {"message": message}}, status=status.HTTP_200_OK)


def _recent_runs(run):
    since = run.finished_at - timedelta(days=RECENT_RUN_DAYS)
    runs = (
        RouteRun.objects.filter(user=run.user, finished_at__gte=since, finished_at__lt=run.finished_at)
        .order_by("-finished_at", "-id")[:MAX_RECENT_RUNS]
    )
    return [
        {
            "date": timezone.localtime(item.finished_at).date().isoformat(),
            "distance_m": item.distance_m,
            "run_type": (item.plan or {}).get("run_type"),
            "average_pace_s": int(round(item.duration_s * 1000 / item.distance_m)) if item.distance_m >= 100 else None,
        }
        for item in runs
    ]


def build_report(run, language):
    facts = report_facts(run.plan, run.splits or [], run.distance_m, run.duration_s, run.off_route_count, run.off_route_seconds)
    style = user_style(run.user)
    return {
        **facts,
        "plan": run.plan,
        "comment": generate_comment(run.plan, facts, language),
        "recommendation": recommend_next_run(style, run.plan, facts, _recent_runs(run), language),
        "language": language,
    }


@api_view(["GET"])
def run_report(request, run_id):
    if not request.user.is_authenticated:
        return _auth_required()
    try:
        run = RouteRun.objects.get(id=run_id, user=request.user)
    except RouteRun.DoesNotExist:
        return _error("Run not found.", status.HTTP_404_NOT_FOUND)
    language = request_language(request)
    if not run.report or run.report.get("language") != language:
        run.report = build_report(run, language)
        run.save(update_fields=["report"])
    return Response({"status": "success", "data": run.report}, status=status.HTTP_200_OK)
