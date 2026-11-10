package com.example.runtime.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runtime.data.remote.FollowUser
import com.example.runtime.data.remote.UserProfile
import com.example.runtime.data.repository.SocialRepository
import com.example.runtime.ui.common.UiText
import com.example.runtime.ui.common.toUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class FollowListType { FOLLOWERS, FOLLOWING }

class UserProfileViewModel(
    private val repository: SocialRepository = SocialRepository(),
) : ViewModel() {

    var profile by mutableStateOf<UserProfile?>(null)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set
    var avatar by mutableStateOf<ByteArray?>(null)
        private set
    var isFollowWorking by mutableStateOf(false)
        private set
    var followError by mutableStateOf<UiText?>(null)
        private set
    var listType by mutableStateOf<FollowListType?>(null)
        private set
    var listUsers by mutableStateOf<List<FollowUser>?>(null)
        private set

    private var loadJob: Job? = null
    private var listJob: Job? = null

    fun load(username: String) {
        if (profile?.username != username) {
            profile = null
            avatar = null
        }
        error = null
        followError = null
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val loaded = repository.profile(username)
                profile = loaded
                avatar = loaded.avatarUrl?.let { url ->
                    try {
                        repository.avatar(url)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toUiText()
            }
        }
    }

    fun toggleFollow() {
        val current = profile ?: return
        if (current.isMe || isFollowWorking) return
        isFollowWorking = true
        followError = null
        viewModelScope.launch {
            try {
                val state = if (current.isFollowing) repository.unfollow(current.username) else repository.follow(current.username)
                profile = profile?.takeIf { it.username == current.username }?.copy(
                    isFollowing = state.isFollowing,
                    followerCount = state.followerCount,
                    followingCount = state.followingCount,
                ) ?: profile
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                followError = e.toUiText()
            } finally {
                isFollowWorking = false
            }
        }
    }

    fun openList(type: FollowListType) {
        val username = profile?.username ?: return
        listType = type
        listUsers = null
        listJob?.cancel()
        listJob = viewModelScope.launch {
            listUsers = try {
                if (type == FollowListType.FOLLOWERS) repository.followers(username) else repository.following(username)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun closeList() {
        listJob?.cancel()
        listType = null
        listUsers = null
    }
}
