from datetime import timedelta

from django.db import IntegrityError, transaction
from django.db.models import Count, Q
from rest_framework import status
from rest_framework.decorators import api_view
from rest_framework.response import Response

from api.models import (
    REPORT_REASONS,
    REPORTS_TO_HIDE,
    Comment,
    CommentReport,
    Post,
    PostLike,
    PostReport,
    PostScrap,
)


MAX_TITLE_LENGTH = 100
MAX_BODY_LENGTH = 2000
MAX_COMMENT_LENGTH = 500
MAX_QUERY_LENGTH = 50
FILTERS = ("all", "scraps", "mine", "following")
EDITED_AFTER = timedelta(seconds=1)


def _error(message, code):
    return Response({"status": "error", "message": message}, status=code)


def _auth_required():
    return _error("Authentication required.", status.HTTP_401_UNAUTHORIZED)


def _post_not_found():
    return _error("Post not found.", status.HTTP_404_NOT_FOUND)


def _hidden_comment_ids():
    return Comment.objects.annotate(report_count=Count("reports")).filter(report_count__gte=REPORTS_TO_HIDE).values("id")


def _visible_posts():
    return (
        Post.objects.select_related("author")
        .annotate(
            report_count=Count("reports", distinct=True),
            like_count=Count("likes", distinct=True),
            scrap_count=Count("scraps", distinct=True),
            comment_count=Count("comments", filter=~Q(comments__id__in=_hidden_comment_ids()), distinct=True),
        )
        .filter(report_count__lt=REPORTS_TO_HIDE)
    )


def _get_post(post_id):
    return _visible_posts().filter(id=post_id).first()


def _post_data(post, user):
    is_mine = user.is_authenticated and post.author_id == user.id
    data = {
        "id": post.id,
        "title": post.title,
        "body": post.body,
        "author": None if post.anonymous else post.author.username,
        "anonymous": post.anonymous,
        "is_mine": is_mine,
        "created_at": post.created_at,
        "edited": post.updated_at - post.created_at > EDITED_AFTER,
        "like_count": post.like_count,
        "scrap_count": post.scrap_count,
        "comment_count": post.comment_count,
        "liked": False,
        "scrapped": False,
        "reported": False,
    }
    if user.is_authenticated:
        data["liked"] = post.likes.filter(user=user).exists()
        data["scrapped"] = post.scraps.filter(user=user).exists()
        data["reported"] = post.reports.filter(user=user).exists()
    return data


def _comments_data(post, user):
    comments = list(
        post.comments.select_related("author").annotate(report_count=Count("reports"))
    )
    numbers = {}
    for comment in comments:
        if comment.anonymous and comment.author_id != post.author_id and comment.author_id not in numbers:
            numbers[comment.author_id] = len(numbers) + 1
    reported = set()
    if user.is_authenticated:
        reported = set(CommentReport.objects.filter(user=user, comment__post=post).values_list("comment_id", flat=True))
    return [
        {
            "id": comment.id,
            "body": comment.body,
            "author": None if comment.anonymous else comment.author.username,
            "anonymous": comment.anonymous,
            "anonymous_number": numbers.get(comment.author_id) if comment.anonymous else None,
            "is_post_author": comment.author_id == post.author_id,
            "is_mine": user.is_authenticated and comment.author_id == user.id,
            "reported": comment.id in reported,
            "created_at": comment.created_at,
        }
        for comment in comments
        if comment.report_count < REPORTS_TO_HIDE
    ]


def _parse_post(data, partial=False):
    fields = {}
    for name, limit in (("title", MAX_TITLE_LENGTH), ("body", MAX_BODY_LENGTH)):
        if partial and name not in data:
            continue
        value = data.get(name)
        if not isinstance(value, str) or not value.strip() or len(value.strip()) > limit:
            return None, f"{name.capitalize()} must be 1 to {limit} characters."
        fields[name] = value.strip()
    if not partial:
        anonymous = data.get("anonymous", True)
        if not isinstance(anonymous, bool):
            return None, "anonymous must be true or false."
        fields["anonymous"] = anonymous
    return fields, None


def _parse_reason(data):
    reason = data.get("reason")
    if reason not in REPORT_REASONS:
        return None, _error(f"reason must be one of: {', '.join(REPORT_REASONS)}", status.HTTP_400_BAD_REQUEST)
    return reason, None


@api_view(["GET", "POST"])
def posts_list(request):
    if request.method == "GET":
        post_filter = request.query_params.get("filter") or "all"
        if post_filter not in FILTERS:
            return _error(f"filter must be one of: {', '.join(FILTERS)}", status.HTTP_400_BAD_REQUEST)
        query = (request.query_params.get("q") or "").strip()[:MAX_QUERY_LENGTH]

        posts = _visible_posts()
        if post_filter != "all" and not request.user.is_authenticated:
            return _auth_required()
        if post_filter == "scraps":
            posts = posts.filter(scraps__user=request.user).order_by("-scraps__created_at", "-id")
        else:
            if post_filter == "mine":
                posts = posts.filter(author=request.user)
            elif post_filter == "following":
                posts = posts.filter(author__followers__follower=request.user, anonymous=False)
            posts = posts.order_by("-created_at", "-id")
        if query:
            posts = posts.filter(Q(title__icontains=query) | Q(body__icontains=query))

        data = [_post_data(post, request.user) for post in posts]
        return Response({"status": "success", "data": data}, status=status.HTTP_200_OK)

    if not request.user.is_authenticated:
        return _auth_required()
    fields, error = _parse_post(request.data)
    if error:
        return _error(error, status.HTTP_400_BAD_REQUEST)
    post = Post.objects.create(author=request.user, **fields)
    return Response(
        {"status": "success", "data": _post_data(_get_post(post.id), request.user)},
        status=status.HTTP_201_CREATED,
    )


@api_view(["GET", "PATCH", "DELETE"])
def posts_detail(request, post_id):
    post = _get_post(post_id)
    if post is None:
        return _post_not_found()

    if request.method == "GET":
        data = _post_data(post, request.user)
        data["comments"] = _comments_data(post, request.user)
        return Response({"status": "success", "data": data}, status=status.HTTP_200_OK)

    if not request.user.is_authenticated:
        return _auth_required()
    if post.author_id != request.user.id:
        return _error("You can only change your own post.", status.HTTP_403_FORBIDDEN)

    if request.method == "DELETE":
        post.delete()
        return Response({"status": "success"}, status=status.HTTP_200_OK)

    fields, error = _parse_post(request.data, partial=True)
    if error:
        return _error(error, status.HTTP_400_BAD_REQUEST)
    if fields:
        for name, value in fields.items():
            setattr(post, name, value)
        post.save(update_fields=[*fields, "updated_at"])
    return Response(
        {"status": "success", "data": _post_data(_get_post(post.id), request.user)},
        status=status.HTTP_200_OK,
    )


def _toggle(request, post_id, model, own_error=None):
    if not request.user.is_authenticated:
        return _auth_required()
    post = _get_post(post_id)
    if post is None:
        return _post_not_found()
    if own_error and post.author_id == request.user.id:
        return _error(own_error, status.HTTP_400_BAD_REQUEST)

    if request.method == "POST":
        try:
            with transaction.atomic():
                model.objects.create(post=post, user=request.user)
        except IntegrityError:
            pass
        code = status.HTTP_201_CREATED
    else:
        model.objects.filter(post=post, user=request.user).delete()
        code = status.HTTP_200_OK
    return Response({"status": "success", "data": _post_data(_get_post(post.id), request.user)}, status=code)


@api_view(["POST", "DELETE"])
def post_like(request, post_id):
    return _toggle(request, post_id, PostLike, own_error="You can't like your own post.")


@api_view(["POST", "DELETE"])
def post_scrap(request, post_id):
    return _toggle(request, post_id, PostScrap)


@api_view(["POST"])
def post_report(request, post_id):
    if not request.user.is_authenticated:
        return _auth_required()
    post = _get_post(post_id)
    if post is None:
        return _post_not_found()
    if post.author_id == request.user.id:
        return _error("You can't report your own post.", status.HTTP_400_BAD_REQUEST)
    reason, error = _parse_reason(request.data)
    if error:
        return error
    try:
        with transaction.atomic():
            PostReport.objects.create(post=post, user=request.user, reason=reason)
    except IntegrityError:
        return _error("You already reported this post.", status.HTTP_400_BAD_REQUEST)
    hidden = post.reports.count() >= REPORTS_TO_HIDE
    return Response({"status": "success", "data": {"hidden": hidden}}, status=status.HTTP_201_CREATED)


@api_view(["GET", "POST"])
def post_comments(request, post_id):
    post = _get_post(post_id)
    if post is None:
        return _post_not_found()

    if request.method == "POST":
        if not request.user.is_authenticated:
            return _auth_required()
        body = request.data.get("body")
        anonymous = request.data.get("anonymous", True)
        if not isinstance(body, str) or not body.strip() or len(body.strip()) > MAX_COMMENT_LENGTH:
            return _error(f"Comment must be 1 to {MAX_COMMENT_LENGTH} characters.", status.HTTP_400_BAD_REQUEST)
        if not isinstance(anonymous, bool):
            return _error("anonymous must be true or false.", status.HTTP_400_BAD_REQUEST)
        Comment.objects.create(post=post, author=request.user, body=body.strip(), anonymous=anonymous)
        code = status.HTTP_201_CREATED
    else:
        code = status.HTTP_200_OK

    return Response({"status": "success", "data": _comments_data(post, request.user)}, status=code)


def _get_comment(comment_id):
    comment = (
        Comment.objects.select_related("post")
        .annotate(report_count=Count("reports"))
        .filter(id=comment_id, report_count__lt=REPORTS_TO_HIDE)
        .first()
    )
    if comment is None or _get_post(comment.post_id) is None:
        return None
    return comment


@api_view(["DELETE"])
def comment_detail(request, comment_id):
    if not request.user.is_authenticated:
        return _auth_required()
    comment = _get_comment(comment_id)
    if comment is None:
        return _error("Comment not found.", status.HTTP_404_NOT_FOUND)
    if comment.author_id != request.user.id:
        return _error("You can only delete your own comment.", status.HTTP_403_FORBIDDEN)
    post = comment.post
    comment.delete()
    return Response({"status": "success", "data": _comments_data(post, request.user)}, status=status.HTTP_200_OK)


@api_view(["POST"])
def comment_report(request, comment_id):
    if not request.user.is_authenticated:
        return _auth_required()
    comment = _get_comment(comment_id)
    if comment is None:
        return _error("Comment not found.", status.HTTP_404_NOT_FOUND)
    if comment.author_id == request.user.id:
        return _error("You can't report your own comment.", status.HTTP_400_BAD_REQUEST)
    reason, error = _parse_reason(request.data)
    if error:
        return error
    try:
        with transaction.atomic():
            CommentReport.objects.create(comment=comment, user=request.user, reason=reason)
    except IntegrityError:
        return _error("You already reported this comment.", status.HTTP_400_BAD_REQUEST)
    hidden = comment.reports.count() >= REPORTS_TO_HIDE
    return Response({"status": "success", "data": {"hidden": hidden}}, status=status.HTTP_201_CREATED)
