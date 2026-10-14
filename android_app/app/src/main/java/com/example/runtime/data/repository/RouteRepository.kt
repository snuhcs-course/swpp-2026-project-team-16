package com.example.runtime.data.repository

import com.example.runtime.data.remote.ApiClient
import com.example.runtime.data.remote.ApiService
import com.example.runtime.data.remote.GenerateRouteRequest
import com.example.runtime.data.remote.GeneratedRouteData
import com.example.runtime.data.remote.Place
import com.example.runtime.data.remote.SaveRouteRequest
import com.example.runtime.data.remote.SavedRoute

class RouteRepository(private val api: ApiService = ApiClient.api) {

    suspend fun generate(
        startingPoint: Place,
        endPoint: Place?,
        distanceMeters: Int,
        language: String,
    ): GeneratedRouteData =
        api.generateRoute(
            GenerateRouteRequest(
                startingPoint = startingPoint.point,
                distance = distanceMeters,
                language = language,
                endPoint = endPoint?.point,
                startingPointName = startingPoint.name,
                endPointName = endPoint?.name,
            )
        ).requireData()

    suspend fun searchPlaces(query: String, language: String): List<Place> =
        api.searchPlaces(query, language).data.orEmpty()

    suspend fun saveTemporaryRoute(temporaryRouteId: Int) {
        api.saveRoute(SaveRouteRequest(temporaryRouteId = temporaryRouteId))
    }

    suspend fun savedRoutes(): List<SavedRoute> = api.savedRoutes()

    suspend fun savedRoute(routeId: Int): SavedRoute = api.savedRoute(routeId).requireData()

    suspend fun deleteSavedRoute(routeId: Int) {
        api.deleteSavedRoute(routeId)
    }
}
