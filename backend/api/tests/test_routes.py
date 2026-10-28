from unittest import mock

from django.contrib.auth import get_user_model
from django.test import TestCase
from rest_framework.test import APIRequestFactory, force_authenticate

from api.models import Route, TemporaryRoute, UserSavedRoute
from api.views.routes import saved_routes_detail, saved_routes_list


User = get_user_model()


class SavedRoutesTestBase(TestCase):
    def setUp(self):
        self.factory = APIRequestFactory()
        self.user = User.objects.create_user(
            username="runner",
            email="runner@example.com",
            password="password123",
        )
        self.other_user = User.objects.create_user(
            username="other",
            email="other@example.com",
            password="password123",
        )

    def create_route(self, created_by=None):
        return Route.objects.create(
            created_by=created_by,
            distance=1000,
            briefing="A test route",
            start_name="Start",
            end_name="End",
            route=[{
                "type": "LineString",
                "coordinates": [[127.0, 37.0], [127.1, 37.1]],
            }],
        )

    def call_view(self, view, method, data=None, user=None, route_id=None):
        method = method.lower()
        request_method = getattr(self.factory, method)

        if method == "get":
            request = request_method("/")
        else:
            request = request_method("/", data or {}, format="json")

        if user is not None:
            force_authenticate(request, user=user)

        if route_id is not None:
            return view(request, route_id=route_id)

        return view(request)


class SavedRoutesListTests(SavedRoutesTestBase):
    def test_get_requires_authentication(self):
        response = self.call_view(saved_routes_list, "get")

        self.assertEqual(response.status_code, 401)

    def test_get_returns_only_the_current_users_saved_routes(self):
        route = self.create_route(created_by=self.user)
        UserSavedRoute.objects.create(user=self.user, route=route, name="My route")

        other_route = self.create_route(created_by=self.other_user)
        UserSavedRoute.objects.create(
            user=self.other_user,
            route=other_route,
            name="Other user's route",
        )

        response = self.call_view(saved_routes_list, "get", user=self.user)

        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(response.data[0]["id"], route.id)
        self.assertEqual(response.data[0]["name"], "My route")
        self.assertEqual(response.data[0]["created_by"], "runner")

    def test_post_requires_authentication(self):
        response = self.call_view(
            saved_routes_list,
            "post",
            data={"route_id": 1},
        )

        self.assertEqual(response.status_code, 401)

    def test_post_saves_route_with_trimmed_name(self):
        route = self.create_route(created_by=self.user)

        response = self.call_view(
            saved_routes_list,
            "post",
            data={"route_id": route.id, "name": "  My route  "},
            user=self.user,
        )

        self.assertEqual(response.status_code, 201)
        self.assertTrue(
            UserSavedRoute.objects.filter(
                user=self.user,
                route=route,
                name="My route",
            ).exists()
        )

    def test_post_rejects_missing_or_both_route_ids(self):
        cases = [
            {},
            {"route_id": 1, "temporary_route_id": 1},
        ]

        for data in cases:
            with self.subTest(data=data):
                response = self.call_view(
                    saved_routes_list,
                    "post",
                    data=data,
                    user=self.user,
                )

                self.assertEqual(response.status_code, 400)

    def test_post_returns_404_for_unknown_route(self):
        response = self.call_view(
            saved_routes_list,
            "post",
            data={"route_id": 999999},
            user=self.user,
        )

        self.assertEqual(response.status_code, 404)

    def test_post_again_without_name_does_not_create_duplicate(self):
        route = self.create_route(created_by=self.user)
        UserSavedRoute.objects.create(user=self.user, route=route, name="Existing name")

        response = self.call_view(
            saved_routes_list,
            "post",
            data={"route_id": route.id},
            user=self.user,
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(
            UserSavedRoute.objects.filter(user=self.user, route=route).count(),
            1,
        )

    def test_post_again_with_name_updates_saved_route_name(self):
        route = self.create_route(created_by=self.user)
        UserSavedRoute.objects.create(user=self.user, route=route, name="Old name")

        response = self.call_view(
            saved_routes_list,
            "post",
            data={"route_id": route.id, "name": "  New name  "},
            user=self.user,
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(
            UserSavedRoute.objects.get(user=self.user, route=route).name,
            "New name",
        )

    def test_post_rejects_name_longer_than_255_characters(self):
        route = self.create_route(created_by=self.user)

        response = self.call_view(
            saved_routes_list,
            "post",
            data={"route_id": route.id, "name": "x" * 256},
            user=self.user,
        )

        self.assertEqual(response.status_code, 400)

    def test_post_returns_404_for_temporary_route_owned_by_another_user(self):
        temporary_route = TemporaryRoute.objects.create(
            user=self.other_user,
            distance=1000,
            briefing="Temporary route",
            route=[{
                "type": "LineString",
                "coordinates": [[127.0, 37.0], [127.1, 37.1]],
            }],
        )

        response = self.call_view(
            saved_routes_list,
            "post",
            data={"temporary_route_id": temporary_route.id},
            user=self.user,
        )

        self.assertEqual(response.status_code, 404)

    def test_post_saves_temporary_route_generated_as_guest(self):
        temporary_route = TemporaryRoute.objects.create(
            user=None,
            distance=1000,
            briefing="Guest route",
            route=[{
                "type": "LineString",
                "coordinates": [[127.0, 37.0], [127.1, 37.1]],
            }],
        )

        response = self.call_view(
            saved_routes_list,
            "post",
            data={"temporary_route_id": temporary_route.id},
            user=self.user,
        )

        self.assertEqual(response.status_code, 201)
        self.assertFalse(TemporaryRoute.objects.filter(id=temporary_route.id).exists())
        saved = UserSavedRoute.objects.get(user=self.user)
        self.assertEqual(saved.route.created_by, self.user)
        self.assertEqual(saved.route.briefing, "Guest route")

    def test_post_saves_temporary_route_and_deletes_temporary_record(self):
        temporary_route = TemporaryRoute.objects.create(
            user=self.user,
            distance=1000,
            briefing="Temporary route",
            route=[{
                "type": "LineString",
                "coordinates": [[127.0, 37.0], [127.1, 37.1]],
            }],
        )
        route = self.create_route(created_by=self.user)

        with mock.patch.object(
            TemporaryRoute,
            "create_route",
            return_value=route,
        ) as create_route_mock:
            response = self.call_view(
                saved_routes_list,
                "post",
                data={
                    "temporary_route_id": temporary_route.id,
                    "name": "  Saved temporary route  ",
                },
                user=self.user,
            )

        self.assertEqual(response.status_code, 201)
        create_route_mock.assert_called_once_with(created_by=self.user)
        self.assertFalse(
            TemporaryRoute.objects.filter(id=temporary_route.id).exists()
        )
        self.assertTrue(
            UserSavedRoute.objects.filter(
                user=self.user,
                route=route,
                name="Saved temporary route",
            ).exists()
        )


class SavedRoutesDetailTests(SavedRoutesTestBase):
    def setUp(self):
        super().setUp()
        self.route = self.create_route(created_by=self.user)
        self.saved_route = UserSavedRoute.objects.create(
            user=self.user,
            route=self.route,
            name="My route",
        )

    def test_detail_requires_authentication(self):
        response = self.call_view(
            saved_routes_detail,
            "get",
            route_id=self.route.id,
        )

        self.assertEqual(response.status_code, 401)

    def test_get_returns_saved_route_details(self):
        response = self.call_view(
            saved_routes_detail,
            "get",
            user=self.user,
            route_id=self.route.id,
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["data"]["id"], self.route.id)
        self.assertEqual(response.data["data"]["name"], "My route")
        self.assertEqual(response.data["data"]["start_name"], "Start")
        self.assertEqual(response.data["data"]["end_name"], "End")

    def test_get_returns_404_for_route_not_saved_by_user(self):
        response = self.call_view(
            saved_routes_detail,
            "get",
            user=self.other_user,
            route_id=self.route.id,
        )

        self.assertEqual(response.status_code, 404)

    def test_patch_renames_saved_route_and_trims_whitespace(self):
        response = self.call_view(
            saved_routes_detail,
            "patch",
            data={"name": "  New name  "},
            user=self.user,
            route_id=self.route.id,
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["data"]["name"], "New name")
        self.saved_route.refresh_from_db()
        self.assertEqual(self.saved_route.name, "New name")

    def test_patch_rejects_missing_blank_and_too_long_names(self):
        cases = [
            {},
            {"name": "   "},
            {"name": "x" * 256},
        ]

        for data in cases:
            with self.subTest(data=data):
                response = self.call_view(
                    saved_routes_detail,
                    "patch",
                    data=data,
                    user=self.user,
                    route_id=self.route.id,
                )

                self.assertEqual(response.status_code, 400)

    def test_delete_removes_saved_route_but_keeps_route(self):
        response = self.call_view(
            saved_routes_detail,
            "delete",
            user=self.user,
            route_id=self.route.id,
        )

        self.assertEqual(response.status_code, 200)
        self.assertFalse(
            UserSavedRoute.objects.filter(
                user=self.user,
                route=self.route,
            ).exists()
        )
        self.assertTrue(Route.objects.filter(id=self.route.id).exists())