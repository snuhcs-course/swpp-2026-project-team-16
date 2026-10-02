from django.contrib.auth import get_user_model

from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework import status

from rest_framework.authtoken.models import Token


User = get_user_model()


@api_view(["POST"])
def register_user(request):

    username = request.data.get("username")
    email = request.data.get("email")
    password = request.data.get("password")


    if not username or not email or not password:
        return Response(
            {
                "message": "All fields are required"
            },
            status=status.HTTP_400_BAD_REQUEST
        )


    if User.objects.filter(username=username).exists():
        return Response(
            {
                "message": "Username already exists"
            },
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
        {
            "username": user.username,
            "email": user.email,
            "token": token.key,
        },
        status=status.HTTP_201_CREATED
    )

@api_view(["POST"])
def login_user(request):

    username = request.data.get("username")
    password = request.data.get("password")


    if not username or not password:
        return Response(
            {
                "message": "Username and password are required"
            },
            status=status.HTTP_400_BAD_REQUEST
        )


    user = User.objects.filter(username=username).first()


    if user is None or not user.check_password(password):
        return Response(
            {
                "message": "Invalid username or password"
            },
            status=status.HTTP_401_UNAUTHORIZED
        )


    token, created = Token.objects.get_or_create(user=user)


    return Response(
        {
            "username": user.username,
            "email": user.email,
            "token": token.key,
        },
        status=status.HTTP_200_OK
    )

@api_view(["GET"])
def my_profile(request):
    user = request.user

    if not user.is_authenticated:
        return Response(
            {
                "message": "Authentication credentials were not provided."
            },
            status=status.HTTP_401_UNAUTHORIZED
        )

    return Response(
        {
            "username": user.username,
            "email": user.email,
        },
        status=status.HTTP_200_OK
    )