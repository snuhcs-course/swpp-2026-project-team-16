package com.example.runtime.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("api/v1/auth/register/")
    suspend fun register(@Body body: RegisterRequest): ApiResponse<AuthData>

    @POST("api/v1/auth/login/")
    suspend fun login(@Body body: LoginRequest): ApiResponse<AuthData>

    @POST("api/v1/auth/google/")
    suspend fun googleLogin(@Body body: GoogleLoginRequest): ApiResponse<AuthData>

    @POST("api/v1/auth/logout/")
    suspend fun logout(@Header("Authorization") authorization: String): ApiResponse<Unit>

    @GET("api/v1/users/my_profile/")
    suspend fun myProfile(): ApiResponse<ProfileData>

    @POST("api/v1/routes/generate/")
    suspend fun generateRoute(@Body body: GenerateRouteRequest): ApiResponse<GeneratedRouteData>

    @POST("api/v1/elevations/")
    suspend fun getElevations(@Body body: ElevationLookupRequest): ApiResponse<ElevationData>

    @GET("api/v1/routes/saved/")
    suspend fun savedRoutes(): List<SavedRoute>

    @POST("api/v1/routes/saved/")
    suspend fun saveRoute(@Body body: SaveRouteRequest): SaveRouteResponse

    @GET("api/v1/routes/saved/{routeId}/")
    suspend fun savedRoute(@Path("routeId") routeId: Int): ApiResponse<SavedRoute>

    @PATCH("api/v1/routes/saved/{routeId}/")
    suspend fun renameSavedRoute(@Path("routeId") routeId: Int, @Body body: RenameRouteRequest): ApiResponse<RenamedRoute>

    @DELETE("api/v1/routes/saved/{routeId}/")
    suspend fun deleteSavedRoute(@Path("routeId") routeId: Int): ApiResponse<Unit>

    @GET("api/v1/places/search/")
    suspend fun searchPlaces(@Query("q") query: String, @Query("language") language: String): ApiResponse<List<Place>>

    @GET("api/v1/places/reverse/")
    suspend fun reversePlace(
        @Query("lat") latitude: Double,
        @Query("lng") longitude: Double,
        @Query("language") language: String,
    ): ApiResponse<Place>

    @POST("api/v1/runs/")
    suspend fun recordRun(@Body body: RecordRunRequest): ApiResponse<RunRecord>

    @GET("api/v1/runs/")
    suspend fun runs(): ApiResponse<List<RunHistoryItem>>

    @GET("api/v1/community/routes/")
    suspend fun sharedRoutes(@Query("sort") sort: String): ApiResponse<List<SharedRoute>>

    @GET("api/v1/community/routes/{sharedRouteId}/")
    suspend fun sharedRoute(@Path("sharedRouteId") sharedRouteId: Int): ApiResponse<SharedRoute>

    @POST("api/v1/community/routes/")
    suspend fun shareRoute(@Body body: ShareRouteRequest): ApiResponse<SharedRoute>

    @DELETE("api/v1/community/routes/{sharedRouteId}/")
    suspend fun deleteSharedRoute(@Path("sharedRouteId") sharedRouteId: Int): ApiResponse<Unit>

    @GET("api/v1/community/routes/{sharedRouteId}/reviews/")
    suspend fun reviews(@Path("sharedRouteId") sharedRouteId: Int): ApiResponse<List<RouteReview>>

    @POST("api/v1/community/routes/{sharedRouteId}/reviews/")
    suspend fun createReview(@Path("sharedRouteId") sharedRouteId: Int, @Body body: ReviewRequest): ApiResponse<RouteReview>

    @PATCH("api/v1/community/reviews/{reviewId}/")
    suspend fun updateReview(@Path("reviewId") reviewId: Int, @Body body: ReviewRequest): ApiResponse<RouteReview>

    @DELETE("api/v1/community/reviews/{reviewId}/")
    suspend fun deleteReview(@Path("reviewId") reviewId: Int): ApiResponse<Unit>

    @GET("api/v1/groups/")
    suspend fun groups(
        @Query("filter") filter: String,
        @Query("lat") latitude: Double? = null,
        @Query("lon") longitude: Double? = null,
        @Query("radius_km") radiusKm: Double? = null,
    ): ApiResponse<List<RunningGroup>>

    @GET("api/v1/groups/{groupId}/")
    suspend fun group(@Path("groupId") groupId: Int): ApiResponse<RunningGroup>

    @POST("api/v1/groups/")
    suspend fun createGroup(@Body body: CreateGroupRequest): ApiResponse<RunningGroup>

    @DELETE("api/v1/groups/{groupId}/")
    suspend fun deleteGroup(@Path("groupId") groupId: Int): ApiResponse<Unit>

    @POST("api/v1/groups/{groupId}/membership/")
    suspend fun joinGroup(@Path("groupId") groupId: Int): ApiResponse<RunningGroup>

    @DELETE("api/v1/groups/{groupId}/membership/")
    suspend fun leaveGroup(@Path("groupId") groupId: Int): ApiResponse<RunningGroup>

    @GET("api/v1/groups/{groupId}/messages/")
    suspend fun groupMessages(@Path("groupId") groupId: Int, @Query("after") after: Int? = null): ApiResponse<List<GroupMessage>>

    @POST("api/v1/groups/{groupId}/messages/")
    suspend fun sendGroupMessage(@Path("groupId") groupId: Int, @Body body: SendMessageRequest): ApiResponse<GroupMessage>
}
