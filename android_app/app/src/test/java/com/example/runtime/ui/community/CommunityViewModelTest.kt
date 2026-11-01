package com.example.runtime.ui.community

import com.example.runtime.R
import com.example.runtime.data.remote.ApiResponse
import com.example.runtime.data.remote.GeoJsonLineString
import com.example.runtime.data.remote.SaveRouteRequest
import com.example.runtime.data.remote.SaveRouteResponse
import com.example.runtime.data.remote.SharedRoute
import com.example.runtime.data.repository.CommunityRepository
import com.example.runtime.testing.FakeApiService
import com.example.runtime.testing.MainDispatcherRule
import com.example.runtime.ui.common.UiText
import com.example.runtime.ui.route.SaveState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class CommunityViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val api = CommunityApi()
    private val viewModel by lazy { CommunityViewModel(CommunityRepository(api)) }

    @Test
    fun refresh_loadsSharedRoutes() {
        viewModel.refresh()

        assertEquals(listOf(1, 2), viewModel.routes.map { it.id })
        assertNull(viewModel.error)
    }

    @Test
    fun refresh_failureShowsTheError() {
        api.failList = true

        viewModel.refresh()

        assertEquals(UiText.Resource(R.string.error_network), viewModel.error)
        assertTrue(viewModel.routes.isEmpty())
    }

    @Test
    fun loadDetail_replacesTheListItemWithTheFullRoute() {
        viewModel.refresh()

        viewModel.loadDetail(2)

        assertEquals(1, viewModel.detail?.route?.size)
    }

    @Test
    fun save_savesTheUnderlyingRouteOnce() {
        viewModel.refresh()
        viewModel.loadDetail(2)

        viewModel.save()
        viewModel.save()

        assertEquals(listOf(SaveRouteRequest(routeId = 20)), api.saved)
        assertEquals(SaveState.Saved, viewModel.saveState)
    }

    @Test
    fun delete_removesTheRouteFromTheList() {
        viewModel.refresh()
        viewModel.loadDetail(2)
        var deleted = false

        viewModel.delete(2) { deleted = true }

        assertTrue(deleted)
        assertEquals(listOf(1), viewModel.routes.map { it.id })
        assertNull(viewModel.detail)
    }

    private class CommunityApi : FakeApiService() {
        var failList = false
        val saved = mutableListOf<SaveRouteRequest>()

        override suspend fun sharedRoutes(): ApiResponse<List<SharedRoute>> {
            if (failList) throw IOException("offline")
            return ApiResponse(status = "success", data = listOf(shared(1), shared(2)))
        }

        override suspend fun sharedRoute(sharedRouteId: Int): ApiResponse<SharedRoute> =
            ApiResponse(
                status = "success",
                data = shared(sharedRouteId).copy(
                    route = listOf(GeoJsonLineString(listOf(listOf(127.0, 37.0), listOf(127.01, 37.01)))),
                ),
            )

        override suspend fun saveRoute(body: SaveRouteRequest): SaveRouteResponse {
            saved += body
            return SaveRouteResponse(status = "success")
        }

        override suspend fun deleteSharedRoute(sharedRouteId: Int): ApiResponse<Unit> =
            ApiResponse(status = "success")

        private fun shared(id: Int) = SharedRoute(
            id = id,
            routeId = id * 10,
            title = "Route $id",
            sharedBy = "runner",
            sharedAt = "2026-11-01",
            distance = 3000,
        )
    }
}
