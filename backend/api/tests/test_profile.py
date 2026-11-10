from django.contrib.auth import get_user_model
from django.core.files.uploadedfile import SimpleUploadedFile
from rest_framework.authtoken.models import Token
from rest_framework.test import APITestCase


PROFILE_URL = "/api/v1/users/my_profile/"
AVATAR_URL = "/api/v1/users/my_profile/avatar/"
JPEG = b"\xff\xd8\xff\xe0" + b"\x00" * 100
PNG = b"\x89PNG\r\n\x1a\n" + b"\x00" * 100


class ProfileTests(APITestCase):
    def setUp(self):
        self.user = get_user_model().objects.create_user(username="runner", email="runner@test.com", password="password123")
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {Token.objects.create(user=self.user).key}")

    def upload(self, content, name="me.jpg"):
        return self.client.put(AVATAR_URL, {"avatar": SimpleUploadedFile(name, content)}, format="multipart")

    def test_new_profile_is_empty(self):
        data = self.client.get(PROFILE_URL).data["data"]
        self.assertEqual(data, {
            "username": "runner",
            "email": "runner@test.com",
            "display_name": "",
            "status_message": "",
            "avatar_url": None,
            "follower_count": 0,
            "following_count": 0,
        })

    def test_update_name_and_status(self):
        response = self.client.patch(PROFILE_URL, {"display_name": " 예원 ", "status_message": "Sub-30 5k this year"}, format="json")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["data"]["display_name"], "예원")
        self.assertEqual(response.data["data"]["status_message"], "Sub-30 5k this year")
        response = self.client.patch(PROFILE_URL, {"status_message": ""}, format="json")
        self.assertEqual(response.data["data"]["display_name"], "예원")
        self.assertEqual(response.data["data"]["status_message"], "")

    def test_rejects_long_or_invalid_values(self):
        cases = [
            ({"display_name": "a" * 31}, "Name must be 30 characters or fewer."),
            ({"status_message": "a" * 101}, "Status message must be 100 characters or fewer."),
            ({"display_name": 3}, "Name must be 30 characters or fewer."),
        ]
        for body, message in cases:
            with self.subTest(body=body):
                response = self.client.patch(PROFILE_URL, body, format="json")
                self.assertEqual(response.status_code, 400)
                self.assertEqual(response.data["message"], message)

    def test_upload_serve_and_remove_avatar(self):
        response = self.upload(JPEG)

        self.assertEqual(response.status_code, 200)
        url = response.data["data"]["avatar_url"]
        self.assertTrue(url.startswith(f"api/v1/users/{self.user.id}/avatar/?v="))
        self.client.credentials()
        image = self.client.get("/" + url)
        self.assertEqual(image.status_code, 200)
        self.assertEqual(image["Content-Type"], "image/jpeg")
        self.assertEqual(image.content, JPEG)

        self.client.credentials(HTTP_AUTHORIZATION=f"Token {Token.objects.get(user=self.user).key}")
        self.assertEqual(self.upload(PNG, "me.png").status_code, 200)
        self.assertEqual(self.client.get(f"/api/v1/users/{self.user.id}/avatar/")["Content-Type"], "image/png")
        removed = self.client.delete(AVATAR_URL)
        self.assertIsNone(removed.data["data"]["avatar_url"])
        self.assertEqual(self.client.get(f"/api/v1/users/{self.user.id}/avatar/").status_code, 404)

    def test_rejects_bad_avatars(self):
        self.assertEqual(self.upload(b"GIF89a" + b"\x00" * 10, "me.gif").data["message"], "The image must be a JPEG or PNG.")
        self.assertEqual(self.upload(JPEG + b"\x00" * (2 * 1024 * 1024)).data["message"], "The image must be 2 MB or smaller.")
        response = self.client.put(AVATAR_URL, {}, format="multipart")
        self.assertEqual(response.data["message"], "Attach an image as avatar.")

    def test_requires_login(self):
        self.client.credentials()
        self.assertEqual(self.client.patch(PROFILE_URL, {"display_name": "x"}, format="json").status_code, 401)
        self.assertEqual(self.upload(JPEG).status_code, 401)
