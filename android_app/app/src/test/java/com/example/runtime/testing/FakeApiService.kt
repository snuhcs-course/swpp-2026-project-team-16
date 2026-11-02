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
import com.example.runtime.data.remote.ProfileData
import com.example.runtime.data.remote.RecordRunRequest
import com.example.runtime.data.remote.RegisterRequest
import com.example.runtime.data.remote.RenameRouteRequest
import com.example.runtime.data.remote.RenamedRoute
import com.example.runtime.data.remote.ReviewRequest
import com.example.runtime.data.remote.RouteReview
import com.example.runtime.data.remote.RunHistoryItem
import com.example.runtime.data.remote.RunRecord
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
    override suspend fun generateRoute(body: GenerateRouteRequest): ApiResponse<GeneratedRouteData> = unexpected()
    override suspend fun getElevations(body: ElevationLookupRequest): ApiResponse<ElevationData> = unexpected()
    override suspend fun savedRoutes(): List<SavedRoute> = unexpected()
    override suspend fun saveRoute(body: SaveRouteRequest): SaveRouteResponse = unexpected()
    override suspend fun savedRoute(routeId: Int): ApiResponse<SavedRoute> = unexpected()
    override suspend fun renameSavedRoute(routeId: Int, body: RenameRouteRequest): ApiResponse<RenamedRoute> = unexpected()
    override suspend fun deleteSavedRoute(routeId: Int): ApiResponse<Unit> = unexpected()
    override suspend fun searchPlaces(query: String, language: String): ApiResponse<List<Place>> = unexpected()
    override suspend fun reversePlace(latitude: Double, longitude: Double, language: String): ApiResponse<Place> = unexpected()
    override suspend fun recordRun(body: RecordRunRequest): ApiResponse<RunRecord> = unexpected()
    override suspend fun runs(): ApiResponse<List<RunHistoryItem>> = unexpected()
    override suspend fun sharedRoutes(sort: String): ApiResponse<List<SharedRoute>> = unexpected()
    override suspend fun sharedRoute(sharedRouteId: Int): ApiResponse<SharedRoute> = unexpected()
    override suspend fun shareRoute(body: ShareRouteRequest): ApiResponse<SharedRoute> = unexpected()
    override suspend fun deleteSharedRoute(sharedRouteId: Int): ApiResponse<Unit> = unexpected()
    override suspend fun reviews(sharedRouteId: Int): ApiResponse<List<RouteReview>> = unexpected()
    override suspend fun createReview(sharedRouteId: Int, body: ReviewRequest): ApiResponse<RouteReview> = unexpected()
    override suspend fun updateReview(reviewId: Int, body: ReviewRequest): ApiResponse<RouteReview> = unexpected()
    override suspend fun deleteReview(reviewId: Int): ApiResponse<Unit> = unexpected()

    private fun unexpected(): Nothing = throw AssertionError("Unexpected API call")
}
