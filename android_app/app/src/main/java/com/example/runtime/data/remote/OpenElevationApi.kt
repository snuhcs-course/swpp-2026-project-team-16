package com.example.runtime.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

@Serializable
data class ElevationPoint(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class ElevationRequest(
    val locations: List<ElevationPoint>
)

@Serializable
data class ElevationResponse(
    val results: List<ElevationResult>
)

@Serializable
data class ElevationResult(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double
)

interface OpenElevationApiService {
    @POST("api/v1/lookup")
    suspend fun getElevation(@Body request: ElevationRequest): ElevationResponse
}