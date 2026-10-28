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
import com.example.runtime.data.remote.RenameRouteRequest
import com.example.runtime.data.remote.SavedRoute
import kotlinx.coroutines.CancellationException
import com.example.runtime.data.remote.OpenElevationApiService

class RouteRepository(
    private val api: ApiService = ApiClient.api,
    private val elevationApi: OpenElevationApiService = OpenElevationClient.api,
) {
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

    suspend fun reversePlace(latitude: Double, longitude: Double, language: String): Place? =
        api.reversePlace(latitude, longitude, language).data

    suspend fun saveTemporaryRoute(temporaryRouteId: Int) {
        api.saveRoute(SaveRouteRequest(temporaryRouteId = temporaryRouteId))
    }

    suspend fun savedRoutes(): List<SavedRoute> = api.savedRoutes()

    suspend fun savedRoute(routeId: Int): SavedRoute = api.savedRoute(routeId).requireData()

    suspend fun renameSavedRoute(routeId: Int, name: String): String =
        api.renameSavedRoute(routeId, RenameRouteRequest(name)).requireData().name

    suspend fun deleteSavedRoute(routeId: Int) {
        api.deleteSavedRoute(routeId)
    }

    // Open-Elevation API 연동, 고도 받아오기
    suspend fun getElevations(
        coordinates: List<Pair<Double, Double>>,
    ): Result<List<Double>> {
        if (coordinates.isEmpty()) {
            return Result.success(emptyList())
        }

        return try {
            val points = coordinates.map { (lat, lng) ->
                ElevationPoint(latitude = lat, longitude = lng)
            }
            val request = ElevationRequest(locations = points)
            val response = elevationApi.getElevation(request)

            require(response.results.size == points.size) {
                "Elevation response size did not match the request size."
            }

            Result.success(response.results.map { it.elevation })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("RouteRepository", "Elevation API request failed", e)
            Result.failure(e)
        }
    }

    // 경사도(Slope %) 계산
    // 세 구간의 median 으로 noise smoothing 시도..해봤습니다..
    fun calculateSlopes(
        elevations: List<Double>,
        distancesMeters: List<Double>,
    ): List<Double> {

        val expectedDistanceCount = (elevations.size - 1).coerceAtLeast(0)

        require(distancesMeters.size == expectedDistanceCount) {
            "Expected $expectedDistanceCount distances for ${elevations.size} elevations, " +
             "but received ${distancesMeters.size}."
        }

        if (elevations.size < 2) return emptyList()

        require(distancesMeters.all { it.isFinite() && it > 0.0 }) {
            "All segment distances must be finite and greater than zero."
        }

        val rawSlopes = distancesMeters.indices.map { index ->
            val elevationChange = elevations[index + 1] - elevations[index]
            (elevationChange / distancesMeters[index]) * 100.0
        }

        return rawSlopes.indices.map { index ->
            val start = (index - 1).coerceAtLeast(0)
            val endExclusive = (index + 2).coerceAtMost(rawSlopes.size)
            val sortedWindow = rawSlopes.subList(start, endExclusive).sorted()
            val middle = sortedWindow.size / 2


            if (sortedWindow.size % 2 == 1) {
                sortedWindow[middle]
            } else {
                (sortedWindow[middle - 1] + sortedWindow[middle]) / 2.0
            }
        }
    }
}
