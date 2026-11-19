# Generated for consent-based, privacy-limited telemetry.
from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("api", "0019_distance_goals"),
    ]

    operations = [
        migrations.CreateModel(
            name="TelemetryEvent",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("event_name", models.CharField(choices=[("route_generation", "Route generation"), ("place_search", "Place search"), ("authentication", "Authentication"), ("pace_plan", "Pace plan"), ("run_session", "Run session"), ("run_report", "Run report"), ("app_crash", "App crash")], db_index=True, max_length=32)),
                ("outcome", models.CharField(choices=[("started", "Started"), ("succeeded", "Succeeded"), ("failed", "Failed"), ("completed", "Completed"), ("crashed", "Crashed")], db_index=True, max_length=16)),
                ("duration_ms", models.PositiveIntegerField(blank=True, null=True)),
                ("failure_category", models.CharField(blank=True, choices=[("network", "Network"), ("server", "Server"), ("validation", "Validation"), ("no_results", "No results"), ("permission", "Permission"), ("unknown", "Unknown")], default="", max_length=16)),
                ("app_version", models.CharField(max_length=32)),
                ("received_at", models.DateTimeField(auto_now_add=True, db_index=True)),
            ],
            options={
                "ordering": ["-received_at"],
                "indexes": [models.Index(fields=["event_name", "received_at"], name="telemetry_name_date_idx")],
            },
        ),
    ]
