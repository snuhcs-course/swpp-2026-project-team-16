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
        )
        return route

    

class Route(models.Model):
    created_by = models.ForeignKey(User, on_delete=models.SET_NULL, null=True, blank=True)
    distance = models.PositiveIntegerField(null=False, blank=False)
    briefing = models.CharField(max_length=255, null=False, blank=False)
    route = models.JSONField(null=False, blank=False) 
    start_name = models.CharField(max_length=100, blank=True, default="")
    end_name = models.CharField(max_length=100, blank=True, default="")
    
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
