from django.contrib import admin
from django.urls import path
from api.views import auth, generate_route, routes

urlpatterns = [
    path("api/v1/auth/register/", auth.register_user, name="register_user"),
    path("api/v1/auth/login/", auth.login_user, name="login_user"),

    # User
    path("api/v1/users/my_profile/", auth.my_profile, name="my_profile"),

    # Routes
    path("api/v1/routes/generate/", generate_route.generate_route_api, name="generate_route"),
    path("api/v1/routes/saved/", routes.saved_routes_list, name="saved_routes_list"),
    path("api/v1/routes/saved/<int:route_id>/", routes.saved_routes_detail, name="saved_routes_detail"),
]