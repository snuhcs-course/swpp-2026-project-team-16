import math
from datetime import timedelta

from django.contrib.auth import get_user_model
from django.contrib.auth.hashers import check_password, make_password
from django.db import transaction
from django.db.models import Count, Q
from django.utils import timezone
from django.utils.dateparse import parse_datetime
from rest_framework import status
from rest_framework.decorators import api_view
from rest_framework.response import Response

from api.models import Follow, GroupInvite, GroupMember, GroupMessage, Route, RunningGroup
from api.route_algorithm.briefing import DEFAULT_LANGUAGE
from api.services.language import request_language
from api.services.notifications import notify


User = get_user_model()

MAX_TITLE_LENGTH = 60
MAX_DESCRIPTION_LENGTH = 300
MAX_MESSAGE_LENGTH = 500
MIN_CAPACITY = 2
MAX_CAPACITY = 20
MIN_PASSWORD_LENGTH = 4
MAX_PASSWORD_LENGTH = 20
CLOSING_SOON_WINDOW = timedelta(hours=24)
DEFAULT_RADIUS_KM = 5.0
MAX_RADIUS_KM = 50.0
MESSAGE_PAGE_SIZE = 100
RANKING_MIN_COMPLETION = 0.8
FILTERS = ("all", "closing_soon", "mine", "following")
EARTH_RADIUS_M = 6_371_000


def _error(message, code):
    return Response({"status": "error", "message": message}, status=code)


def _auth_required():
    return _error("Authentication required.", status.HTTP_401_UNAUTHORIZED)


def _not_found():
    return _error("Group not found.", status.HTTP_404_NOT_FOUND)


def _distance_m(lat1, lon1, lat2, lon2):
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    d_phi = phi2 - phi1
    d_lambda = math.radians(lon2 - lon1)
    a = math.sin(d_phi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(d_lambda / 2) ** 2
    return 2 * EARTH_RADIUS_M * math.asin(math.sqrt(a))


def _groups():
    return RunningGroup.objects.select_related("host", "route").annotate(member_count=Count("members"))


def _member_count(group):
    count = getattr(group, "member_count", None)
    return group.members.count() if count is None else count


def _status(group, now):
    if now >= group.join_deadline:
        return "closed"
    if _member_count(group) >= group.capacity:
        return "full"
    return "open"


def _is_closing_soon(group, now):
    return _status(group, now) == "open" and (
        group.join_deadline - now <= CLOSING_SOON_WINDOW or group.capacity - _member_count(group) == 1
    )


def _ranking(group, user):
    best = {}
    min_distance = group.route.distance * RANKING_MIN_COMPLETION
    for run in group.runs.select_related("user").filter(distance_m__gte=min_distance, duration_s__gt=0):
        pace = run.duration_s / (run.distance_m / 1000)
        if run.user_id not in best or pace < best[run.user_id][0]:
            best[run.user_id] = (pace, run)
    ordered = sorted(best.values(), key=lambda item: (item[0], item[1].finished_at))
    return [
        {
            "rank": index + 1,
            "username": run.user.username,
            "pace_s_per_km": round(pace),
            "distance_m": run.distance_m,
            "duration_s": run.duration_s,
            "finished_at": run.finished_at,
            "is_me": run.user_id == user.id,
        }
        for index, (pace, run) in enumerate(ordered)
    ]


def _group_data(group, user, now, origin=None, detail=False, language=DEFAULT_LANGUAGE):
    member_count = _member_count(group)
    is_member = user.is_authenticated and group.members.filter(user=user).exists()
    data = {
        "id": group.id,
        "title": group.title,
        "description": group.description,
        "host": group.host.username,
        "route_id": group.route_id,
        "route_distance": group.route.distance,
        "start_name": group.route.localized_names(language)[0] or group.start_name,
        "start_latitude": group.start_latitude,
        "start_longitude": group.start_longitude,
        "capacity": group.capacity,
        "member_count": member_count,
        "meet_at": group.meet_at,
        "join_deadline": group.join_deadline,
        "status": _status(group, now),
        "closing_soon": _is_closing_soon(group, now),
        "is_member": is_member,
        "is_host": user.is_authenticated and group.host_id == user.id,
        "is_private": group.is_private,
        "my_invite_id": (
            group.invites.filter(invitee=user).values_list("id", flat=True).first() if user.is_authenticated and not is_member else None
        ),
        "distance_from_me_m": (
            round(_distance_m(origin[0], origin[1], group.start_latitude, group.start_longitude)) if origin else None
        ),
    }
    if detail:
        data["route"] = group.route.route
        hidden = group.is_private and not is_member
        data["members"] = [] if hidden else [member.user.username for member in group.members.select_related("user")]
        data["ranking"] = [] if hidden else _ranking(group, user)
    return data


def _parse_float(value, low, high):
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if math.isfinite(number) and low <= number <= high else None


def _parse_origin(params):
    if params.get("lat") is None and params.get("lon") is None:
        return None, None, None
    lat = _parse_float(params.get("lat"), -90, 90)
    lon = _parse_float(params.get("lon"), -180, 180)
    if lat is None or lon is None:
        return None, None, "lat and lon must be valid coordinates."
    radius_km = params.get("radius_km")
    radius_km = DEFAULT_RADIUS_KM if radius_km is None else _parse_float(radius_km, 0.1, MAX_RADIUS_KM)
    if radius_km is None:
        return None, None, f"radius_km must be between 0.1 and {MAX_RADIUS_KM:g}."
    return (lat, lon), radius_km, None


def _parse_time(value):
    if not isinstance(value, str):
        return None
    parsed = parse_datetime(value)
    if parsed is None:
        return None
    return parsed if timezone.is_aware(parsed) else timezone.make_aware(parsed)


def _parse_group(data, now):
    title = data.get("title")
    description = data.get("description") or ""
    capacity = data.get("capacity")
    if not isinstance(title, str) or not title.strip() or len(title.strip()) > MAX_TITLE_LENGTH:
        return None, f"Title must be 1 to {MAX_TITLE_LENGTH} characters."
    if not isinstance(description, str) or len(description.strip()) > MAX_DESCRIPTION_LENGTH:
        return None, f"Description must be {MAX_DESCRIPTION_LENGTH} characters or fewer."
    if isinstance(capacity, bool) or not isinstance(capacity, int) or not MIN_CAPACITY <= capacity <= MAX_CAPACITY:
        return None, f"Capacity must be between {MIN_CAPACITY} and {MAX_CAPACITY}."
    meet_at = _parse_time(data.get("meet_at"))
    join_deadline = _parse_time(data.get("join_deadline"))
    if meet_at is None or join_deadline is None:
        return None, "meet_at and join_deadline must be ISO 8601 date-times."
    if join_deadline <= now:
        return None, "The join deadline must be in the future."
    if join_deadline > meet_at:
        return None, "The join deadline must be before the run starts."
    is_private = data.get("is_private", False)
    if not isinstance(is_private, bool):
        return None, "is_private must be true or false."
    password = data.get("password") or ""
    if is_private and (
        not isinstance(password, str)
        or not password.strip()
        or not MIN_PASSWORD_LENGTH <= len(password) <= MAX_PASSWORD_LENGTH
    ):
        return None, f"The password must be {MIN_PASSWORD_LENGTH} to {MAX_PASSWORD_LENGTH} characters."
    return {
        "is_private": is_private,
        "password": make_password(password) if is_private else "",
        "title": title.strip(),
        "description": description.strip(),
        "capacity": capacity,
        "meet_at": meet_at,
        "join_deadline": join_deadline,
    }, None


def _route_start(route):
    for line in route.route:
        coordinates = line.get("coordinates") or []
        if coordinates:
            return coordinates[0]
    return None


@api_view(["GET", "POST"])
def groups_list(request):
    now = timezone.now()
    if request.method == "GET":
        group_filter = request.query_params.get("filter") or "all"
        if group_filter not in FILTERS:
            return _error(f"filter must be one of: {', '.join(FILTERS)}", status.HTTP_400_BAD_REQUEST)
        origin, radius_km, error = _parse_origin(request.query_params)
        if error:
            return _error(error, status.HTTP_400_BAD_REQUEST)

        groups = _groups()
        if group_filter == "mine":
            if not request.user.is_authenticated:
                return _auth_required()
            groups = groups.filter(members__user=request.user).order_by("-meet_at", "-id")
        elif group_filter == "following":
            if not request.user.is_authenticated:
                return _auth_required()
            groups = groups.filter(host__followers__follower=request.user, meet_at__gt=now).order_by("meet_at", "id")
        else:
            groups = groups.filter(meet_at__gt=now).order_by("meet_at", "id")
        groups = list(groups)

        if group_filter == "closing_soon":
            groups = sorted((g for g in groups if _is_closing_soon(g, now)), key=lambda g: g.join_deadline)
        if origin:
            distances = {
                g.id: _distance_m(origin[0], origin[1], g.start_latitude, g.start_longitude) for g in groups
            }
            groups = sorted((g for g in groups if distances[g.id] <= radius_km * 1000), key=lambda g: distances[g.id])

        data = [_group_data(group, request.user, now, origin, language=request_language(request)) for group in groups]
        return Response({"status": "success", "data": data}, status=status.HTTP_200_OK)

    if not request.user.is_authenticated:
        return _auth_required()
    try:
        route = Route.objects.filter(
            Q(shared__isnull=False) | Q(usersavedroute__user=request.user) | Q(created_by=request.user),
            id=request.data.get("route_id"),
        ).distinct().get()
    except (Route.DoesNotExist, ValueError, TypeError):
        return _error("Route not found.", status.HTTP_404_NOT_FOUND)
    start = _route_start(route)
    if start is None:
        return _error("Route not found.", status.HTTP_404_NOT_FOUND)

    fields, error = _parse_group(request.data, now)
    if error:
        return _error(error, status.HTTP_400_BAD_REQUEST)

    with transaction.atomic():
        group = RunningGroup.objects.create(
            host=request.user,
            route=route,
            start_name=route.start_name,
            start_longitude=start[0],
            start_latitude=start[1],
            **fields,
        )
        GroupMember.objects.create(group=group, user=request.user)
    return Response(
        {"status": "success", "data": _group_data(_groups().get(id=group.id), request.user, now, detail=True, language=request_language(request))},
        status=status.HTTP_201_CREATED,
    )


@api_view(["GET", "DELETE"])
def groups_detail(request, group_id):
    try:
        group = _groups().get(id=group_id)
    except RunningGroup.DoesNotExist:
        return _not_found()

    if request.method == "GET":
        return Response(
            {"status": "success", "data": _group_data(group, request.user, timezone.now(), detail=True, language=request_language(request))},
            status=status.HTTP_200_OK,
        )

    if not request.user.is_authenticated:
        return _auth_required()
    if group.host_id != request.user.id:
        return _error("Only the host can delete this group.", status.HTTP_403_FORBIDDEN)
    group.delete()
    return Response({"status": "success"}, status=status.HTTP_200_OK)


@api_view(["POST", "DELETE"])
def group_membership(request, group_id):
    if not request.user.is_authenticated:
        return _auth_required()
    now = timezone.now()

    with transaction.atomic():
        try:
            group = RunningGroup.objects.select_for_update().select_related("host", "route").get(id=group_id)
        except RunningGroup.DoesNotExist:
            return _not_found()
        membership = group.members.filter(user=request.user).first()

        if request.method == "POST":
            if membership:
                return _error("You already joined this group.", status.HTTP_400_BAD_REQUEST)
            group_status = _status(group, now)
            if group_status == "closed":
                return _error("This group is no longer taking members.", status.HTTP_400_BAD_REQUEST)
            if group_status == "full":
                return _error("This group is full.", status.HTTP_400_BAD_REQUEST)
            invite = group.invites.filter(invitee=request.user).first()
            if group.is_private and invite is None:
                password = request.data.get("password")
                if not isinstance(password, str) or not check_password(password, group.password):
                    return _error("The password is incorrect.", status.HTTP_403_FORBIDDEN)
            GroupMember.objects.create(group=group, user=request.user)
            group.invites.filter(invitee=request.user).delete()
            code = status.HTTP_201_CREATED
        else:
            if membership is None:
                return _error("You are not in this group.", status.HTTP_400_BAD_REQUEST)
            if group.host_id == request.user.id:
                return _error("The host can't leave. Delete the group instead.", status.HTTP_400_BAD_REQUEST)
            if now >= group.meet_at:
                return _error("You can't leave after the run has started.", status.HTTP_400_BAD_REQUEST)
            membership.delete()
            code = status.HTTP_200_OK

    group = _groups().get(id=group.id)
    if request.method == "POST":
        notify(group.host, "group_join", actor=request.user, group=group)
    return Response({"status": "success", "data": _group_data(group, request.user, now, detail=True, language=request_language(request))}, status=code)


def _message_data(message, user):
    return {
        "id": message.id,
        "author": message.user.username,
        "text": message.text,
        "created_at": message.created_at,
        "is_mine": message.user_id == user.id,
    }


@api_view(["GET", "POST"])
def group_messages(request, group_id):
    if not request.user.is_authenticated:
        return _auth_required()
    try:
        group = RunningGroup.objects.get(id=group_id)
    except RunningGroup.DoesNotExist:
        return _not_found()
    if not group.members.filter(user=request.user).exists():
        return _error("Join this group to see the chat.", status.HTTP_403_FORBIDDEN)

    if request.method == "GET":
        messages = group.messages.select_related("user")
        after = request.query_params.get("after")
        if after is not None:
            try:
                messages = messages.filter(id__gt=int(after))[:MESSAGE_PAGE_SIZE]
            except ValueError:
                return _error("after must be a message id.", status.HTTP_400_BAD_REQUEST)
        else:
            messages = reversed(list(messages.order_by("-id")[:MESSAGE_PAGE_SIZE]))
        data = [_message_data(message, request.user) for message in messages]
        return Response({"status": "success", "data": data}, status=status.HTTP_200_OK)

    text = request.data.get("text")
    if not isinstance(text, str) or not text.strip() or len(text.strip()) > MAX_MESSAGE_LENGTH:
        return _error(f"Message must be 1 to {MAX_MESSAGE_LENGTH} characters.", status.HTTP_400_BAD_REQUEST)
    message = GroupMessage.objects.create(group=group, user=request.user, text=text.strip())
    for member in group.members.exclude(user=request.user).select_related("user"):
        notify(member.user, "group_message", actor=request.user, group=group, text=message.text)
    return Response(
        {"status": "success", "data": _message_data(message, request.user)},
        status=status.HTTP_201_CREATED,
    )


def mutual_follow_ids(user):
    following = Follow.objects.filter(follower=user).values("followee_id")
    return set(Follow.objects.filter(followee=user, follower_id__in=following).values_list("follower_id", flat=True))


def _invite_data(invite, user, now, language=DEFAULT_LANGUAGE):
    return {
        "id": invite.id,
        "inviter": invite.inviter.username,
        "created_at": invite.created_at,
        "group": _group_data(_groups().get(id=invite.group_id), user, now, language=language),
    }


@api_view(["GET", "POST"])
def group_invites(request, group_id):
    if not request.user.is_authenticated:
        return _auth_required()
    try:
        group = _groups().get(id=group_id)
    except RunningGroup.DoesNotExist:
        return _not_found()
    if not group.members.filter(user=request.user).exists():
        return _error("Join this group to invite friends.", status.HTTP_403_FORBIDDEN)

    mutuals = mutual_follow_ids(request.user)
    if request.method == "GET":
        member_ids = set(group.members.values_list("user_id", flat=True))
        invited_ids = set(group.invites.values_list("invitee_id", flat=True))
        users = User.objects.filter(id__in=mutuals).select_related("profile").order_by("username")
        data = [
            {
                "username": friend.username,
                "display_name": getattr(getattr(friend, "profile", None), "display_name", ""),
                "is_member": friend.id in member_ids,
                "is_invited": friend.id in invited_ids,
            }
            for friend in users
        ]
        return Response({"status": "success", "data": data}, status=status.HTTP_200_OK)

    invitee = User.objects.filter(username=request.data.get("username")).first()
    if invitee is None:
        return _error("User not found.", status.HTTP_404_NOT_FOUND)
    if invitee.id not in mutuals:
        return _error("You can only invite people who follow each other with you.", status.HTTP_403_FORBIDDEN)
    if group.members.filter(user=invitee).exists():
        return _error("They are already in this group.", status.HTTP_400_BAD_REQUEST)
    now = timezone.now()
    if _status(group, now) != "open":
        return _error("This group is no longer taking members.", status.HTTP_400_BAD_REQUEST)
    invite, created = GroupInvite.objects.get_or_create(group=group, invitee=invitee, defaults={"inviter": request.user})
    if created:
        notify(invitee, "group_invite", actor=request.user, group=group)
    return Response(
        {"status": "success", "data": _invite_data(invite, request.user, now, request_language(request))},
        status=status.HTTP_201_CREATED,
    )


@api_view(["GET"])
def my_invites(request):
    if not request.user.is_authenticated:
        return _auth_required()
    now = timezone.now()
    invites = GroupInvite.objects.select_related("inviter").filter(invitee=request.user, group__meet_at__gt=now)
    language = request_language(request)
    data = [_invite_data(invite, request.user, now, language) for invite in invites]
    return Response({"status": "success", "data": data}, status=status.HTTP_200_OK)


@api_view(["DELETE"])
def invite_detail(request, invite_id):
    if not request.user.is_authenticated:
        return _auth_required()
    deleted, _ = GroupInvite.objects.filter(id=invite_id, invitee=request.user).delete()
    if not deleted:
        return _error("Invite not found.", status.HTTP_404_NOT_FOUND)
    return Response({"status": "success"}, status=status.HTTP_200_OK)
