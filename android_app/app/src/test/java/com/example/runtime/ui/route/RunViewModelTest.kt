package com.example.runtime.ui.route

import com.example.runtime.R
import com.example.runtime.data.remote.ApiResponse
import com.example.runtime.data.remote.SaveRouteResponse
import com.example.runtime.data.remote.SaveRouteRequest
import com.example.runtime.data.remote.GeoJsonLineString
import com.example.runtime.data.remote.RouteBody
import com.example.runtime.data.remote.GeneratedRouteData
import com.example.runtime.data.remote.GenerateRouteRequest
import com.example.runtime.data.remote.RecordRunRequest
import com.example.runtime.data.remote.NextRunRecommendation
import com.example.runtime.data.remote.RateRunRequest
import com.example.runtime.data.remote.RunPlan
import com.example.runtime.data.remote.LiveRunCoachRequest
import com.example.runtime.data.remote.LiveRunCoachResponse
import com.example.runtime.data.remote.RunRating
import com.example.runtime.data.remote.RunRecord
import com.example.runtime.data.remote.RunReport
import com.example.runtime.data.remote.ShareRouteRequest
import com.example.runtime.data.remote.SharedRoute
import com.example.runtime.data.repository.CoachRepository
import com.example.runtime.data.repository.CommunityRepository
import com.example.runtime.data.repository.RouteRepository
import com.example.runtime.testing.FakeApiService
import com.example.runtime.testing.MainDispatcherRule
import com.example.runtime.ui.common.UiText
import com.example.runtime.data.run.RunMetrics
import com.example.runtime.data.run.RunTrackingState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class RunViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val api = RunApi()
    private val online = MutableStateFlow(true)
    private var now = 0L
    private val viewModel by lazy { RunViewModel(CommunityRepository(api), CoachRepository(api), RouteRepository(api), clock = { now }, retryDelayMs = 0, online = online) }
    private val straightRoute = listOf(GeoJsonLineString(listOf(listOf(126.95, 37.46), listOf(126.96, 37.46))))

    @After
    fun resetTracking() {
        RunTrackingState.update(RunMetrics())
        RunTrackingState.resetTrail()
    }

    private fun runAt(latitude: Double, longitude: Double, timeMs: Long, isPaused: Boolean = false) {
        now = timeMs
        RunTrackingState.update(RunMetrics(isTracking = true, isPaused = isPaused, latitude = latitude, longitude = longitude))
    }

    @Test
    fun routeProgress_keepsUpdatingWithoutTheRunScreen() {
        viewModel.start(RunTarget(straightRoute, 880))

        runAt(37.46, 126.951, 0)
        runAt(37.46, 126.953, 1_000)

        assertTrue(viewModel.routeProgress.distanceAlongMeters > 200.0)
        assertFalse(viewModel.isOffRoute)
    }

    @Test
    fun voiceCues_playWithoutTheRunScreen() {
        val spoken = mutableListOf<Pair<Int, Int?>>()
        viewModel.speakCue = { messageId, distance -> spoken += messageId to distance }
        viewModel.start(RunTarget(straightRoute, 880, uphillSections = listOf(RouteUphillSection(400, 500))))

        listOf(126.951, 126.951, 126.9525, 126.9545, 126.9565, 126.9585, 126.95995).forEachIndexed { index, longitude ->
            runAt(37.46, longitude, index * 1_000L)
        }

        assertEquals(
            listOf(
                R.string.run_voice_started to null,
                R.string.run_voice_uphill_approaching to 180,
                R.string.run_voice_arrived to null,
            ),
            spoken,
        )
    }

    @Test
    fun voiceCues_stopAfterTheRunEnds() {
        val spoken = mutableListOf<Int>()
        viewModel.speakCue = { messageId, _ -> spoken += messageId }
        viewModel.start(RunTarget(straightRoute, 880))
        runAt(37.46, 126.951, 0)
        viewModel.finish(RunResult(distanceMeters = 100, elapsedSeconds = 60), isLoggedIn = false)

        listOf(126.9525, 126.9545, 126.9565, 126.9585, 126.95995).forEachIndexed { index, longitude ->
            runAt(37.46, longitude, (index + 1) * 1_000L)
        }

        assertEquals(listOf(R.string.run_voice_started), spoken)
    }

    @Test
    fun kilometerMilestoneRequestsCoachingOnceWithSplitsPlanAndNearbyHill() {
        val spoken = mutableListOf<String>()
        viewModel.speakCoachCue = { spoken += it }
        val plan = PLAN.copy(
            distanceMeters = 5000,
            paceSegments = listOf(
                com.example.runtime.data.remote.PaceSegment(0, 1000, 360, 390),
                com.example.runtime.data.remote.PaceSegment(1000, 5000, 370, 400),
            ),
        )
        viewModel.start(
            RunTarget(
                straightRoute,
                5000,
                plan = plan,
                uphillSections = listOf(RouteUphillSection(1150, 1450)),
            ),
        )
        RunTrackingState.recordSplits(1000.0, 360L)
        RunTrackingState.update(RunMetrics(isTracking = true, distanceMeters = 1000.0, elapsedSeconds = 360L))

        assertEquals(1, api.liveCoachRequests.size)
        assertEquals(listOf(360), api.liveCoachRequests.single().splitPacesSeconds)
        assertEquals(370, api.liveCoachRequests.single().nextTargetPaceMinSeconds)
        assertEquals(150, api.liveCoachRequests.single().upcomingHills.single().distanceAheadMeters)
        assertEquals(listOf("다음 구간도 차분하게 이어가세요."), spoken)

        RunTrackingState.update(RunMetrics(isTracking = true, distanceMeters = 1010.0, elapsedSeconds = 365L))
        assertEquals(1, api.liveCoachRequests.size)
    }

    @Test
    fun kilometerCoachingIsSkippedAfterTheRunFinishes() {
        viewModel.start(RunTarget(straightRoute, 3000, plan = PLAN))
        viewModel.finish(RunResult(1000, 360, splitTimes = listOf(360)), isLoggedIn = false)
        RunTrackingState.recordSplits(1000.0, 360L)
        RunTrackingState.update(RunMetrics(isTracking = true, distanceMeters = 1000.0, elapsedSeconds = 360L))

        assertTrue(api.liveCoachRequests.isEmpty())
    }

    @Test
    fun offRoute_isCountedAndTimedWhileTheRunIsActive() {
        viewModel.start(RunTarget(straightRoute, 880))
        runAt(37.46, 126.951, 0)

        runAt(37.461, 126.951, 1_000)
        runAt(37.4611, 126.951, 6_000)
        assertTrue(viewModel.isOffRoute)
        runAt(37.46, 126.952, 9_000)

        assertFalse(viewModel.isOffRoute)
        assertEquals(1 to 3L, viewModel.offRouteTotals())
    }

    @Test
    fun offRouteTotals_includeAnOpenOffRouteStretch() {
        viewModel.start(RunTarget(straightRoute, 880))
        runAt(37.46, 126.951, 0)
        runAt(37.461, 126.951, 1_000)
        runAt(37.4611, 126.951, 6_000)

        now = 8_500

        assertEquals(1 to 2L, viewModel.offRouteTotals())
    }

    @Test
    fun offRoute_isNotCountedWhilePaused() {
        viewModel.start(RunTarget(straightRoute, 880))
        runAt(37.46, 126.951, 0)
        runAt(37.461, 126.951, 1_000, isPaused = true)
        runAt(37.4611, 126.951, 6_000, isPaused = true)

        assertFalse(viewModel.isOffRoute)
        assertEquals(0 to 0L, viewModel.offRouteTotals())
    }

    @Test
    fun start_keepsTheActiveRun() {
        viewModel.start(RunTarget(straightRoute, 880, routeId = 1))
        runAt(37.46, 126.951, 0)

        assertFalse(viewModel.start(RunTarget(emptyList(), 3000, routeId = 2)))
        assertEquals(1, viewModel.target?.routeId)
        assertTrue(viewModel.isRunActive)
    }

    @Test
    fun start_replacesATargetThatWasNotStarted() {
        viewModel.start(RunTarget(straightRoute, 880, routeId = 1))

        assertTrue(viewModel.start(RunTarget(emptyList(), 3000, routeId = 2)))
        assertEquals(2, viewModel.target?.routeId)
    }

    @Test
    fun start_resetsOffRouteTotals() {
        viewModel.start(RunTarget(straightRoute, 880))
        runAt(37.46, 126.951, 0)
        runAt(37.461, 126.951, 1_000)
        runAt(37.4611, 126.951, 6_000)
        viewModel.finish(RunResult(500, 300), isLoggedIn = false)
        RunTrackingState.update(RunMetrics())

        assertTrue(viewModel.start(RunTarget(straightRoute, 880)))

        assertEquals(0 to 0L, viewModel.offRouteTotals())
        assertFalse(viewModel.isOffRoute)
    }

    @Test
    fun recordRun_usesTheTemporaryRouteUntilItIsSaved() {
        viewModel.start(RunTarget(emptyList(), 3000, temporaryRouteId = 7))

        viewModel.finish(RunResult(3010, 1200), isLoggedIn = true)

        assertEquals(RecordRunRequest(temporaryRouteId = 7, distanceMeters = 3010, durationSeconds = 1200), api.runs.single())
        assertEquals(RunRecordState.Saved(routeId = 42, sharedRouteId = null), viewModel.recordState)
    }

    @Test
    fun recordRun_prefersTheSavedRoute() {
        viewModel.start(RunTarget(emptyList(), 3000, routeId = 42, temporaryRouteId = 7))

        viewModel.finish(RunResult(3010, 1200), isLoggedIn = true)

        assertEquals(42, api.runs.single().routeId)
        assertNull(api.runs.single().temporaryRouteId)
    }

    @Test
    fun recordRun_sendsTheGroup() {
        viewModel.start(RunTarget(emptyList(), 3000, routeId = 42, groupId = 5))

        viewModel.finish(RunResult(3010, 1200), isLoggedIn = true)

        assertEquals(RecordRunRequest(routeId = 42, distanceMeters = 3010, durationSeconds = 1200, groupId = 5), api.runs.single())
    }

    @Test
    fun recordRun_isSentOnlyOnce() {
        viewModel.start(RunTarget(emptyList(), 3000, routeId = 42))

        viewModel.finish(RunResult(3010, 1200), isLoggedIn = true)
        viewModel.finish(RunResult(3010, 1200), isLoggedIn = true)

        assertEquals(1, api.runs.size)
    }

    @Test
    fun recordRun_failureCanBeRetried() {
        viewModel.start(RunTarget(emptyList(), 3000, routeId = 42))
        api.failRun = true

        viewModel.finish(RunResult(3010, 1200), isLoggedIn = true)
        assertEquals(RunRecordState.Failed(UiText.Resource(R.string.error_network)), viewModel.recordState)

        api.failRun = false
        viewModel.retryRecord()
        assertEquals(RunRecordState.Saved(routeId = 42, sharedRouteId = null), viewModel.recordState)
        assertEquals(2, api.runs.size)
    }

    @Test
    fun share_postsTrimmedTextAndRemembersTheSharedRoute() {
        viewModel.start(RunTarget(emptyList(), 3000, routeId = 42))
        viewModel.finish(RunResult(3010, 1200), isLoggedIn = true)
        var sharedId: Int? = null

        viewModel.share("  Morning loop  ", "  Flat  ") { sharedId = it }

        assertEquals(ShareRouteRequest(42, "Morning loop", "Flat"), api.shares.single())
        assertEquals(5, sharedId)
        assertEquals(RunRecordState.Saved(routeId = 42, sharedRouteId = 5), viewModel.recordState)
        assertEquals(ShareState.Idle, viewModel.shareState)
    }

    @Test
    fun share_needsARecordedRunAndATitle() {
        viewModel.start(RunTarget(emptyList(), 3000, routeId = 42))

        viewModel.share("Morning loop", "") {}
        viewModel.finish(RunResult(3010, 1200), isLoggedIn = true)
        viewModel.share("   ", "") {}

        assertEquals(0, api.shares.size)
    }

    @Test
    fun share_failureShowsTheError() {
        viewModel.start(RunTarget(emptyList(), 3000, routeId = 42))
        viewModel.finish(RunResult(3010, 1200), isLoggedIn = true)
        api.failShare = true

        viewModel.share("Morning loop", "") {}

        assertEquals(ShareState.Failed(UiText.Resource(R.string.error_network)), viewModel.shareState)
    }

    @Test
    fun finish_sendsThePlanSplitsAndOffRouteTime() {
        viewModel.start(RunTarget(emptyList(), 3000, routeId = 42, plan = PLAN))

        viewModel.finish(
            RunResult(3010, 1200, splitTimes = listOf(390, 790, 1180), offRouteCount = 1, offRouteSeconds = 40),
            isLoggedIn = true,
        )

        val request = api.runs.single()
        assertEquals(PLAN, request.plan)
        assertEquals(listOf(390L, 400L, 390L), request.splits)
        assertEquals(1, request.offRouteCount)
        assertEquals(40L, request.offRouteSeconds)
    }

    @Test
    fun finish_loadsTheReportOfTheSavedRun() {
        viewModel.start(RunTarget(emptyList(), 3000, routeId = 42))

        viewModel.finish(RunResult(3010, 1200), isLoggedIn = true)

        assertEquals(listOf(1), api.reportRequests)
        assertEquals(ReportState.Loaded(REPORT), viewModel.reportState)
    }

    @Test
    fun finish_asAGuestKeepsTheResultWithoutSaving() {
        viewModel.start(RunTarget(emptyList(), 3000, routeId = 42))

        viewModel.finish(RunResult(3010, 1200), isLoggedIn = false)

        assertEquals(RunResult(3010, 1200), viewModel.result)
        assertEquals(0, api.runs.size)
        assertEquals(ReportState.Idle, viewModel.reportState)
    }

    @Test
    fun report_failureCanBeRetried() {
        viewModel.start(RunTarget(emptyList(), 3000, routeId = 42))
        api.failReport = true

        viewModel.finish(RunResult(3010, 1200), isLoggedIn = true)
        assertEquals(ReportState.Failed, viewModel.reportState)

        api.failReport = false
        viewModel.loadReport()
        assertEquals(ReportState.Loaded(REPORT), viewModel.reportState)
    }

    @Test
    fun rateRun_savesTheStarsForTheSavedRun() {
        viewModel.start(RunTarget(emptyList(), 3000, routeId = 42))
        viewModel.finish(RunResult(3010, 1200), isLoggedIn = true)

        viewModel.rateRun(4)

        assertEquals(listOf(1 to 4), api.ratings)
        assertEquals(4, viewModel.runRating)
    }

    @Test
    fun rateRun_failureRestoresThePreviousStars() {
        viewModel.start(RunTarget(emptyList(), 3000, routeId = 42))
        viewModel.finish(RunResult(3010, 1200), isLoggedIn = true)
        viewModel.rateRun(5)
        api.failRating = true

        viewModel.rateRun(2)

        assertEquals(5, viewModel.runRating)
    }

    @Test
    fun freeRun_sendsThePathInsteadOfARoute() {
        viewModel.start(RunTarget(emptyList(), 0, routeId = 42, isFree = true))

        viewModel.finish(RunResult(2010, 700, path = listOf(listOf(127.0, 37.5), listOf(127.0, 37.51))), isLoggedIn = true)

        val request = api.runs.single()
        assertNull(request.routeId)
        assertNull(request.temporaryRouteId)
        assertEquals(2, request.path?.size)
    }

    @Test
    fun guidedRun_doesNotSendAPath() {
        viewModel.start(RunTarget(emptyList(), 3000, routeId = 42))

        viewModel.finish(RunResult(3010, 1200, path = listOf(listOf(127.0, 37.5))), isLoggedIn = true)

        assertNull(api.runs.single().path)
    }

    @Test
    fun finish_savesTheRunOnceTheConnectionIsBack() {
        api.failRun = true
        viewModel.start(RunTarget(straightRoute, 880, routeId = 42))
        viewModel.finish(RunResult(1000, 300), isLoggedIn = true)
        assertTrue(viewModel.recordState is RunRecordState.Failed)

        api.failRun = false
        online.value = false
        online.value = true

        assertTrue(viewModel.recordState is RunRecordState.Saved)
        assertEquals(2, api.runs.size)
    }

    @Test
    fun reconnecting_doesNotSaveTheRunTwice() {
        viewModel.start(RunTarget(straightRoute, 880, routeId = 42))
        viewModel.finish(RunResult(1000, 300), isLoggedIn = true)

        online.value = false
        online.value = true

        assertEquals(1, api.runs.size)
    }

    @Test
    fun makeReturnRoute_guidesBackToTheStart() {
        viewModel.start(RunTarget(emptyList(), 0, isFree = true))

        viewModel.makeReturnRoute(RouteCoordinate(37.51, 127.0), RouteCoordinate(37.5, 127.0), 2000, "ko")

        val request = api.generateRequests.single()
        assertEquals(listOf(127.0, 37.51), request.startingPoint.coordinates)
        assertEquals(listOf(127.0, 37.5), request.endPoint?.coordinates)
        assertEquals(2000, request.distance)
        assertEquals(1, viewModel.target?.lines?.size)
        assertEquals(true, viewModel.target?.isFree)
    }

    @Test
    fun makeReturnRoute_retriesDroppedConnections() {
        api.generateFailures = 2
        viewModel.start(RunTarget(emptyList(), 0, isFree = true))

        viewModel.makeReturnRoute(RouteCoordinate(37.51, 127.0), RouteCoordinate(37.5, 127.0), 2000, "ko")

        assertEquals(3, api.generateRequests.size)
        assertEquals(1, viewModel.target?.lines?.size)
        assertNull(viewModel.returnRouteError)
    }

    @Test
    fun makeReturnRoute_showsAnErrorAfterTheRetries() {
        api.generateFailures = 3
        viewModel.start(RunTarget(emptyList(), 0, isFree = true))

        viewModel.makeReturnRoute(RouteCoordinate(37.51, 127.0), RouteCoordinate(37.5, 127.0), 2000, "ko")

        assertEquals(3, api.generateRequests.size)
        assertEquals(UiText.Resource(R.string.error_network), viewModel.returnRouteError)
        assertFalse(viewModel.isMakingReturnRoute)
    }

    @Test
    fun saveFreeRoute_savesTheRecordedRoute() {
        viewModel.start(RunTarget(emptyList(), 0, isFree = true))
        viewModel.finish(RunResult(2010, 700, path = listOf(listOf(127.0, 37.5), listOf(127.0, 37.51))), isLoggedIn = true)

        viewModel.saveFreeRoute()

        assertEquals(listOf(42), api.savedRouteIds)
        assertEquals(SaveRouteState.Saved, viewModel.saveRouteState)
    }

    private class RunApi : FakeApiService() {
        var failRun = false
        var failShare = false
        val runs = mutableListOf<RecordRunRequest>()
        val liveCoachRequests = mutableListOf<LiveRunCoachRequest>()
        val shares = mutableListOf<ShareRouteRequest>()

        override suspend fun recordRun(body: RecordRunRequest): ApiResponse<RunRecord> {
            runs += body
            if (failRun) throw IOException("offline")
            return ApiResponse(status = "success", data = RunRecord(id = runs.size, routeId = 42))
        }

        var failReport = false
        var failRating = false
        val generateRequests = mutableListOf<GenerateRouteRequest>()
        val savedRouteIds = mutableListOf<Int>()

        var generateFailures = 0

        override suspend fun generateRoute(body: GenerateRouteRequest): ApiResponse<GeneratedRouteData> {
            generateRequests += body
            if (generateFailures > 0) {
                generateFailures--
                throw IOException("unexpected end of stream")
            }
            return ApiResponse(
                status = "success",
                data = GeneratedRouteData(
                    temporaryRouteId = 9,
                    route = RouteBody(
                        distance = body.distance,
                        briefing = "",
                        route = listOf(GeoJsonLineString(listOf(listOf(127.0, 37.51), listOf(127.0, 37.5)))),
                    ),
                ),
            )
        }

        override suspend fun saveRoute(body: SaveRouteRequest): SaveRouteResponse {
            savedRouteIds += requireNotNull(body.routeId)
            return SaveRouteResponse(status = "success", routeId = body.routeId)
        }
        val ratings = mutableListOf<Pair<Int, Int>>()

        override suspend fun rateRun(runId: Int, body: RateRunRequest): ApiResponse<RunRating> {
            if (failRating) throw IOException("offline")
            ratings += runId to body.rating
            return ApiResponse(status = "success", data = RunRating(runId, body.rating))
        }
        val reportRequests = mutableListOf<Int>()

        override suspend fun runReport(runId: Int): ApiResponse<RunReport> {
            reportRequests += runId
            if (failReport) throw IOException("offline")
            return ApiResponse(status = "success", data = REPORT)
        }

        override suspend fun liveRunCoaching(body: LiveRunCoachRequest): ApiResponse<LiveRunCoachResponse> {
            liveCoachRequests += body
            return ApiResponse(status = "success", data = LiveRunCoachResponse("다음 구간도 차분하게 이어가세요."))
        }

        override suspend fun shareRoute(body: ShareRouteRequest): ApiResponse<SharedRoute> {
            shares += body
            if (failShare) throw IOException("offline")
            return ApiResponse(
                status = "success",
                data = SharedRoute(
                    id = 5,
                    routeId = requireNotNull(body.routeId),
                    title = body.title,
                    sharedBy = "runner",
                    sharedAt = "2026-11-01",
                    distance = 3000,
                ),
            )
        }
    }

    private companion object {
        val PLAN = RunPlan("recovery", 3000, 375, 405)
        val REPORT = RunReport("Nice and steady.", NextRunRecommendation(1, "Rest a day.", RunPlan("easy", 4000, 360, 390)))
    }
}
