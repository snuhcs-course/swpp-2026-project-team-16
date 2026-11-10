from django.contrib.auth import get_user_model
from django.db import IntegrityError
from django.utils import timezone
from rest_framework import status
from rest_framework.decorators import api_view
from rest_framework.response import Response

from api.models import Follow
from api.services.language import request_language
from api.services.notifications import notify
from api.views.board import _post_data, _visible_posts
from api.views.community import _shared_route_data, _shared_routes
from api.views.groups import _group_data, _groups
from api.views.profile import get_profile


User = get_user_model()

PROFILE_ITEM_LIMIT = 20


def _error(message, code):
    return Response({"status": "error", "message": message}, status=code)


def _auth_required():
    return _error("Authentication required.", status.HTTP_401_UNAUTHORIZED)


def _user_not_found():
    return _error("User not found.", status.HTTP_404_NOT_FOUND)


def avatar_url(user):
    profile = get_profile(user)
    if not profile.avatar_updated_at:
        return None
    return f"api/v1/users/{user.id}/avatar/?v={int(profile.avatar_updated_at.timestamp())}"


def follow_counts(user):
    return {
        "follower_count": Follow.objects.filter(followee=user).count(),
        "following_count": Follow.objects.filter(follower=user).count(),
    }


def followed_ids(user):
    if not user.is_authenticated:
        return set()
    return set(Follow.objects.filter(follower=user).values_list("followee_id", flat=True))


def _user_summary(target, viewer, following):
    profile = get_profile(target)
    return {
        "username": target.username,
        "display_name": profile.display_name,
        "avatar_url": avatar_url(target),
        "is_following": target.id in following,
        "is_me": viewer.is_authenticated and viewer.id == target.id,
    }


def _follow_state(target, viewer):
    return {
        **follow_counts(target),
        "is_following": viewer.is_authenticated and Follow.objects.filter(follower=viewer, followee=target).exists(),
    }


def _get_user(username):
    return User.objects.filter(username=username).first()


@api_view(["GET"])
def user_profile(request, username):
    target = _get_user(username)
    if target is None:
        return _user_not_found()
    viewer = request.user
    language = request_language(request)
    now = timezone.now()
    profile = get_profile(target)
    shared_routes = _shared_routes().filter(shared_by=target)[:PROFILE_ITEM_LIMIT]
    groups = _groups().filter(host=target, meet_at__gt=now).order_by("meet_at", "id")[:PROFILE_ITEM_LIMIT]
    posts = _visible_posts().filter(author=target, anonymous=False).order_by("-created_at", "-id")[:PROFILE_ITEM_LIMIT]
    data = {
        "username": target.username,
        "display_name": profile.display_name,
        "status_message": profile.status_message,
        "avatar_url": avatar_url(target),
        "is_me": viewer.is_authenticated and viewer.id == target.id,
        **_follow_state(target, viewer),
        "shared_routes": [_shared_route_data(route, viewer, language=language) for route in shared_routes],
        "groups": [_group_data(group, viewer, now, language=language) for group in groups],
        "posts": [_post_data(post, viewer) for post in posts],
    }
    return Response({"status": "success", "data": data}, status=status.HTTP_200_OK)


@api_view(["POST", "DELETE"])
def user_follow(request, username):
    if not request.user.is_authenticated:
        return _auth_required()
    target = _get_user(username)
    if target is None:
        return _user_not_found()
    if target.id == request.user.id:
        return _error("You can't follow yourself.", status.HTTP_400_BAD_REQUEST)
    if request.method == "POST":
        try:
            _, created = Follow.objects.get_or_create(follower=request.user, followee=target)
        except IntegrityError:
            created = False
        if created:
            notify(target, "follow", actor=request.user)
    else:
        Follow.objects.filter(follower=request.user, followee=target).delete()
    return Response({"status": "success", "data": _follow_state(target, request.user)}, status=status.HTTP_200_OK)


def _follow_list(request, username, followers):
    target = _get_user(username)
    if target is None:
        return _user_not_found()
    if followers:
        users = User.objects.filter(following__followee=target).order_by("-following__created_at", "id")
    else:
        users = User.objects.filter(followers__follower=target).order_by("-followers__created_at", "id")
    following = followed_ids(request.user)
    data = [_user_summary(user, request.user, following) for user in users]
    return Response({"status": "success", "data": data}, status=status.HTTP_200_OK)


@api_view(["GET"])
def user_followers(request, username):
    return _follow_list(request, username, followers=True)


@api_view(["GET"])
def user_following(request, username):
    return _follow_list(request, username, followers=False)
