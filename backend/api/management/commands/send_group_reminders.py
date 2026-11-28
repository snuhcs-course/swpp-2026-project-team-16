from django.core.management.base import BaseCommand

from api.services.group_reminders import send_due_group_reminders


class Command(BaseCommand):
    help = "Notify members of groups that meet within the next 10 minutes."

    def handle(self, *args, **options):
        reminded = send_due_group_reminders()
        self.stdout.write(self.style.SUCCESS(f"Sent reminders for {reminded} group(s)."))
