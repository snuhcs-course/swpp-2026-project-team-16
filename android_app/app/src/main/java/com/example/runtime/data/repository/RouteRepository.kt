package com.example.runtime.data.repository

import com.example.runtime.data.remote.ApiClient
import com.example.runtime.data.remote.ApiService
import com.example.runtime.data.remote.GenerateRouteRequest
import com.example.runtime.data.remote.GeneratedRouteData
import com.example.runtime.data.remote.GeoJsonPoint
import com.example.runtime.data.remote.SaveRouteRequest
import com.example.runtime.data.remote.SavedRoute

class RouteRepository(private val api: ApiService = ApiClient.api) {

    suspend fun generate(startingPoint: GeoJsonPoint, distanceMeters: Int): GeneratedRouteData =
        api.generateRoute(GenerateRouteRequest(startingPoint, distanceMeters)).requireData()

    suspend fun saveTemporaryRoute(temporaryRouteId: Int) {
        api.saveRoute(SaveRouteRequest(temporaryRouteId = temporaryRouteId))
    }

    suspend fun savedRoutes(): List<SavedRoute> = api.savedRoutes()

    suspend fun savedRoute(routeId: Int): SavedRoute = api.savedRoute(routeId).requireData()

    suspend fun deleteSavedRoute(routeId: Int) {
        api.deleteSavedRoute(routeId)
    }
}
