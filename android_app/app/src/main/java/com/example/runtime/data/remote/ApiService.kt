package com.example.runtime.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    @POST("api/v1/auth/register/")
    suspend fun register(@Body body: RegisterRequest): ApiResponse<AuthData>

    @POST("api/v1/auth/login/")
    suspend fun login(@Body body: LoginRequest): ApiResponse<AuthData>

    @GET("api/v1/users/my_profile/")
    suspend fun myProfile(): ApiResponse<ProfileData>

    @POST("api/v1/routes/generate/")
    suspend fun generateRoute(@Body body: GenerateRouteRequest): ApiResponse<GeneratedRouteData>

    @GET("api/v1/routes/saved/")
    suspend fun savedRoutes(): List<SavedRoute>

    @POST("api/v1/routes/saved/")
    suspend fun saveRoute(@Body body: SaveRouteRequest): SaveRouteResponse

    @GET("api/v1/routes/saved/{routeId}/")
    suspend fun savedRoute(@Path("routeId") routeId: Int): ApiResponse<SavedRoute>

    @DELETE("api/v1/routes/saved/{routeId}/")
    suspend fun deleteSavedRoute(@Path("routeId") routeId: Int): ApiResponse<Unit>
}
