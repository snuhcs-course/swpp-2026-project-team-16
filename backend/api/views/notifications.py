from rest_framework import status
from rest_framework.decorators import api_view
from rest_framework.response import Response

from api.models import DeviceToken, Notification
from api.services.language import request_language
from api.services.notifications import render, user_settings, validate_settings
from api.views.profile import get_profile


PAGE_SIZE = 50
MAX_TOKEN_LENGTH = 512


def _error(message, code):
    return Response({"status": "error", "message": message}, status=code)


def _auth_required():
    return _error("Authentication required.", status.HTTP_401_UNAUTHORIZED)


def _notification_data(notification, language):
    title, body = render(notification, language)
    return {
        "id": notification.id,
        "kind": notification.kind,
        "title": title,
        "body": body,
        "actor": notification.actor.username if notification.actor_id else None,
        "group_id": notification.group_id,
        "post_id": notification.post_id,
        "shared_route_id": notification.shared_route_id,
        "read": notification.read,
        "created_at": notification.created_at,
    }


@api_view(["GET"])
def notifications_list(request):
    if not request.user.is_authenticated:
        return _auth_required()
    language = request_language(request)
    notifications = (
        Notification.objects.filter(recipient=request.user)
        .select_related("actor", "group", "post", "shared_route")[:PAGE_SIZE]
    )
    unread = Notification.objects.filter(recipient=request.user, read=False).count()
    return Response(
        {
            "status": "success",
            "data": {
                "unread_count": unread,
                "items": [_notification_data(notification, language) for notification in notifications],
            },
        },
        status=status.HTTP_200_OK,
    )


@api_view(["POST"])
def notifications_read(request):
    if not request.user.is_authenticated:
        return _auth_required()
    notifications = Notification.objects.filter(recipient=request.user, read=False)
    notification_id = request.data.get("id")
    if notification_id is not None:
        notifications = notifications.filter(id=notification_id)
    notifications.update(read=True)
    unread = Notification.objects.filter(recipient=request.user, read=False).count()
    return Response({"status": "success", "data": {"unread_count": unread}}, status=status.HTTP_200_OK)


@api_view(["GET", "PUT"])
def notification_settings(request):
    if not request.user.is_authenticated:
        return _auth_required()
    if request.method == "PUT":
        changes, error = validate_settings(request.data)
        if error:
            return _error(error, status.HTTP_400_BAD_REQUEST)
        profile = get_profile(request.user)
        profile.notification_settings = {**user_settings(request.user), **changes}
        profile.save(update_fields=["notification_settings"])
    return Response({"status": "success", "data": user_settings(request.user)}, status=status.HTTP_200_OK)


@api_view(["POST", "DELETE"])
def devices(request):
    if not request.user.is_authenticated:
        return _auth_required()
    token = request.data.get("token")
    if not isinstance(token, str) or not token.strip() or len(token) > MAX_TOKEN_LENGTH:
        return _error("A device token is required.", status.HTTP_400_BAD_REQUEST)
    if request.method == "POST":
        DeviceToken.objects.update_or_create(
            token=token,
            defaults={"user": request.user, "language": request_language(request)},
        )
        return Response({"status": "success"}, status=status.HTTP_201_CREATED)
    DeviceToken.objects.filter(token=token, user=request.user).delete()
    return Response({"status": "success"}, status=status.HTTP_200_OK)
