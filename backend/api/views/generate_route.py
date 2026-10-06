from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework import status

from api.models import TemporaryRoute
from api.route_algorithm.algorithm import create_route
from api.route_algorithm.briefing import DEFAULT_LANGUAGE, SUPPORTED_LANGUAGES
from api.route_algorithm.classes import GeoJSONPoint


@api_view(["POST"])
def generate_route_api(request):
    if not request.user.is_authenticated:
        user = None
    else:
        user = request.user


    starting_point = request.data.get("starting_point")
    distance = request.data.get("distance")

    if not isinstance(starting_point, dict):
        return Response(
            {
                "status": "error",
                "message": "starting_point must be a GeoJSON Point",
            },
            status=status.HTTP_400_BAD_REQUEST,
        )

    if starting_point.get("type") != "Point":
        return Response(
            {
                "status": "error",
                "message": "starting_point must have type Point",
            },
            status=status.HTTP_400_BAD_REQUEST,
        )

    coordinates = starting_point.get("coordinates")

    if not isinstance(coordinates, list) or len(coordinates) != 2:
        return Response(
            {
                "status": "error",
                "message": (
                    "starting_point coordinates must be "
                    "[longitude, latitude]"
                ),
            },
            status=status.HTTP_400_BAD_REQUEST,
        )

    longitude, latitude = coordinates

    if not isinstance(longitude, (int, float)):
        return Response(
            {
                "status": "error",
                "message": "longitude must be a number",
            },
            status=status.HTTP_400_BAD_REQUEST,
        )

    if not isinstance(latitude, (int, float)):
        return Response(
            {
                "status": "error",
                "message": "latitude must be a number",
            },
            status=status.HTTP_400_BAD_REQUEST,
        )

    if not -180 <= longitude <= 180:
        return Response(
            {
                "status": "error",
                "message": "longitude must be between -180 and 180",
            },
            status=status.HTTP_400_BAD_REQUEST,
        )

    if not -90 <= latitude <= 90:
        return Response(
            {
                "status": "error",
                "message": "latitude must be between -90 and 90",
            },
            status=status.HTTP_400_BAD_REQUEST,
        )

    if isinstance(distance, bool) or not isinstance(distance, int):
        return Response(
            {
                "status": "error",
                "message": "distance must be an integer",
            },
            status=status.HTTP_400_BAD_REQUEST,
        )

    if distance <= 100:
        return Response(
            {
                "status": "error",
                "message": "distance must be greater than 100",
            },
            status=status.HTTP_400_BAD_REQUEST,
        )


    language = request.data.get("language", DEFAULT_LANGUAGE)

    if language not in SUPPORTED_LANGUAGES:
        return Response(
            {
                "status": "error",
                "message": f"language must be one of: {', '.join(SUPPORTED_LANGUAGES)}",
            },
            status=status.HTTP_400_BAD_REQUEST,
        )


    start_point = GeoJSONPoint(
        coordinates=(
            longitude,
            latitude,
        )
    )


    # Call route algorithm

    route = create_route(
        starting_point=start_point,
        distance=distance,
        language=language,
    )

    temporary_route = TemporaryRoute.objects.create(
        distance=route.distance,
        briefing=route.briefing,
        route=route.to_dict()['route'],
        user=user,
    )


    return Response(
        {
            "status": "success",
            'data': {
                "temporary_route_id": temporary_route.id,
                'route': {**route.to_dict()},
            }
        }
    )