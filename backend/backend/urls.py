from django.contrib import admin
from django.urls import path
from api.views import auth, board, challenges, coach, community, elevation, generate_route, groups, notifications, places, profile, routes, social, weather

urlpatterns = [
    path("api/v1/auth/register/", auth.register_user, name="register_user"),
    path("api/v1/auth/login/", auth.login_user, name="login_user"),
    path("api/v1/auth/google/", auth.google_login, name="google_login"),
    path("api/v1/auth/logout/", auth.logout_user, name="logout_user"),

    # User
    path("api/v1/users/my_profile/", auth.my_profile, name="my_profile"),
    path("api/v1/users/my_profile/avatar/", profile.my_avatar, name="my_avatar"),
    path("api/v1/users/my_profile/running_style/", coach.running_style, name="running_style"),
    path("api/v1/users/my_profile/notification_settings/", notifications.notification_settings, name="notification_settings"),
    path("api/v1/notifications/", notifications.notifications_list, name="notifications_list"),
    path("api/v1/notifications/read/", notifications.notifications_read, name="notifications_read"),
    path("api/v1/devices/", notifications.devices, name="devices"),
    path("api/v1/users/<int:user_id>/avatar/", profile.user_avatar, name="user_avatar"),
    path("api/v1/users/<str:username>/profile/", social.user_profile, name="user_profile"),
    path("api/v1/users/<str:username>/follow/", social.user_follow, name="user_follow"),
    path("api/v1/users/<str:username>/followers/", social.user_followers, name="user_followers"),
    path("api/v1/users/<str:username>/following/", social.user_following, name="user_following"),

    # Routes
    path("api/v1/routes/generate/", generate_route.generate_route_api, name="generate_route"),
    path("api/v1/elevations/", elevation.elevations_api, name="elevations"),
    path("api/v1/routes/saved/", routes.saved_routes_list, name="saved_routes_list"),
    path("api/v1/routes/saved/<int:route_id>/", routes.saved_routes_detail, name="saved_routes_detail"),

    # Community
    path("api/v1/runs/", community.route_runs, name="route_runs"),
    path("api/v1/runs/plan/", coach.run_plan, name="run_plan"),
    path("api/v1/runs/<int:run_id>/", community.run_detail, name="run_detail"),
    path("api/v1/runs/<int:run_id>/report/", coach.run_report, name="run_report"),
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
    path("api/v1/groups/<int:group_id>/invites/", groups.group_invites, name="group_invites"),
    path("api/v1/invites/", groups.my_invites, name="my_invites"),
    path("api/v1/invites/<int:invite_id>/", groups.invite_detail, name="invite_detail"),
    path("api/v1/board/posts/", board.posts_list, name="posts_list"),
    path("api/v1/board/posts/<int:post_id>/", board.posts_detail, name="posts_detail"),
    path("api/v1/board/posts/<int:post_id>/like/", board.post_like, name="post_like"),
    path("api/v1/board/posts/<int:post_id>/scrap/", board.post_scrap, name="post_scrap"),
    path("api/v1/board/posts/<int:post_id>/report/", board.post_report, name="post_report"),
    path("api/v1/board/posts/<int:post_id>/comments/", board.post_comments, name="post_comments"),
    path("api/v1/board/comments/<int:comment_id>/", board.comment_detail, name="comment_detail"),
    path("api/v1/board/comments/<int:comment_id>/report/", board.comment_report, name="comment_report"),

    path("api/v1/weather/", weather.weather_briefing, name="weather_briefing"),
    path("api/v1/challenges/", challenges.challenges, name="challenges"),
    path("api/v1/challenges/goals/", challenges.challenge_goals, name="challenge_goals"),
    path("api/v1/places/search/", places.search_places_api, name="search_places"),
    path("api/v1/places/reverse/", places.reverse_geocode_api, name="reverse_geocode"),
    path("api/v1/places/translate/", places.translate_place_api, name="translate_place"),
]
