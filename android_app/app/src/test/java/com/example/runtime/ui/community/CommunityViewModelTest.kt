package com.example.runtime.ui.community

import com.example.runtime.R
import com.example.runtime.data.remote.ApiResponse
import com.example.runtime.data.remote.GeoJsonLineString
import com.example.runtime.data.remote.ReviewRequest
import com.example.runtime.data.remote.RouteReview
import com.example.runtime.data.remote.SaveRouteRequest
import com.example.runtime.data.remote.SaveRouteResponse
import com.example.runtime.data.remote.SharedRoute
import com.example.runtime.data.repository.CommunityRepository
import com.example.runtime.testing.FakeApiService
import com.example.runtime.testing.MainDispatcherRule
import com.example.runtime.ui.common.UiText
import com.example.runtime.ui.route.SaveState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class CommunityViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val api = CommunityApi()
    private val viewModel by lazy { CommunityViewModel(CommunityRepository(api)) }

    @Test
    fun refresh_loadsSharedRoutes() {
        viewModel.refresh()

        assertEquals(listOf(1, 2), viewModel.routes.map { it.id })
        assertNull(viewModel.error)
    }

    @Test
    fun refresh_failureShowsTheError() {
        api.failList = true

        viewModel.refresh()

        assertEquals(UiText.Resource(R.string.error_network), viewModel.error)
        assertTrue(viewModel.routes.isEmpty())
    }

    @Test
    fun loadDetail_replacesTheListItemWithTheFullRoute() {
        viewModel.refresh()

        viewModel.loadDetail(2)

        assertEquals(1, viewModel.detail?.route?.size)
    }

    @Test
    fun save_savesTheUnderlyingRouteOnce() {
        viewModel.refresh()
        viewModel.loadDetail(2)

        viewModel.save()
        viewModel.save()

        assertEquals(listOf(SaveRouteRequest(routeId = 20)), api.saved)
        assertEquals(SaveState.Saved, viewModel.saveState)
    }

    @Test
    fun delete_removesTheRouteFromTheList() {
        viewModel.refresh()
        viewModel.loadDetail(2)
        var deleted = false

        viewModel.delete(2) { deleted = true }

        assertTrue(deleted)
        assertEquals(listOf(1), viewModel.routes.map { it.id })
        assertNull(viewModel.detail)
    }

    @Test
    fun changeSort_reloadsWithTheNewSort() {
        viewModel.refresh()

        viewModel.changeSort(RouteSort.RATING)
        viewModel.changeSort(RouteSort.RATING)

        assertEquals(listOf("latest", "rating"), api.sorts)
        assertEquals(RouteSort.RATING, viewModel.sort)
    }

    @Test
    fun submitReview_createsThenUpdatesMyReview() {
        viewModel.refresh()
        viewModel.loadDetail(2)
        var done = 0

        viewModel.submitReview(4, "  Nice  ") { done++ }
        assertEquals(50, viewModel.detail?.myReviewId)
        viewModel.submitReview(2, "Changed") { done++ }

        assertEquals(
            listOf("create 2" to ReviewRequest(4, "Nice"), "update 50" to ReviewRequest(2, "Changed")),
            api.reviewRequests,
        )
        assertEquals(2, done)
        assertEquals(listOf(2), viewModel.reviews.map { it.rating })
        assertEquals(ReviewSubmitState.Idle, viewModel.reviewSubmitState)
    }

    @Test
    fun submitReview_ignoresMissingRatingAndShowsFailures() {
        viewModel.refresh()
        viewModel.loadDetail(2)

        viewModel.submitReview(0, "") {}
        assertTrue(api.reviewRequests.isEmpty())

        api.failReview = true
        viewModel.submitReview(5, "") {}
        assertEquals(ReviewSubmitState.Failed(UiText.Resource(R.string.error_network)), viewModel.reviewSubmitState)
    }

    @Test
    fun deleteReview_reloadsTheReviews() {
        viewModel.refresh()
        viewModel.loadDetail(2)
        viewModel.submitReview(5, "") {}
        var deleted = false

        viewModel.deleteReview(50) { deleted = true }

        assertTrue(deleted)
        assertTrue(viewModel.reviews.isEmpty())
        assertNull(viewModel.detail?.myReviewId)
    }

    @Test
    fun toggleFollowingOnly_requestsOnlyFollowedRoutes() {
        viewModel.refresh()

        viewModel.toggleFollowingOnly()
        viewModel.toggleFollowingOnly()

        assertEquals(listOf(null, "following", null), api.filters)
    }

    @Test
    fun reset_turnsTheFollowingFilterOff() {
        viewModel.toggleFollowingOnly()

        viewModel.reset()

        assertEquals(false, viewModel.followingOnly)
    }

    private class CommunityApi : FakeApiService() {
        var failList = false
        var failReview = false
        val saved = mutableListOf<SaveRouteRequest>()
        val sorts = mutableListOf<String>()
        val reviews = mutableListOf<RouteReview>()
        val reviewRequests = mutableListOf<Pair<String, ReviewRequest>>()

        val filters = mutableListOf<String?>()

        override suspend fun sharedRoutes(sort: String, filter: String?): ApiResponse<List<SharedRoute>> {
            sorts += sort
            filters += filter
            if (failList) throw IOException("offline")
            return ApiResponse(status = "success", data = listOf(shared(1), shared(2)))
        }

        override suspend fun sharedRoute(sharedRouteId: Int): ApiResponse<SharedRoute> =
            ApiResponse(
                status = "success",
                data = shared(sharedRouteId).copy(
                    route = listOf(GeoJsonLineString(listOf(listOf(127.0, 37.0), listOf(127.01, 37.01)))),
                    reviewCount = reviews.size,
                    myReviewId = reviews.firstOrNull { it.isMine }?.id,
                    canReview = true,
                ),
            )

        override suspend fun reviews(sharedRouteId: Int): ApiResponse<List<RouteReview>> =
            ApiResponse(status = "success", data = reviews.toList())

        override suspend fun createReview(sharedRouteId: Int, body: ReviewRequest): ApiResponse<RouteReview> {
            if (failReview) throw IOException("offline")
            reviewRequests += "create $sharedRouteId" to body
            val review = RouteReview(id = 50, rating = body.rating, comment = body.comment, author = "runner", createdAt = "2026-11-02", isMine = true)
            reviews += review
            return ApiResponse(status = "success", data = review)
        }

        override suspend fun updateReview(reviewId: Int, body: ReviewRequest): ApiResponse<RouteReview> {
            reviewRequests += "update $reviewId" to body
            val review = reviews.single { it.id == reviewId }.copy(rating = body.rating, comment = body.comment)
            reviews.replaceAll { if (it.id == reviewId) review else it }
            return ApiResponse(status = "success", data = review)
        }

        override suspend fun deleteReview(reviewId: Int): ApiResponse<Unit> {
            reviews.removeAll { it.id == reviewId }
            return ApiResponse(status = "success")
        }

        override suspend fun saveRoute(body: SaveRouteRequest): SaveRouteResponse {
            saved += body
            return SaveRouteResponse(status = "success")
        }

        override suspend fun deleteSharedRoute(sharedRouteId: Int): ApiResponse<Unit> =
            ApiResponse(status = "success")

        private fun shared(id: Int) = SharedRoute(
            id = id,
            routeId = id * 10,
            title = "Route $id",
            sharedBy = "runner",
            sharedAt = "2026-11-01",
            distance = 3000,
        )
    }
}
