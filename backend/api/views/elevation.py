import math

from rest_framework import status
from rest_framework.decorators import api_view
from rest_framework.response import Response

from api.route_algorithm.elevation import ElevationError, fetch_elevations


MAX_COORDINATES_PER_REQUEST = 1000


@api_view(["POST"])
def elevations_api(request):
    coordinates = request.data.get("coordinates")
    if not isinstance(coordinates, list) or len(coordinates) > MAX_COORDINATES_PER_REQUEST:
        return Response(
            {"status": "error", "message": "coordinates must be a list of at most 1000 points"},
            status=status.HTTP_400_BAD_REQUEST,
        )

    points = []
    for index, coordinate in enumerate(coordinates):
        if not isinstance(coordinate, dict):
            return Response(
                {"status": "error", "message": f"coordinates[{index}] must contain latitude and longitude"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        latitude = coordinate.get("latitude")
        longitude = coordinate.get("longitude")
        if (
            isinstance(latitude, bool)
            or not isinstance(latitude, (int, float))
            or not math.isfinite(latitude)
            or not -90 <= latitude <= 90
        ):
            return Response(
                {"status": "error", "message": f"coordinates[{index}].latitude must be between -90 and 90"},
                status=status.HTTP_400_BAD_REQUEST,
            )
        if (
            isinstance(longitude, bool)
            or not isinstance(longitude, (int, float))
            or not math.isfinite(longitude)
            or not -180 <= longitude <= 180
        ):
            return Response(
                {"status": "error", "message": f"coordinates[{index}].longitude must be between -180 and 180"},
                status=status.HTTP_400_BAD_REQUEST,
            )
        points.append((longitude, latitude))

    try:
        elevations = fetch_elevations(points)
    except ElevationError:
        return Response(
            {"status": "error", "message": "Elevation data is temporarily unavailable."},
            status=status.HTTP_503_SERVICE_UNAVAILABLE,
        )

    return Response({"status": "success", "data": {"elevations": elevations}})
