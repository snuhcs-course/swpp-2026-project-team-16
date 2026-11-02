package com.example.runtime.ui.community

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runtime.R
import com.example.runtime.data.remote.RouteReview
import com.example.runtime.data.remote.SharedRoute
import com.example.runtime.data.repository.CommunityRepository
import com.example.runtime.ui.common.UiText
import com.example.runtime.ui.common.toUiText
import com.example.runtime.ui.route.SaveState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

enum class RouteSort(val key: String, @StringRes val label: Int) {
    LATEST("latest", R.string.sort_latest),
    RATING("rating", R.string.sort_rating),
    REVIEWS("reviews", R.string.sort_reviews),
}

sealed interface ReviewSubmitState {
    data object Idle : ReviewSubmitState
    data object Submitting : ReviewSubmitState
    data class Failed(val message: UiText) : ReviewSubmitState
}

class CommunityViewModel(
    private val repository: CommunityRepository = CommunityRepository(),
) : ViewModel() {

    var routes by mutableStateOf<List<SharedRoute>>(emptyList())
        private set

    var sort by mutableStateOf(RouteSort.LATEST)
        private set

    var selectedTab by mutableIntStateOf(0)

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<UiText?>(null)
        private set

    var detail by mutableStateOf<SharedRoute?>(null)
        private set

    var detailError by mutableStateOf<UiText?>(null)
        private set

    var saveState by mutableStateOf<SaveState>(SaveState.Idle)
        private set

    var isDeleting by mutableStateOf(false)
        private set

    var reviews by mutableStateOf<List<RouteReview>>(emptyList())
        private set

    var reviewSubmitState by mutableStateOf<ReviewSubmitState>(ReviewSubmitState.Idle)
        private set

    private var detailJob: Job? = null
    private var refreshJob: Job? = null

    fun changeSort(sort: RouteSort) {
        if (sort == this.sort) return
        this.sort = sort
        refreshJob?.cancel()
        isLoading = false
        refresh()
    }

    fun refresh() {
        if (isLoading) return
        isLoading = true
        error = null
        val sort = sort
        refreshJob = viewModelScope.launch {
            try {
                routes = repository.sharedRoutes(sort.key)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toUiText()
            } finally {
                if (this@CommunityViewModel.sort == sort) isLoading = false
            }
        }
    }

    fun loadDetail(sharedRouteId: Int) {
        detailJob?.cancel()
        detail = routes.firstOrNull { it.id == sharedRouteId }
        reviews = emptyList()
        detailError = null
        saveState = SaveState.Idle
        reviewSubmitState = ReviewSubmitState.Idle
        detailJob = viewModelScope.launch { fetchDetail(sharedRouteId) }
    }

    private suspend fun fetchDetail(sharedRouteId: Int) {
        try {
            val (route, routeReviews) = coroutineScope {
                val route = async { repository.sharedRoute(sharedRouteId) }
                val routeReviews = async { repository.reviews(sharedRouteId) }
                route.await() to routeReviews.await()
            }
            detail = route
            reviews = routeReviews
            routes = routes.map { if (it.id == route.id) route.copy(route = emptyList()) else it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            detailError = e.toUiText()
        }
    }

    fun submitReview(rating: Int, comment: String, onDone: () -> Unit) {
        val route = detail ?: return
        if (rating !in 1..5 || reviewSubmitState == ReviewSubmitState.Submitting) return
        reviewSubmitState = ReviewSubmitState.Submitting
        viewModelScope.launch {
            try {
                val myReviewId = route.myReviewId
                if (myReviewId == null) {
                    repository.createReview(route.id, rating, comment.trim())
                } else {
                    repository.updateReview(myReviewId, rating, comment.trim())
                }
                reviewSubmitState = ReviewSubmitState.Idle
                onDone()
                fetchDetail(route.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reviewSubmitState = ReviewSubmitState.Failed(e.toUiText())
            }
        }
    }

    fun deleteReview(reviewId: Int, onDeleted: () -> Unit) {
        val route = detail ?: return
        viewModelScope.launch {
            try {
                repository.deleteReview(reviewId)
                onDeleted()
                fetchDetail(route.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                detailError = e.toUiText()
            }
        }
    }

    fun save() {
        val route = detail ?: return
        if (saveState == SaveState.Saving || saveState == SaveState.Saved) return
        saveState = SaveState.Saving
        viewModelScope.launch {
            saveState = try {
                repository.saveRoute(route.routeId)
                SaveState.Saved
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SaveState.Failed(e.toUiText())
            }
        }
    }

    fun delete(sharedRouteId: Int, onDeleted: () -> Unit) {
        if (isDeleting) return
        isDeleting = true
        detailError = null
        viewModelScope.launch {
            try {
                repository.deleteSharedRoute(sharedRouteId)
                routes = routes.filterNot { it.id == sharedRouteId }
                detail = null
                onDeleted()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                detailError = e.toUiText()
            } finally {
                isDeleting = false
            }
        }
    }

    fun reset() {
        detailJob?.cancel()
        routes = emptyList()
        error = null
        detail = null
        reviews = emptyList()
        detailError = null
        saveState = SaveState.Idle
        reviewSubmitState = ReviewSubmitState.Idle
    }
}
