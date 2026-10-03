from django.contrib.auth import get_user_model

from django.db import transaction
from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework import status

from api.models import UserSavedRoute, Route, TemporaryRoute


User = get_user_model()


@api_view(['GET', 'POST'])
def saved_routes_list(request):
    if not request.user.is_authenticated:
        return Response({'status': 'error', 'message': 'Authentication required.'}, status=status.HTTP_401_UNAUTHORIZED)

    
    if request.method == 'GET':
        user_routes = UserSavedRoute.objects.filter(user=request.user)
        user_routes = user_routes.select_related('route', 'route__created_by')

        routes_data = [
            {
                "id": user_route.route.id,
                "distance": user_route.route.distance,
                "briefing": user_route.route.briefing,
                "route": user_route.route.route,
                "created_by": user_route.route.created_by.username if user_route.route.created_by else None,
                "saved_at": user_route.saved_at
            }
            for user_route in user_routes
        ]
        return Response(routes_data, status=status.HTTP_200_OK)

    elif request.method == 'POST':

        route_id = request.data.get('route_id')
        temporary_route_id = request.data.get('temporary_route_id')

        if not route_id and not temporary_route_id:
            return Response({'status': 'error', 'message': 'route_id or temporary_route_id is required.'}, status=status.HTTP_400_BAD_REQUEST)
        
        if temporary_route_id:
            try:
                temporary_route = TemporaryRoute.objects.get(id=temporary_route_id, user=request.user)
            except TemporaryRoute.DoesNotExist:
                return Response({'status': 'error', 'message': 'Temporary route not found.'}, status=status.HTTP_404_NOT_FOUND)
                    
            with transaction.atomic():
                route = temporary_route.create_route(created_by=request.user)
                temporary_route.delete()
                UserSavedRoute.objects.get_or_create(
                    user=request.user,
                    route=route
                )

            return Response({'status': 'success', 'message': 'Temporary route saved successfully.', 'route_id': route.id}, status=status.HTTP_201_CREATED)

        elif route_id:
            try:
                route = Route.objects.get(id=route_id)
            except Route.DoesNotExist:
                return Response({'status': 'error', 'message': 'Route not found.'}, status=status.HTTP_404_NOT_FOUND)

            UserSavedRoute.objects.get_or_create(user=request.user, route=route)
            return Response({'status': 'success', 'message': 'Route saved successfully.'}, status=status.HTTP_201_CREATED)



@api_view(['GET', 'DELETE'])
def saved_routes_detail(request, route_id):
    if not request.user.is_authenticated:
        return Response({'status': 'error', 'message': 'Authentication required.'}, status=status.HTTP_401_UNAUTHORIZED)
    
    try:
        user_saved_route = UserSavedRoute.objects.get(user=request.user, route__id=route_id)
    except UserSavedRoute.DoesNotExist:
        return Response({'status': 'error', 'message': 'Route not found.'}, status=status.HTTP_404_NOT_FOUND)

    if request.method == 'GET':

        route = user_saved_route.route
        route_data = {
                "id": route.id,
                "distance": route.distance,
                "briefing": route.briefing,
                "route": route.route,
                "created_by": route.created_by.username if route.created_by else None,
                "saved_at": user_saved_route.saved_at
                }
        return Response({'status': 'success', 'data': route_data}, status=status.HTTP_200_OK)


    elif request.method == 'DELETE':
        user_saved_route.delete()
        return Response({'status': 'success', 'message': 'Route deleted successfully.'}, status=status.HTTP_200_OK)
