from django.utils import timezone
from rest_framework import status
from rest_framework.decorators import api_view
from rest_framework.response import Response

from api.services.challenges import challenge_summary, validate_goals
from api.services.language import request_language
from api.views.profile import get_profile


def _auth_required():
    return Response({"status": "error", "message": "Authentication required."}, status=status.HTTP_401_UNAUTHORIZED)


@api_view(["GET"])
def challenges(request):
    if not request.user.is_authenticated:
        return _auth_required()
    data = challenge_summary(
        request.user,
        get_profile(request.user),
        request.query_params.get("tz"),
        request_language(request),
        timezone.now(),
    )
    return Response({"status": "success", "data": data}, status=status.HTTP_200_OK)


@api_view(["PUT"])
def challenge_goals(request):
    if not request.user.is_authenticated:
        return _auth_required()
    goals, error = validate_goals(request.data)
    if error:
        return Response({"status": "error", "message": error}, status=status.HTTP_400_BAD_REQUEST)
    profile = get_profile(request.user)
    for key, value in goals.items():
        setattr(profile, key, value)
    if goals:
        profile.save(update_fields=list(goals))
    return Response(
        {"status": "success", "data": {"weekly_goal_m": profile.weekly_goal_m, "monthly_goal_m": profile.monthly_goal_m}},
        status=status.HTTP_200_OK,
    )
