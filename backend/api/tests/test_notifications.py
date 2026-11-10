from datetime import timedelta
from unittest import mock

from django.contrib.auth import get_user_model
from django.test import TestCase
from django.utils import timezone
from rest_framework.authtoken.models import Token
from rest_framework.test import APITestCase

from api.models import DeviceToken, Follow, GroupMember, Notification, Post, Route, RouteRun, RunningGroup, SharedRoute
from api.services.notifications import notify, push_payload, render


LINE = [{"type": "LineString", "coordinates": [[127.0, 37.0], [127.01, 37.01]]}]


class NotificationTestBase(APITestCase):
    def setUp(self):
        User = get_user_model()
        self.alice = User.objects.create_user(username="alice", email="a@test.com", password="password123")
        self.bob = User.objects.create_user(username="bob", email="b@test.com", password="password123")

    def log_in(self, user):
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {Token.objects.get_or_create(user=user)[0].key}")

    def kinds(self, user):
        return list(Notification.objects.filter(recipient=user).values_list("kind", flat=True))


class NotifyTests(TestCase):
    def setUp(self):
        User = get_user_model()
        self.alice = User.objects.create_user(username="alice", email="a@test.com", password="password123")
        self.bob = User.objects.create_user(username="bob", email="b@test.com", password="password123")

    def test_pushes_only_enabled_kinds_and_never_to_yourself(self):
        sent = []
        notify(self.alice, "follow", actor=self.bob, sender=sent.append)
        self.alice.profile.notification_settings = {"follow": False}
        self.alice.profile.save()
        notify(self.alice, "follow", actor=self.bob, sender=sent.append)
        notify(self.alice, "follow", actor=self.alice, sender=sent.append)

        self.assertEqual(len(sent), 1)
        self.assertEqual(Notification.objects.count(), 2)

    def test_renders_text_in_both_languages_and_hides_anonymous_actors(self):
        post = Post.objects.create(author=self.alice, title="Shoes?", body="b")
        named = notify(self.alice, "post_comment", actor=self.bob, post=post, text="Try these " * 20, sender=lambda n: None)
        anonymous = notify(self.alice, "post_comment", actor=None, post=post, text="hi", sender=lambda n: None)

        self.assertEqual(render(named, "en")[0], "New comment")
        self.assertTrue(render(named, "ko")[1].startswith("bob님이 'Shoes?' 글에 댓글을 남겼어요: Try these"))
        self.assertTrue(render(named, "ko")[1].endswith("…"))
        self.assertTrue(render(anonymous, "en")[1].startswith("Someone commented"))
        payload = push_payload(named, "en")
        self.assertEqual((payload["kind"], payload["post_id"], payload["actor"]), ("post_comment", str(post.id), "bob"))


class NotificationApiTests(NotificationTestBase):
    def test_list_and_mark_read(self):
        notify(self.alice, "follow", actor=self.bob, sender=lambda n: None)
        second = notify(self.alice, "follow", actor=self.bob, sender=lambda n: None)
        self.log_in(self.alice)

        listed = self.client.get("/api/v1/notifications/?language=en").data["data"]
        one = self.client.post("/api/v1/notifications/read/", {"id": second.id}, format="json").data["data"]
        all_read = self.client.post("/api/v1/notifications/read/", {}, format="json").data["data"]

        self.assertEqual(listed["unread_count"], 2)
        self.assertEqual(listed["items"][0]["body"], "bob started following you.")
        self.assertEqual(one["unread_count"], 1)
        self.assertEqual(all_read["unread_count"], 0)

    def test_settings_round_trip(self):
        self.log_in(self.alice)
        defaults = self.client.get("/api/v1/users/my_profile/notification_settings/").data["data"]
        updated = self.client.put("/api/v1/users/my_profile/notification_settings/", {"post_like": False}, format="json").data["data"]
        bad = self.client.put("/api/v1/users/my_profile/notification_settings/", {"spam": True}, format="json")

        self.assertTrue(all(defaults.values()))
        self.assertFalse(updated["post_like"])
        self.assertTrue(updated["follow"])
        self.assertEqual(bad.status_code, 400)

    def test_register_and_remove_a_device(self):
        self.log_in(self.alice)
        self.client.post("/api/v1/devices/?language=en", {"token": "abc"}, format="json")
        self.log_in(self.bob)
        self.client.post("/api/v1/devices/", {"token": "abc"}, format="json")

        device = DeviceToken.objects.get()
        self.assertEqual(device.user, self.bob)
        self.client.delete("/api/v1/devices/", {"token": "abc"}, format="json")
        self.assertFalse(DeviceToken.objects.exists())
        self.assertEqual(self.client.post("/api/v1/devices/", {}, format="json").status_code, 400)


@mock.patch("api.services.notifications.send_push_async")
class NotificationTriggerTests(NotificationTestBase):
    def test_follow(self, _):
        self.log_in(self.bob)
        self.client.post("/api/v1/users/alice/follow/")
        self.client.post("/api/v1/users/alice/follow/")
        self.assertEqual(self.kinds(self.alice), ["follow"])

    def test_like_and_comments(self, _):
        post = Post.objects.create(author=self.alice, title="Shoes?", body="b")
        self.log_in(self.bob)
        self.client.post(f"/api/v1/board/posts/{post.id}/like/")
        self.client.post(f"/api/v1/board/posts/{post.id}/comments/", {"body": "nice", "anonymous": True}, format="json")
        self.log_in(self.alice)
        self.client.post(f"/api/v1/board/posts/{post.id}/comments/", {"body": "thanks"}, format="json")

        self.assertEqual(sorted(self.kinds(self.alice)), ["post_comment", "post_like"])
        self.assertIsNone(Notification.objects.get(kind="post_comment").actor)

    def test_group_join_invite_and_messages(self, _):
        route = Route.objects.create(created_by=self.alice, distance=3000, briefing="b", route=LINE)
        now = timezone.now()
        group = RunningGroup.objects.create(
            host=self.alice, route=route, title="Crew", capacity=4,
            meet_at=now + timedelta(days=2), join_deadline=now + timedelta(days=1),
            start_latitude=37.0, start_longitude=127.0,
        )
        GroupMember.objects.create(group=group, user=self.alice)
        carol = get_user_model().objects.create_user(username="carol", email="c@test.com", password="password123")
        Follow.objects.create(follower=self.alice, followee=carol)
        Follow.objects.create(follower=carol, followee=self.alice)

        self.log_in(self.bob)
        self.client.post(f"/api/v1/groups/{group.id}/membership/", {}, format="json")
        self.client.post(f"/api/v1/groups/{group.id}/messages/", {"text": "see you"}, format="json")
        self.log_in(self.alice)
        self.client.post(f"/api/v1/groups/{group.id}/invites/", {"username": "carol"}, format="json")

        self.assertEqual(sorted(self.kinds(self.alice)), ["group_join", "group_message"])
        self.assertEqual(self.kinds(carol), ["group_invite"])
        self.assertEqual(self.kinds(self.bob), [])

    def test_route_review(self, _):
        route = Route.objects.create(created_by=self.alice, distance=3000, briefing="b", route=LINE)
        shared = SharedRoute.objects.create(route=route, shared_by=self.alice, title="Loop")
        RouteRun.objects.create(user=self.bob, route=route, distance_m=3000, duration_s=900)
        self.log_in(self.bob)

        self.client.post(f"/api/v1/community/routes/{shared.id}/reviews/", {"rating": 5, "comment": "Great"}, format="json")

        self.assertEqual(self.kinds(self.alice), ["route_review"])


class AccountDeletionTests(NotificationTestBase):
    def test_needs_the_password_for_password_accounts(self):
        self.log_in(self.alice)

        wrong = self.client.delete("/api/v1/users/my_profile/", {"password": "nope"}, format="json")
        right = self.client.delete("/api/v1/users/my_profile/", {"password": "password123"}, format="json")

        self.assertEqual(wrong.status_code, 403)
        self.assertEqual(right.status_code, 200)
        self.assertFalse(get_user_model().objects.filter(username="alice").exists())

    def test_google_accounts_delete_without_a_password(self):
        self.bob.set_unusable_password()
        self.bob.save()
        self.log_in(self.bob)

        self.assertFalse(self.client.get("/api/v1/users/my_profile/").data["data"]["has_password"])
        self.assertEqual(self.client.delete("/api/v1/users/my_profile/", {}, format="json").status_code, 200)
        self.assertFalse(get_user_model().objects.filter(username="bob").exists())
