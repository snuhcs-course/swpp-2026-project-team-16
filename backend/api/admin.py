from django.contrib import admin
from .models import ActivityLog, TelemetryEvent


@admin.register(TelemetryEvent)
class TelemetryEventAdmin(admin.ModelAdmin):
    list_display = ("received_at", "event_name", "outcome", "duration_ms", "failure_category", "app_version")
    list_filter = ("event_name", "outcome", "failure_category", "received_at")
    date_hierarchy = "received_at"
    ordering = ("-received_at",)
    readonly_fields = ("event_name", "outcome", "duration_ms", "failure_category", "app_version", "received_at")
    search_fields = ("event_name", "outcome", "failure_category", "app_version")

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        return False


@admin.register(ActivityLog)
class ActivityLogAdmin(admin.ModelAdmin):
    list_display = ("created_at", "user", "action")
    list_filter = ("action", "created_at")
    date_hierarchy = "created_at"
    search_fields = ("user__username", "action")
    readonly_fields = ("user", "action", "details", "created_at")

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False
