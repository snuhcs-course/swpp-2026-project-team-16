from datetime import timedelta

from django.contrib.auth import get_user_model
from django.utils import timezone
from rest_framework.authtoken.models import Token
from rest_framework.test import APITestCase

from api.models import Follow, GroupMember, Post, Route, RunningGroup, SharedRoute


LINE = [{"type": "LineString", "coordinates": [[127.0, 37.0], [127.01, 37.01]]}]


class SocialTestBase(APITestCase):
    def setUp(self):
        User = get_user_model()
        self.me = User.objects.create_user(username="me", email="me@test.com", password="password123")
        self.star = User.objects.create_user(username="star", email="star@test.com", password="password123")
        self.other = User.objects.create_user(username="other", email="other@test.com", password="password123")

    def log_in(self, user):
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {Token.objects.get_or_create(user=user)[0].key}")

    def share(self, user, title):
        route = Route.objects.create(created_by=user, distance=3000, briefing="b", route=LINE)
        return SharedRoute.objects.create(route=route, shared_by=user, title=title)

    def group(self, host, title, meet=timedelta(days=2)):
        route = Route.objects.create(created_by=host, distance=3000, briefing="b", route=LINE)
        now = timezone.now()
        group = RunningGroup.objects.create(
            host=host, route=route, title=title, capacity=4,
            meet_at=now + meet, join_deadline=now + meet - timedelta(hours=1),
            start_latitude=37.0, start_longitude=127.0,
        )
        GroupMember.objects.create(group=group, user=host)
        return group


class FollowTests(SocialTestBase):
    def test_follow_and_unfollow(self):
        self.log_in(self.me)

        followed = self.client.post("/api/v1/users/star/follow/")
        again = self.client.post("/api/v1/users/star/follow/")
        self.assertEqual(followed.status_code, 200)
        self.assertEqual(again.data["data"], {"follower_count": 1, "following_count": 0, "is_following": True})
        self.assertEqual(Follow.objects.count(), 1)

        unfollowed = self.client.delete("/api/v1/users/star/follow/")
        self.assertEqual(unfollowed.data["data"]["is_following"], False)
        self.assertEqual(Follow.objects.count(), 0)

    def test_cannot_follow_yourself_or_a_missing_user(self):
        self.assertEqual(self.client.post("/api/v1/users/star/follow/").status_code, 401)
        self.log_in(self.me)
        self.assertEqual(self.client.post("/api/v1/users/me/follow/").status_code, 400)
        self.assertEqual(self.client.post("/api/v1/users/ghost/follow/").status_code, 404)

    def test_follower_and_following_lists(self):
        Follow.objects.create(follower=self.me, followee=self.star)
        Follow.objects.create(follower=self.other, followee=self.star)
        Follow.objects.create(follower=self.star, followee=self.other)
        self.log_in(self.me)

        followers = self.client.get("/api/v1/users/star/followers/").data["data"]
        following = self.client.get("/api/v1/users/star/following/").data["data"]

        self.assertEqual({user["username"] for user in followers}, {"me", "other"})
        self.assertTrue(next(u for u in followers if u["username"] == "me")["is_me"])
        self.assertEqual([user["username"] for user in following], ["other"])
        self.assertFalse(following[0]["is_following"])

    def test_my_profile_includes_follow_counts(self):
        Follow.objects.create(follower=self.other, followee=self.me)
        self.log_in(self.me)

        data = self.client.get("/api/v1/users/my_profile/").data["data"]

        self.assertEqual((data["follower_count"], data["following_count"]), (1, 0))


class UserProfileTests(SocialTestBase):
    def test_profile_shows_routes_upcoming_groups_and_named_posts_only(self):
        self.share(self.star, "Han river loop")
        self.group(self.star, "Morning crew")
        self.group(self.star, "Old crew", meet=timedelta(days=-1))
        Post.objects.create(author=self.star, title="Named post", body="b", anonymous=False)
        Post.objects.create(author=self.star, title="Secret post", body="b", anonymous=True)
        Follow.objects.create(follower=self.me, followee=self.star)
        self.log_in(self.me)

        data = self.client.get("/api/v1/users/star/profile/").data["data"]

        self.assertEqual(data["username"], "star")
        self.assertTrue(data["is_following"])
        self.assertFalse(data["is_me"])
        self.assertEqual(data["follower_count"], 1)
        self.assertEqual([r["title"] for r in data["shared_routes"]], ["Han river loop"])
        self.assertEqual([g["title"] for g in data["groups"]], ["Morning crew"])
        self.assertEqual([p["title"] for p in data["posts"]], ["Named post"])

    def test_profile_is_public_and_missing_users_404(self):
        self.assertEqual(self.client.get("/api/v1/users/star/profile/").status_code, 200)
        self.assertEqual(self.client.get("/api/v1/users/ghost/profile/").status_code, 404)


class FollowingFilterTests(SocialTestBase):
    def setUp(self):
        super().setUp()
        Follow.objects.create(follower=self.me, followee=self.star)

    def test_shared_routes_from_people_i_follow(self):
        self.share(self.star, "Star route")
        self.share(self.other, "Other route")
        self.assertEqual(self.client.get("/api/v1/community/routes/?filter=following").status_code, 401)
        self.log_in(self.me)

        data = self.client.get("/api/v1/community/routes/?filter=following").data["data"]

        self.assertEqual([r["title"] for r in data], ["Star route"])
        self.assertEqual(self.client.get("/api/v1/community/routes/?filter=nope").status_code, 400)

    def test_upcoming_groups_hosted_by_people_i_follow(self):
        self.group(self.star, "Star crew")
        self.group(self.star, "Past crew", meet=timedelta(days=-1))
        self.group(self.other, "Other crew")
        self.log_in(self.me)

        data = self.client.get("/api/v1/groups/?filter=following").data["data"]

        self.assertEqual([g["title"] for g in data], ["Star crew"])

    def test_board_shows_only_named_posts_from_people_i_follow(self):
        Post.objects.create(author=self.star, title="Named", body="b", anonymous=False)
        Post.objects.create(author=self.star, title="Anonymous", body="b", anonymous=True)
        Post.objects.create(author=self.other, title="Stranger", body="b", anonymous=False)
        self.log_in(self.me)

        data = self.client.get("/api/v1/board/posts/?filter=following").data["data"]

        self.assertEqual([p["title"] for p in data], ["Named"])
