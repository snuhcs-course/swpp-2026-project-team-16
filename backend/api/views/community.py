from django.db import IntegrityError, transaction
from datetime import timedelta

from django.db.models import Avg, Count, F, Q
from rest_framework import status
from rest_framework.decorators import api_view
from rest_framework.response import Response

from api.route_algorithm.briefing import DEFAULT_LANGUAGE
from api.services.language import request_language
from api.services.run_coach import validate_plan, validate_splits
from api.models import Route, RouteReview, RouteRun, RunningGroup, SharedRoute, TemporaryRoute, UserSavedRoute


MAX_TITLE_LENGTH = 60
MAX_DESCRIPTION_LENGTH = 300
MAX_COMMENT_LENGTH = 500
SORTS = ("latest", "rating", "reviews")
EDITED_AFTER = timedelta(seconds=1)


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


def _shared_route_data(shared_route, user, include_route=False, language=DEFAULT_LANGUAGE):
    route = shared_route.route
    start_name, end_name = route.localized_names(language)
    data = {
        "id": shared_route.id,
        "route_id": route.id,
        "title": shared_route.title,
        "description": shared_route.description,
        "shared_by": shared_route.shared_by.username,
        "shared_at": shared_route.shared_at,
        "distance": route.distance,
        "start_name": start_name,
        "end_name": end_name,
        "run_count": shared_route.run_count,
        "review_count": shared_route.review_count,
        "rating_average": (
            round(shared_route.rating_average, 1) if shared_route.rating_average is not None else None
        ),
        "is_mine": user.is_authenticated and shared_route.shared_by_id == user.id,
    }
    if include_route:
        counts = dict(shared_route.reviews.order_by().values_list("rating").annotate(count=Count("id")))
        my_review = shared_route.reviews.filter(user=user).first() if user.is_authenticated else None
        data["briefing"] = route.briefing
        data["route"] = route.route
        data["rating_counts"] = {str(rating): counts.get(rating, 0) for rating in range(1, 6)}
        data["can_review"] = _can_review(user, shared_route)
        data["my_review_id"] = my_review.id if my_review else None
    return data


def _shared_routes(sort="latest"):
    shared_routes = SharedRoute.objects.select_related("route", "shared_by").annotate(
        run_count=Count("route__runs", distinct=True),
        review_count=Count("reviews", distinct=True),
        rating_average=Avg("reviews__rating"),
    )
    if sort == "rating":
        return shared_routes.order_by(F("rating_average").desc(nulls_last=True), "-review_count", "-shared_at")
    if sort == "reviews":
        return shared_routes.order_by("-review_count", "-shared_at")
    return shared_routes.order_by("-shared_at", "-id")


def _can_review(user, shared_route):
    if not user.is_authenticated or shared_route.shared_by_id == user.id:
        return False
    return (
        RouteRun.objects.filter(user=user, route_id=shared_route.route_id).exists()
        or UserSavedRoute.objects.filter(user=user, route_id=shared_route.route_id).exists()
    )


def _review_data(review, user):
    return {
        "id": review.id,
        "rating": review.rating,
        "comment": review.comment,
        "author": review.user.username,
        "created_at": review.created_at,
        "edited": review.updated_at - review.created_at > EDITED_AFTER,
        "is_mine": user.is_authenticated and review.user_id == user.id,
    }


def _parse_review(data):
    rating = data.get("rating")
    comment = data.get("comment") or ""
    if isinstance(rating, bool) or not isinstance(rating, int) or not 1 <= rating <= 5:
        return None, None, _error("Rating must be a whole number from 1 to 5.", status.HTTP_400_BAD_REQUEST)
    if not isinstance(comment, str) or len(comment.strip()) > MAX_COMMENT_LENGTH:
        return None, None, _error(
            f"The comment must be {MAX_COMMENT_LENGTH} characters or fewer.",
            status.HTTP_400_BAD_REQUEST,
        )
    return rating, comment.strip(), None


def _run_data(run, saved_route_ids, language=DEFAULT_LANGUAGE):
    route = run.route
    start_name, end_name = route.localized_names(language)
    shared_route = getattr(route, "shared", None)
    return {
        "id": run.id,
        "route_id": route.id,
        "distance_m": run.distance_m,
        "duration_s": run.duration_s,
        "finished_at": run.finished_at,
        "route_distance": route.distance,
        "start_name": start_name,
        "end_name": end_name,
        "shared_route_id": shared_route.id if shared_route else None,
        "shared_title": shared_route.title if shared_route else "",
        "is_saved": route.id in saved_route_ids,
        "route": route.route,
    }


@api_view(["GET", "POST"])
def route_runs(request):
    if not request.user.is_authenticated:
        return _auth_required()

    if request.method == "GET":
        runs = RouteRun.objects.filter(user=request.user).select_related("route", "route__shared").order_by("-finished_at", "-id")
        saved_route_ids = set(UserSavedRoute.objects.filter(user=request.user).values_list("route_id", flat=True))
        data = [_run_data(run, saved_route_ids, request_language(request)) for run in runs]
        return Response({"status": "success", "data": data}, status=status.HTTP_200_OK)

    route_id = request.data.get("route_id")
    temporary_route_id = request.data.get("temporary_route_id")
    if bool(route_id) == bool(temporary_route_id):
        return _error("Provide either route_id or temporary_route_id.", status.HTTP_400_BAD_REQUEST)

    distance_m = _non_negative_int(request.data.get("distance_m"))
    duration_s = _non_negative_int(request.data.get("duration_s"))
    if distance_m is None or duration_s is None:
        return _error("distance_m and duration_s must be non-negative integers.", status.HTTP_400_BAD_REQUEST)

    plan, error = validate_plan(request.data.get("plan"))
    if error:
        return _error(error, status.HTTP_400_BAD_REQUEST)
    splits, error = validate_splits(request.data.get("splits"))
    if error:
        return _error(error, status.HTTP_400_BAD_REQUEST)
    off_route_count = _non_negative_int(request.data.get("off_route_count", 0))
    off_route_seconds = _non_negative_int(request.data.get("off_route_seconds", 0))
    if off_route_count is None or off_route_seconds is None:
        return _error("off_route_count and off_route_seconds must be non-negative integers.", status.HTTP_400_BAD_REQUEST)

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
                Q(shared__isnull=False)
                | Q(usersavedroute__user=request.user)
                | Q(created_by=request.user)
                | Q(groups__members__user=request.user)
                | Q(runs__user=request.user),
                id=route_id,
            ).distinct().get()
        except (Route.DoesNotExist, ValueError):
            return _error("Route not found.", status.HTTP_404_NOT_FOUND)

    group = None
    group_id = request.data.get("group_id")
    if group_id is not None:
        try:
            group = RunningGroup.objects.get(id=group_id, members__user=request.user)
        except (RunningGroup.DoesNotExist, ValueError, TypeError):
            return _error("Group not found.", status.HTTP_404_NOT_FOUND)
        if group.route_id != route.id:
            return _error("This run is not on the group's route.", status.HTTP_400_BAD_REQUEST)

    run = RouteRun.objects.create(
        user=request.user,
        route=route,
        group=group,
        distance_m=distance_m,
        duration_s=duration_s,
        plan=plan,
        splits=splits,
        off_route_count=off_route_count,
        off_route_seconds=off_route_seconds,
    )
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
        sort = request.query_params.get("sort") or "latest"
        if sort not in SORTS:
            return _error(f"sort must be one of: {', '.join(SORTS)}", status.HTTP_400_BAD_REQUEST)
        data = [_shared_route_data(shared_route, request.user, language=request_language(request)) for shared_route in _shared_routes(sort)]
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

    route_id = request.data.get("route_id")
    temporary_route_id = request.data.get("temporary_route_id")
    if bool(route_id) == bool(temporary_route_id):
        return _error("Provide either route_id or temporary_route_id.", status.HTTP_400_BAD_REQUEST)

    if temporary_route_id:
        try:
            temporary_route = TemporaryRoute.objects.get(
                Q(user=request.user) | Q(user__isnull=True),
                id=temporary_route_id,
            )
        except (TemporaryRoute.DoesNotExist, ValueError, TypeError):
            return _error("Temporary route not found.", status.HTTP_404_NOT_FOUND)
    else:
        try:
            route = Route.objects.get(id=route_id)
        except (Route.DoesNotExist, ValueError, TypeError):
            return _error("Route not found.", status.HTTP_404_NOT_FOUND)
        if not (
            route.created_by_id == request.user.id
            or RouteRun.objects.filter(user=request.user, route=route).exists()
            or UserSavedRoute.objects.filter(user=request.user, route=route).exists()
        ):
            return _error("Run or save this route before sharing it.", status.HTTP_403_FORBIDDEN)

    try:
        with transaction.atomic():
            if temporary_route_id:
                route = temporary_route.create_route(created_by=request.user)
                if temporary_route.saved_route_id is None:
                    temporary_route.saved_route = route
                    temporary_route.save(update_fields=["saved_route"])
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
        {"status": "success", "data": _shared_route_data(shared_route, request.user, include_route=True, language=request_language(request))},
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
            {"status": "success", "data": _shared_route_data(shared_route, request.user, include_route=True, language=request_language(request))},
            status=status.HTTP_200_OK,
        )

    if not request.user.is_authenticated:
        return _auth_required()
    if shared_route.shared_by_id != request.user.id:
        return _error("Only the person who shared this route can remove it.", status.HTTP_403_FORBIDDEN)

    shared_route.delete()
    return Response({"status": "success", "message": "Shared route removed."}, status=status.HTTP_200_OK)


@api_view(["GET", "POST"])
def shared_route_reviews(request, shared_route_id):
    try:
        shared_route = SharedRoute.objects.get(id=shared_route_id)
    except SharedRoute.DoesNotExist:
        return _error("Shared route not found.", status.HTTP_404_NOT_FOUND)

    if request.method == "GET":
        reviews = shared_route.reviews.select_related("user")
        return Response(
            {"status": "success", "data": [_review_data(review, request.user) for review in reviews]},
            status=status.HTTP_200_OK,
        )

    if not request.user.is_authenticated:
        return _auth_required()
    if shared_route.shared_by_id == request.user.id:
        return _error("You can't review a route you shared.", status.HTTP_403_FORBIDDEN)
    if not _can_review(request.user, shared_route):
        return _error("Run or save this route before reviewing it.", status.HTTP_403_FORBIDDEN)

    rating, comment, error = _parse_review(request.data)
    if error:
        return error

    try:
        with transaction.atomic():
            review = RouteReview.objects.create(
                shared_route=shared_route, user=request.user, rating=rating, comment=comment
            )
    except IntegrityError:
        return _error("You already reviewed this route.", status.HTTP_409_CONFLICT)

    return Response({"status": "success", "data": _review_data(review, request.user)}, status=status.HTTP_201_CREATED)


@api_view(["PATCH", "DELETE"])
def review_detail(request, review_id):
    if not request.user.is_authenticated:
        return _auth_required()
    try:
        review = RouteReview.objects.select_related("user").get(id=review_id, user=request.user)
    except RouteReview.DoesNotExist:
        return _error("Review not found.", status.HTTP_404_NOT_FOUND)

    if request.method == "DELETE":
        review.delete()
        return Response({"status": "success", "message": "Review deleted."}, status=status.HTTP_200_OK)

    rating, comment, error = _parse_review(request.data)
    if error:
        return error
    review.rating = rating
    review.comment = comment
    review.save(update_fields=["rating", "comment", "updated_at"])
    return Response({"status": "success", "data": _review_data(review, request.user)}, status=status.HTTP_200_OK)
