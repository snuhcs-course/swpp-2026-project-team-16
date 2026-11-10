package com.example.runtime.data.repository

import com.example.runtime.data.remote.ApiClient
import com.example.runtime.data.remote.ApiService
import com.example.runtime.data.remote.RecordRunRequest
import com.example.runtime.data.remote.ReviewRequest
import com.example.runtime.data.remote.RouteReview
import com.example.runtime.data.remote.RunHistoryItem
import com.example.runtime.data.remote.RunRecord
import com.example.runtime.data.remote.SaveRouteRequest
import com.example.runtime.data.remote.ShareRouteRequest
import com.example.runtime.data.remote.SharedRoute

class CommunityRepository(private val api: ApiService = ApiClient.api) {

    suspend fun recordRun(request: RecordRunRequest): RunRecord = api.recordRun(request).requireData()

    suspend fun runs(): List<RunHistoryItem> = api.runs().data.orEmpty()

    suspend fun sharedRoutes(sort: String, filter: String? = null): List<SharedRoute> = api.sharedRoutes(sort, filter).data.orEmpty()

    suspend fun sharedRoute(sharedRouteId: Int): SharedRoute = api.sharedRoute(sharedRouteId).requireData()

    suspend fun shareRoute(routeId: Int, title: String, description: String): SharedRoute =
        api.shareRoute(ShareRouteRequest(routeId, title, description)).requireData()

    suspend fun shareGeneratedRoute(temporaryRouteId: Int, title: String, description: String): SharedRoute =
        api.shareRoute(ShareRouteRequest(title = title, description = description, temporaryRouteId = temporaryRouteId))
            .requireData()

    suspend fun deleteSharedRoute(sharedRouteId: Int) {
        api.deleteSharedRoute(sharedRouteId)
    }

    suspend fun reviews(sharedRouteId: Int): List<RouteReview> = api.reviews(sharedRouteId).data.orEmpty()

    suspend fun createReview(sharedRouteId: Int, rating: Int, comment: String): RouteReview =
        api.createReview(sharedRouteId, ReviewRequest(rating, comment)).requireData()

    suspend fun updateReview(reviewId: Int, rating: Int, comment: String): RouteReview =
        api.updateReview(reviewId, ReviewRequest(rating, comment)).requireData()

    suspend fun deleteReview(reviewId: Int) {
        api.deleteReview(reviewId)
    }

    suspend fun saveRoute(routeId: Int) {
        api.saveRoute(SaveRouteRequest(routeId = routeId))
    }
}
