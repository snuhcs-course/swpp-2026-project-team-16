package com.example.runtime.ui.mypage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runtime.data.remote.SavedRoute
import com.example.runtime.data.repository.RouteRepository
import com.example.runtime.data.repository.UserRepository
import com.example.runtime.ui.common.UiText
import com.example.runtime.ui.common.toUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class MyPageViewModel(
    private val routeRepository: RouteRepository = RouteRepository(),
    private val userRepository: UserRepository = UserRepository(),
) : ViewModel() {

    var username by mutableStateOf<String?>(null)
        private set

    var routes by mutableStateOf<List<SavedRoute>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<UiText?>(null)
        private set

    var detail by mutableStateOf<SavedRoute?>(null)
        private set

    var detailError by mutableStateOf<UiText?>(null)
        private set

    var isDeleting by mutableStateOf(false)
        private set

    fun refresh() {
        if (isLoading) return
        isLoading = true
        error = null
        viewModelScope.launch {
            try {
                val (profile, saved) = coroutineScope {
                    val profile = async { userRepository.profile() }
                    val saved = async { routeRepository.savedRoutes() }
                    profile.await() to saved.await()
                }
                username = profile.username
                routes = saved
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toUiText()
            } finally {
                isLoading = false
            }
        }
    }

    fun loadDetail(routeId: Int) {
        detail = routes.firstOrNull { it.id == routeId }
        detailError = null
        viewModelScope.launch {
            try {
                detail = routeRepository.savedRoute(routeId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                detailError = e.toUiText()
            }
        }
    }

    fun delete(routeId: Int, onDeleted: () -> Unit) {
        if (isDeleting) return
        isDeleting = true
        detailError = null
        viewModelScope.launch {
            try {
                routeRepository.deleteSavedRoute(routeId)
                routes = routes.filterNot { it.id == routeId }
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
}
