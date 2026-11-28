from datetime import timedelta
from io import StringIO
from unittest import mock

from django.contrib.auth import get_user_model
from django.core.management import call_command
from django.test import TestCase
from django.utils import timezone

from api.models import GroupMember, Notification, Route, RunningGroup
from api.services import group_reminders
from api.services.group_reminders import send_due_group_reminders, start_reminder_thread
from api.services.notifications import push_payload, render
from api.views.profile import get_profile


LINE = [{"type": "LineString", "coordinates": [[127.0, 37.0], [127.01, 37.01]]}]


class GroupReminderTests(TestCase):
    def setUp(self):
        User = get_user_model()
        self.host = User.objects.create_user(username="host", email="h@test.com", password="password123")
        self.runner = User.objects.create_user(username="runner", email="r@test.com", password="password123")
        self.route = Route.objects.create(created_by=self.host, distance=3000, briefing="b", route=LINE)
        self.now = timezone.now()
        self.sent = []

    def make_group(self, meet_in, title="Crew"):
        meet_at = self.now + meet_in
        group = RunningGroup.objects.create(
            host=self.host, route=self.route, title=title, capacity=4,
            meet_at=meet_at, join_deadline=meet_at - timedelta(minutes=1),
            start_latitude=37.0, start_longitude=127.0,
        )
        for user in (self.host, self.runner):
            GroupMember.objects.create(group=group, user=user)
        return group

    def run_reminders(self, now=None):
        return send_due_group_reminders(now=now or self.now, sender=self.sent.append)

    def test_reminds_every_member_including_the_host_once(self):
        group = self.make_group(timedelta(minutes=9))

        self.assertEqual(self.run_reminders(), 1)
        self.assertEqual(self.run_reminders(self.now + timedelta(minutes=1)), 0)

        notifications = Notification.objects.filter(kind="group_reminder")
        self.assertEqual(sorted(n.recipient.username for n in notifications), ["host", "runner"])
        self.assertEqual(len(self.sent), 2)
        group.refresh_from_db()
        self.assertEqual(group.reminder_sent_at, self.now)
        notification = notifications.first()
        self.assertEqual(render(notification, "ko"), ("곧 그룹 러닝이 시작돼요", "'Crew' 러닝이 10분 안에 시작해요."))
        self.assertEqual(render(notification, "en")[1], "'Crew' starts within 10 minutes.")
        self.assertEqual(push_payload(notification, "en")["group_id"], str(group.id))

    def test_skips_far_future_and_past_groups(self):
        self.make_group(timedelta(minutes=11), title="Later")
        self.make_group(timedelta(minutes=-1), title="Past")

        self.assertEqual(self.run_reminders(), 0)
        self.assertFalse(Notification.objects.exists())

    def test_reminds_a_group_once_it_comes_into_range(self):
        self.make_group(timedelta(minutes=30))

        self.assertEqual(self.run_reminders(), 0)
        self.assertEqual(self.run_reminders(self.now + timedelta(minutes=25)), 1)

    def test_does_not_remind_an_already_claimed_group(self):
        group = self.make_group(timedelta(minutes=5))
        RunningGroup.objects.filter(id=group.id).update(reminder_sent_at=self.now - timedelta(minutes=1))

        self.assertEqual(self.run_reminders(), 0)
        self.assertFalse(Notification.objects.exists())

    def test_losing_the_claim_race_sends_nothing(self):
        group = self.make_group(timedelta(minutes=5))
        original_filter = RunningGroup.objects.filter

        def claim_first(*args, **kwargs):
            if "id" in kwargs and "reminder_sent_at__isnull" in kwargs:
                original_filter(id=group.id).update(reminder_sent_at=self.now)
            return original_filter(*args, **kwargs)

        with mock.patch.object(RunningGroup.objects, "filter", side_effect=claim_first):
            self.assertEqual(self.run_reminders(), 0)
        self.assertFalse(Notification.objects.exists())

    def test_respects_disabled_reminder_setting(self):
        self.make_group(timedelta(minutes=5))
        profile = get_profile(self.runner)
        profile.notification_settings = {"group_reminder": False}
        profile.save()

        self.run_reminders()

        self.assertEqual(Notification.objects.filter(kind="group_reminder").count(), 2)
        self.assertEqual([n.recipient.username for n in self.sent], ["host"])

    @mock.patch("api.services.notifications.send_push_async")
    def test_management_command(self, _):
        self.make_group(timedelta(minutes=5))
        out = StringIO()

        call_command("send_group_reminders", stdout=out)

        self.assertIn("1 group", out.getvalue())
        self.assertEqual(Notification.objects.filter(kind="group_reminder").count(), 2)

    @mock.patch("api.services.group_reminders.threading.Thread")
    def test_thread_starts_only_when_enabled_and_once(self, thread):
        with mock.patch.dict("os.environ", {"GROUP_REMINDERS": ""}), mock.patch.object(group_reminders, "_started", False):
            self.assertFalse(start_reminder_thread())
        with mock.patch.dict("os.environ", {"GROUP_REMINDERS": "1"}), mock.patch.object(group_reminders, "_started", False):
            self.assertTrue(start_reminder_thread())
            self.assertFalse(start_reminder_thread())
        thread.assert_called_once()
        self.assertTrue(thread.call_args.kwargs["daemon"])
