package com.example.runtime.ui.route

import com.example.runtime.R
import com.example.runtime.data.remote.ApiResponse
import com.example.runtime.data.remote.RecordRunRequest
import com.example.runtime.data.remote.RunRecord
import com.example.runtime.data.remote.ShareRouteRequest
import com.example.runtime.data.remote.SharedRoute
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
    private val viewModel by lazy { RunViewModel(CommunityRepository(api)) }

    @Test
    fun recordRun_usesTheTemporaryRouteUntilItIsSaved() {
        viewModel.start(RunTarget(emptyList(), 3000, temporaryRouteId = 7))

        viewModel.recordRun(3010, 1200)

        assertEquals(RecordRunRequest(temporaryRouteId = 7, distanceMeters = 3010, durationSeconds = 1200), api.runs.single())
        assertEquals(RunRecordState.Saved(routeId = 42, sharedRouteId = null), viewModel.recordState)
    }

    @Test
    fun recordRun_prefersTheSavedRoute() {
        viewModel.start(RunTarget(emptyList(), 3000, routeId = 42, temporaryRouteId = 7))

        viewModel.recordRun(3010, 1200)

        assertEquals(42, api.runs.single().routeId)
        assertNull(api.runs.single().temporaryRouteId)
    }

    @Test
    fun recordRun_sendsTheGroup() {
        viewModel.start(RunTarget(emptyList(), 3000, routeId = 42, groupId = 5))

        viewModel.recordRun(3010, 1200)

        assertEquals(RecordRunRequest(routeId = 42, distanceMeters = 3010, durationSeconds = 1200, groupId = 5), api.runs.single())
    }

    @Test
    fun recordRun_isSentOnlyOnce() {
        viewModel.start(RunTarget(emptyList(), 3000, routeId = 42))

        viewModel.recordRun(3010, 1200)
        viewModel.recordRun(3010, 1200)

        assertEquals(1, api.runs.size)
    }

    @Test
    fun recordRun_failureCanBeRetried() {
        viewModel.start(RunTarget(emptyList(), 3000, routeId = 42))
        api.failRun = true

        viewModel.recordRun(3010, 1200)
        assertEquals(RunRecordState.Failed(UiText.Resource(R.string.error_network)), viewModel.recordState)

        api.failRun = false
        viewModel.retryRecord()
        assertEquals(RunRecordState.Saved(routeId = 42, sharedRouteId = null), viewModel.recordState)
        assertEquals(2, api.runs.size)
    }

    @Test
    fun share_postsTrimmedTextAndRemembersTheSharedRoute() {
        viewModel.start(RunTarget(emptyList(), 3000, routeId = 42))
        viewModel.recordRun(3010, 1200)
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
        viewModel.recordRun(3010, 1200)
        viewModel.share("   ", "") {}

        assertEquals(0, api.shares.size)
    }

    @Test
    fun share_failureShowsTheError() {
        viewModel.start(RunTarget(emptyList(), 3000, routeId = 42))
        viewModel.recordRun(3010, 1200)
        api.failShare = true

        viewModel.share("Morning loop", "") {}

        assertEquals(ShareState.Failed(UiText.Resource(R.string.error_network)), viewModel.shareState)
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

        override suspend fun shareRoute(body: ShareRouteRequest): ApiResponse<SharedRoute> {
            shares += body
            if (failShare) throw IOException("offline")
            return ApiResponse(
                status = "success",
                data = SharedRoute(
                    id = 5,
                    routeId = body.routeId,
                    title = body.title,
                    sharedBy = "runner",
                    sharedAt = "2026-11-01",
                    distance = 3000,
                ),
            )
        }
    }
}
