from django.contrib.auth import get_user_model
from django.http import HttpResponse
from django.utils import timezone
from rest_framework import status
from rest_framework.decorators import api_view
from rest_framework.response import Response

from api.models import Profile


User = get_user_model()

MAX_DISPLAY_NAME_LENGTH = 30
MAX_STATUS_LENGTH = 100
MAX_AVATAR_BYTES = 2 * 1024 * 1024
AVATAR_SIGNATURES = {
    b"\xff\xd8\xff": "image/jpeg",
    b"\x89PNG\r\n\x1a\n": "image/png",
}


def _error(message, code):
    return Response({"status": "error", "message": message}, status=code)


def _auth_required():
    return _error("Authentication credentials were not provided.", status.HTTP_401_UNAUTHORIZED)


def get_profile(user):
    return Profile.objects.get_or_create(user=user)[0]


def profile_data(user):
    profile = get_profile(user)
    avatar_url = None
    if profile.avatar_updated_at:
        avatar_url = f"api/v1/users/{user.id}/avatar/?v={int(profile.avatar_updated_at.timestamp())}"
    return {
        "username": user.username,
        "email": user.email,
        "display_name": profile.display_name,
        "status_message": profile.status_message,
        "avatar_url": avatar_url,
        "follower_count": user.followers.count(),
        "following_count": user.following.count(),
    }


def update_profile(user, data):
    profile = get_profile(user)
    fields = []
    for name, limit in (("display_name", MAX_DISPLAY_NAME_LENGTH), ("status_message", MAX_STATUS_LENGTH)):
        if name not in data:
            continue
        value = data.get(name)
        if not isinstance(value, str) or len(value.strip()) > limit:
            label = "Name" if name == "display_name" else "Status message"
            return f"{label} must be {limit} characters or fewer."
        setattr(profile, name, value.strip())
        fields.append(name)
    if fields:
        profile.save(update_fields=fields)
    return None


def _image_type(content):
    for signature, content_type in AVATAR_SIGNATURES.items():
        if content.startswith(signature):
            return content_type
    return None


@api_view(["PUT", "DELETE"])
def my_avatar(request):
    if not request.user.is_authenticated:
        return _auth_required()
    profile = get_profile(request.user)

    if request.method == "DELETE":
        profile.avatar = None
        profile.avatar_type = ""
        profile.avatar_updated_at = None
        profile.save(update_fields=["avatar", "avatar_type", "avatar_updated_at"])
        return Response({"status": "success", "data": profile_data(request.user)}, status=status.HTTP_200_OK)

    upload = request.FILES.get("avatar")
    if upload is None:
        return _error("Attach an image as avatar.", status.HTTP_400_BAD_REQUEST)
    if upload.size > MAX_AVATAR_BYTES:
        return _error("The image must be 2 MB or smaller.", status.HTTP_400_BAD_REQUEST)
    content = upload.read()
    content_type = _image_type(content)
    if content_type is None:
        return _error("The image must be a JPEG or PNG.", status.HTTP_400_BAD_REQUEST)

    profile.avatar = content
    profile.avatar_type = content_type
    profile.avatar_updated_at = timezone.now()
    profile.save(update_fields=["avatar", "avatar_type", "avatar_updated_at"])
    return Response({"status": "success", "data": profile_data(request.user)}, status=status.HTTP_200_OK)


@api_view(["GET"])
def user_avatar(request, user_id):
    profile = Profile.objects.filter(user_id=user_id, avatar_updated_at__isnull=False).first()
    if profile is None or not profile.avatar:
        return _error("Avatar not found.", status.HTTP_404_NOT_FOUND)
    response = HttpResponse(bytes(profile.avatar), content_type=profile.avatar_type)
    response["Cache-Control"] = "public, max-age=86400"
    return response
