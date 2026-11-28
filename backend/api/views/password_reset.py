import logging
import secrets
from datetime import timedelta

from django.contrib.auth import get_user_model
from django.core.exceptions import ValidationError
from django.core.mail import send_mail
from django.core.validators import validate_email
from django.db import transaction
from django.db.models import F
from django.utils import timezone
from django.utils.crypto import constant_time_compare, salted_hmac
from rest_framework import status
from rest_framework.authtoken.models import Token
from rest_framework.decorators import api_view, throttle_classes
from rest_framework.response import Response
from rest_framework.throttling import SimpleRateThrottle

from api.models import PasswordResetCode
from api.services.language import request_language
from api.views.auth import MIN_PASSWORD_LENGTH


logger = logging.getLogger(__name__)

User = get_user_model()

CODE_DIGITS = 6
CODE_LIFETIME = timedelta(minutes=10)
MAX_ATTEMPTS = 5
HASH_SALT = "api.password_reset"

RESET_SENT = "If an account uses this email, we sent a reset code."
INVALID_CODE = "The code is invalid or has expired."
PASSWORD_RESET = "Your password has been reset."

EMAIL_TEXT = {
    "ko": (
        "RunTime 비밀번호 재설정 코드",
        "RunTime 비밀번호 재설정 코드는 {code}예요.\n"
        "10분 안에 앱에 입력해 주세요. 요청하지 않았다면 이 메일은 무시해도 돼요.",
    ),
    "en": (
        "Your RunTime password reset code",
        "Your RunTime password reset code is {code}.\n"
        "Enter it in the app within 10 minutes. If you didn't ask for this, you can ignore this email.",
    ),
}


class ClientIpThrottle(SimpleRateThrottle):
    def get_cache_key(self, request, view):
        return self.cache_format % {"scope": self.scope, "ident": self.get_ident(request)}


class PasswordResetThrottle(ClientIpThrottle):
    scope = "password_reset"


class PasswordResetConfirmThrottle(ClientIpThrottle):
    scope = "password_reset_confirm"


def _error(message):
    return Response({"status": "error", "message": message}, status=status.HTTP_400_BAD_REQUEST)


def _hash_code(user, code):
    return salted_hmac(HASH_SALT, f"{user.pk}:{code}").hexdigest()


def _clean_email(value):
    if not isinstance(value, str):
        return None
    value = value.strip()
    try:
        validate_email(value)
    except ValidationError:
        return None
    return value


def _reset_user(email):
    users = [user for user in User.objects.filter(email__iexact=email) if user.has_usable_password()]
    return users[0] if len(users) == 1 else None


@api_view(["POST"])
@throttle_classes([PasswordResetThrottle])
def request_password_reset(request):
    email = _clean_email(request.data.get("email"))
    if email is None:
        return _error("Enter a valid email address")

    user = _reset_user(email)
    if user is not None:
        code = f"{secrets.randbelow(10 ** CODE_DIGITS):0{CODE_DIGITS}d}"
        with transaction.atomic():
            PasswordResetCode.objects.filter(user=user).delete()
            PasswordResetCode.objects.create(
                user=user,
                code_hash=_hash_code(user, code),
                expires_at=timezone.now() + CODE_LIFETIME,
            )
        subject, body = EMAIL_TEXT[request_language(request)]
        try:
            send_mail(subject, body.format(code=code), None, [user.email])
        except Exception:
            logger.exception("Could not send the password reset email")

    return Response({"status": "success", "message": RESET_SENT}, status=status.HTTP_200_OK)


@api_view(["POST"])
@throttle_classes([PasswordResetConfirmThrottle])
def confirm_password_reset(request):
    email = request.data.get("email")
    code = request.data.get("code")
    new_password = request.data.get("new_password")

    if not all(isinstance(value, str) and value.strip() for value in (email, code, new_password)):
        return _error("All fields are required")
    if len(new_password) < MIN_PASSWORD_LENGTH:
        return _error(f"Password must be at least {MIN_PASSWORD_LENGTH} characters")

    email = _clean_email(email)
    user = _reset_user(email) if email else None
    if user is None:
        return _error(INVALID_CODE)

    with transaction.atomic():
        reset = (
            PasswordResetCode.objects.select_for_update()
            .filter(user=user, used_at__isnull=True, expires_at__gt=timezone.now(), attempts__lt=MAX_ATTEMPTS)
            .order_by("-created_at", "-id")
            .first()
        )
        if reset is None:
            return _error(INVALID_CODE)
        if not constant_time_compare(reset.code_hash, _hash_code(user, code.strip())):
            PasswordResetCode.objects.filter(pk=reset.pk).update(attempts=F("attempts") + 1)
            return _error(INVALID_CODE)

        user.set_password(new_password)
        user.save(update_fields=["password"])
        reset.used_at = timezone.now()
        reset.save(update_fields=["used_at"])
        Token.objects.filter(user=user).delete()

    return Response({"status": "success", "message": PASSWORD_RESET}, status=status.HTTP_200_OK)
