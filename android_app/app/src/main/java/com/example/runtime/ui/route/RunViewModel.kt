package com.example.runtime.ui.route

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runtime.data.remote.GeoJsonLineString
import com.example.runtime.data.remote.RecordRunRequest
import com.example.runtime.data.remote.RunPlan
import com.example.runtime.data.remote.RunReport
import com.example.runtime.data.repository.CoachRepository
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
    val plan: RunPlan? = null,
)

sealed interface RunRecordState {
    data object Idle : RunRecordState
    data object Saving : RunRecordState
    data class Saved(val routeId: Int, val sharedRouteId: Int?) : RunRecordState
    data class Failed(val message: UiText) : RunRecordState
}

sealed interface ReportState {
    data object Idle : ReportState
    data object Loading : ReportState
    data class Loaded(val report: RunReport) : ReportState
    data object Failed : ReportState
}

sealed interface ShareState {
    data object Idle : ShareState
    data object Sharing : ShareState
    data class Failed(val message: UiText) : ShareState
}

class RunViewModel(
    private val repository: CommunityRepository = CommunityRepository(),
    private val coachRepository: CoachRepository = CoachRepository(),
) : ViewModel() {

    var target by mutableStateOf<RunTarget?>(null)
        private set

    var recordState by mutableStateOf<RunRecordState>(RunRecordState.Idle)
        private set

    var shareState by mutableStateOf<ShareState>(ShareState.Idle)
        private set

    var result by mutableStateOf<RunResult?>(null)
        private set

    var reportState by mutableStateOf<ReportState>(ReportState.Idle)
        private set

    var runRating by mutableStateOf<Int?>(null)
        private set

    private var savedRunId: Int? = null

    fun rateRun(rating: Int) {
        val runId = savedRunId ?: return
        val previous = runRating
        runRating = rating
        viewModelScope.launch {
            runRating = try {
                repository.rateRun(runId, rating)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                previous
            }
        }
    }

    fun start(target: RunTarget) {
        this.target = target
        recordState = RunRecordState.Idle
        shareState = ShareState.Idle
        reportState = ReportState.Idle
        result = null
        runRating = null
        savedRunId = null
    }

    fun finish(result: RunResult, isLoggedIn: Boolean) {
        this.result = result
        if (isLoggedIn) recordRun(result)
    }

    private fun recordRun(result: RunResult) {
        val target = target ?: return
        if (recordState == RunRecordState.Saving || recordState is RunRecordState.Saved) return
        recordState = RunRecordState.Saving
        viewModelScope.launch {
            recordState = try {
                val record = repository.recordRun(
                    RecordRunRequest(
                        routeId = target.routeId,
                        temporaryRouteId = target.temporaryRouteId.takeIf { target.routeId == null },
                        distanceMeters = result.distanceMeters,
                        durationSeconds = result.elapsedSeconds,
                        groupId = target.groupId,
                        plan = target.plan,
                        splits = result.splitDurations,
                        offRouteCount = result.offRouteCount,
                        offRouteSeconds = result.offRouteSeconds,
                    ),
                )
                savedRunId = record.id
                RunRecordState.Saved(record.routeId, record.sharedRouteId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RunRecordState.Failed(e.toUiText())
            }
            if (recordState is RunRecordState.Saved) loadReport()
        }
    }

    fun retryRecord() {
        recordRun(result ?: return)
    }

    fun loadReport() {
        val runId = savedRunId ?: return
        if (reportState == ReportState.Loading || reportState is ReportState.Loaded) return
        reportState = ReportState.Loading
        viewModelScope.launch {
            reportState = try {
                ReportState.Loaded(coachRepository.runReport(runId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ReportState.Failed
            }
        }
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
