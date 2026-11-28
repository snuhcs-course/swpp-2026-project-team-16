import json

from django.contrib.admin.views.decorators import staff_member_required
from django.core.paginator import Paginator
from django.db.models import Count, Max
from django.shortcuts import render

from api.models import ActivityLog

PAGE_SIZE = 100
ACTION_LABELS = dict(ActivityLog.ACTIONS)


def _km(meters):
    return f"{meters / 1000:.2f} km" if isinstance(meters, (int, float)) else "?"


def _clock(seconds):
    if not isinstance(seconds, (int, float)):
        return "?"
    seconds = int(seconds)
    hours, rest = divmod(seconds, 3600)
    minutes, seconds = divmod(rest, 60)
    return f"{hours}:{minutes:02d}:{seconds:02d}" if hours else f"{minutes}:{seconds:02d}"


def _point(point, name=""):
    if name:
        return name
    if isinstance(point, list) and len(point) == 2:
        return f"{point[1]:.5f}, {point[0]:.5f}"
    return "?"


def _short(text, limit=80):
    text = (text or "").replace("\n", " ")
    return text if len(text) <= limit else text[: limit - 1] + "…"


def summarize(log):
    d = log.details or {}
    action = log.action
    if action == "route_generated":
        end = _point(d.get("end"), d.get("end_name")) if d.get("end") else "loop"
        got = ", ".join(_km(option.get("distance_m")) for option in d.get("options", []))
        return f"{_point(d.get('start'), d.get('start_name'))} → {end} · asked {_km(d.get('requested_distance_m'))} · got {got or '?'}"
    if action == "run_recorded":
        kind = "free run" if d.get("free_run") else f"route #{d.get('route_id')}"
        pace = _clock(d.get("average_pace_s_per_km")) + " /km" if d.get("average_pace_s_per_km") else "--"
        speed = f"{d.get('average_speed_kmh')} km/h" if d.get("average_speed_kmh") is not None else "--"
        return f"{_km(d.get('distance_m'))} in {_clock(d.get('duration_s'))} · {pace} · {speed} · {kind}"
    if action == "route_saved":
        return f"route #{d.get('route_id')} {_km(d.get('distance_m'))} {d.get('name') or ''}".strip()
    if action == "run_rated":
        return f"run #{d.get('run_id')} rated {d.get('rating')}/5"
    if action == "route_shared":
        return f"#{d.get('shared_route_id')} {_short(d.get('title'))}"
    if action == "route_reviewed":
        return f"shared route #{d.get('shared_route_id')} · {d.get('rating')}/5 · {_short(d.get('comment'))}"
    if action == "post_created":
        return _short(d.get("title"))
    if action == "comment_created":
        return f"post #{d.get('post_id')} · {_short(d.get('body'))}"
    if action in ("group_created", "group_joined", "group_left"):
        return f"#{d.get('group_id')} {_short(d.get('title'))}"
    if action == "group_message":
        return f"group #{d.get('group_id')} · {_short(d.get('text'))}"
    if action in ("followed", "unfollowed", "blocked", "unblocked"):
        return d.get("username", "")
    if action in ("signed_up", "logged_in"):
        return d.get("method", "")
    return ""


@staff_member_required
def activity_dashboard(request):
    username = request.GET.get("user", "").strip()
    action = request.GET.get("action", "")
    logs = ActivityLog.objects.select_related("user")
    if username == "guest":
        logs = logs.filter(user__isnull=True)
    elif username:
        logs = logs.filter(user__username__iexact=username)
    if action in ACTION_LABELS:
        logs = logs.filter(action=action)
    else:
        action = ""

    page = Paginator(logs, PAGE_SIZE).get_page(request.GET.get("page"))
    rows = [
        {
            "time": log.created_at,
            "user": log.user.username if log.user else "guest",
            "action": ACTION_LABELS.get(log.action, log.action),
            "summary": summarize(log),
            "details": json.dumps(log.details, ensure_ascii=False, indent=2),
        }
        for log in page
    ]
    users = (
        ActivityLog.objects.exclude(user__isnull=True)
        .values("user__username")
        .annotate(count=Count("id"), last=Max("created_at"))
        .order_by("-last")[:30]
    )
    return render(request, "api/activity_log.html", {
        "rows": rows,
        "page": page,
        "username": username,
        "action": action,
        "actions": ActivityLog.ACTIONS,
        "users": users,
        "total": page.paginator.count,
    })
