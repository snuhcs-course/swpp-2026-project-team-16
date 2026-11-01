from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("api", "0004_usersavedroute_name"),
    ]

    operations = [
        migrations.CreateModel(
            name="ElevationCache",
            fields=[
                ("coordinate_key", models.CharField(max_length=32, primary_key=True, serialize=False)),
                ("elevation_m", models.FloatField()),
                ("cached_at", models.DateTimeField(auto_now_add=True)),
            ],
        ),
    ]
