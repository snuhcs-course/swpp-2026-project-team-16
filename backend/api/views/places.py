from rest_framework import status
from rest_framework.decorators import api_view
from rest_framework.response import Response

from api.route_algorithm.briefing import DEFAULT_LANGUAGE, SUPPORTED_LANGUAGES
from api.services.place_translation import translate_places
from api.services.tmap import PlaceSearchError, reverse_geocode, search_places


MAX_QUERY_LENGTH = 50


def _invalid_language(language):
    if language in SUPPORTED_LANGUAGES:
        return None
    return Response(
        {"status": "error", "message": f"language must be one of: {', '.join(SUPPORTED_LANGUAGES)}"},
        status=status.HTTP_400_BAD_REQUEST,
    )


def _coordinate(value, low, high):
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if low <= number <= high else None


@api_view(["GET"])
def search_places_api(request):
    query = (request.query_params.get("q") or "").strip()
    language = request.query_params.get("language") or DEFAULT_LANGUAGE

    if not query:
        return Response(
            {"status": "error", "message": "q is required"},
            status=status.HTTP_400_BAD_REQUEST,
        )

    if len(query) > MAX_QUERY_LENGTH:
        return Response(
            {"status": "error", "message": f"q must be {MAX_QUERY_LENGTH} characters or fewer"},
            status=status.HTTP_400_BAD_REQUEST,
        )

    if error := _invalid_language(language):
        return error

    try:
        places = search_places(query)
    except PlaceSearchError:
        return Response(
            {"status": "error", "message": "Could not search places. Please try again later."},
            status=status.HTTP_503_SERVICE_UNAVAILABLE,
        )

    if language == "en" and places:
        places = translate_places(places)

    return Response(
        {"status": "success", "data": [place.to_dict() for place in places]},
        status=status.HTTP_200_OK,
    )


@api_view(["GET"])
def reverse_geocode_api(request):
    latitude = _coordinate(request.query_params.get("lat"), -90, 90)
    longitude = _coordinate(request.query_params.get("lng"), -180, 180)
    language = request.query_params.get("language") or DEFAULT_LANGUAGE

    if latitude is None or longitude is None:
        return Response(
            {"status": "error", "message": "lat and lng must be valid coordinates"},
            status=status.HTTP_400_BAD_REQUEST,
        )

    if error := _invalid_language(language):
        return error

    try:
        place = reverse_geocode(latitude, longitude)
    except PlaceSearchError:
        return Response(
            {"status": "error", "message": "Could not find this place. Please try again later."},
            status=status.HTTP_503_SERVICE_UNAVAILABLE,
        )

    if place is None:
        return Response(
            {"status": "error", "message": "No address found for this point."},
            status=status.HTTP_404_NOT_FOUND,
        )

    if language == "en":
        place = translate_places([place])[0]

    return Response({"status": "success", "data": place.to_dict()}, status=status.HTTP_200_OK)
