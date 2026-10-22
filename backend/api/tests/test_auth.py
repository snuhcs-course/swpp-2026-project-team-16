from unittest import mock

from django.contrib.auth import get_user_model
from rest_framework.authtoken.models import Token
from rest_framework.test import APITestCase


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

    def test_unknown_email_returns_not_found(self):
        with mock.patch(VERIFY, return_value={"email": "new@test.com", "email_verified": True}):
            response = self.client.post(GOOGLE_URL, {"credential": "id-token"}, format="json")

        self.assertEqual(response.status_code, 404)

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
            {"username": "runner", "email": "r@test.com"},
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