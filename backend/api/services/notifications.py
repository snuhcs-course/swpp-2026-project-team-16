import logging
import os
import threading

from django.db import connection, transaction

from api.models import DeviceToken, Notification


logger = logging.getLogger(__name__)

KINDS = ("follow", "group_invite", "group_join", "group_message", "post_like", "post_comment", "route_review")
FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
FCM_URL = "https://fcm.googleapis.com/v1/projects/{project}/messages:send"
FCM_TIMEOUT_SECONDS = 10
MAX_SNIPPET_LENGTH = 80

TITLES = {
    "ko": {
        "follow": "새 팔로워",
        "group_invite": "그룹 초대",
        "group_join": "그룹 참가",
        "group_message": "{group}",
        "post_like": "좋아요",
        "post_comment": "새 댓글",
        "route_review": "새 리뷰",
    },
    "en": {
        "follow": "New follower",
        "group_invite": "Group invite",
        "group_join": "New member",
        "group_message": "{group}",
        "post_like": "New like",
        "post_comment": "New comment",
        "route_review": "New review",
    },
}

BODIES = {
    "ko": {
        "follow": "{actor}님이 회원님을 팔로우했어요.",
        "group_invite": "{actor}님이 '{group}' 그룹에 초대했어요.",
        "group_join": "{actor}님이 '{group}' 그룹에 참가했어요.",
        "group_message": "{actor}: {text}",
        "post_like": "{actor}님이 '{post}' 글을 좋아해요.",
        "post_comment": "{actor}님이 '{post}' 글에 댓글을 남겼어요: {text}",
        "route_review": "{actor}님이 '{route}' 코스에 리뷰를 남겼어요.",
    },
    "en": {
        "follow": "{actor} started following you.",
        "group_invite": "{actor} invited you to '{group}'.",
        "group_join": "{actor} joined '{group}'.",
        "group_message": "{actor}: {text}",
        "post_like": "{actor} liked your post '{post}'.",
        "post_comment": "{actor} commented on '{post}': {text}",
        "route_review": "{actor} reviewed your route '{route}'.",
    },
}
SOMEONE = {"ko": "누군가", "en": "Someone"}


def default_settings():
    return {kind: True for kind in KINDS}


def user_settings(user):
    from api.views.profile import get_profile

    saved = get_profile(user).notification_settings or {}
    return {kind: bool(saved.get(kind, True)) for kind in KINDS}


def validate_settings(data):
    if not isinstance(data, dict):
        return None, "Settings must be an object."
    unknown = [key for key in data if key not in KINDS]
    if unknown:
        return None, f"Unknown notification types: {', '.join(unknown)}"
    if any(not isinstance(value, bool) for value in data.values()):
        return None, "Each setting must be true or false."
    return data, None


def _display_name(user, language):
    if user is None:
        return SOMEONE[language]
    from api.views.profile import get_profile

    return get_profile(user).display_name or user.username


def _snippet(text):
    text = " ".join(text.split())
    return text if len(text) <= MAX_SNIPPET_LENGTH else text[: MAX_SNIPPET_LENGTH - 1] + "…"


def render(notification, language):
    language = language if language in BODIES else "ko"
    values = {
        "actor": _display_name(notification.actor, language),
        "group": notification.group.title if notification.group_id else "",
        "post": notification.post.title if notification.post_id else "",
        "route": notification.shared_route.title if notification.shared_route_id else "",
        "text": notification.text,
    }
    return (
        TITLES[language][notification.kind].format(**values),
        BODIES[language][notification.kind].format(**values),
    )


def notify(recipient, kind, actor=None, post=None, group=None, shared_route=None, text="", sender=None):
    if recipient is None or (actor is not None and recipient.id == actor.id):
        return None
    notification = Notification.objects.create(
        recipient=recipient,
        actor=actor,
        kind=kind,
        post=post,
        group=group,
        shared_route=shared_route,
        text=_snippet(text),
    )
    if user_settings(recipient).get(kind, True):
        (sender or send_push_async)(notification)
    return notification


def push_payload(notification, language):
    title, body = render(notification, language)
    data = {
        "notification_id": str(notification.id),
        "kind": notification.kind,
        "title": title,
        "body": body,
    }
    if notification.actor_id:
        data["actor"] = notification.actor.username
    if notification.group_id:
        data["group_id"] = str(notification.group_id)
    if notification.post_id:
        data["post_id"] = str(notification.post_id)
    if notification.shared_route_id:
        data["shared_route_id"] = str(notification.shared_route_id)
    return data


_credentials = None
_credentials_lock = threading.Lock()


def _fcm_credentials():
    global _credentials
    path = os.getenv("FIREBASE_CREDENTIALS")
    if not path or not os.path.exists(path):
        return None
    with _credentials_lock:
        if _credentials is None:
            from google.oauth2 import service_account

            _credentials = service_account.Credentials.from_service_account_file(path, scopes=[FCM_SCOPE])
        if not _credentials.valid:
            from google.auth.transport.requests import Request

            _credentials.refresh(Request())
        return _credentials


def send_push(notification):
    devices = list(DeviceToken.objects.filter(user_id=notification.recipient_id))
    if not devices:
        return
    try:
        credentials = _fcm_credentials()
    except Exception:
        logger.exception("Could not load Firebase credentials")
        return
    if credentials is None:
        return

    import requests

    url = FCM_URL.format(project=credentials.project_id)
    headers = {"Authorization": f"Bearer {credentials.token}", "Content-Type": "application/json"}
    for device in devices:
        message = {
            "message": {
                "token": device.token,
                "data": push_payload(notification, device.language),
                "android": {"priority": "high"},
            }
        }
        try:
            response = requests.post(url, json=message, headers=headers, timeout=FCM_TIMEOUT_SECONDS)
        except requests.RequestException:
            logger.warning("FCM request failed")
            continue
        if response.status_code == 404 or (response.status_code == 400 and "UNREGISTERED" in response.text):
            device.delete()
        elif response.status_code >= 400:
            logger.warning("FCM rejected a message: %s", response.status_code)


def _send_and_close(notification_id):
    try:
        notification = Notification.objects.select_related("actor", "group", "post", "shared_route").filter(id=notification_id).first()
        if notification is not None:
            send_push(notification)
    finally:
        connection.close()


def send_push_async(notification):
    if not os.getenv("FIREBASE_CREDENTIALS"):
        return
    transaction.on_commit(
        lambda: threading.Thread(target=_send_and_close, args=(notification.id,), daemon=True).start()
    )
