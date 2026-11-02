from django.contrib.auth import get_user_model
from rest_framework.authtoken.models import Token
from rest_framework.test import APITestCase

from api.models import Comment, CommentReport, Post, PostReport, PostScrap


POSTS_URL = "/api/v1/board/posts/"
COMMENTS_URL = "/api/v1/board/comments/"


class BoardTestBase(APITestCase):
    def setUp(self):
        User = get_user_model()
        self.writer = User.objects.create_user(username="writer", email="writer@test.com", password="password123")
        self.reader = User.objects.create_user(username="reader", email="reader@test.com", password="password123")
        self.users = [
            User.objects.create_user(username=f"user{i}", email=f"user{i}@test.com", password="password123")
            for i in range(5)
        ]

    def log_in(self, user):
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {Token.objects.get_or_create(user=user)[0].key}")

    def post(self, title="Knee pain", body="My knee hurts after 5k. Any tips?", anonymous=True, author=None):
        return Post.objects.create(author=author or self.writer, title=title, body=body, anonymous=anonymous)

    def detail_url(self, post):
        return f"{POSTS_URL}{post.id}/"


class PostTests(BoardTestBase):
    def test_write_requires_login_but_reading_is_public(self):
        self.post()
        self.assertEqual(self.client.post(POSTS_URL, {"title": "a", "body": "b"}, format="json").status_code, 401)
        self.assertEqual(len(self.client.get(POSTS_URL).data["data"]), 1)

    def test_anonymous_by_default(self):
        self.log_in(self.writer)

        response = self.client.post(POSTS_URL, {"title": " Knee pain ", "body": " Tips? "}, format="json")

        self.assertEqual(response.status_code, 201)
        data = response.data["data"]
        self.assertEqual((data["title"], data["body"]), ("Knee pain", "Tips?"))
        self.assertIsNone(data["author"])
        self.assertTrue(data["anonymous"])
        self.assertTrue(data["is_mine"])

    def test_named_post_shows_the_username(self):
        self.log_in(self.writer)
        response = self.client.post(POSTS_URL, {"title": "Hi", "body": "Hello", "anonymous": False}, format="json")
        self.assertEqual(response.data["data"]["author"], "writer")

    def test_rejects_invalid_posts(self):
        self.log_in(self.writer)
        cases = [
            ({"title": " ", "body": "b"}, "Title must be 1 to 100 characters."),
            ({"title": "a" * 101, "body": "b"}, "Title must be 1 to 100 characters."),
            ({"title": "a", "body": "b" * 2001}, "Body must be 1 to 2000 characters."),
            ({"title": "a", "body": "b", "anonymous": "yes"}, "anonymous must be true or false."),
        ]
        for body, message in cases:
            with self.subTest(body=body):
                response = self.client.post(POSTS_URL, body, format="json")
                self.assertEqual(response.status_code, 400)
                self.assertEqual(response.data["message"], message)

    def test_only_the_author_edits_or_deletes(self):
        post = self.post()
        self.log_in(self.reader)
        self.assertEqual(self.client.patch(self.detail_url(post), {"title": "x"}, format="json").status_code, 403)
        self.assertEqual(self.client.delete(self.detail_url(post)).status_code, 403)

        self.log_in(self.writer)
        response = self.client.patch(self.detail_url(post), {"body": "Fixed it"}, format="json")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["data"]["body"], "Fixed it")
        self.assertEqual(response.data["data"]["title"], "Knee pain")
        self.assertEqual(self.client.delete(self.detail_url(post)).status_code, 200)
        self.assertFalse(Post.objects.exists())

    def test_filters_and_search(self):
        mine = self.post(title="Shoes for flat feet")
        other = self.post(title="Rainy day runs", author=self.reader)
        PostScrap.objects.create(post=mine, user=self.reader)
        self.log_in(self.reader)

        def ids(**params):
            return [p["id"] for p in self.client.get(POSTS_URL, params).data["data"]]

        self.assertEqual(ids(), [other.id, mine.id])
        self.assertEqual(ids(filter="mine"), [other.id])
        self.assertEqual(ids(filter="scraps"), [mine.id])
        self.assertEqual(ids(q="shoes"), [mine.id])
        self.assertEqual(self.client.get(POSTS_URL, {"filter": "hot"}).status_code, 400)
        self.client.credentials()
        self.assertEqual(self.client.get(POSTS_URL, {"filter": "mine"}).status_code, 401)


class ReactionTests(BoardTestBase):
    def test_like_and_scrap_toggle(self):
        post = self.post()
        self.log_in(self.reader)

        liked = self.client.post(f"{self.detail_url(post)}like/").data["data"]
        self.client.post(f"{self.detail_url(post)}like/")
        scrapped = self.client.post(f"{self.detail_url(post)}scrap/").data["data"]

        self.assertEqual((liked["like_count"], liked["liked"]), (1, True))
        self.assertEqual((scrapped["scrap_count"], scrapped["scrapped"], scrapped["like_count"]), (1, True, 1))
        unliked = self.client.delete(f"{self.detail_url(post)}like/").data["data"]
        self.assertEqual((unliked["like_count"], unliked["liked"]), (0, False))

    def test_cannot_like_own_post(self):
        post = self.post()
        self.log_in(self.writer)
        response = self.client.post(f"{self.detail_url(post)}like/")
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.data["message"], "You can't like your own post.")


class CommentTests(BoardTestBase):
    def comment(self, post, user, body="Ice it", anonymous=True):
        self.log_in(user)
        return self.client.post(
            f"{self.detail_url(post)}comments/", {"body": body, "anonymous": anonymous}, format="json"
        )

    def test_anonymous_commenters_are_numbered_and_the_writer_is_marked(self):
        post = self.post()
        self.comment(post, self.reader, "Rest a week")
        self.comment(post, self.writer, "Thanks!")
        self.comment(post, self.users[0], "See a doctor")
        self.comment(post, self.reader, "Also stretch")
        response = self.comment(post, self.users[1], "Me too", anonymous=False)

        comments = response.data["data"]
        self.assertEqual(response.status_code, 201)
        self.assertEqual([c["anonymous_number"] for c in comments], [1, None, 2, 1, None])
        self.assertEqual([c["is_post_author"] for c in comments], [False, True, False, False, False])
        self.assertEqual(comments[4]["author"], "user1")
        self.assertIsNone(comments[0]["author"])
        self.assertTrue(comments[4]["is_mine"])
        detail = self.client.get(self.detail_url(post)).data["data"]
        self.assertEqual(detail["comment_count"], 5)
        self.assertEqual(len(detail["comments"]), 5)

    def test_rejects_empty_comment_and_requires_login(self):
        post = self.post()
        self.assertEqual(self.comment(post, self.reader, "  ").status_code, 400)
        self.client.credentials()
        response = self.client.post(f"{self.detail_url(post)}comments/", {"body": "hi"}, format="json")
        self.assertEqual(response.status_code, 401)

    def test_only_the_author_deletes_a_comment(self):
        post = self.post()
        comment_id = self.comment(post, self.reader).data["data"][0]["id"]
        self.log_in(self.writer)
        self.assertEqual(self.client.delete(f"{COMMENTS_URL}{comment_id}/").status_code, 403)
        self.log_in(self.reader)
        response = self.client.delete(f"{COMMENTS_URL}{comment_id}/")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["data"], [])


class ReportTests(BoardTestBase):
    def report(self, url, user, reason="abuse"):
        self.log_in(user)
        return self.client.post(url, {"reason": reason}, format="json")

    def test_post_is_hidden_after_five_reports(self):
        post = self.post()
        url = f"{self.detail_url(post)}report/"
        for user in self.users[:4]:
            self.assertFalse(self.report(url, user).data["data"]["hidden"])

        response = self.report(url, self.users[4])

        self.assertTrue(response.data["data"]["hidden"])
        self.assertEqual(self.client.get(self.detail_url(post)).status_code, 404)
        self.assertEqual(self.client.get(POSTS_URL).data["data"], [])

    def test_report_rules(self):
        post = self.post()
        url = f"{self.detail_url(post)}report/"
        self.assertEqual(self.report(url, self.writer).data["message"], "You can't report your own post.")
        self.assertEqual(self.report(url, self.reader, reason="boring").status_code, 400)
        self.assertEqual(self.report(url, self.reader).status_code, 201)
        self.assertEqual(self.report(url, self.reader).data["message"], "You already reported this post.")
        self.assertTrue(self.client.get(self.detail_url(post)).data["data"]["reported"])
        self.assertEqual(PostReport.objects.count(), 1)

    def test_comment_is_hidden_after_five_reports(self):
        post = self.post()
        comment = Comment.objects.create(post=post, author=self.reader, body="spam spam")
        Comment.objects.create(post=post, author=self.writer, body="real")
        for user in self.users:
            CommentReport.objects.create(comment=comment, user=user, reason="spam")

        detail = self.client.get(self.detail_url(post)).data["data"]

        self.assertEqual([c["body"] for c in detail["comments"]], ["real"])
        self.assertEqual(detail["comment_count"], 1)
        self.assertEqual(self.report(f"{COMMENTS_URL}{comment.id}/report/", self.writer).status_code, 404)

    def test_cannot_report_own_comment(self):
        post = self.post()
        comment = Comment.objects.create(post=post, author=self.reader, body="hi")
        response = self.report(f"{COMMENTS_URL}{comment.id}/report/", self.reader)
        self.assertEqual(response.data["message"], "You can't report your own comment.")
        self.assertEqual(self.report(f"{COMMENTS_URL}{comment.id}/report/", self.writer, "spam").status_code, 201)
