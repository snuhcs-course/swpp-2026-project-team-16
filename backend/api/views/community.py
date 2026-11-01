from django.db import IntegrityError, transaction
from django.db.models import Count, Q
from rest_framework import status
from rest_framework.decorators import api_view
from rest_framework.response import Response

from api.models import Route, RouteRun, SharedRoute, TemporaryRoute


MAX_TITLE_LENGTH = 60
MAX_DESCRIPTION_LENGTH = 300


def _error(message, code):
    return Response({"status": "error", "message": message}, status=code)


def _auth_required():
    return _error("Authentication required.", status.HTTP_401_UNAUTHORIZED)


def _non_negative_int(value):
    if isinstance(value, bool):
        return None
    try:
        number = int(value)
    except (TypeError, ValueError):
        return None
    return number if number >= 0 else None


def _shared_route_data(shared_route, user, include_route=False):
    route = shared_route.route
    data = {
        "id": shared_route.id,
        "route_id": route.id,
        "title": shared_route.title,
        "description": shared_route.description,
        "shared_by": shared_route.shared_by.username,
        "shared_at": shared_route.shared_at,
        "distance": route.distance,
        "start_name": route.start_name,
        "end_name": route.end_name,
        "run_count": shared_route.run_count,
        "is_mine": user.is_authenticated and shared_route.shared_by_id == user.id,
    }
    if include_route:
        data["briefing"] = route.briefing
        data["route"] = route.route
    return data


def _shared_routes():
    return (
        SharedRoute.objects.select_related("route", "shared_by")
        .annotate(run_count=Count("route__runs"))
        .order_by("-shared_at", "-id")
    )


@api_view(["POST"])
def route_runs(request):
    if not request.user.is_authenticated:
        return _auth_required()

    route_id = request.data.get("route_id")
    temporary_route_id = request.data.get("temporary_route_id")
    if bool(route_id) == bool(temporary_route_id):
        return _error("Provide either route_id or temporary_route_id.", status.HTTP_400_BAD_REQUEST)

    distance_m = _non_negative_int(request.data.get("distance_m"))
    duration_s = _non_negative_int(request.data.get("duration_s"))
    if distance_m is None or duration_s is None:
        return _error("distance_m and duration_s must be non-negative integers.", status.HTTP_400_BAD_REQUEST)

    if temporary_route_id:
        try:
            temporary_route = TemporaryRoute.objects.get(
                Q(user=request.user) | Q(user__isnull=True),
                id=temporary_route_id,
            )
        except (TemporaryRoute.DoesNotExist, ValueError):
            return _error("Temporary route not found.", status.HTTP_404_NOT_FOUND)

        with transaction.atomic():
            route = temporary_route.create_route(created_by=request.user)
            if temporary_route.saved_route_id is None:
                temporary_route.saved_route = route
                temporary_route.save(update_fields=["saved_route"])
    else:
        try:
            route = Route.objects.filter(
                Q(shared__isnull=False) | Q(usersavedroute__user=request.user) | Q(created_by=request.user),
                id=route_id,
            ).distinct().get()
        except (Route.DoesNotExist, ValueError):
            return _error("Route not found.", status.HTTP_404_NOT_FOUND)

    run = RouteRun.objects.create(user=request.user, route=route, distance_m=distance_m, duration_s=duration_s)
    shared_route = SharedRoute.objects.filter(route=route).first()

    return Response(
        {
            "status": "success",
            "data": {
                "id": run.id,
                "route_id": route.id,
                "shared_route_id": shared_route.id if shared_route else None,
            },
        },
        status=status.HTTP_201_CREATED,
    )


@api_view(["GET", "POST"])
def shared_routes_list(request):
    if request.method == "GET":
        data = [_shared_route_data(shared_route, request.user) for shared_route in _shared_routes()]
        return Response({"status": "success", "data": data}, status=status.HTTP_200_OK)

    if not request.user.is_authenticated:
        return _auth_required()

    title = request.data.get("title")
    description = request.data.get("description") or ""
    if not isinstance(title, str) or not title.strip():
        return _error("A title is required.", status.HTTP_400_BAD_REQUEST)
    if len(title.strip()) > MAX_TITLE_LENGTH:
        return _error(f"The title must be {MAX_TITLE_LENGTH} characters or fewer.", status.HTTP_400_BAD_REQUEST)
    if not isinstance(description, str) or len(description.strip()) > MAX_DESCRIPTION_LENGTH:
        return _error(
            f"The description must be {MAX_DESCRIPTION_LENGTH} characters or fewer.",
            status.HTTP_400_BAD_REQUEST,
        )

    try:
        route = Route.objects.get(id=request.data.get("route_id"))
    except (Route.DoesNotExist, ValueError, TypeError):
        return _error("Route not found.", status.HTTP_404_NOT_FOUND)

    if not RouteRun.objects.filter(user=request.user, route=route).exists():
        return _error("Run this route before sharing it.", status.HTTP_403_FORBIDDEN)

    try:
        with transaction.atomic():
            shared_route = SharedRoute.objects.create(
                route=route,
                shared_by=request.user,
                title=title.strip(),
                description=description.strip(),
            )
    except IntegrityError:
        return _error("This route is already shared.", status.HTTP_409_CONFLICT)

    shared_route = _shared_routes().get(id=shared_route.id)
    return Response(
        {"status": "success", "data": _shared_route_data(shared_route, request.user, include_route=True)},
        status=status.HTTP_201_CREATED,
    )


@api_view(["GET", "DELETE"])
def shared_routes_detail(request, shared_route_id):
    try:
        shared_route = _shared_routes().get(id=shared_route_id)
    except SharedRoute.DoesNotExist:
        return _error("Shared route not found.", status.HTTP_404_NOT_FOUND)

    if request.method == "GET":
        return Response(
            {"status": "success", "data": _shared_route_data(shared_route, request.user, include_route=True)},
            status=status.HTTP_200_OK,
        )

    if not request.user.is_authenticated:
        return _auth_required()
    if shared_route.shared_by_id != request.user.id:
        return _error("Only the person who shared this route can remove it.", status.HTTP_403_FORBIDDEN)

    shared_route.delete()
    return Response({"status": "success", "message": "Shared route removed."}, status=status.HTTP_200_OK)
