from django.db.models import Q

from api.models import Block


def blocked_ids(user):
    if user is None or not user.is_authenticated:
        return set()
    return set(Block.objects.filter(blocker=user).values_list("blocked_id", flat=True))


def has_blocked(blocker, blocked):
    if blocker is None or blocked is None:
        return False
    return Block.objects.filter(blocker_id=blocker.id, blocked_id=blocked.id).exists()


def blocked_between(user, other):
    return Block.objects.filter(
        Q(blocker_id=user.id, blocked_id=other.id) | Q(blocker_id=other.id, blocked_id=user.id)
    ).exists()
