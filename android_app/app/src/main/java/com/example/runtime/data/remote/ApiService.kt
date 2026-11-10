package com.example.runtime.data.remote

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

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

    @PATCH("api/v1/users/my_profile/")
    suspend fun updateProfile(@Body body: ProfileUpdateRequest): ApiResponse<ProfileData>

    @Multipart
    @PUT("api/v1/users/my_profile/avatar/")
    suspend fun uploadAvatar(@Part avatar: MultipartBody.Part): ApiResponse<ProfileData>

    @DELETE("api/v1/users/my_profile/avatar/")
    suspend fun deleteAvatar(): ApiResponse<ProfileData>

    @GET
    suspend fun download(@Url url: String): ResponseBody

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

    @GET("api/v1/places/translate/")
    suspend fun translatePlace(
        @Query("name") name: String,
        @Query("address") address: String,
        @Query("language") language: String,
    ): ApiResponse<PlaceName>

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

    @POST("api/v1/runs/plan/")
    suspend fun planRun(@Body body: RunPlanRequest): ApiResponse<RunPlan>

    @GET("api/v1/runs/{runId}/report/")
    suspend fun runReport(@Path("runId") runId: Int): ApiResponse<RunReport>

    @GET("api/v1/users/my_profile/running_style/")
    suspend fun runningStyle(): ApiResponse<RunningStyle>

    @PUT("api/v1/users/my_profile/running_style/")
    suspend fun updateRunningStyle(@Body body: RunningStyle): ApiResponse<RunningStyle>

    @GET("api/v1/community/routes/")
    suspend fun sharedRoutes(@Query("sort") sort: String, @Query("filter") filter: String? = null): ApiResponse<List<SharedRoute>>

    @GET("api/v1/users/{username}/profile/")
    suspend fun userProfile(@Path("username") username: String): ApiResponse<UserProfile>

    @POST("api/v1/users/{username}/follow/")
    suspend fun follow(@Path("username") username: String): ApiResponse<FollowState>

    @DELETE("api/v1/users/{username}/follow/")
    suspend fun unfollow(@Path("username") username: String): ApiResponse<FollowState>

    @GET("api/v1/users/{username}/followers/")
    suspend fun followers(@Path("username") username: String): ApiResponse<List<FollowUser>>

    @GET("api/v1/users/{username}/following/")
    suspend fun following(@Path("username") username: String): ApiResponse<List<FollowUser>>

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
    suspend fun joinGroup(@Path("groupId") groupId: Int, @Body body: JoinGroupRequest): ApiResponse<RunningGroup>

    @DELETE("api/v1/groups/{groupId}/membership/")
    suspend fun leaveGroup(@Path("groupId") groupId: Int): ApiResponse<RunningGroup>

    @GET("api/v1/groups/{groupId}/messages/")
    suspend fun groupMessages(@Path("groupId") groupId: Int, @Query("after") after: Int? = null): ApiResponse<List<GroupMessage>>

    @POST("api/v1/groups/{groupId}/messages/")
    suspend fun sendGroupMessage(@Path("groupId") groupId: Int, @Body body: SendMessageRequest): ApiResponse<GroupMessage>

    @GET("api/v1/board/posts/")
    suspend fun posts(@Query("filter") filter: String, @Query("q") query: String? = null): ApiResponse<List<BoardPost>>

    @GET("api/v1/board/posts/{postId}/")
    suspend fun post(@Path("postId") postId: Int): ApiResponse<BoardPost>

    @POST("api/v1/board/posts/")
    suspend fun createPost(@Body body: PostRequest): ApiResponse<BoardPost>

    @PATCH("api/v1/board/posts/{postId}/")
    suspend fun updatePost(@Path("postId") postId: Int, @Body body: PostRequest): ApiResponse<BoardPost>

    @DELETE("api/v1/board/posts/{postId}/")
    suspend fun deletePost(@Path("postId") postId: Int): ApiResponse<Unit>

    @POST("api/v1/board/posts/{postId}/like/")
    suspend fun likePost(@Path("postId") postId: Int): ApiResponse<BoardPost>

    @DELETE("api/v1/board/posts/{postId}/like/")
    suspend fun unlikePost(@Path("postId") postId: Int): ApiResponse<BoardPost>

    @POST("api/v1/board/posts/{postId}/scrap/")
    suspend fun scrapPost(@Path("postId") postId: Int): ApiResponse<BoardPost>

    @DELETE("api/v1/board/posts/{postId}/scrap/")
    suspend fun unscrapPost(@Path("postId") postId: Int): ApiResponse<BoardPost>

    @POST("api/v1/board/posts/{postId}/report/")
    suspend fun reportPost(@Path("postId") postId: Int, @Body body: ReportRequest): ApiResponse<ReportResult>

    @POST("api/v1/board/posts/{postId}/comments/")
    suspend fun addComment(@Path("postId") postId: Int, @Body body: CommentRequest): ApiResponse<List<BoardComment>>

    @DELETE("api/v1/board/comments/{commentId}/")
    suspend fun deleteComment(@Path("commentId") commentId: Int): ApiResponse<List<BoardComment>>

    @POST("api/v1/board/comments/{commentId}/report/")
    suspend fun reportComment(@Path("commentId") commentId: Int, @Body body: ReportRequest): ApiResponse<ReportResult>
}
