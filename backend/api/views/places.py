from rest_framework import status
from rest_framework.decorators import api_view
from rest_framework.response import Response

from api.route_algorithm.briefing import DEFAULT_LANGUAGE, SUPPORTED_LANGUAGES
from api.services.place_translation import translate_places
from api.services.tmap import PlaceSearchError, search_places


MAX_QUERY_LENGTH = 50


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

    if language not in SUPPORTED_LANGUAGES:
        return Response(
            {"status": "error", "message": f"language must be one of: {', '.join(SUPPORTED_LANGUAGES)}"},
            status=status.HTTP_400_BAD_REQUEST,
        )

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
