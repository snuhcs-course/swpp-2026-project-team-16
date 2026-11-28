package com.example.runtime.ui.route

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runtime.R
import com.example.runtime.data.remote.GeneratedRouteData
import com.example.runtime.data.remote.GeoJsonLineString
import com.example.runtime.data.remote.NetworkStatus
import com.example.runtime.data.remote.NoConnectivityException
import com.example.runtime.data.remote.RecordRunRequest
import com.example.runtime.data.remote.RunPlan
import com.example.runtime.data.remote.RunReport
import com.example.runtime.data.remote.LiveRunCoachHill
import com.example.runtime.data.remote.LiveRunCoachRequest
import com.example.runtime.data.repository.CoachRepository
import com.example.runtime.data.repository.CommunityRepository
import com.example.runtime.data.repository.RouteRepository
import com.example.runtime.data.remote.GeoJsonPoint
import com.example.runtime.data.remote.Place
import com.example.runtime.data.remote.TokenStore
import com.example.runtime.data.run.RunMetrics
import com.example.runtime.data.run.RunSnapshot
import com.example.runtime.data.run.RunSnapshotStore
import com.example.runtime.data.run.RunStorage
import com.example.runtime.data.run.RunTrackingState
import com.example.runtime.data.run.PaceAlertSettings
import com.example.runtime.data.telemetry.Telemetry
import com.example.runtime.ui.common.UiText
import com.example.runtime.ui.common.toUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import java.io.IOException

data class RunTarget(
    val lines: List<GeoJsonLineString>,
    val distanceMeters: Int,
    val routeId: Int? = null,
    val temporaryRouteId: Int? = null,
    val startName: String = "",
    val groupId: Int? = null,
    val plan: RunPlan? = null,
    val isFree: Boolean = false,
    val uphillSections: List<RouteUphillSection> = emptyList(),
    val paceAlertSettings: PaceAlertSettings? = null,
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

sealed interface SaveRouteState {
    data object Idle : SaveRouteState
    data object Saving : SaveRouteState
    data object Saved : SaveRouteState
    data class Failed(val message: UiText) : SaveRouteState
}

sealed interface ShareState {
    data object Idle : ShareState
    data object Sharing : ShareState
    data class Failed(val message: UiText) : ShareState
}

class RunViewModel(
    private val repository: CommunityRepository = CommunityRepository(),
    private val coachRepository: CoachRepository = CoachRepository(),
    private val routeRepository: RouteRepository = RouteRepository(),
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val retryDelayMs: Long = RETURN_ROUTE_RETRY_DELAY_MS,
    private val online: Flow<Boolean> = NetworkStatus.online,
    private val snapshotStore: RunSnapshotStore = RunStorage.snapshots,
    private val currentUser: () -> String? = { TokenStore.username },
    private val wallClock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    var routePoints by mutableStateOf<List<RouteCoordinate>>(emptyList())
        private set
    var routeProgress by mutableStateOf(RouteProgress(0.0, Double.POSITIVE_INFINITY, 0.0))
        private set
    var isOffRoute by mutableStateOf(false)
        private set

    private var progressMatcher = RouteProgressMatcher(emptyList())
    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null
    private var offRouteCount = 0
    private var offRouteMillis = 0L
    private var offRouteSince: Long? = null
    private var voiceGuide = RouteVoiceGuide(emptyList(), 0.0)
    private var uphillVoiceGuide = UphillVoiceGuide(emptyList())
    private var lastCoachedKilometer = 0
    private var runSessionId = 0L
    private val liveCoachMutex = Mutex()
    private val liveCoachJobs = mutableListOf<Job>()

    var speakCue: (messageId: Int, distanceMeters: Int?) -> Unit = { _, _ -> }
    var speakCoachCue: (message: String) -> Unit = {}
    var stopTracking: () -> Unit = {}

    private var snapshotOwner: String? = null
    private var snapshotStartedAt: Long? = null
    private var lastSnapshotAt: Long? = null
    private var lastSnapshotPaused = false

    var interruptedRun by mutableStateOf<RunSnapshot?>(null)
        private set

    val isRunActive: Boolean
        get() = target != null && result == null && RunTrackingState.metrics.value.isTracking

    private fun setRoute(lines: List<GeoJsonLineString>) {
        routePoints = lines.flatMap { line ->
            line.coordinates.mapNotNull { coordinate ->
                if (coordinate.size >= 2) RouteCoordinate(coordinate[1], coordinate[0]) else null
            }
        }.distinctConsecutivePoints()
        progressMatcher = RouteProgressMatcher(routePoints)
        lastLatitude = null
        lastLongitude = null
        val turnGuide = RouteTurnGuide(routePoints)
        voiceGuide = RouteVoiceGuide(turnGuide.turns, turnGuide.routeLengthMeters)
        uphillVoiceGuide = UphillVoiceGuide(target?.uphillSections.orEmpty())
    }

    private fun onMetrics(metrics: RunMetrics) {
        val latitude = metrics.latitude
        val longitude = metrics.longitude
        if (latitude != null && longitude != null && (latitude != lastLatitude || longitude != lastLongitude)) {
            lastLatitude = latitude
            lastLongitude = longitude
            routeProgress = progressMatcher.update(latitude, longitude, clock(), metrics.accuracyMeters)
        }
        val offRoute = metrics.isTracking && !metrics.isPaused && latitude != null && longitude != null &&
            routeProgress.isOffRoute
        if (offRoute != isOffRoute) {
            val now = clock()
            if (offRoute) {
                offRouteCount += 1
                offRouteSince = now
            } else {
                offRouteSince?.let { offRouteMillis += now - it }
                offRouteSince = null
            }
            isOffRoute = offRoute
        }
        if (target != null && result == null) {
            announce(metrics)
            requestKilometerCoaching(metrics)
            snapshotIfDue(metrics)
        }
    }

    private fun snapshotIfDue(metrics: RunMetrics) {
        if (!metrics.isTracking || snapshotOwner == null) return
        val justPaused = metrics.isPaused && !lastSnapshotPaused
        lastSnapshotPaused = metrics.isPaused
        val last = lastSnapshotAt
        if (last != null && clock() - last < SNAPSHOT_INTERVAL_MS && !justPaused) return
        writeSnapshot(metrics)
    }

    fun saveSnapshot() {
        val metrics = RunTrackingState.metrics.value
        if (target == null || result != null || !metrics.isTracking) return
        writeSnapshot(metrics)
    }

    private fun writeSnapshot(metrics: RunMetrics) {
        val owner = snapshotOwner ?: return
        val runTarget = target ?: return
        val now = wallClock()
        val startedAt = snapshotStartedAt ?: (now - metrics.elapsedSeconds * 1000L).also { snapshotStartedAt = it }
        lastSnapshotAt = clock()
        val (count, seconds) = offRouteTotals()
        snapshotStore.save(
            RunSnapshot(
                owner = owner,
                routeId = runTarget.routeId,
                temporaryRouteId = runTarget.temporaryRouteId,
                groupId = runTarget.groupId,
                plan = runTarget.plan,
                isFree = runTarget.isFree,
                startName = runTarget.startName,
                distanceMeters = metrics.distanceMeters,
                elapsedSeconds = metrics.elapsedSeconds,
                splitTimes = RunTrackingState.splitTimes.value,
                offRouteCount = count,
                offRouteSeconds = seconds,
                path = if (runTarget.isFree) currentPath() else emptyList(),
                startedAtMillis = startedAt,
                savedAtMillis = now,
            ),
        )
    }

    private fun currentPath(): List<List<Double>> =
        RunTrackingState.trail.value.map { listOf(it.longitude, it.latitude) }

    fun checkForInterruptedRun() {
        if (interruptedRun != null || isRunActive) return
        val snapshot = snapshotStore.load() ?: return
        val user = currentUser() ?: return
        if (snapshot.owner != user || wallClock() - snapshot.savedAtMillis > MAX_SNAPSHOT_AGE_MS) {
            snapshotStore.clear()
            return
        }
        interruptedRun = snapshot
    }

    fun recoverInterruptedRun(): Boolean {
        val snapshot = interruptedRun ?: return false
        interruptedRun = null
        val live = RunTrackingState.metrics.value
        val orphaned = live.isTracking && target == null
        val latest = if (orphaned && live.elapsedSeconds >= snapshot.elapsedSeconds) {
            snapshot.copy(
                distanceMeters = live.distanceMeters,
                elapsedSeconds = live.elapsedSeconds,
                splitTimes = RunTrackingState.splitTimes.value,
                path = if (snapshot.isFree) currentPath() else emptyList(),
                savedAtMillis = wallClock(),
            )
        } else {
            snapshot
        }
        if (orphaned) stopTracking()
        start(latest.toTarget())
        cancelLiveCoachRequests()
        snapshotOwner = null
        snapshotStore.clear()
        val recovered = latest.toResult()
        result = recovered
        Telemetry.track("run_session", "completed")
        recordRun(recovered)
        return true
    }

    fun discardInterruptedRun() {
        interruptedRun ?: return
        interruptedRun = null
        if (RunTrackingState.metrics.value.isTracking && target == null) stopTracking()
        snapshotStore.clear()
    }

    private fun RunSnapshot.toTarget() = RunTarget(
        lines = emptyList(),
        distanceMeters = plan?.distanceMeters ?: 0,
        routeId = routeId,
        temporaryRouteId = temporaryRouteId,
        startName = startName,
        groupId = groupId,
        plan = plan,
        isFree = isFree,
    )

    private fun RunSnapshot.toResult() = RunResult(
        distanceMeters = distanceMeters.roundToInt(),
        elapsedSeconds = elapsedSeconds,
        splitTimes = splitTimes,
        offRouteCount = offRouteCount,
        offRouteSeconds = offRouteSeconds,
        finishedAtMillis = savedAtMillis,
        path = path,
    )

    private fun requestKilometerCoaching(metrics: RunMetrics) {
        if (!metrics.isTracking) return
        val completedKilometer = (metrics.distanceMeters / METERS_PER_KILOMETER).toInt().coerceAtMost(MAX_COACHED_KILOMETERS)
        if (completedKilometer <= lastCoachedKilometer) return
        lastCoachedKilometer = completedKilometer

        val runTarget = target ?: return
        val cumulativeSplits = RunTrackingState.splitTimes.value.take(completedKilometer)
        if (cumulativeSplits.size < completedKilometer) return
        var previousElapsed = 0L
        val splitPaces = cumulativeSplits.map { elapsed ->
            val pace = (elapsed - previousElapsed).toInt()
            previousElapsed = elapsed
            pace
        }
        if (splitPaces.any { it <= 0 }) return

        val nextSectionStart = completedKilometer * METERS_PER_KILOMETER.toInt()
        val plan = runTarget.plan
        val nextPaceSection = plan?.paceSegments?.firstOrNull {
            nextSectionStart >= it.startMeters && nextSectionStart < it.endMeters
        }
        val nextPaceMin = nextPaceSection?.paceMinSeconds ?: plan?.paceMinSeconds
        val nextPaceMax = nextPaceSection?.paceMaxSeconds ?: plan?.paceMaxSeconds
        val nearbyHills = runTarget.uphillSections
            .filter { it.endMeters >= nextSectionStart && it.startMeters <= nextSectionStart + HILL_LOOKAHEAD_METERS }
            .take(MAX_UPCOMING_HILLS)
            .map {
                LiveRunCoachHill(
                    distanceAheadMeters = (it.startMeters - nextSectionStart).coerceAtLeast(0),
                    lengthMeters = (it.endMeters - it.startMeters).coerceAtLeast(1),
                )
            }
        val request = LiveRunCoachRequest(
            completedKilometer = completedKilometer,
            distanceMeters = metrics.distanceMeters.roundToInt(),
            elapsedSeconds = metrics.elapsedSeconds,
            splitPacesSeconds = splitPaces,
            plan = plan,
            nextTargetPaceMinSeconds = nextPaceMin,
            nextTargetPaceMaxSeconds = nextPaceMax,
            upcomingHills = nearbyHills,
        )
        val sessionId = runSessionId
        val job = viewModelScope.launch {
            liveCoachMutex.withLock {
                if (sessionId != runSessionId || result != null) return@withLock
                val startedAt = Telemetry.startTime()
                Telemetry.track("live_run_coaching", "started")
                try {
                    val message = coachRepository.liveRunCoaching(request).message
                    Telemetry.track("live_run_coaching", "succeeded", Telemetry.startTime() - startedAt)
                    if (sessionId == runSessionId && result == null) speakCoachCue(message)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Telemetry.track(
                        "live_run_coaching",
                        "failed",
                        Telemetry.startTime() - startedAt,
                        Telemetry.failureCategory(e),
                    )
                    // The regular kilometer split has already been spoken; a failed AI request is non-blocking.
                }
            }
        }
        liveCoachJobs += job
    }

    private fun cancelLiveCoachRequests() {
        runSessionId += 1
        liveCoachJobs.forEach { it.cancel() }
        liveCoachJobs.clear()
    }

    private fun announce(metrics: RunMetrics) {
        val progress = routeProgress.distanceAlongMeters
        voiceGuide.update(progress, metrics.isTracking, metrics.isPaused, isOffRoute)
            ?.messageId()
            ?.let { (messageId, distance) -> speakCue(messageId, distance) }
        if (!metrics.isTracking || metrics.isPaused || isOffRoute) return
        uphillVoiceGuide.update(progress)?.let { speakCue(R.string.run_voice_uphill_approaching, it) }
    }

    private fun RouteVoiceCue.messageId(): Pair<Int, Int?>? {
        val messageId = when (kind) {
            RouteVoiceCue.Kind.START -> R.string.run_voice_started
            RouteVoiceCue.Kind.OFF_ROUTE -> R.string.run_voice_off_route
            RouteVoiceCue.Kind.REJOINED -> R.string.run_voice_rejoined
            RouteVoiceCue.Kind.ARRIVED -> R.string.run_voice_arrived
            RouteVoiceCue.Kind.TURN_APPROACHING -> when (direction) {
                RouteTurnDirection.LEFT -> R.string.run_voice_turn_approaching_left
                RouteTurnDirection.RIGHT -> R.string.run_voice_turn_approaching_right
                RouteTurnDirection.UTURN -> R.string.run_voice_turn_approaching_uturn
                null -> return null
            }
            RouteVoiceCue.Kind.TURN_NOW -> when (direction) {
                RouteTurnDirection.LEFT -> R.string.run_voice_turn_now_left
                RouteTurnDirection.RIGHT -> R.string.run_voice_turn_now_right
                RouteTurnDirection.UTURN -> R.string.run_voice_turn_now_uturn
                null -> return null
            }
        }
        return messageId to distanceMeters
    }

    fun offRouteTotals(): Pair<Int, Long> {
        val open = offRouteSince?.let { clock() - it } ?: 0L
        return offRouteCount to (offRouteMillis + open) / 1000L
    }

    var isMakingReturnRoute by mutableStateOf(false)
        private set
    var returnRouteError by mutableStateOf<UiText?>(null)
        private set

    fun makeReturnRoute(from: RouteCoordinate, to: RouteCoordinate, distanceMeters: Int, language: String) {
        if (isMakingReturnRoute) return
        isMakingReturnRoute = true
        returnRouteError = null
        viewModelScope.launch {
            try {
                val generated = generateReturnRoute(from, to, distanceMeters, language)
                useGuide(generated.route.route, generated.route.distance)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                returnRouteError = e.toUiText()
            } finally {
                isMakingReturnRoute = false
            }
        }
    }

    private suspend fun generateReturnRoute(
        from: RouteCoordinate,
        to: RouteCoordinate,
        distanceMeters: Int,
        language: String,
    ): GeneratedRouteData {
        val request = suspend {
            routeRepository.generate(
                startingPoint = Place("", "", GeoJsonPoint.of(from.longitude, from.latitude)),
                endPoint = Place("", "", GeoJsonPoint.of(to.longitude, to.latitude)),
                distanceMeters = distanceMeters,
                language = language,
            )
        }
        repeat(RETURN_ROUTE_RETRIES) {
            try {
                return request()
            } catch (e: NoConnectivityException) {
                throw e
            } catch (e: IOException) {
                delay(retryDelayMs)
            }
        }
        return request()
    }

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

    fun start(target: RunTarget): Boolean {
        if (isRunActive) return false
        cancelLiveCoachRequests()
        lastCoachedKilometer = 0
        this.target = target
        setRoute(target.lines)
        routeProgress = RouteProgress(0.0, Double.POSITIVE_INFINITY, progressMatcher.routeLengthMeters)
        isOffRoute = false
        offRouteCount = 0
        offRouteMillis = 0L
        offRouteSince = null
        recordState = RunRecordState.Idle
        shareState = ShareState.Idle
        reportState = ReportState.Idle
        result = null
        runRating = null
        saveRouteState = SaveRouteState.Idle
        savedRunId = null
        snapshotOwner = currentUser()
        snapshotStartedAt = null
        lastSnapshotAt = null
        lastSnapshotPaused = false
        return true
    }

    fun finish(result: RunResult, isLoggedIn: Boolean) {
        cancelLiveCoachRequests()
        this.result = result
        snapshotOwner = null
        snapshotStore.clear()
        Telemetry.track("run_session", "completed")
        if (isLoggedIn) recordRun(result)
    }

    private fun recordRun(result: RunResult) {
        val target = target ?: return
        if (recordState == RunRecordState.Saving || recordState is RunRecordState.Saved) return
        recordState = RunRecordState.Saving
        viewModelScope.launch {
            recordState = try {
                val record = repository.recordRun(recordRequest(target, result))
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

    private fun recordRequest(target: RunTarget, result: RunResult) = RecordRunRequest(
        routeId = target.routeId,
        temporaryRouteId = target.temporaryRouteId.takeIf { target.routeId == null },
        distanceMeters = result.distanceMeters,
        durationSeconds = result.elapsedSeconds,
        groupId = target.groupId,
        plan = target.plan,
        splits = result.splitDurations,
        offRouteCount = result.offRouteCount,
        offRouteSeconds = result.offRouteSeconds,
        path = result.path.takeIf { target.isFree },
    ).let { request ->
        if (target.isFree) request.copy(routeId = null, temporaryRouteId = null) else request
    }

    fun retryRecord() {
        recordRun(result ?: return)
    }

    fun useGuide(lines: List<GeoJsonLineString>, distanceMeters: Int) {
        val current = target ?: return
        target = current.copy(lines = lines, distanceMeters = distanceMeters, uphillSections = emptyList())
        setRoute(lines)
    }

    var saveRouteState by mutableStateOf<SaveRouteState>(SaveRouteState.Idle)
        private set

    fun saveFreeRoute() {
        val saved = recordState as? RunRecordState.Saved ?: return
        if (saveRouteState != SaveRouteState.Idle && saveRouteState !is SaveRouteState.Failed) return
        saveRouteState = SaveRouteState.Saving
        viewModelScope.launch {
            saveRouteState = try {
                repository.saveRoute(saved.routeId)
                SaveRouteState.Saved
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SaveRouteState.Failed(e.toUiText())
            }
        }
    }

    fun loadReport() {
        val runId = savedRunId ?: return
        if (reportState == ReportState.Loading || reportState is ReportState.Loaded) return
        reportState = ReportState.Loading
        val startedAt = Telemetry.startTime()
        Telemetry.track("run_report", "started")
        viewModelScope.launch {
            reportState = try {
                val loaded = coachRepository.runReport(runId)
                Telemetry.track("run_report", "succeeded", Telemetry.startTime() - startedAt)
                ReportState.Loaded(loaded)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Telemetry.track("run_report", "failed", Telemetry.startTime() - startedAt, Telemetry.failureCategory(e))
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

    init {
        viewModelScope.launch {
            RunTrackingState.metrics.collect(::onMetrics)
        }
        viewModelScope.launch {
            online.distinctUntilChanged().filter { it }.collect { retryAfterReconnect() }
        }
    }

    private fun retryAfterReconnect() {
        when {
            recordState is RunRecordState.Failed -> retryRecord()
            reportState == ReportState.Failed -> loadReport()
        }
    }

    private companion object {
        const val METERS_PER_KILOMETER = 1000.0
        const val MAX_COACHED_KILOMETERS = 60
        const val HILL_LOOKAHEAD_METERS = 2000
        const val MAX_UPCOMING_HILLS = 5
        const val SNAPSHOT_INTERVAL_MS = 10_000L
        const val MAX_SNAPSHOT_AGE_MS = 24 * 60 * 60 * 1000L
    }
}

private const val RETURN_ROUTE_RETRIES = 2
private const val RETURN_ROUTE_RETRY_DELAY_MS = 1_500L
