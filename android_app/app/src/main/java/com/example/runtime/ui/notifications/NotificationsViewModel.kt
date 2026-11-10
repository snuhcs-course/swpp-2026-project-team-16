package com.example.runtime.ui.notifications

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runtime.R
import com.example.runtime.data.remote.AppNotification
import com.example.runtime.data.repository.NotificationRepository
import com.example.runtime.ui.common.UiText
import com.example.runtime.ui.common.toUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class NotificationKind(val apiValue: String, @StringRes val label: Int, @StringRes val description: Int) {
    FOLLOW("follow", R.string.notification_kind_follow, R.string.notification_kind_follow_desc),
    GROUP_INVITE("group_invite", R.string.notification_kind_group_invite, R.string.notification_kind_group_invite_desc),
    GROUP_JOIN("group_join", R.string.notification_kind_group_join, R.string.notification_kind_group_join_desc),
    GROUP_MESSAGE("group_message", R.string.notification_kind_group_message, R.string.notification_kind_group_message_desc),
    POST_LIKE("post_like", R.string.notification_kind_post_like, R.string.notification_kind_post_like_desc),
    POST_COMMENT("post_comment", R.string.notification_kind_post_comment, R.string.notification_kind_post_comment_desc),
    ROUTE_REVIEW("route_review", R.string.notification_kind_route_review, R.string.notification_kind_route_review_desc),
}

class NotificationsViewModel(
    private val repository: NotificationRepository = NotificationRepository(),
) : ViewModel() {

    var items by mutableStateOf<List<AppNotification>?>(null)
        private set
    var unreadCount by mutableIntStateOf(0)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set

    var settings by mutableStateOf<Map<String, Boolean>?>(null)
        private set
    var settingsError by mutableStateOf<UiText?>(null)
        private set
    var isDeleting by mutableStateOf(false)
        private set
    var deleteError by mutableStateOf<UiText?>(null)
        private set

    fun load() {
        error = null
        viewModelScope.launch {
            try {
                val page = repository.notifications()
                items = page.items
                unreadCount = page.unreadCount
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toUiText()
                if (items == null) items = emptyList()
            }
        }
    }

    fun markRead(notificationId: Int?) {
        items = items?.map { if (notificationId == null || it.id == notificationId) it.copy(read = true) else it }
        viewModelScope.launch {
            try {
                unreadCount = repository.markRead(notificationId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun loadSettings() {
        settingsError = null
        viewModelScope.launch {
            try {
                settings = repository.settings()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                settingsError = e.toUiText()
            }
        }
    }

    fun toggle(kind: NotificationKind, enabled: Boolean) {
        val previous = settings ?: return
        settings = previous + (kind.apiValue to enabled)
        settingsError = null
        viewModelScope.launch {
            try {
                settings = repository.updateSettings(mapOf(kind.apiValue to enabled))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                settings = previous
                settingsError = e.toUiText()
            }
        }
    }

    fun setAll(enabled: Boolean) {
        val previous = settings ?: return
        val changes = NotificationKind.entries.associate { it.apiValue to enabled }
        settings = previous + changes
        settingsError = null
        viewModelScope.launch {
            try {
                settings = repository.updateSettings(changes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                settings = previous
                settingsError = e.toUiText()
            }
        }
    }

    fun deleteAccount(password: String?, onDeleted: () -> Unit) {
        if (isDeleting) return
        isDeleting = true
        deleteError = null
        viewModelScope.launch {
            try {
                repository.deleteAccount(password)
                onDeleted()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                deleteError = e.toUiText()
            } finally {
                isDeleting = false
            }
        }
    }

    fun clearDeleteError() {
        deleteError = null
    }

    fun reset() {
        items = null
        unreadCount = 0
        error = null
        settings = null
        settingsError = null
        deleteError = null
    }
}
