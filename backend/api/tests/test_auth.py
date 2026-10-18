from unittest import mock

from django.contrib.auth import get_user_model
from rest_framework.authtoken.models import Token
from rest_framework.test import APITestCase


LOGOUT_URL = "/api/v1/auth/logout/"
GOOGLE_URL = "/api/v1/auth/google/"
PROFILE_URL = "/api/v1/users/my_profile/"
VERIFY = "api.views.auth.id_token.verify_oauth2_token"


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
