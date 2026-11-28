import logging
import os
import threading
import time
from datetime import timedelta

from django.db import close_old_connections, connection
from django.utils import timezone

from api.models import RunningGroup
from api.services.notifications import notify


logger = logging.getLogger(__name__)

REMINDER_LEAD = timedelta(minutes=10)
INTERVAL_SECONDS = 60
ENV_FLAG = "GROUP_REMINDERS"

_started = False
_start_lock = threading.Lock()


def send_due_group_reminders(now=None, sender=None):
    now = now or timezone.now()
    due = RunningGroup.objects.filter(
        meet_at__gt=now,
        meet_at__lte=now + REMINDER_LEAD,
        reminder_sent_at__isnull=True,
    ).values_list("id", flat=True)
    reminded = 0
    for group_id in list(due):
        claimed = RunningGroup.objects.filter(id=group_id, reminder_sent_at__isnull=True).update(reminder_sent_at=now)
        if claimed != 1:
            continue
        group = RunningGroup.objects.get(id=group_id)
        for member in group.members.select_related("user"):
            notify(member.user, "group_reminder", group=group, sender=sender)
        reminded += 1
    return reminded


def _run_forever(interval):
    while True:
        close_old_connections()
        try:
            send_due_group_reminders()
        except Exception:
            logger.exception("Sending group reminders failed")
        finally:
            connection.close()
        time.sleep(interval)


def start_reminder_thread(interval=INTERVAL_SECONDS):
    global _started
    if os.getenv(ENV_FLAG) != "1":
        return False
    with _start_lock:
        if _started:
            return False
        _started = True
    threading.Thread(target=_run_forever, args=(interval,), name="group-reminders", daemon=True).start()
    return True
