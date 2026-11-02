from datetime import timedelta

from django.contrib.auth import get_user_model
from django.utils import timezone
from rest_framework.authtoken.models import Token
from rest_framework.test import APITestCase

from api.models import GroupMember, Route, RouteRun, RunningGroup, SharedRoute, UserSavedRoute


GROUPS_URL = "/api/v1/groups/"
RUNS_URL = "/api/v1/runs/"
SNU = [126.952, 37.460]
GANGNAM = [127.027, 37.498]


def line(start):
    return [{"type": "LineString", "coordinates": [start, [start[0] + 0.01, start[1] + 0.01]]}]


class GroupTestBase(APITestCase):
    def setUp(self):
        User = get_user_model()
        self.host = User.objects.create_user(username="host", email="host@test.com", password="password123")
        self.runner = User.objects.create_user(username="runner", email="runner@test.com", password="password123")
        self.other = User.objects.create_user(username="other", email="other@test.com", password="password123")
        self.route = Route.objects.create(
            created_by=self.host, distance=5000, briefing="b", route=line(SNU), start_name="서울대입구역"
        )
        self.now = timezone.now()

    def log_in(self, user):
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {Token.objects.get_or_create(user=user)[0].key}")

    def body(self, **extra):
        return {
            "route_id": self.route.id,
            "title": "Morning 5k",
            "description": "Easy pace",
            "capacity": 3,
            "meet_at": (self.now + timedelta(days=3)).isoformat(),
            "join_deadline": (self.now + timedelta(days=2)).isoformat(),
            **extra,
        }

    def create_group(self, **extra):
        self.log_in(self.host)
        return self.client.post(GROUPS_URL, self.body(**extra), format="json")

    def make_group(self, route=None, capacity=3, deadline=timedelta(days=2), meet=timedelta(days=3), host=None):
        route = route or self.route
        start = route.route[0]["coordinates"][0]
        group = RunningGroup.objects.create(
            host=host or self.host,
            route=route,
            title="Group",
            capacity=capacity,
            meet_at=self.now + meet,
            join_deadline=self.now + deadline,
            start_longitude=start[0],
            start_latitude=start[1],
        )
        GroupMember.objects.create(group=group, user=group.host)
        return group

    def membership_url(self, group):
        return f"{GROUPS_URL}{group.id}/membership/"

    def messages_url(self, group):
        return f"{GROUPS_URL}{group.id}/messages/"


class CreateGroupTests(GroupTestBase):
    def test_requires_authentication(self):
        self.assertEqual(self.client.post(GROUPS_URL, self.body(), format="json").status_code, 401)

    def test_host_joins_their_new_group(self):
        response = self.create_group()

        self.assertEqual(response.status_code, 201)
        data = response.data["data"]
        self.assertEqual(data["member_count"], 1)
        self.assertEqual(data["capacity"], 3)
        self.assertEqual(data["status"], "open")
        self.assertTrue(data["is_host"])
        self.assertTrue(data["is_member"])
        self.assertEqual(data["start_name"], "서울대입구역")
        self.assertEqual([data["start_longitude"], data["start_latitude"]], SNU)
        self.assertEqual(data["members"], ["host"])

    def test_can_use_a_shared_or_saved_route(self):
        shared_route = Route.objects.create(created_by=self.other, distance=3000, briefing="b", route=line(SNU))
        SharedRoute.objects.create(route=shared_route, shared_by=self.other, title="Loop")
        saved_route = Route.objects.create(created_by=self.other, distance=3000, briefing="b", route=line(SNU))
        UserSavedRoute.objects.create(user=self.host, route=saved_route)
        private_route = Route.objects.create(created_by=self.other, distance=3000, briefing="b", route=line(SNU))

        self.assertEqual(self.create_group(route_id=shared_route.id).status_code, 201)
        self.assertEqual(self.create_group(route_id=saved_route.id).status_code, 201)
        self.assertEqual(self.create_group(route_id=private_route.id).status_code, 404)

    def test_rejects_invalid_fields(self):
        cases = [
            ({"title": " "}, "Title must be 1 to 60 characters."),
            ({"description": "a" * 301}, "Description must be 300 characters or fewer."),
            ({"capacity": 1}, "Capacity must be between 2 and 20."),
            ({"capacity": 21}, "Capacity must be between 2 and 20."),
            ({"capacity": True}, "Capacity must be between 2 and 20."),
            ({"meet_at": "tomorrow"}, "meet_at and join_deadline must be ISO 8601 date-times."),
            (
                {"join_deadline": (self.now - timedelta(hours=1)).isoformat()},
                "The join deadline must be in the future.",
            ),
            (
                {"join_deadline": (self.now + timedelta(days=4)).isoformat()},
                "The join deadline must be before the run starts.",
            ),
        ]
        for extra, message in cases:
            with self.subTest(extra=extra):
                response = self.create_group(**extra)
                self.assertEqual(response.status_code, 400)
                self.assertEqual(response.data["message"], message)


class MembershipTests(GroupTestBase):
    def test_join_until_full_then_closed(self):
        group = self.make_group(capacity=2)
        self.log_in(self.runner)

        response = self.client.post(self.membership_url(group))

        self.assertEqual(response.status_code, 201)
        self.assertEqual(response.data["data"]["status"], "full")
        self.assertEqual(response.data["data"]["members"], ["host", "runner"])
        self.log_in(self.other)
        response = self.client.post(self.membership_url(group))
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.data["message"], "This group is full.")

    def test_cannot_join_twice_or_after_the_deadline(self):
        group = self.make_group()
        self.log_in(self.runner)
        self.client.post(self.membership_url(group))
        self.assertEqual(self.client.post(self.membership_url(group)).data["message"], "You already joined this group.")

        late = self.make_group(deadline=-timedelta(hours=1), meet=timedelta(hours=1))
        response = self.client.post(self.membership_url(late))
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.data["message"], "This group is no longer taking members.")

    def test_leaving_frees_a_spot(self):
        group = self.make_group(capacity=2)
        self.log_in(self.runner)
        self.client.post(self.membership_url(group))

        response = self.client.delete(self.membership_url(group))

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["data"]["status"], "open")
        self.assertFalse(response.data["data"]["is_member"])

    def test_host_cannot_leave_and_nobody_leaves_after_the_start(self):
        group = self.make_group()
        self.log_in(self.host)
        self.assertEqual(self.client.delete(self.membership_url(group)).status_code, 400)

        started = self.make_group(deadline=-timedelta(hours=2), meet=-timedelta(hours=1))
        GroupMember.objects.create(group=started, user=self.runner)
        self.log_in(self.runner)
        response = self.client.delete(self.membership_url(started))
        self.assertEqual(response.data["message"], "You can't leave after the run has started.")

    def test_only_the_host_can_delete(self):
        group = self.make_group()
        self.log_in(self.runner)
        self.assertEqual(self.client.delete(f"{GROUPS_URL}{group.id}/").status_code, 403)

        self.log_in(self.host)
        self.assertEqual(self.client.delete(f"{GROUPS_URL}{group.id}/").status_code, 200)
        self.assertFalse(RunningGroup.objects.exists())


class GroupListTests(GroupTestBase):
    def test_lists_upcoming_groups_in_meet_order(self):
        later = self.make_group(meet=timedelta(days=5))
        sooner = self.make_group(meet=timedelta(days=3))
        self.make_group(deadline=-timedelta(hours=2), meet=-timedelta(hours=1))

        response = self.client.get(GROUPS_URL)

        self.assertEqual(response.status_code, 200)
        self.assertEqual([g["id"] for g in response.data["data"]], [sooner.id, later.id])

    def test_closing_soon_by_deadline_or_last_spot(self):
        by_time = self.make_group(deadline=timedelta(hours=5))
        by_spot = self.make_group(capacity=2)
        self.make_group(capacity=5)
        full = self.make_group(capacity=2, deadline=timedelta(hours=1))
        GroupMember.objects.create(group=full, user=self.runner)

        response = self.client.get(GROUPS_URL, {"filter": "closing_soon"})

        self.assertEqual([g["id"] for g in response.data["data"]], [by_time.id, by_spot.id])
        self.assertTrue(all(g["closing_soon"] for g in response.data["data"]))

    def test_filters_by_distance_from_me(self):
        far_route = Route.objects.create(created_by=self.host, distance=5000, briefing="b", route=line(GANGNAM))
        near = self.make_group()
        self.make_group(route=far_route)

        response = self.client.get(GROUPS_URL, {"lat": 37.465, "lon": 126.950, "radius_km": 3})

        self.assertEqual([g["id"] for g in response.data["data"]], [near.id])
        self.assertLess(response.data["data"][0]["distance_from_me_m"], 1000)

    def test_mine_includes_past_groups(self):
        past = self.make_group(deadline=-timedelta(days=2), meet=-timedelta(days=1))
        GroupMember.objects.create(group=past, user=self.runner)
        self.make_group()
        self.log_in(self.runner)

        response = self.client.get(GROUPS_URL, {"filter": "mine"})

        self.assertEqual([g["id"] for g in response.data["data"]], [past.id])
        self.assertEqual(response.data["data"][0]["status"], "closed")

    def test_rejects_bad_query(self):
        self.assertEqual(self.client.get(GROUPS_URL, {"filter": "popular"}).status_code, 400)
        self.assertEqual(self.client.get(GROUPS_URL, {"lat": "x", "lon": 126.9}).status_code, 400)
        self.assertEqual(self.client.get(GROUPS_URL, {"lat": 37.4, "lon": 126.9, "radius_km": 500}).status_code, 400)
        self.assertEqual(self.client.get(GROUPS_URL, {"filter": "mine"}).status_code, 401)


class GroupRunTests(GroupTestBase):
    def setUp(self):
        super().setUp()
        self.group = self.make_group()
        GroupMember.objects.create(group=self.group, user=self.runner)

    def record(self, user, distance_m, duration_s, **extra):
        self.log_in(user)
        body = {"route_id": self.route.id, "group_id": self.group.id, "distance_m": distance_m, "duration_s": duration_s}
        return self.client.post(RUNS_URL, {**body, **extra}, format="json")

    def test_ranking_uses_each_members_best_pace(self):
        self.record(self.host, 5000, 1800)
        self.record(self.runner, 5000, 1600)
        self.record(self.runner, 5000, 1500)
        self.record(self.host, 2000, 300)

        self.log_in(self.runner)
        ranking = self.client.get(f"{GROUPS_URL}{self.group.id}/").data["data"]["ranking"]

        self.assertEqual([r["username"] for r in ranking], ["runner", "host"])
        self.assertEqual(ranking[0]["pace_s_per_km"], 300)
        self.assertEqual(ranking[0]["rank"], 1)
        self.assertTrue(ranking[0]["is_me"])
        self.assertEqual(ranking[1]["pace_s_per_km"], 360)
        self.assertEqual(RouteRun.objects.filter(group=self.group).count(), 4)

    def test_only_members_can_record_on_the_group_route(self):
        other_route = Route.objects.create(created_by=self.runner, distance=3000, briefing="b", route=line(SNU))
        self.assertEqual(self.record(self.other, 5000, 1500).status_code, 404)
        self.assertEqual(self.record(self.runner, 3000, 900, route_id=other_route.id).status_code, 400)


class GroupChatTests(GroupTestBase):
    def setUp(self):
        super().setUp()
        self.group = self.make_group()
        GroupMember.objects.create(group=self.group, user=self.runner)

    def test_members_chat_and_poll_for_new_messages(self):
        self.log_in(self.host)
        first = self.client.post(self.messages_url(self.group), {"text": " Meet at exit 3 "}, format="json")
        self.log_in(self.runner)
        self.client.post(self.messages_url(self.group), {"text": "See you"}, format="json")

        everything = self.client.get(self.messages_url(self.group)).data["data"]
        newer = self.client.get(self.messages_url(self.group), {"after": first.data["data"]["id"]}).data["data"]

        self.assertEqual(first.status_code, 201)
        self.assertEqual([m["text"] for m in everything], ["Meet at exit 3", "See you"])
        self.assertEqual([m["is_mine"] for m in everything], [False, True])
        self.assertEqual([m["text"] for m in newer], ["See you"])

    def test_non_members_cannot_read_or_write(self):
        self.log_in(self.other)
        self.assertEqual(self.client.get(self.messages_url(self.group)).status_code, 403)
        self.assertEqual(self.client.post(self.messages_url(self.group), {"text": "hi"}, format="json").status_code, 403)

    def test_rejects_empty_or_long_messages(self):
        self.log_in(self.runner)
        for text in ["  ", "a" * 501, 3]:
            with self.subTest(text=text):
                response = self.client.post(self.messages_url(self.group), {"text": text}, format="json")
                self.assertEqual(response.status_code, 400)
