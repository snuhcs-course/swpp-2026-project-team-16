package com.example.runtime.ui.mypage

import com.example.runtime.R
import com.example.runtime.data.remote.ApiResponse
import com.example.runtime.data.remote.ProfileData
import com.example.runtime.data.remote.RenameRouteRequest
import com.example.runtime.data.remote.RenamedRoute
import com.example.runtime.data.remote.SavedRoute
import com.example.runtime.data.repository.RouteRepository
import com.example.runtime.data.repository.UserRepository
import com.example.runtime.testing.FakeApiService
import com.example.runtime.testing.MainDispatcherRule
import com.example.runtime.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class MyPageViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val api = MyPageApi()
    private val viewModel by lazy { MyPageViewModel(RouteRepository(api), UserRepository(api)) }

    @Test
    fun refresh_loadsProfileAndRoutes() {
        viewModel.refresh()

        assertEquals("runner", viewModel.username)
        assertEquals("runner@test.com", viewModel.email)
        assertEquals(listOf(1, 2, 3), viewModel.routes.map { it.id })
    }

    @Test
    fun rename_updatesListAndDetailWithTheSavedName() {
        viewModel.refresh()
        viewModel.loadDetail(2)
        var renamed = false

        viewModel.rename(2, "  Morning Run  ") { renamed = true }

        assertTrue(renamed)
        assertEquals("Morning Run", api.renameRequests.single().name)
        assertEquals("Morning Run", viewModel.routes.first { it.id == 2 }.name)
        assertEquals("Morning Run", viewModel.detail?.name)
    }

    @Test
    fun rename_ignoresBlankNames() {
        viewModel.refresh()

        viewModel.rename(2, "   ") { }

        assertTrue(api.renameRequests.isEmpty())
    }

    @Test
    fun deleteFromList_removesTheRouteRightAway() {
        viewModel.refresh()

        viewModel.deleteFromList(2)

        assertEquals(listOf(1, 3), viewModel.routes.map { it.id })
        assertEquals(listOf(2), api.deletedIds)
        assertNull(viewModel.error)
    }

    @Test
    fun deleteFromList_putsTheRouteBackWhenDeleteFails() {
        viewModel.refresh()
        api.failDelete = true

        viewModel.deleteFromList(2)

        assertEquals(listOf(1, 2, 3), viewModel.routes.map { it.id })
        assertEquals(UiText.Resource(R.string.error_network), viewModel.error)
    }

    @Test
    fun deleteFromList_ignoresUnknownRoutes() {
        viewModel.refresh()

        viewModel.deleteFromList(99)

        assertEquals(listOf(1, 2, 3), viewModel.routes.map { it.id })
        assertTrue(api.deletedIds.isEmpty())
    }

    @Test
    fun reset_clearsTheUserData() {
        viewModel.refresh()
        viewModel.loadDetail(1)

        viewModel.reset()

        assertNull(viewModel.username)
        assertNull(viewModel.email)
        assertTrue(viewModel.routes.isEmpty())
        assertNull(viewModel.detail)
    }

    private class MyPageApi : FakeApiService() {
        var failDelete = false
        val renameRequests = mutableListOf<RenameRouteRequest>()
        val deletedIds = mutableListOf<Int>()
        private val routes = listOf(route(1), route(2), route(3))

        override suspend fun myProfile() =
            ApiResponse(status = "success", data = ProfileData("runner", "runner@test.com"))

        override suspend fun savedRoutes() = routes

        override suspend fun savedRoute(routeId: Int) =
            ApiResponse(status = "success", data = routes.first { it.id == routeId })

        override suspend fun renameSavedRoute(routeId: Int, body: RenameRouteRequest): ApiResponse<RenamedRoute> {
            renameRequests += body
            return ApiResponse(status = "success", data = RenamedRoute(routeId, body.name))
        }

        override suspend fun deleteSavedRoute(routeId: Int): ApiResponse<Unit> {
            if (failDelete) throw IOException("offline")
            deletedIds += routeId
            return ApiResponse(status = "success")
        }

        private fun route(id: Int) = SavedRoute(
            id = id,
            distance = 3000,
            briefing = "Route $id",
            route = emptyList(),
            savedAt = "2026-10-21T00:00:00Z",
        )
    }
}
