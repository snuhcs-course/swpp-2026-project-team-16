package com.example.runtime.testing

import com.example.runtime.data.remote.ApiResponse
import com.example.runtime.data.remote.ApiService
import com.example.runtime.data.remote.ElevationData
import com.example.runtime.data.remote.ElevationLookupRequest
import com.example.runtime.data.remote.AuthData
import com.example.runtime.data.remote.GenerateRouteRequest
import com.example.runtime.data.remote.GeneratedRouteData
import com.example.runtime.data.remote.GoogleLoginRequest
import com.example.runtime.data.remote.LoginRequest
import com.example.runtime.data.remote.Place
import com.example.runtime.data.remote.PlaceName
import com.example.runtime.data.remote.ProfileData
import com.example.runtime.data.remote.ProfileUpdateRequest
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import com.example.runtime.data.remote.RecordRunRequest
import com.example.runtime.data.remote.RegisterRequest
import com.example.runtime.data.remote.RenameRouteRequest
import com.example.runtime.data.remote.RenamedRoute
import com.example.runtime.data.remote.ReviewRequest
import com.example.runtime.data.remote.RouteReview
import com.example.runtime.data.remote.RunHistoryItem
import com.example.runtime.data.remote.RunPlan
import com.example.runtime.data.remote.RunPlanRequest
import com.example.runtime.data.remote.RunReport
import com.example.runtime.data.remote.RunningStyle
import com.example.runtime.data.remote.BoardComment
import com.example.runtime.data.remote.BoardPost
import com.example.runtime.data.remote.CommentRequest
import com.example.runtime.data.remote.CreateGroupRequest
import com.example.runtime.data.remote.PostRequest
import com.example.runtime.data.remote.ReportRequest
import com.example.runtime.data.remote.ReportResult
import com.example.runtime.data.remote.GroupMessage
import com.example.runtime.data.remote.RunRecord
import com.example.runtime.data.remote.RunningGroup
import com.example.runtime.data.remote.SendMessageRequest
import com.example.runtime.data.remote.SaveRouteRequest
import com.example.runtime.data.remote.SaveRouteResponse
import com.example.runtime.data.remote.SavedRoute
import com.example.runtime.data.remote.ShareRouteRequest
import com.example.runtime.data.remote.SharedRoute

open class FakeApiService : ApiService {
    override suspend fun register(body: RegisterRequest): ApiResponse<AuthData> = unexpected()
    override suspend fun login(body: LoginRequest): ApiResponse<AuthData> = unexpected()
    override suspend fun googleLogin(body: GoogleLoginRequest): ApiResponse<AuthData> = unexpected()
    override suspend fun logout(authorization: String): ApiResponse<Unit> = unexpected()
    override suspend fun myProfile(): ApiResponse<ProfileData> = unexpected()
    override suspend fun updateProfile(body: ProfileUpdateRequest): ApiResponse<ProfileData> = unexpected()
    override suspend fun uploadAvatar(avatar: MultipartBody.Part): ApiResponse<ProfileData> = unexpected()
    override suspend fun deleteAvatar(): ApiResponse<ProfileData> = unexpected()
    override suspend fun download(url: String): ResponseBody = unexpected()
    override suspend fun generateRoute(body: GenerateRouteRequest): ApiResponse<GeneratedRouteData> = unexpected()
    override suspend fun getElevations(body: ElevationLookupRequest): ApiResponse<ElevationData> = unexpected()
    override suspend fun savedRoutes(): List<SavedRoute> = unexpected()
    override suspend fun saveRoute(body: SaveRouteRequest): SaveRouteResponse = unexpected()
    override suspend fun savedRoute(routeId: Int): ApiResponse<SavedRoute> = unexpected()
    override suspend fun renameSavedRoute(routeId: Int, body: RenameRouteRequest): ApiResponse<RenamedRoute> = unexpected()
    override suspend fun deleteSavedRoute(routeId: Int): ApiResponse<Unit> = unexpected()
    override suspend fun searchPlaces(query: String, language: String): ApiResponse<List<Place>> = unexpected()
    override suspend fun reversePlace(latitude: Double, longitude: Double, language: String): ApiResponse<Place> = unexpected()
    override suspend fun translatePlace(name: String, address: String, language: String): ApiResponse<PlaceName> = unexpected()
    override suspend fun recordRun(body: RecordRunRequest): ApiResponse<RunRecord> = unexpected()
    override suspend fun runs(): ApiResponse<List<RunHistoryItem>> = unexpected()
    override suspend fun planRun(body: RunPlanRequest): ApiResponse<RunPlan> = unexpected()
    override suspend fun runReport(runId: Int): ApiResponse<RunReport> = unexpected()
    override suspend fun runningStyle(): ApiResponse<RunningStyle> = unexpected()
    override suspend fun updateRunningStyle(body: RunningStyle): ApiResponse<RunningStyle> = unexpected()
    override suspend fun sharedRoutes(sort: String): ApiResponse<List<SharedRoute>> = unexpected()
    override suspend fun sharedRoute(sharedRouteId: Int): ApiResponse<SharedRoute> = unexpected()
    override suspend fun shareRoute(body: ShareRouteRequest): ApiResponse<SharedRoute> = unexpected()
    override suspend fun deleteSharedRoute(sharedRouteId: Int): ApiResponse<Unit> = unexpected()
    override suspend fun reviews(sharedRouteId: Int): ApiResponse<List<RouteReview>> = unexpected()
    override suspend fun createReview(sharedRouteId: Int, body: ReviewRequest): ApiResponse<RouteReview> = unexpected()
    override suspend fun updateReview(reviewId: Int, body: ReviewRequest): ApiResponse<RouteReview> = unexpected()
    override suspend fun deleteReview(reviewId: Int): ApiResponse<Unit> = unexpected()

    override suspend fun groups(filter: String, latitude: Double?, longitude: Double?, radiusKm: Double?): ApiResponse<List<RunningGroup>> = unexpected()
    override suspend fun group(groupId: Int): ApiResponse<RunningGroup> = unexpected()
    override suspend fun createGroup(body: CreateGroupRequest): ApiResponse<RunningGroup> = unexpected()
    override suspend fun deleteGroup(groupId: Int): ApiResponse<Unit> = unexpected()
    override suspend fun joinGroup(groupId: Int): ApiResponse<RunningGroup> = unexpected()
    override suspend fun leaveGroup(groupId: Int): ApiResponse<RunningGroup> = unexpected()
    override suspend fun groupMessages(groupId: Int, after: Int?): ApiResponse<List<GroupMessage>> = unexpected()
    override suspend fun sendGroupMessage(groupId: Int, body: SendMessageRequest): ApiResponse<GroupMessage> = unexpected()
    override suspend fun posts(filter: String, query: String?): ApiResponse<List<BoardPost>> = unexpected()
    override suspend fun post(postId: Int): ApiResponse<BoardPost> = unexpected()
    override suspend fun createPost(body: PostRequest): ApiResponse<BoardPost> = unexpected()
    override suspend fun updatePost(postId: Int, body: PostRequest): ApiResponse<BoardPost> = unexpected()
    override suspend fun deletePost(postId: Int): ApiResponse<Unit> = unexpected()
    override suspend fun likePost(postId: Int): ApiResponse<BoardPost> = unexpected()
    override suspend fun unlikePost(postId: Int): ApiResponse<BoardPost> = unexpected()
    override suspend fun scrapPost(postId: Int): ApiResponse<BoardPost> = unexpected()
    override suspend fun unscrapPost(postId: Int): ApiResponse<BoardPost> = unexpected()
    override suspend fun reportPost(postId: Int, body: ReportRequest): ApiResponse<ReportResult> = unexpected()
    override suspend fun addComment(postId: Int, body: CommentRequest): ApiResponse<List<BoardComment>> = unexpected()
    override suspend fun deleteComment(commentId: Int): ApiResponse<List<BoardComment>> = unexpected()
    override suspend fun reportComment(commentId: Int, body: ReportRequest): ApiResponse<ReportResult> = unexpected()

    private fun unexpected(): Nothing = throw AssertionError("Unexpected API call")
}
