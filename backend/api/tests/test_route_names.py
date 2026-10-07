from unittest import mock

from django.contrib.auth import get_user_model
from rest_framework.authtoken.models import Token

from api.route_algorithm.llm import LLMError
from api.tests.test_generate_route import END, URL, FakeMapTestCase, body


SAVED_URL = "/api/v1/routes/saved/"


@mock.patch("api.route_algorithm.briefing.generate_text", side_effect=LLMError("offline"))
class RouteNameTests(FakeMapTestCase):
    def setUp(self):
        super().setUp()
        user = get_user_model().objects.create_user(username="runner", email="r@test.com", password="password123")
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {Token.objects.create(user=user).key}")

    def generate_and_save(self, **extra):
        response = self.client.post(URL, body(**extra), format="json")
        self.assertEqual(response.status_code, 200)
        temporary_route_id = response.data["data"]["temporary_route_id"]
        saved = self.client.post(SAVED_URL, {"temporary_route_id": temporary_route_id}, format="json")
        self.assertEqual(saved.status_code, 201)
        return saved.data["route_id"]

    def test_saved_routes_include_place_names(self, llm):
        route_id = self.generate_and_save(
            end_point={"type": "Point", "coordinates": END},
            starting_point_name=" 코엑스 ",
            end_point_name="강남역",
        )

        listed = self.client.get(SAVED_URL).data[0]
        self.assertEqual((listed["start_name"], listed["end_name"]), ("코엑스", "강남역"))

        detail = self.client.get(f"{SAVED_URL}{route_id}/").data["data"]
        self.assertEqual((detail["start_name"], detail["end_name"]), ("코엑스", "강남역"))

    def test_names_default_to_empty(self, llm):
        self.generate_and_save()
        listed = self.client.get(SAVED_URL).data[0]
        self.assertEqual((listed["start_name"], listed["end_name"]), ("", ""))

    def test_rejects_invalid_names(self, llm):
        response = self.client.post(URL, body(starting_point_name=123), format="json")
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.data["message"], "starting_point_name must be a string")

        response = self.client.post(URL, body(end_point_name="a" * 101), format="json")
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.data["message"], "end_point_name must be 100 characters or fewer")
