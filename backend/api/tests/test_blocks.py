from api.models import (
    ActivityLog,
    Block,
    Comment,
    Follow,
    GroupInvite,
    GroupMember,
    GroupMessage,
    Notification,
    Post,
    RouteReview,
)
from api.services.notifications import notify
from api.tests.test_social import SocialTestBase
from api.views.activity import summarize


class BlockEndpointTests(SocialTestBase):
    def test_block_and_unblock(self):
        self.log_in(self.me)

        blocked = self.client.post("/api/v1/users/star/block/")
        again = self.client.post("/api/v1/users/star/block/")

        self.assertEqual(blocked.status_code, 200)
        self.assertTrue(again.data["data"]["is_blocked"])
        self.assertEqual(Block.objects.count(), 1)

        unblocked = self.client.delete("/api/v1/users/star/block/")
        self.assertFalse(unblocked.data["data"]["is_blocked"])
        self.assertEqual(Block.objects.count(), 0)

    def test_cannot_block_yourself_missing_users_or_anonymously(self):
        self.assertEqual(self.client.post("/api/v1/users/star/block/").status_code, 401)
        self.log_in(self.me)
        self.assertEqual(self.client.post("/api/v1/users/me/block/").status_code, 400)
        self.assertEqual(self.client.post("/api/v1/users/ghost/block/").status_code, 404)

    def test_blocking_removes_follows_both_ways_and_invites_between_them(self):
        Follow.objects.create(follower=self.me, followee=self.star)
        Follow.objects.create(follower=self.star, followee=self.me)
        Follow.objects.create(follower=self.other, followee=self.star)
        mine = self.group(self.me, "Mine")
        theirs = self.group(self.star, "Theirs")
        GroupInvite.objects.create(group=mine, inviter=self.me, invitee=self.star)
        GroupInvite.objects.create(group=theirs, inviter=self.star, invitee=self.me)
        GroupInvite.objects.create(group=theirs, inviter=self.star, invitee=self.other)
        self.log_in(self.me)

        data = self.client.post("/api/v1/users/star/block/").data["data"]

        self.assertEqual(data["follower_count"], 1)
        self.assertFalse(data["is_following"])
        self.assertEqual(Follow.objects.count(), 1)
        self.assertEqual(list(GroupInvite.objects.values_list("invitee__username", flat=True)), ["other"])

    def test_my_blocks_lists_blocked_users(self):
        Block.objects.create(blocker=self.me, blocked=self.star)
        Block.objects.create(blocker=self.me, blocked=self.other)
        Block.objects.create(blocker=self.star, blocked=self.other)
        self.assertEqual(self.client.get("/api/v1/users/my_profile/blocks/").status_code, 401)
        self.log_in(self.me)

        data = self.client.get("/api/v1/users/my_profile/blocks/").data["data"]

        self.assertEqual([user["username"] for user in data], ["other", "star"])
        self.assertIn("blocked_at", data[0])

    def test_block_and_unblock_are_logged(self):
        self.log_in(self.me)
        self.client.post("/api/v1/users/star/block/")
        self.client.post("/api/v1/users/star/block/")
        self.client.delete("/api/v1/users/star/block/")
        self.client.delete("/api/v1/users/star/block/")

        logs = list(ActivityLog.objects.filter(user=self.me).order_by("id"))

        self.assertEqual([log.action for log in logs], ["blocked", "unblocked"])
        self.assertEqual(summarize(logs[0]), "star")


class BlockProfileTests(SocialTestBase):
    def test_profile_shows_blocked_state_without_content(self):
        self.share(self.star, "Star route")
        self.group(self.star, "Star crew")
        Post.objects.create(author=self.star, title="Named", body="b", anonymous=False)
        Block.objects.create(blocker=self.me, blocked=self.star)
        self.log_in(self.me)

        data = self.client.get("/api/v1/users/star/profile/").data["data"]

        self.assertTrue(data["is_blocked"])
        self.assertEqual((data["shared_routes"], data["groups"], data["posts"]), ([], [], []))

    def test_profile_is_not_blocked_by_default(self):
        self.log_in(self.me)
        self.assertFalse(self.client.get("/api/v1/users/star/profile/").data["data"]["is_blocked"])
        self.assertFalse(self.client.get("/api/v1/users/star/profile/").data["data"]["is_me"])


class BlockInteractionTests(SocialTestBase):
    def setUp(self):
        super().setUp()
        Block.objects.create(blocker=self.me, blocked=self.star)

    def test_neither_side_can_follow(self):
        self.log_in(self.star)
        self.assertEqual(self.client.post("/api/v1/users/me/follow/").status_code, 403)
        self.log_in(self.me)
        self.assertEqual(self.client.post("/api/v1/users/star/follow/").status_code, 403)
        self.assertEqual(Follow.objects.count(), 0)

    def test_blocked_user_cannot_invite_the_blocker(self):
        group = self.group(self.star, "Crew")
        Follow.objects.create(follower=self.star, followee=self.me)
        Follow.objects.create(follower=self.me, followee=self.star)
        self.log_in(self.star)

        response = self.client.post(f"/api/v1/groups/{group.id}/invites/", {"username": "me"}, format="json")

        self.assertEqual(response.status_code, 403)
        self.assertFalse(GroupInvite.objects.exists())

    def test_notify_skips_recipients_who_blocked_the_actor(self):
        self.assertIsNone(notify(self.me, "follow", actor=self.star, sender=lambda n: None))
        self.assertIsNotNone(notify(self.star, "follow", actor=self.me, sender=lambda n: None))
        self.assertIsNotNone(notify(self.me, "follow", actor=self.other, sender=lambda n: None))
        self.assertEqual(Notification.objects.filter(recipient=self.me).count(), 1)

    def test_anonymous_comments_from_blocked_users_do_not_notify(self):
        post = Post.objects.create(author=self.me, title="Mine", body="b")
        self.log_in(self.star)

        response = self.client.post(f"/api/v1/board/posts/{post.id}/comments/", {"body": "hi"}, format="json")

        self.assertEqual(response.status_code, 201)
        self.assertFalse(Notification.objects.filter(recipient=self.me).exists())


class BlockedContentTests(SocialTestBase):
    def setUp(self):
        super().setUp()
        Block.objects.create(blocker=self.me, blocked=self.star)

    def test_board_hides_posts_and_comments_from_blocked_users(self):
        hidden = Post.objects.create(author=self.star, title="Hidden", body="b", anonymous=True)
        post = Post.objects.create(author=self.other, title="Visible", body="b")
        Comment.objects.create(post=post, author=self.star, body="blocked comment")
        Comment.objects.create(post=post, author=self.other, body="ok comment")
        self.log_in(self.me)

        posts = self.client.get("/api/v1/board/posts/").data["data"]
        detail = self.client.get(f"/api/v1/board/posts/{post.id}/").data["data"]
        comments = self.client.get(f"/api/v1/board/posts/{post.id}/comments/").data["data"]

        self.assertEqual([(p["title"], p["comment_count"]) for p in posts], [("Visible", 1)])
        self.assertEqual(detail["comment_count"], 1)
        self.assertEqual([c["body"] for c in detail["comments"]], ["ok comment"])
        self.assertEqual([c["body"] for c in comments], ["ok comment"])
        self.assertEqual(self.client.get(f"/api/v1/board/posts/{hidden.id}/").status_code, 404)

    def test_blocked_user_still_sees_the_blockers_posts(self):
        Post.objects.create(author=self.me, title="Mine", body="b")
        self.log_in(self.star)

        posts = self.client.get("/api/v1/board/posts/").data["data"]

        self.assertEqual([p["title"] for p in posts], ["Mine"])

    def test_group_chat_hides_messages_from_blocked_users(self):
        group = self.group(self.other, "Crew")
        GroupMember.objects.create(group=group, user=self.me)
        GroupMember.objects.create(group=group, user=self.star)
        GroupMessage.objects.create(group=group, user=self.star, text="from star")
        GroupMessage.objects.create(group=group, user=self.other, text="from other")
        self.log_in(self.me)

        messages = self.client.get(f"/api/v1/groups/{group.id}/messages/").data["data"]
        after = self.client.get(f"/api/v1/groups/{group.id}/messages/?after=0").data["data"]

        self.assertEqual([m["text"] for m in messages], ["from other"])
        self.assertEqual([m["text"] for m in after], ["from other"])
        self.assertEqual(GroupMember.objects.filter(group=group).count(), 3)

    def test_shared_routes_and_reviews_hide_blocked_users(self):
        self.share(self.star, "Star route")
        shared = self.share(self.other, "Other route")
        RouteReview.objects.create(shared_route=shared, user=self.star, rating=1, comment="bad")
        RouteReview.objects.create(shared_route=shared, user=self.other, rating=5, comment="good")
        self.log_in(self.me)

        routes = self.client.get("/api/v1/community/routes/").data["data"]
        reviews = self.client.get(f"/api/v1/community/routes/{shared.id}/reviews/").data["data"]

        self.assertEqual([r["title"] for r in routes], ["Other route"])
        self.assertEqual([r["comment"] for r in reviews], ["good"])
