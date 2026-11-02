from django.contrib import admin
from django.urls import path
from api.views import auth, board, community, elevation, generate_route, groups, places, routes

urlpatterns = [
    path("api/v1/auth/register/", auth.register_user, name="register_user"),
    path("api/v1/auth/login/", auth.login_user, name="login_user"),
    path("api/v1/auth/google/", auth.google_login, name="google_login"),
    path("api/v1/auth/logout/", auth.logout_user, name="logout_user"),

    # User
    path("api/v1/users/my_profile/", auth.my_profile, name="my_profile"),

    # Routes
    path("api/v1/routes/generate/", generate_route.generate_route_api, name="generate_route"),
    path("api/v1/elevations/", elevation.elevations_api, name="elevations"),
    path("api/v1/routes/saved/", routes.saved_routes_list, name="saved_routes_list"),
    path("api/v1/routes/saved/<int:route_id>/", routes.saved_routes_detail, name="saved_routes_detail"),

    # Community
    path("api/v1/runs/", community.route_runs, name="route_runs"),
    path("api/v1/community/routes/", community.shared_routes_list, name="shared_routes_list"),
    path(
        "api/v1/community/routes/<int:shared_route_id>/",
        community.shared_routes_detail,
        name="shared_routes_detail",
    ),
    path(
        "api/v1/community/routes/<int:shared_route_id>/reviews/",
        community.shared_route_reviews,
        name="shared_route_reviews",
    ),
    path("api/v1/community/reviews/<int:review_id>/", community.review_detail, name="review_detail"),
    path("api/v1/groups/", groups.groups_list, name="groups_list"),
    path("api/v1/groups/<int:group_id>/", groups.groups_detail, name="groups_detail"),
    path("api/v1/groups/<int:group_id>/membership/", groups.group_membership, name="group_membership"),
    path("api/v1/groups/<int:group_id>/messages/", groups.group_messages, name="group_messages"),
    path("api/v1/board/posts/", board.posts_list, name="posts_list"),
    path("api/v1/board/posts/<int:post_id>/", board.posts_detail, name="posts_detail"),
    path("api/v1/board/posts/<int:post_id>/like/", board.post_like, name="post_like"),
    path("api/v1/board/posts/<int:post_id>/scrap/", board.post_scrap, name="post_scrap"),
    path("api/v1/board/posts/<int:post_id>/report/", board.post_report, name="post_report"),
    path("api/v1/board/posts/<int:post_id>/comments/", board.post_comments, name="post_comments"),
    path("api/v1/board/comments/<int:comment_id>/", board.comment_detail, name="comment_detail"),
    path("api/v1/board/comments/<int:comment_id>/report/", board.comment_report, name="comment_report"),

    path("api/v1/places/search/", places.search_places_api, name="search_places"),
    path("api/v1/places/reverse/", places.reverse_geocode_api, name="reverse_geocode"),
]
