package com.example.runtime.ui.mypage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runtime.data.remote.RunHistoryItem
import com.example.runtime.data.remote.SavedRoute
import com.example.runtime.data.repository.CommunityRepository
import com.example.runtime.data.repository.RouteRepository
import com.example.runtime.data.repository.UserRepository
import com.example.runtime.ui.common.UiText
import com.example.runtime.ui.common.toUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class MyPageViewModel(
    private val routeRepository: RouteRepository = RouteRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val communityRepository: CommunityRepository = CommunityRepository(),
) : ViewModel() {

    var username by mutableStateOf<String?>(null)
        private set

    var email by mutableStateOf<String?>(null)
        private set

    var routes by mutableStateOf<List<SavedRoute>>(emptyList())
        private set

    var runs by mutableStateOf<List<RunHistoryItem>>(emptyList())
        private set

    var selectedTab by mutableIntStateOf(0)

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

    var isRenaming by mutableStateOf(false)
        private set

    private var sessionScope = newSessionScope()

    private fun newSessionScope() =
        CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job]))

    fun reset() {
        sessionScope.cancel()
        sessionScope = newSessionScope()
        username = null
        email = null
        routes = emptyList()
        runs = emptyList()
        selectedTab = 0
        isLoading = false
        error = null
        detail = null
        detailError = null
        isDeleting = false
        isRenaming = false
    }

    fun refresh() {
        if (isLoading) return
        isLoading = true
        error = null
        sessionScope.launch {
            try {
                coroutineScope {
                    val profile = async { userRepository.profile() }
                    val saved = async { routeRepository.savedRoutes() }
                    val history = async { communityRepository.runs() }
                    profile.await().let {
                        username = it.username
                        email = it.email
                    }
                    routes = saved.await()
                    runs = history.await()
                }
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
        sessionScope.launch {
            try {
                detail = routeRepository.savedRoute(routeId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                detailError = e.toUiText()
            }
        }
    }

    fun rename(routeId: Int, name: String, onRenamed: () -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || isRenaming) return
        isRenaming = true
        detailError = null
        sessionScope.launch {
            try {
                val saved = routeRepository.renameSavedRoute(routeId, trimmed)
                routes = routes.map { if (it.id == routeId) it.copy(name = saved) else it }
                detail = detail?.let { if (it.id == routeId) it.copy(name = saved) else it }
                onRenamed()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                detailError = e.toUiText()
            } finally {
                isRenaming = false
            }
        }
    }

    fun deleteFromList(routeId: Int) {
        val index = routes.indexOfFirst { it.id == routeId }
        if (index < 0) return
        val removed = routes[index]
        routes = routes.filterNot { it.id == routeId }
        error = null
        sessionScope.launch {
            try {
                routeRepository.deleteSavedRoute(routeId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                routes = routes.toMutableList().apply { add(index.coerceAtMost(size), removed) }
                error = e.toUiText()
            }
        }
    }

    fun delete(routeId: Int, onDeleted: () -> Unit) {
        if (isDeleting) return
        isDeleting = true
        detailError = null
        sessionScope.launch {
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
