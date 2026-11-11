from django.db import models
from django.contrib.auth import get_user_model

User = get_user_model()

class TemporaryRoute(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE, null=True, blank=True, related_name='temporary_routes')
    distance = models.PositiveIntegerField(null=False, blank=False)
    briefing = models.CharField(max_length=255, null=False, blank=False)
    route = models.JSONField(null=False, blank=False) 
    start_name = models.CharField(max_length=100, blank=True, default="")
    end_name = models.CharField(max_length=100, blank=True, default="")
    start_name_en = models.CharField(max_length=100, blank=True, default="")
    end_name_en = models.CharField(max_length=100, blank=True, default="")
    saved_route = models.ForeignKey("Route", on_delete=models.SET_NULL, null=True, blank=True, related_name="+")

    def _validate_route(self):
        if not isinstance(self.route, list):
            raise TypeError(
                "route must be a list of GeoJSONLineString"
            )

        for index, line in enumerate(self.route):
            if not isinstance(line, dict) or line.get("type") != "LineString":
                raise TypeError(
                    f"route[{index}] must be a GeoJSONLineString dictionary"
                )

    def save(self, *args, **kwargs):
        self._validate_route()
        super().save(*args, **kwargs)

    
    def create_route(self, created_by: User) -> "Route":
        if self.saved_route is not None:
            return self.saved_route
        route =  Route.objects.create(
            created_by=created_by,
            distance=self.distance,
            briefing=self.briefing,
            route=self.route,
            start_name=self.start_name,
            end_name=self.end_name,
            start_name_en=self.start_name_en,
            end_name_en=self.end_name_en,
        )
        return route

    

class Route(models.Model):
    created_by = models.ForeignKey(User, on_delete=models.SET_NULL, null=True, blank=True)
    distance = models.PositiveIntegerField(null=False, blank=False)
    briefing = models.CharField(max_length=255, null=False, blank=False)
    route = models.JSONField(null=False, blank=False) 
    start_name = models.CharField(max_length=100, blank=True, default="")
    end_name = models.CharField(max_length=100, blank=True, default="")
    start_name_en = models.CharField(max_length=100, blank=True, default="")
    end_name_en = models.CharField(max_length=100, blank=True, default="")
    source = models.CharField(max_length=10, default="generated")

    def localized_names(self, language):
        if language == "en":
            return self.start_name_en or self.start_name, self.end_name_en or self.end_name
        return self.start_name, self.end_name

    def _validate_route(self):
        if not isinstance(self.route, list):
            raise TypeError(
                "route must be a list of GeoJSONLineString"
            )

        for index, line in enumerate(self.route):
            if not isinstance(line, dict) or line.get("type") != "LineString":
                raise TypeError(
                    f"route[{index}] must be a GeoJSONLineString dictionary"
                )



class UserSavedRoute(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE)
    route = models.ForeignKey(Route, on_delete=models.CASCADE)
    name = models.CharField(max_length=255, blank=True, default="")
    saved_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        constraints = [
            models.UniqueConstraint(fields=['user', 'route'], name='unique_user_route')
        ]


class ElevationCache(models.Model):
    """Elevation returned by Open-Meteo for a six-decimal coordinate."""

    coordinate_key = models.CharField(max_length=32, primary_key=True)
    elevation_m = models.FloatField()
    cached_at = models.DateTimeField(auto_now_add=True)


class RouteRun(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="runs")
    route = models.ForeignKey(Route, on_delete=models.CASCADE, related_name="runs")
    distance_m = models.PositiveIntegerField()
    duration_s = models.PositiveIntegerField()
    group = models.ForeignKey("RunningGroup", on_delete=models.SET_NULL, null=True, blank=True, related_name="runs")
    finished_at = models.DateTimeField(auto_now_add=True)
    plan = models.JSONField(null=True, blank=True)
    splits = models.JSONField(default=list, blank=True)
    off_route_count = models.PositiveIntegerField(default=0)
    off_route_seconds = models.PositiveIntegerField(default=0)
    report = models.JSONField(null=True, blank=True)
    rating = models.PositiveSmallIntegerField(null=True, blank=True)


class SharedRoute(models.Model):
    route = models.OneToOneField(Route, on_delete=models.CASCADE, related_name="shared")
    shared_by = models.ForeignKey(User, on_delete=models.CASCADE, related_name="shared_routes")
    title = models.CharField(max_length=60)
    description = models.CharField(max_length=300, blank=True, default="")
    shared_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["-shared_at", "-id"]


class RouteReview(models.Model):
    shared_route = models.ForeignKey(SharedRoute, on_delete=models.CASCADE, related_name="reviews")
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="route_reviews")
    rating = models.PositiveSmallIntegerField()
    comment = models.CharField(max_length=500, blank=True, default="")
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ["-created_at", "-id"]
        constraints = [
            models.UniqueConstraint(fields=["shared_route", "user"], name="unique_review_per_user"),
            models.CheckConstraint(condition=models.Q(rating__gte=1, rating__lte=5), name="rating_between_1_and_5"),
        ]


class RunningGroup(models.Model):
    host = models.ForeignKey(User, on_delete=models.CASCADE, related_name="hosted_groups")
    route = models.ForeignKey(Route, on_delete=models.CASCADE, related_name="groups")
    title = models.CharField(max_length=60)
    description = models.CharField(max_length=300, blank=True, default="")
    capacity = models.PositiveSmallIntegerField()
    meet_at = models.DateTimeField()
    join_deadline = models.DateTimeField()
    start_name = models.CharField(max_length=100, blank=True, default="")
    start_latitude = models.FloatField()
    start_longitude = models.FloatField()
    is_private = models.BooleanField(default=False)
    password = models.CharField(max_length=128, blank=True, default="")
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["meet_at", "id"]
        constraints = [
            models.CheckConstraint(condition=models.Q(capacity__gte=2, capacity__lte=20), name="group_capacity_between_2_and_20"),
            models.CheckConstraint(condition=models.Q(join_deadline__lte=models.F("meet_at")), name="group_deadline_before_meet"),
        ]


class GroupMember(models.Model):
    group = models.ForeignKey(RunningGroup, on_delete=models.CASCADE, related_name="members")
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="group_memberships")
    joined_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["joined_at", "id"]
        constraints = [
            models.UniqueConstraint(fields=["group", "user"], name="unique_group_member"),
        ]


class GroupMessage(models.Model):
    group = models.ForeignKey(RunningGroup, on_delete=models.CASCADE, related_name="messages")
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="group_messages")
    text = models.CharField(max_length=500)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["id"]


REPORT_REASONS = ["spam", "abuse", "inappropriate", "other"]
REPORTS_TO_HIDE = 5


class Post(models.Model):
    author = models.ForeignKey(User, on_delete=models.CASCADE, related_name="posts")
    title = models.CharField(max_length=100)
    body = models.CharField(max_length=2000)
    anonymous = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ["-created_at", "-id"]


class PostLike(models.Model):
    post = models.ForeignKey(Post, on_delete=models.CASCADE, related_name="likes")
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="post_likes")

    class Meta:
        constraints = [models.UniqueConstraint(fields=["post", "user"], name="unique_post_like")]


class PostScrap(models.Model):
    post = models.ForeignKey(Post, on_delete=models.CASCADE, related_name="scraps")
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="post_scraps")
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        constraints = [models.UniqueConstraint(fields=["post", "user"], name="unique_post_scrap")]


class PostReport(models.Model):
    post = models.ForeignKey(Post, on_delete=models.CASCADE, related_name="reports")
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="post_reports")
    reason = models.CharField(max_length=20)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        constraints = [models.UniqueConstraint(fields=["post", "user"], name="unique_post_report")]


class Comment(models.Model):
    post = models.ForeignKey(Post, on_delete=models.CASCADE, related_name="comments")
    author = models.ForeignKey(User, on_delete=models.CASCADE, related_name="comments")
    body = models.CharField(max_length=500)
    anonymous = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["created_at", "id"]


class CommentReport(models.Model):
    comment = models.ForeignKey(Comment, on_delete=models.CASCADE, related_name="reports")
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="comment_reports")
    reason = models.CharField(max_length=20)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        constraints = [models.UniqueConstraint(fields=["comment", "user"], name="unique_comment_report")]


class Profile(models.Model):
    user = models.OneToOneField(User, on_delete=models.CASCADE, related_name="profile")
    display_name = models.CharField(max_length=30, blank=True, default="")
    status_message = models.CharField(max_length=100, blank=True, default="")
    avatar = models.BinaryField(null=True, blank=True)
    avatar_type = models.CharField(max_length=20, blank=True, default="")
    avatar_updated_at = models.DateTimeField(null=True, blank=True)
    running_style = models.JSONField(default=dict, blank=True)
    notification_settings = models.JSONField(default=dict, blank=True)
    weekly_goal_m = models.PositiveIntegerField(default=0)
    monthly_goal_m = models.PositiveIntegerField(default=0)


class Follow(models.Model):
    follower = models.ForeignKey(User, on_delete=models.CASCADE, related_name="following")
    followee = models.ForeignKey(User, on_delete=models.CASCADE, related_name="followers")
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        constraints = [
            models.UniqueConstraint(fields=["follower", "followee"], name="unique_follow"),
            models.CheckConstraint(condition=~models.Q(follower=models.F("followee")), name="no_self_follow"),
        ]


class GroupInvite(models.Model):
    group = models.ForeignKey(RunningGroup, on_delete=models.CASCADE, related_name="invites")
    inviter = models.ForeignKey(User, on_delete=models.CASCADE, related_name="sent_group_invites")
    invitee = models.ForeignKey(User, on_delete=models.CASCADE, related_name="group_invites")
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        constraints = [models.UniqueConstraint(fields=["group", "invitee"], name="unique_group_invite")]
        ordering = ["-created_at", "-id"]



class Notification(models.Model):
    recipient = models.ForeignKey(User, on_delete=models.CASCADE, related_name="notifications")
    actor = models.ForeignKey(User, on_delete=models.SET_NULL, null=True, blank=True, related_name="+")
    kind = models.CharField(max_length=30)
    post = models.ForeignKey(Post, on_delete=models.CASCADE, null=True, blank=True, related_name="+")
    group = models.ForeignKey(RunningGroup, on_delete=models.CASCADE, null=True, blank=True, related_name="+")
    shared_route = models.ForeignKey(SharedRoute, on_delete=models.CASCADE, null=True, blank=True, related_name="+")
    text = models.CharField(max_length=200, blank=True, default="")
    read = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["-created_at", "-id"]


class DeviceToken(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name="device_tokens")
    token = models.CharField(max_length=512, unique=True)
    language = models.CharField(max_length=5, default="ko")
    updated_at = models.DateTimeField(auto_now=True)
