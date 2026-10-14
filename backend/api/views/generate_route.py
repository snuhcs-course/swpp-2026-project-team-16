from concurrent.futures import ThreadPoolExecutor
from concurrent.futures import TimeoutError as FutureTimeoutError

import requests
from osmnx._errors import InsufficientResponseError, ResponseStatusCodeError
from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework import status

from api.models import TemporaryRoute
from api.route_algorithm.algorithm import create_route
from api.route_algorithm.briefing import DEFAULT_LANGUAGE, SUPPORTED_LANGUAGES
from api.route_algorithm.classes import GeoJSONPoint


ROUTE_TIMEOUT_SECONDS = 35
MAX_PLACE_NAME_LENGTH = 100
MAP_UNAVAILABLE_MESSAGE = "Could not load map data. Please try again later."

route_executor = ThreadPoolExecutor(max_workers=4)


def unavailable_response(message):
    return Response(
        {
            "status": "error",
            "message": message,
        },
        status=status.HTTP_503_SERVICE_UNAVAILABLE,
    )


def error_response(message):
    return Response(
        {
            "status": "error",
            "message": message,
        },
        status=status.HTTP_400_BAD_REQUEST,
    )


def parse_place_name(value, name):
    if value is None:
        return "", None

    if not isinstance(value, str):
        return None, f"{name} must be a string"

    value = value.strip()

    if len(value) > MAX_PLACE_NAME_LENGTH:
        return None, f"{name} must be {MAX_PLACE_NAME_LENGTH} characters or fewer"

    return value, None


def parse_point(value, name):
    if not isinstance(value, dict):
        return None, f"{name} must be a GeoJSON Point"

    if value.get("type") != "Point":
        return None, f"{name} must have type Point"

    coordinates = value.get("coordinates")

    if not isinstance(coordinates, list) or len(coordinates) != 2:
        return None, f"{name} coordinates must be [longitude, latitude]"

    longitude, latitude = coordinates

    if isinstance(longitude, bool) or not isinstance(longitude, (int, float)):
        return None, f"{name} longitude must be a number"

    if isinstance(latitude, bool) or not isinstance(latitude, (int, float)):
        return None, f"{name} latitude must be a number"

    if not -180 <= longitude <= 180:
        return None, f"{name} longitude must be between -180 and 180"

    if not -90 <= latitude <= 90:
        return None, f"{name} latitude must be between -90 and 90"

    return GeoJSONPoint(coordinates=(longitude, latitude)), None


@api_view(["POST"])
def generate_route_api(request):
    if not request.user.is_authenticated:
        user = None
    else:
        user = request.user


    starting_point, error = parse_point(request.data.get("starting_point"), "starting_point")

    if error:
        return error_response(error)

    end_point_data = request.data.get("end_point")
    end_point = None

    if end_point_data is not None:
        end_point, error = parse_point(end_point_data, "end_point")

        if error:
            return error_response(error)

    distance = request.data.get("distance")

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


    start_name, error = parse_place_name(request.data.get("starting_point_name"), "starting_point_name")

    if error:
        return error_response(error)

    end_name, error = parse_place_name(request.data.get("end_point_name"), "end_point_name")

    if error:
        return error_response(error)

    language = request.data.get("language", DEFAULT_LANGUAGE)

    if language not in SUPPORTED_LANGUAGES:
        return Response(
            {
                "status": "error",
                "message": f"language must be one of: {', '.join(SUPPORTED_LANGUAGES)}",
            },
            status=status.HTTP_400_BAD_REQUEST,
        )


    # Call route algorithm

    future = route_executor.submit(
        create_route,
        starting_point=starting_point,
        end_point=end_point,
        distance=distance,
        language=language,
    )

    try:
        route = future.result(timeout=ROUTE_TIMEOUT_SECONDS)
    except FutureTimeoutError:
        return unavailable_response("Route generation is taking too long. Please try again in a moment.")
    except (requests.RequestException, InsufficientResponseError, ResponseStatusCodeError):
        return unavailable_response(MAP_UNAVAILABLE_MESSAGE)
    except ValueError as e:
        return error_response(str(e))

    temporary_route = TemporaryRoute.objects.create(
        distance=route.distance,
        briefing=route.briefing,
        route=route.to_dict()['route'],
        user=user,
        start_name=start_name,
        end_name=end_name,
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