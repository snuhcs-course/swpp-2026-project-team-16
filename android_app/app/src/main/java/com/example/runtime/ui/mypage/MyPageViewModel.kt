package com.example.runtime.ui.mypage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runtime.data.remote.ProfileData
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

sealed interface ProfileEditState {
    data object Idle : ProfileEditState
    data object Saving : ProfileEditState
    data class Failed(val message: UiText) : ProfileEditState
}

class MyPageViewModel(
    private val routeRepository: RouteRepository = RouteRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val communityRepository: CommunityRepository = CommunityRepository(),
) : ViewModel() {

    var username by mutableStateOf<String?>(null)
        private set

    var email by mutableStateOf<String?>(null)
        private set

    var displayName by mutableStateOf("")
        private set

    var statusMessage by mutableStateOf("")
        private set

    var followerCount by mutableStateOf(0)
        private set

    var followingCount by mutableStateOf(0)
        private set

    var inviteCount by mutableStateOf(0)
        private set

    var avatar by mutableStateOf<ByteArray?>(null)
        private set

    var profileState by mutableStateOf<ProfileEditState>(ProfileEditState.Idle)
        private set

    var isUploadingAvatar by mutableStateOf(false)
        private set

    private var avatarUrl: String? = null

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
        displayName = ""
        statusMessage = ""
        followerCount = 0
        followingCount = 0
        inviteCount = 0
        avatar = null
        avatarUrl = null
        profileState = ProfileEditState.Idle
        isUploadingAvatar = false
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
                    showProfile(profile.await())
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

    private fun showProfile(profile: ProfileData) {
        username = profile.username
        email = profile.email
        displayName = profile.displayName
        statusMessage = profile.statusMessage
        followerCount = profile.followerCount
        followingCount = profile.followingCount
        inviteCount = profile.inviteCount
        if (profile.avatarUrl == avatarUrl) return
        avatarUrl = profile.avatarUrl
        val url = profile.avatarUrl
        if (url == null) {
            avatar = null
            return
        }
        sessionScope.launch {
            try {
                val bytes = userRepository.avatar(url)
                if (avatarUrl == url) avatar = bytes
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (avatarUrl == url) avatarUrl = null
            }
        }
    }

    fun resetProfileEdit() {
        profileState = ProfileEditState.Idle
    }

    fun saveProfile(name: String, status: String, onSaved: () -> Unit) {
        if (profileState == ProfileEditState.Saving) return
        profileState = ProfileEditState.Saving
        sessionScope.launch {
            profileState = try {
                showProfile(userRepository.updateProfile(name.trim(), status.trim()))
                onSaved()
                ProfileEditState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ProfileEditState.Failed(e.toUiText())
            }
        }
    }

    fun uploadAvatar(jpeg: ByteArray, onFailed: (UiText) -> Unit) {
        if (isUploadingAvatar) return
        isUploadingAvatar = true
        sessionScope.launch {
            try {
                val profile = userRepository.uploadAvatar(jpeg)
                avatar = jpeg
                avatarUrl = profile.avatarUrl
                showProfile(profile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailed(e.toUiText())
            } finally {
                isUploadingAvatar = false
            }
        }
    }

    fun removeAvatar(onFailed: (UiText) -> Unit) {
        if (isUploadingAvatar) return
        isUploadingAvatar = true
        sessionScope.launch {
            try {
                showProfile(userRepository.deleteAvatar())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailed(e.toUiText())
            } finally {
                isUploadingAvatar = false
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
