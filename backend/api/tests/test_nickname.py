from django.contrib.auth import get_user_model
from rest_framework.authtoken.models import Token
from rest_framework.test import APITestCase

from api.models import Profile


REGISTER_URL = "/api/v1/auth/register/"
PROFILE_URL = "/api/v1/users/my_profile/"


class NicknameTests(APITestCase):
    def setUp(self):
        User = get_user_model()
        self.alice = User.objects.create_user(username="alice", email="a@test.com", password="password123")
        Profile.objects.create(user=self.alice, display_name="Runner Kim")
        self.bob = User.objects.create_user(username="bob", email="b@test.com", password="password123")

    def log_in(self, user):
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {Token.objects.get_or_create(user=user)[0].key}")

    def register(self, **extra):
        body = {"username": "carol", "email": "c@test.com", "password": "password123", **extra}
        return self.client.post(REGISTER_URL, body, format="json")

    def test_sign_up_with_a_nickname(self):
        response = self.register(display_name="  Dawn Runner ")

        self.assertEqual(response.status_code, 201)
        self.assertEqual(Profile.objects.get(user__username="carol").display_name, "Dawn Runner")

    def test_sign_up_without_a_nickname_still_works(self):
        self.assertEqual(self.register().status_code, 201)

    def test_sign_up_rejects_a_taken_nickname_in_any_case(self):
        for name in ("runner kim", "BOB"):
            response = self.register(display_name=name)
            self.assertEqual(response.status_code, 400)
            self.assertEqual(response.data["message"], "This nickname is already taken.")
        self.assertFalse(get_user_model().objects.filter(username="carol").exists())

    def test_username_cannot_copy_someone_elses_nickname(self):
        response = self.client.post(REGISTER_URL, {"username": "Runner Kim", "email": "x@test.com", "password": "password123"}, format="json")

        self.assertEqual(response.status_code, 400)

    def test_nickname_can_match_your_own_username(self):
        self.assertEqual(self.register(display_name="Carol").status_code, 201)

    def test_editing_profile_rejects_taken_nicknames_but_keeps_your_own(self):
        self.log_in(self.bob)
        taken = self.client.patch(PROFILE_URL, {"display_name": "RUNNER KIM"}, format="json")
        username = self.client.patch(PROFILE_URL, {"display_name": "alice"}, format="json")
        own = self.client.patch(PROFILE_URL, {"display_name": "Bob"}, format="json")

        self.assertEqual(taken.status_code, 400)
        self.assertEqual(username.status_code, 400)
        self.assertEqual(own.status_code, 200)

        self.log_in(self.alice)
        same = self.client.patch(PROFILE_URL, {"display_name": "Runner Kim", "status_message": "hi"}, format="json")
        self.assertEqual(same.status_code, 200)
