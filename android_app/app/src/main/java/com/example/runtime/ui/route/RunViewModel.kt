package com.example.runtime.ui.route

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runtime.data.remote.GeoJsonLineString
import com.example.runtime.data.repository.CommunityRepository
import com.example.runtime.ui.common.UiText
import com.example.runtime.ui.common.toUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class RunTarget(
    val lines: List<GeoJsonLineString>,
    val distanceMeters: Int,
    val routeId: Int? = null,
    val temporaryRouteId: Int? = null,
    val startName: String = "",
    val groupId: Int? = null,
)

sealed interface RunRecordState {
    data object Idle : RunRecordState
    data object Saving : RunRecordState
    data class Saved(val routeId: Int, val sharedRouteId: Int?) : RunRecordState
    data class Failed(val message: UiText) : RunRecordState
}

sealed interface ShareState {
    data object Idle : ShareState
    data object Sharing : ShareState
    data class Failed(val message: UiText) : ShareState
}

class RunViewModel(
    private val repository: CommunityRepository = CommunityRepository(),
) : ViewModel() {

    var target by mutableStateOf<RunTarget?>(null)
        private set

    var recordState by mutableStateOf<RunRecordState>(RunRecordState.Idle)
        private set

    var shareState by mutableStateOf<ShareState>(ShareState.Idle)
        private set

    private var lastRun: Pair<Int, Long>? = null

    fun start(target: RunTarget) {
        this.target = target
        recordState = RunRecordState.Idle
        shareState = ShareState.Idle
        lastRun = null
    }

    fun recordRun(distanceMeters: Int, durationSeconds: Long) {
        val target = target ?: return
        if (recordState == RunRecordState.Saving || recordState is RunRecordState.Saved) return
        lastRun = distanceMeters to durationSeconds
        recordState = RunRecordState.Saving
        viewModelScope.launch {
            recordState = try {
                val record = repository.recordRun(
                    routeId = target.routeId,
                    temporaryRouteId = target.temporaryRouteId.takeIf { target.routeId == null },
                    distanceMeters = distanceMeters,
                    durationSeconds = durationSeconds,
                    groupId = target.groupId,
                )
                RunRecordState.Saved(record.routeId, record.sharedRouteId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RunRecordState.Failed(e.toUiText())
            }
        }
    }

    fun retryRecord() {
        val (distanceMeters, durationSeconds) = lastRun ?: return
        recordRun(distanceMeters, durationSeconds)
    }

    fun share(title: String, description: String, onShared: (Int) -> Unit) {
        val saved = recordState as? RunRecordState.Saved ?: return
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty() || shareState == ShareState.Sharing) return
        shareState = ShareState.Sharing
        viewModelScope.launch {
            try {
                val shared = repository.shareRoute(saved.routeId, trimmedTitle, description.trim())
                recordState = saved.copy(sharedRouteId = shared.id)
                shareState = ShareState.Idle
                onShared(shared.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                shareState = ShareState.Failed(e.toUiText())
            }
        }
    }
}
