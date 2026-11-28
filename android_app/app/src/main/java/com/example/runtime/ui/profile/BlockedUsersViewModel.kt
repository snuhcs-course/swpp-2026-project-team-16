package com.example.runtime.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runtime.data.remote.BlockedUser
import com.example.runtime.data.repository.SocialRepository
import com.example.runtime.ui.common.UiText
import com.example.runtime.ui.common.toUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class BlockedUsersViewModel(
    private val repository: SocialRepository = SocialRepository(),
) : ViewModel() {

    var users by mutableStateOf<List<BlockedUser>?>(null)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set
    var unblocking by mutableStateOf<Set<String>>(emptySet())
        private set

    private var loadJob: Job? = null

    fun load() {
        error = null
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                users = repository.blockedUsers()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toUiText()
            }
        }
    }

    fun unblock(username: String, onUnblocked: (String) -> Unit = {}) {
        if (username in unblocking) return
        unblocking = unblocking + username
        error = null
        viewModelScope.launch {
            try {
                repository.unblock(username)
                users = users?.filterNot { it.username == username }
                onUnblocked(username)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toUiText()
            } finally {
                unblocking = unblocking - username
            }
        }
    }

    fun reset() {
        loadJob?.cancel()
        users = null
        error = null
        unblocking = emptySet()
    }
}
