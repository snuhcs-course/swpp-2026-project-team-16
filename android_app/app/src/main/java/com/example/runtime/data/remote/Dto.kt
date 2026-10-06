package com.example.runtime.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Request/response models for backend/api_schema.md.

// Common wrapper: {"status": "success" | "error", "message": ..., "data": ...}
@Serializable
data class ApiResponse<T>(
    val status: String,
    val message: String? = null,
    val data: T? = null,
)

// GeoJSON coordinates are [longitude, latitude] (longitude first).
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

// Auth

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

// Route generation

@Serializable
data class GenerateRouteRequest(
    @SerialName("starting_point") val startingPoint: GeoJsonPoint,
    val distance: Int, // meters, must be > 100
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

// Saved routes

// Send exactly one of the two ids.
@Serializable
data class SaveRouteRequest(
    @SerialName("temporary_route_id") val temporaryRouteId: Int? = null,
    @SerialName("route_id") val routeId: Int? = null,
)

// Not wrapped in "data"; route_id is only returned when saving a temporary route.
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
