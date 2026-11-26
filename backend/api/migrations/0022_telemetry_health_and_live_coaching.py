from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("api", "0021_activitylog"),
    ]

    operations = [
        migrations.AlterField(
            model_name="telemetryevent",
            name="event_name",
            field=models.CharField(
                choices=[
                    ("route_generation", "Route generation"),
                    ("place_search", "Place search"),
                    ("authentication", "Authentication"),
                    ("pace_plan", "Pace plan"),
                    ("run_session", "Run session"),
                    ("run_report", "Run report"),
                    ("live_run_coaching", "Live run coaching"),
                    ("app_session", "App session"),
                    ("app_anr", "App ANR"),
                    ("app_crash", "App crash"),
                ],
                db_index=True,
                max_length=32,
            ),
        ),
        migrations.AlterField(
            model_name="telemetryevent",
            name="outcome",
            field=models.CharField(
                choices=[
                    ("started", "Started"),
                    ("succeeded", "Succeeded"),
                    ("failed", "Failed"),
                    ("completed", "Completed"),
                    ("anr", "ANR detected"),
                    ("crashed", "Crashed"),
                ],
                db_index=True,
                max_length=16,
            ),
        ),
    ]
