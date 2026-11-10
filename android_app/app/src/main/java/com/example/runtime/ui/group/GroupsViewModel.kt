package com.example.runtime.ui.group

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runtime.R
import com.example.runtime.data.remote.CreateGroupRequest
import com.example.runtime.data.remote.GroupMessage
import com.example.runtime.data.remote.RunningGroup
import com.example.runtime.data.repository.GroupRepository
import com.example.runtime.data.repository.RouteRepository
import com.example.runtime.ui.common.routeTitle
import com.example.runtime.ui.common.UiText
import com.example.runtime.ui.common.toUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

enum class GroupFilter(val key: String, @StringRes val label: Int) {
    ALL("all", R.string.group_filter_all),
    CLOSING_SOON("closing_soon", R.string.group_filter_closing_soon),
    NEARBY("all", R.string.group_filter_nearby),
    MINE("mine", R.string.group_filter_mine),
}

val NEARBY_RADII_KM = listOf(1.0, 3.0, 5.0, 10.0)
const val MAX_GROUP_TITLE_LENGTH = 60
const val MAX_GROUP_DESCRIPTION_LENGTH = 300
const val MAX_MESSAGE_LENGTH = 500
const val MIN_GROUP_CAPACITY = 2
const val MAX_GROUP_CAPACITY = 20
private const val CHAT_POLL_MS = 3_000L

sealed interface GroupActionState {
    data object Idle : GroupActionState
    data object Working : GroupActionState
    data class Failed(val message: UiText) : GroupActionState
}

class GroupsViewModel(
    private val repository: GroupRepository = GroupRepository(),
    private val routeRepository: RouteRepository = RouteRepository(),
) : ViewModel() {

    var groups by mutableStateOf<List<RunningGroup>>(emptyList())
        private set

    var filter by mutableStateOf(GroupFilter.ALL)
        private set

    var radiusKm by mutableStateOf(3.0)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<UiText?>(null)
        private set

    var detail by mutableStateOf<RunningGroup?>(null)
        private set

    var detailError by mutableStateOf<UiText?>(null)
        private set

    var actionState by mutableStateOf<GroupActionState>(GroupActionState.Idle)
        private set

    var createState by mutableStateOf<GroupActionState>(GroupActionState.Idle)
        private set

    var messages by mutableStateOf<List<GroupMessage>>(emptyList())
        private set

    var chatError by mutableStateOf<UiText?>(null)
        private set

    var isSending by mutableStateOf(false)
        private set

    var routeChoices by mutableStateOf<List<GroupRouteChoice>?>(null)
        private set

    private var location: Pair<Double, Double>? = null
    private var refreshJob: Job? = null
    private var detailJob: Job? = null
    private var chatJob: Job? = null

    fun changeFilter(filter: GroupFilter) {
        if (filter == this.filter) return
        this.filter = filter
        reload()
    }

    fun showNearby(latitude: Double, longitude: Double) {
        location = latitude to longitude
        filter = GroupFilter.NEARBY
        reload()
    }

    fun changeRadius(radiusKm: Double) {
        if (radiusKm == this.radiusKm) return
        this.radiusKm = radiusKm
        if (filter == GroupFilter.NEARBY) reload()
    }

    private fun reload() {
        refreshJob?.cancel()
        isLoading = false
        refresh()
    }

    fun refresh() {
        if (isLoading) return
        isLoading = true
        error = null
        val filter = filter
        val origin = location.takeIf { filter == GroupFilter.NEARBY }
        val radius = radiusKm.takeIf { origin != null }
        refreshJob = viewModelScope.launch {
            try {
                groups = repository.groups(filter.key, origin?.first, origin?.second, radius)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toUiText()
            } finally {
                isLoading = false
            }
        }
    }

    fun loadDetail(groupId: Int) {
        detailJob?.cancel()
        detail = detail?.takeIf { it.id == groupId } ?: groups.firstOrNull { it.id == groupId }
        detailError = null
        actionState = GroupActionState.Idle
        detailJob = viewModelScope.launch {
            try {
                showDetail(repository.group(groupId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                detailError = e.toUiText()
            }
        }
    }

    private fun showDetail(group: RunningGroup) {
        detail = group
        groups = groups.map { if (it.id == group.id) group.copy(route = emptyList(), distanceFromMe = it.distanceFromMe) else it }
    }

    fun join(password: String? = null, onJoined: () -> Unit = {}) = act({ repository.join(it, password) }, onJoined)

    fun leave(onLeft: () -> Unit = {}) = act({ repository.leave(it) }, onLeft)

    private fun act(call: suspend (Int) -> RunningGroup, onDone: () -> Unit) {
        val groupId = detail?.id ?: return
        if (actionState == GroupActionState.Working) return
        actionState = GroupActionState.Working
        viewModelScope.launch {
            actionState = try {
                showDetail(call(groupId))
                onDone()
                GroupActionState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                GroupActionState.Failed(e.toUiText())
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val groupId = detail?.id ?: return
        if (actionState == GroupActionState.Working) return
        actionState = GroupActionState.Working
        viewModelScope.launch {
            actionState = try {
                repository.delete(groupId)
                groups = groups.filterNot { it.id == groupId }
                detail = null
                onDeleted()
                GroupActionState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                GroupActionState.Failed(e.toUiText())
            }
        }
    }

    fun loadRouteChoices() {
        viewModelScope.launch {
            routeChoices = try {
                routeRepository.savedRoutes().map {
                    GroupRouteChoice(it.id, it.name.ifBlank { routeTitle(it.startName, it.endName, it.distance) }, it.distance)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun resetCreate() {
        createState = GroupActionState.Idle
    }

    fun create(
        routeId: Int,
        title: String,
        description: String,
        capacity: Int,
        meetAt: Instant,
        joinDeadline: Instant,
        isPrivate: Boolean = false,
        password: String = "",
        onCreated: (Int) -> Unit,
    ) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty() || createState == GroupActionState.Working) return
        if (isPrivate && !isValidGroupPassword(password)) {
            createState = GroupActionState.Failed(UiText.Resource(R.string.error_group_password))
            return
        }
        if (joinDeadline.isAfter(meetAt)) {
            createState = GroupActionState.Failed(UiText.Resource(R.string.error_group_deadline_after_run))
            return
        }
        createState = GroupActionState.Working
        viewModelScope.launch {
            createState = try {
                val group = repository.create(
                    CreateGroupRequest(
                        routeId = routeId,
                        title = trimmedTitle,
                        description = description.trim(),
                        capacity = capacity.coerceIn(MIN_GROUP_CAPACITY, MAX_GROUP_CAPACITY),
                        meetAt = meetAt.toString(),
                        joinDeadline = joinDeadline.toString(),
                        isPrivate = isPrivate,
                        password = password.takeIf { isPrivate },
                    )
                )
                detail = group
                groups = emptyList()
                onCreated(group.id)
                GroupActionState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                GroupActionState.Failed(e.toUiText())
            }
        }
    }

    fun openChat(groupId: Int) {
        chatJob?.cancel()
        messages = emptyList()
        chatError = null
        chatJob = viewModelScope.launch {
            while (true) {
                fetchMessages(groupId)
                delay(CHAT_POLL_MS)
            }
        }
    }

    fun closeChat() {
        chatJob?.cancel()
        chatJob = null
    }

    private suspend fun fetchMessages(groupId: Int) {
        try {
            val newer = repository.messages(groupId, messages.lastOrNull()?.id)
            addMessages(newer)
            chatError = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            chatError = e.toUiText()
        }
    }

    private fun addMessages(newer: List<GroupMessage>) {
        if (newer.isEmpty()) return
        val known = messages.map { it.id }.toSet()
        messages = (messages + newer.filterNot { it.id in known }).sortedBy { it.id }
    }

    fun send(groupId: Int, text: String, onSent: () -> Unit) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || isSending) return
        isSending = true
        viewModelScope.launch {
            try {
                addMessages(listOf(repository.send(groupId, trimmed.take(MAX_MESSAGE_LENGTH))))
                chatError = null
                onSent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                chatError = e.toUiText()
            } finally {
                isSending = false
            }
        }
    }

    fun reset() {
        closeChat()
        groups = emptyList()
        detail = null
        messages = emptyList()
        routeChoices = null
        filter = GroupFilter.ALL
        location = null
    }
}

const val MIN_GROUP_PASSWORD_LENGTH = 4
const val MAX_GROUP_PASSWORD_LENGTH = 20

fun isValidGroupPassword(password: String): Boolean =
    password.isNotBlank() && password.length in MIN_GROUP_PASSWORD_LENGTH..MAX_GROUP_PASSWORD_LENGTH
