import logging

from django.db import transaction

from api.models import ActivityLog

logger = logging.getLogger(__name__)


def log_activity(user, action, **details):
    actor = user if user is not None and user.is_authenticated else None
    try:
        with transaction.atomic():
            ActivityLog.objects.create(user=actor, action=action, details=details)
    except Exception:
        logger.exception("Could not record activity %s", action)


def route_line_endpoints(lines):
    points = [point for line in lines or [] for point in (line.get("coordinates") or [])]
    if not points:
        return None, None
    return points[0], points[-1]
