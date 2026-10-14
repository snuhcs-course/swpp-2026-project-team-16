package com.example.runtime.data.repository

import android.util.Log
import com.example.runtime.data.remote.ApiClient
import com.example.runtime.data.remote.ApiService
import com.example.runtime.data.remote.ElevationPoint
import com.example.runtime.data.remote.ElevationRequest
import com.example.runtime.data.remote.OpenElevationClient
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

    // Open-Elevation API 연동, 고도 받아오기
    suspend fun getElevations(coordinates: List<Pair<Double, Double>>): List<Double> {
        return try {
            val points = coordinates.map { (lat, lng) ->
                ElevationPoint(latitude = lat, longitude = lng)
            }
            val request = ElevationRequest(locations = points)
            val response = OpenElevationClient.api.getElevation(request)

            Log.d("ElevationTest", "고도 API 응답 성공: ${response.results}")
            response.results.map { it.elevation }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ElevationTest", "고도 API 에러: ${e.message}")
            emptyList()
        }
    }

    // 경사도(Slope %) 계산
    fun calculateSlopes(elevations: List<Double>, distancesMeters: List<Double>): List<Double> {
        val slopes = mutableListOf<Double>()
        for (i in 0 until elevations.size - 1) {
            val elevationChange = elevations[i + 1] - elevations[i]
            val distance = distancesMeters.getOrElse(i) { 1.0 }
            val slope = if (distance > 0) (elevationChange / distance) * 100.0 else 0.0
            slopes.add(slope)
        }
        return slopes
    }
}