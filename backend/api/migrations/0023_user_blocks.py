import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0022_telemetry_health_and_live_coaching'),
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.AlterField(
            model_name='activitylog',
            name='action',
            field=models.CharField(choices=[('signed_up', 'Signed up'), ('logged_in', 'Logged in'), ('route_generated', 'Generated a route'), ('route_saved', 'Saved a route'), ('run_recorded', 'Finished a run'), ('run_rated', 'Rated a run'), ('route_shared', 'Shared a route'), ('route_reviewed', 'Reviewed a route'), ('post_created', 'Wrote a post'), ('comment_created', 'Wrote a comment'), ('group_created', 'Created a group'), ('group_joined', 'Joined a group'), ('group_left', 'Left a group'), ('group_message', 'Sent a group message'), ('followed', 'Followed a user'), ('unfollowed', 'Unfollowed a user'), ('blocked', 'Blocked a user'), ('unblocked', 'Unblocked a user')], db_index=True, max_length=32),
        ),
        migrations.CreateModel(
            name='Block',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('blocked', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='blocked_by', to=settings.AUTH_USER_MODEL)),
                ('blocker', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='blocks', to=settings.AUTH_USER_MODEL)),
            ],
            options={
                'ordering': ['-created_at', '-id'],
                'constraints': [models.UniqueConstraint(fields=('blocker', 'blocked'), name='unique_block'), models.CheckConstraint(condition=models.Q(('blocker', models.F('blocked')), _negated=True), name='no_self_block')],
            },
        ),
    ]
