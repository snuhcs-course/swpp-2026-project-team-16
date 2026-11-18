from unittest import mock

from django.contrib.auth import get_user_model
from rest_framework.authtoken.models import Token
from rest_framework.test import APITestCase

from api.models import Profile


REGISTER_URL = "/api/v1/auth/register/"
LOGOUT_URL = "/api/v1/auth/logout/"
GOOGLE_URL = "/api/v1/auth/google/"
PROFILE_URL = "/api/v1/users/my_profile/"
VERIFY = "api.views.auth.id_token.verify_oauth2_token"
LOGIN_URL = "/api/v1/auth/login/"


class RegisterTests(APITestCase):
    def register(self, **overrides):
        body = {"username": "runner", "email": "r@test.com", "password": "password123", **overrides}
        return self.client.post(REGISTER_URL, body, format="json")

    def test_register_trims_fields(self):
        response = self.register(username=" runner ", email=" r@test.com ")

        self.assertEqual(response.status_code, 201)
        self.assertEqual((response.data["data"]["username"], response.data["data"]["email"]), ("runner", "r@test.com"))

    def test_invalid_input_is_rejected(self):
        cases = [
            ({"username": "  "}, "All fields are required"),
            ({"password": 12345678}, "All fields are required"),
            ({"email": "not-an-email"}, "Enter a valid email address"),
            ({"password": "short"}, "Password must be at least 8 characters"),
        ]
        for overrides, message in cases:
            with self.subTest(overrides=overrides):
                response = self.register(**overrides)
                self.assertEqual((response.status_code, response.data["message"]), (400, message))

    def test_duplicate_email_is_rejected(self):
        self.register()

        response = self.register(username="other", email="R@Test.com")

        self.assertEqual((response.status_code, response.data["message"]), (400, "Email already exists"))


class LogoutTests(APITestCase):
    def setUp(self):
        self.user = get_user_model().objects.create_user(username="runner", email="r@test.com", password="password123")
        self.token = Token.objects.create(user=self.user)

    def test_logout_revokes_token(self):
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {self.token.key}")

        response = self.client.post(LOGOUT_URL)

        self.assertEqual(response.status_code, 200)
        self.assertFalse(Token.objects.filter(user=self.user).exists())
        self.assertEqual(self.client.get(PROFILE_URL).status_code, 401)

    def test_logout_requires_token(self):
        self.assertEqual(self.client.post(LOGOUT_URL).status_code, 401)


class GoogleLoginTests(APITestCase):
    def setUp(self):
        self.user = get_user_model().objects.create_user(username="runner", email="r@test.com", password="password123")

    def test_existing_user_gets_token(self):
        with mock.patch(VERIFY, return_value={"email": "R@test.com", "email_verified": True}):
            response = self.client.post(GOOGLE_URL, {"credential": "id-token"}, format="json")

        self.assertEqual(response.status_code, 200)
        data = response.data["data"]
        self.assertEqual((data["username"], data["email"]), ("runner", "r@test.com"))
        self.assertEqual(data["token"], Token.objects.get(user=self.user).key)

    def google(self, email, name=None, verified=True):
        claims = {"email": email, "email_verified": verified}
        if name is not None:
            claims["name"] = name
        with mock.patch(VERIFY, return_value=claims):
            return self.client.post(GOOGLE_URL, {"credential": "id-token"}, format="json")

    def test_unknown_email_creates_an_account(self):
        response = self.google("new.runner@test.com", name="New Runner")

        self.assertEqual(response.status_code, 201)
        user = get_user_model().objects.get(email="new.runner@test.com")
        self.assertEqual(response.data["data"]["username"], "new.runner")
        self.assertEqual(response.data["data"]["token"], Token.objects.get(user=user).key)
        self.assertFalse(user.has_usable_password())
        self.assertEqual(user.profile.display_name, "New Runner")

    def test_created_account_logs_in_on_the_next_google_sign_in(self):
        first = self.google("new@test.com", name="New")
        second = self.google("new@test.com", name="New")

        self.assertEqual((first.status_code, second.status_code), (201, 200))
        self.assertEqual(first.data["data"]["token"], second.data["data"]["token"])
        self.assertEqual(get_user_model().objects.filter(email="new@test.com").count(), 1)

    def test_taken_username_and_nickname_get_a_number(self):
        other = get_user_model().objects.create_user(username="pacer", email="p@test.com", password="password123")
        Profile.objects.create(user=other, display_name="Fast Pacer")

        response = self.google("RUNNER@other.com", name="fast pacer")

        self.assertEqual(response.status_code, 201)
        user = get_user_model().objects.get(email="RUNNER@other.com")
        self.assertEqual(user.username, "RUNNER2")
        self.assertEqual(user.profile.display_name, "fast pacer2")

    def test_username_drops_invalid_characters_and_has_a_fallback(self):
        self.google("a!b#c@test.com")
        self.google("!!!@test.com")

        users = get_user_model().objects
        self.assertEqual(users.get(email="a!b#c@test.com").username, "abc")
        self.assertEqual(users.get(email="!!!@test.com").username, "runner2")

    def test_account_without_google_name_has_no_nickname(self):
        self.google("quiet@test.com")

        user = get_user_model().objects.get(email="quiet@test.com")
        self.assertFalse(Profile.objects.filter(user=user).exclude(display_name="").exists())

    def test_unverified_email_does_not_create_an_account(self):
        response = self.google("new@test.com", verified=False)

        self.assertEqual(response.status_code, 401)
        self.assertFalse(get_user_model().objects.filter(email="new@test.com").exists())

    def test_invalid_credential_is_rejected(self):
        with mock.patch(VERIFY, side_effect=ValueError("bad token")):
            response = self.client.post(GOOGLE_URL, {"credential": "id-token"}, format="json")

        self.assertEqual(response.status_code, 401)


class LoginTests(APITestCase):
    def setUp(self):
        self.user = get_user_model().objects.create_user(
            username="runner",
            email="r@test.com",
            password="password123",
        )

    def test_valid_credentials_return_token(self):
        response = self.client.post(
            LOGIN_URL,
            {"username": "runner", "password": "password123"},
            format="json",
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["data"]["username"], "runner")
        self.assertEqual(response.data["data"]["token"], Token.objects.get(user=self.user).key)

    def test_missing_credentials_are_rejected(self):
        response = self.client.post(
            LOGIN_URL,
            {"username": "runner"},
            format="json",
        )

        self.assertEqual(response.status_code, 400)

    def test_wrong_password_is_rejected(self):
        response = self.client.post(
            LOGIN_URL,
            {"username": "runner", "password": "wrong-password"},
            format="json",
        )

        self.assertEqual(response.status_code, 401)

    def test_unknown_username_is_rejected(self):
        response = self.client.post(
            LOGIN_URL,
            {"username": "unknown", "password": "password123"},
            format="json",
        )

        self.assertEqual(response.status_code, 401)


class ProfileTests(APITestCase):
    def setUp(self):
        self.user = get_user_model().objects.create_user(
            username="runner",
            email="r@test.com",
            password="password123",
        )
        self.token = Token.objects.create(user=self.user)

    def test_authenticated_user_can_view_profile(self):
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {self.token.key}")

        response = self.client.get(PROFILE_URL)

        self.assertEqual(response.status_code, 200)
        self.assertEqual(
            response.data["data"],
            {
                "username": "runner",
                "email": "r@test.com",
                "display_name": "",
                "status_message": "",
                "avatar_url": None,
                "follower_count": 0,
                "following_count": 0,
                "invite_count": 0,
                "unread_notification_count": 0,
                "has_password": True,
            },
        )

    def test_profile_requires_token(self):
        response = self.client.get(PROFILE_URL)

        self.assertEqual(response.status_code, 401)


    def test_missing_credential_is_rejected(self):
        response = self.client.post(GOOGLE_URL, {}, format="json")

        self.assertEqual(response.status_code, 400)

    def test_unverified_email_is_rejected(self):
        with mock.patch(
            VERIFY,
            return_value={"email": "r@test.com", "email_verified": False},
        ):
            response = self.client.post(
                GOOGLE_URL,
                {"credential": "id-token"},
                format="json",
            )

        self.assertEqual(response.status_code, 401)