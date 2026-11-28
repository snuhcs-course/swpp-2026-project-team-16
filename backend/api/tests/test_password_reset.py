import re
from datetime import timedelta
from unittest import mock

from django.contrib.auth import get_user_model
from django.core import mail
from django.core.cache import cache
from django.utils import timezone
from rest_framework.authtoken.models import Token
from rest_framework.test import APITestCase

from api.models import PasswordResetCode


REQUEST_URL = "/api/v1/auth/password-reset/"
CONFIRM_URL = "/api/v1/auth/password-reset/confirm/"
LOGIN_URL = "/api/v1/auth/login/"
SENT = "If an account uses this email, we sent a reset code."
INVALID = "The code is invalid or has expired."


class PasswordResetTests(APITestCase):
    def setUp(self):
        cache.clear()
        self.addCleanup(cache.clear)
        self.user = get_user_model().objects.create_user(username="runner", email="Runner@Test.com", password="old-password")

    def request_code(self, email="runner@test.com", **extra):
        return self.client.post(REQUEST_URL, {"email": email}, format="json", **extra)

    def sent_code(self):
        return re.search(r"(?<!\d)(\d{6})(?!\d)", mail.outbox[-1].body).group(1)

    def confirm(self, code, password="new-password", email="runner@test.com"):
        return self.client.post(CONFIRM_URL, {"email": email, "code": code, "new_password": password}, format="json")

    def test_request_emails_a_code_and_stores_only_its_hash(self):
        response = self.request_code(HTTP_ACCEPT_LANGUAGE="en-US")

        self.assertEqual((response.status_code, response.data["message"]), (200, SENT))
        self.assertEqual(len(mail.outbox), 1)
        self.assertEqual(mail.outbox[0].to, ["Runner@test.com"])
        self.assertIn("password reset code", mail.outbox[0].subject)
        code = self.sent_code()
        stored = PasswordResetCode.objects.get(user=self.user)
        self.assertNotIn(code, stored.code_hash)
        self.assertAlmostEqual(stored.expires_at, timezone.now() + timedelta(minutes=10), delta=timedelta(seconds=30))

    def test_email_uses_korean_by_default(self):
        self.request_code()

        self.assertIn("비밀번호 재설정 코드", mail.outbox[0].subject)

    def test_unknown_or_passwordless_email_gets_the_same_response_without_mail(self):
        google_user = get_user_model().objects.create_user(username="google", email="g@test.com")
        google_user.set_unusable_password()
        google_user.save()

        for email in ("nobody@test.com", "g@test.com"):
            with self.subTest(email=email):
                response = self.request_code(email)
                self.assertEqual((response.status_code, response.data), (200, {"status": "success", "message": SENT}))
        self.assertEqual(mail.outbox, [])
        self.assertFalse(PasswordResetCode.objects.exists())

    def test_invalid_email_is_rejected(self):
        response = self.request_code("not-an-email")

        self.assertEqual((response.status_code, response.data["message"]), (400, "Enter a valid email address"))

    def test_mail_failure_still_returns_success(self):
        with mock.patch("api.views.password_reset.send_mail", side_effect=OSError("smtp down")):
            with self.assertLogs("api.views.password_reset", level="ERROR"):
                response = self.request_code()

        self.assertEqual(response.status_code, 200)

    def test_confirm_sets_password_and_logs_out_everywhere(self):
        Token.objects.create(user=self.user)
        self.request_code()

        response = self.confirm(self.sent_code())

        self.assertEqual(response.status_code, 200)
        self.user.refresh_from_db()
        self.assertTrue(self.user.check_password("new-password"))
        self.assertFalse(Token.objects.filter(user=self.user).exists())
        self.assertIsNotNone(PasswordResetCode.objects.get(user=self.user).used_at)
        login = self.client.post(LOGIN_URL, {"username": "runner", "password": "new-password"}, format="json")
        self.assertEqual(login.status_code, 200)

    def test_code_works_only_once(self):
        self.request_code()
        code = self.sent_code()
        self.confirm(code)

        response = self.confirm(code, password="another-password")

        self.assertEqual((response.status_code, response.data["message"]), (400, INVALID))

    def test_new_request_invalidates_older_code(self):
        with mock.patch("api.views.password_reset.secrets.randbelow", side_effect=[111111, 2222]):
            self.request_code()
            self.request_code()

        self.assertEqual(self.sent_code(), "002222")
        self.assertEqual(self.confirm("111111").data["message"], INVALID)
        self.assertEqual(self.confirm("002222").status_code, 200)

    def test_expired_code_is_rejected(self):
        self.request_code()
        PasswordResetCode.objects.update(expires_at=timezone.now() - timedelta(seconds=1))

        response = self.confirm(self.sent_code())

        self.assertEqual((response.status_code, response.data["message"]), (400, INVALID))

    def test_code_is_locked_after_five_wrong_tries(self):
        self.request_code()
        code = self.sent_code()
        wrong = "000000" if code != "000000" else "111111"
        for _ in range(5):
            self.assertEqual(self.confirm(wrong).data["message"], INVALID)

        response = self.confirm(code)

        self.assertEqual((response.status_code, response.data["message"]), (400, INVALID))
        self.user.refresh_from_db()
        self.assertTrue(self.user.check_password("old-password"))

    def test_confirm_validates_input(self):
        self.request_code()
        code = self.sent_code()
        cases = [
            ({"email": "runner@test.com", "code": code}, "All fields are required"),
            ({"email": "runner@test.com", "code": code, "new_password": "short"}, "Password must be at least 8 characters"),
            ({"email": "nobody@test.com", "code": code, "new_password": "new-password"}, INVALID),
        ]
        for body, message in cases:
            with self.subTest(body=body):
                response = self.client.post(CONFIRM_URL, body, format="json")
                self.assertEqual((response.status_code, response.data["message"]), (400, message))

    def test_request_is_limited_per_ip(self):
        for _ in range(5):
            self.assertEqual(self.request_code("nobody@test.com").status_code, 200)

        self.assertEqual(self.request_code("nobody@test.com").status_code, 429)

    def test_confirm_is_limited_per_ip(self):
        for _ in range(10):
            self.confirm("123456", email="nobody@test.com")

        self.assertEqual(self.confirm("123456", email="nobody@test.com").status_code, 429)
