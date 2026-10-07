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
    saved_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        constraints = [
            models.UniqueConstraint(fields=['user', 'route'], name='unique_user_route')
        ]


