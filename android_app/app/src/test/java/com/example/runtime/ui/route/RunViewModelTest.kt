package com.example.runtime.ui.route

import com.example.runtime.R
import com.example.runtime.data.remote.ApiResponse
import com.example.runtime.data.remote.RecordRunRequest
import com.example.runtime.data.remote.NextRunRecommendation
import com.example.runtime.data.remote.RunPlan
import com.example.runtime.data.remote.RunRecord
import com.example.runtime.data.remote.RunReport
import com.example.runtime.data.remote.ShareRouteRequest
import com.example.runtime.data.remote.SharedRoute
import com.example.runtime.data.repository.CoachRepository
import com.example.runtime.data.repository.CommunityRepository
import com.example.runtime.testing.FakeApiService
import com.example.runtime.testing.MainDispatcherRule
import com.example.runtime.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class RunViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val api = RunApi()
    private val viewModel by lazy { RunViewModel(CommunityRepository(api), CoachRepository(api)) }

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

    private class RunApi : FakeApiService() {
        var failRun = false
        var failShare = false
        val runs = mutableListOf<RecordRunRequest>()
        val shares = mutableListOf<ShareRouteRequest>()

        override suspend fun recordRun(body: RecordRunRequest): ApiResponse<RunRecord> {
            runs += body
            if (failRun) throw IOException("offline")
            return ApiResponse(status = "success", data = RunRecord(id = runs.size, routeId = 42))
        }

        var failReport = false
        val reportRequests = mutableListOf<Int>()

        override suspend fun runReport(runId: Int): ApiResponse<RunReport> {
            reportRequests += runId
            if (failReport) throw IOException("offline")
            return ApiResponse(status = "success", data = REPORT)
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
