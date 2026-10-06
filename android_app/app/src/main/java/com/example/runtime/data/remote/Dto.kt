package com.example.runtime.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val status: String,
    val message: String? = null,
    val data: T? = null,
) {
    fun requireData(): T = data ?: throw EmptyResponseException(message)
}

@Serializable
data class GeoJsonPoint(
    val coordinates: List<Double>,
    val type: String = "Point",
) {
    companion object {
        fun of(longitude: Double, latitude: Double) = GeoJsonPoint(listOf(longitude, latitude))
    }
}

@Serializable
data class GeoJsonLineString(
    val coordinates: List<List<Double>>,
    val type: String = "LineString",
)

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class AuthData(
    val username: String,
    val email: String,
    val token: String,
)

@Serializable
data class ProfileData(
    val username: String,
    val email: String,
)

@Serializable
data class GenerateRouteRequest(
    @SerialName("starting_point") val startingPoint: GeoJsonPoint,
    val distance: Int,
)

@Serializable
data class GeneratedRouteData(
    @SerialName("temporary_route_id") val temporaryRouteId: Int,
    val route: RouteBody,
)

@Serializable
data class RouteBody(
    val distance: Int,
    val briefing: String,
    val route: List<GeoJsonLineString>,
)

@Serializable
data class SaveRouteRequest(
    @SerialName("temporary_route_id") val temporaryRouteId: Int? = null,
    @SerialName("route_id") val routeId: Int? = null,
)

@Serializable
data class SaveRouteResponse(
    val status: String,
    val message: String? = null,
    @SerialName("route_id") val routeId: Int? = null,
)

@Serializable
data class SavedRoute(
    val id: Int,
    val distance: Int,
    val briefing: String,
    val route: List<GeoJsonLineString>,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("saved_at") val savedAt: String,
)
