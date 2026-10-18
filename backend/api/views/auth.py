from django.contrib.auth import get_user_model
from django.conf import settings

from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework import status

from rest_framework.authtoken.models import Token

from google.auth.exceptions import GoogleAuthError, TransportError
from google.auth.transport import requests as google_requests
from google.oauth2 import id_token


User = get_user_model()


@api_view(["POST"])
def register_user(request):

    username = request.data.get("username")
    email = request.data.get("email")
    password = request.data.get("password")


    if not username or not email or not password:
        return Response(
            {'status': 'error', "message": "All fields are required"},    
            status=status.HTTP_400_BAD_REQUEST
        )


    if User.objects.filter(username=username).exists():
        return Response(
            {'status': 'error', "message": "Username already exists"},
            status=status.HTTP_400_BAD_REQUEST
        )


    user = User.objects.create_user(
        username=username,
        email=email,
        password=password
    )


    token = Token.objects.create(
        user=user
    )


    return Response(
        {'status': 'success',
        'data': {
                "username": user.username,
                "email": user.email,
                "token": token.key,
            },
        },
        status=status.HTTP_201_CREATED
    )




@api_view(["POST"])
def login_user(request):

    username = request.data.get("username")
    password = request.data.get("password")


    if not username or not password:
        return Response(
            {'status': 'error',"message": "Username and password are required"},
            status=status.HTTP_400_BAD_REQUEST
        )


    user = User.objects.filter(username=username).first()


    if user is None or not user.check_password(password):
        return Response(
            {'status': 'error', 'message': 'Invalid username or password'},
            status=status.HTTP_401_UNAUTHORIZED
        )


    token, created = Token.objects.get_or_create(user=user)


    return Response(
        {   'status': 'success',
            'data': { 
                "username": user.username,
                "email": user.email,
                "token": token.key,
            },
        },
        status=status.HTTP_200_OK
    )



@api_view(["POST"])
def google_login(request):
    credential = request.data.get("credential")

    if not credential:
        return Response(
            {"status": "error", "message": "Google credential is required"},
            status=status.HTTP_400_BAD_REQUEST,
        )

    try:
        google_user = id_token.verify_oauth2_token(
            credential,
            google_requests.Request(),
            settings.GOOGLE_CLIENT_ID,
        )

    except TransportError:
        return Response(
            {
                "status": "error",
                "message": "Google authentication is temporarily unavailable.",
            },
            status=status.HTTP_503_SERVICE_UNAVAILABLE,
        )
    except (ValueError, GoogleAuthError):
        return Response(
            {"status": "error", "message": "Invalid Google credential."},
            status=status.HTTP_401_UNAUTHORIZED,
        )

    email = google_user.get("email")

    if not email or google_user.get("email_verified") is not True:
        return Response(
            {"status": "error", "message": "A verified Google email is required"},
            status=status.HTTP_401_UNAUTHORIZED,
        )

    matches = User.objects.filter(email__iexact=email)

    if matches.count() > 1:
        return Response(
            {"status": "error", "message": "Multiple accounts use this email"},
            status=status.HTTP_409_CONFLICT,
        )

    user = matches.first()

    if user is None:
        return Response(
            {"status": "error", "message": "No account found. Register first."},
            status=status.HTTP_404_NOT_FOUND,
        )

    token, _ = Token.objects.get_or_create(user=user)

    return Response({
        "status": "success",
        "data": {
            "username": user.username,
            "email": user.email,
            "token": token.key,
        },
    })



@api_view(["POST"])
def logout_user(request):
    if not request.user.is_authenticated:
        return Response(
            {"status": "error", "message": "Authentication credentials were not provided."},
            status=status.HTTP_401_UNAUTHORIZED
        )

    Token.objects.filter(user=request.user).delete()

    return Response(
        {"status": "success", "message": "Logged out successfully."},
        status=status.HTTP_200_OK
    )




@api_view(["GET"])
def my_profile(request):
    user = request.user

    if not user.is_authenticated:
        return Response(
            {'status': 'error', 'message': 'Authentication credentials were not provided.'},
            status=status.HTTP_401_UNAUTHORIZED
        )

    return Response(
        {   'status': 'success',
            'data': {   
                "username": user.username,
                "email": user.email,
            },
        },
        status=status.HTTP_200_OK
    )