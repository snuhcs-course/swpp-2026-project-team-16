package com.example.runtime.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// Non-2xx responses throw retrofit2.HttpException; the error body is {"status": "error", "message": ...}.
interface ApiService {

    @POST("api/v1/auth/register/")
    suspend fun register(@Body body: RegisterRequest): ApiResponse<AuthData>

    @POST("api/v1/auth/login/")
    suspend fun login(@Body body: LoginRequest): ApiResponse<AuthData>

    @GET("api/v1/users/my_profile/")
    suspend fun myProfile(): ApiResponse<ProfileData>

    // Send the token too, otherwise the generated route can't be saved later.
    @POST("api/v1/routes/generate/")
    suspend fun generateRoute(@Body body: GenerateRouteRequest): ApiResponse<GeneratedRouteData>

    // Returns a bare array, not wrapped in ApiResponse.
    @GET("api/v1/routes/saved/")
    suspend fun savedRoutes(): List<SavedRoute>

    @POST("api/v1/routes/saved/")
    suspend fun saveRoute(@Body body: SaveRouteRequest): SaveRouteResponse

    @GET("api/v1/routes/saved/{routeId}/")
    suspend fun savedRoute(@Path("routeId") routeId: Int): ApiResponse<SavedRoute>

    @DELETE("api/v1/routes/saved/{routeId}/")
    suspend fun deleteSavedRoute(@Path("routeId") routeId: Int): ApiResponse<Unit>
}
