from datetime import timedelta

from django.contrib.auth import get_user_model
from django.utils import timezone
from rest_framework.authtoken.models import Token
from rest_framework.test import APITestCase

from api.models import Follow, GroupInvite, GroupMember, Route, RunningGroup


LINE = [{"type": "LineString", "coordinates": [[127.0, 37.0], [127.01, 37.01]]}]


class GroupInviteTests(APITestCase):
    def setUp(self):
        User = get_user_model()
        self.host = User.objects.create_user(username="host", email="h@test.com", password="password123")
        self.friend = User.objects.create_user(username="friend", email="f@test.com", password="password123")
        self.fan = User.objects.create_user(username="fan", email="fan@test.com", password="password123")
        Follow.objects.create(follower=self.host, followee=self.friend)
        Follow.objects.create(follower=self.friend, followee=self.host)
        Follow.objects.create(follower=self.fan, followee=self.host)
        route = Route.objects.create(created_by=self.host, distance=3000, briefing="b", route=LINE)
        now = timezone.now()
        self.group = RunningGroup.objects.create(
            host=self.host, route=route, title="Secret crew", capacity=3,
            meet_at=now + timedelta(days=2), join_deadline=now + timedelta(days=1),
            start_latitude=37.0, start_longitude=127.0,
            is_private=True, password="!",
        )
        GroupMember.objects.create(group=self.group, user=self.host)

    def log_in(self, user):
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {Token.objects.get_or_create(user=user)[0].key}")

    def url(self):
        return f"/api/v1/groups/{self.group.id}/invites/"

    def invite(self, username):
        return self.client.post(self.url(), {"username": username}, format="json")

    def test_lists_only_mutual_followers(self):
        self.log_in(self.host)

        data = self.client.get(self.url()).data["data"]

        self.assertEqual([u["username"] for u in data], ["friend"])
        self.assertFalse(data[0]["is_invited"])

    def test_invite_a_mutual_follower_once(self):
        self.log_in(self.host)

        first = self.invite("friend")
        second = self.invite("friend")

        self.assertEqual(first.status_code, 201)
        self.assertEqual(second.status_code, 201)
        self.assertEqual(GroupInvite.objects.count(), 1)
        self.assertTrue(self.client.get(self.url()).data["data"][0]["is_invited"])

    def test_cannot_invite_one_way_followers_or_as_a_non_member(self):
        self.log_in(self.host)
        self.assertEqual(self.invite("fan").status_code, 403)
        self.assertEqual(self.invite("ghost").status_code, 404)
        self.log_in(self.friend)
        self.assertEqual(self.client.get(self.url()).status_code, 403)

    def test_invited_friend_joins_a_private_group_without_the_password(self):
        self.log_in(self.host)
        self.invite("friend")
        self.log_in(self.friend)

        invites = self.client.get("/api/v1/invites/").data["data"]
        detail = self.client.get(f"/api/v1/groups/{self.group.id}/").data["data"]
        profile = self.client.get("/api/v1/users/my_profile/").data["data"]
        joined = self.client.post(f"/api/v1/groups/{self.group.id}/membership/", {}, format="json")

        self.assertEqual(invites[0]["inviter"], "host")
        self.assertEqual(invites[0]["group"]["title"], "Secret crew")
        self.assertEqual(detail["my_invite_id"], invites[0]["id"])
        self.assertEqual(profile["invite_count"], 1)
        self.assertEqual(joined.status_code, 201)
        self.assertFalse(GroupInvite.objects.exists())
        self.assertEqual(self.client.get("/api/v1/invites/").data["data"], [])

    def test_decline_an_invite(self):
        self.log_in(self.host)
        invite_id = self.invite("friend").data["data"]["id"]
        self.assertEqual(self.client.delete(f"/api/v1/invites/{invite_id}/").status_code, 404)
        self.log_in(self.friend)

        self.assertEqual(self.client.delete(f"/api/v1/invites/{invite_id}/").status_code, 200)
        self.assertFalse(GroupInvite.objects.exists())

    def test_cannot_invite_members_or_into_a_full_group(self):
        GroupMember.objects.create(group=self.group, user=self.friend)
        self.log_in(self.host)
        self.assertEqual(self.invite("friend").status_code, 400)

        other = get_user_model().objects.create_user(username="buddy", email="b@test.com", password="password123")
        Follow.objects.create(follower=self.host, followee=other)
        Follow.objects.create(follower=other, followee=self.host)
        GroupMember.objects.create(group=self.group, user=self.fan)
        self.assertEqual(self.invite("buddy").status_code, 400)

    def test_members_can_invite_their_own_mutual_followers(self):
        GroupMember.objects.create(group=self.group, user=self.friend)
        buddy = get_user_model().objects.create_user(username="buddy", email="b@test.com", password="password123")
        Follow.objects.create(follower=self.friend, followee=buddy)
        Follow.objects.create(follower=buddy, followee=self.friend)
        self.log_in(self.friend)

        self.assertEqual(self.invite("buddy").status_code, 201)
        self.assertEqual(GroupInvite.objects.get().inviter, self.friend)
