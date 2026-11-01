package com.example.runtime.ui.community

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runtime.data.remote.SharedRoute
import com.example.runtime.data.repository.CommunityRepository
import com.example.runtime.ui.common.UiText
import com.example.runtime.ui.common.toUiText
import com.example.runtime.ui.route.SaveState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CommunityViewModel(
    private val repository: CommunityRepository = CommunityRepository(),
) : ViewModel() {

    var routes by mutableStateOf<List<SharedRoute>>(emptyList())
        private set

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

    private var detailJob: Job? = null

    fun refresh() {
        if (isLoading) return
        isLoading = true
        error = null
        viewModelScope.launch {
            try {
                routes = repository.sharedRoutes()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toUiText()
            } finally {
                isLoading = false
            }
        }
    }

    fun loadDetail(sharedRouteId: Int) {
        detailJob?.cancel()
        detail = routes.firstOrNull { it.id == sharedRouteId }
        detailError = null
        saveState = SaveState.Idle
        detailJob = viewModelScope.launch {
            try {
                detail = repository.sharedRoute(sharedRouteId)
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
        detailError = null
        saveState = SaveState.Idle
    }
}
