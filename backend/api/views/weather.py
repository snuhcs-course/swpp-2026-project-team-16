import math

from rest_framework import status
from rest_framework.decorators import api_view
from rest_framework.response import Response

from api.services.language import request_language
from api.services.weather import WeatherError, build_briefing, fetch_raw


def _coordinate(value, low, high):
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if math.isfinite(number) and low <= number <= high else None


@api_view(["GET"])
def weather_briefing(request):
    latitude = _coordinate(request.query_params.get("lat"), -90, 90)
    longitude = _coordinate(request.query_params.get("lng"), -180, 180)
    if latitude is None or longitude is None:
        return Response(
            {"status": "error", "message": "lat and lng must be valid coordinates."},
            status=status.HTTP_400_BAD_REQUEST,
        )
    try:
        data = build_briefing(fetch_raw(latitude, longitude), request_language(request))
    except WeatherError:
        return Response(
            {"status": "error", "message": "Weather is unavailable right now."},
            status=status.HTTP_503_SERVICE_UNAVAILABLE,
        )
    return Response({"status": "success", "data": data}, status=status.HTTP_200_OK)
