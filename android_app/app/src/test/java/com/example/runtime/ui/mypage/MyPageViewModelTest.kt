package com.example.runtime.ui.mypage

import com.example.runtime.R
import com.example.runtime.data.remote.ApiResponse
import com.example.runtime.data.remote.ProfileData
import com.example.runtime.data.remote.ProfileUpdateRequest
import com.example.runtime.data.remote.RenameRouteRequest
import com.example.runtime.data.remote.RenamedRoute
import com.example.runtime.data.remote.RunHistoryItem
import com.example.runtime.data.remote.SavedRoute
import com.example.runtime.data.repository.CommunityRepository
import com.example.runtime.data.repository.RouteRepository
import com.example.runtime.data.repository.UserRepository
import com.example.runtime.testing.FakeApiService
import com.example.runtime.testing.MainDispatcherRule
import com.example.runtime.ui.common.UiText
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
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
    private val viewModel by lazy { MyPageViewModel(RouteRepository(api), UserRepository(api), CommunityRepository(api)) }

    @Test
    fun refresh_loadsProfileAndRoutes() {
        viewModel.refresh()

        assertEquals("runner", viewModel.username)
        assertEquals("runner@test.com", viewModel.email)
        assertEquals(listOf(1, 2, 3), viewModel.routes.map { it.id })
    }

    @Test
    fun refresh_loadsTheProfileAndAvatarOnce() {
        viewModel.refresh()
        viewModel.refresh()

        assertEquals("Yewon", viewModel.displayName)
        assertEquals("Easy runs", viewModel.statusMessage)
        assertEquals(listOf(1, 2, 3), viewModel.avatar?.map { it.toInt() })
        assertEquals(listOf("api/v1/users/1/avatar/?v=1"), api.downloads)
    }

    @Test
    fun saveProfile_trimsAndShowsTheSavedValues() {
        viewModel.refresh()
        var saved = false

        viewModel.saveProfile("  Dana ", " Sub-30 5k ") { saved = true }

        assertTrue(saved)
        assertEquals(listOf(ProfileUpdateRequest("Dana", "Sub-30 5k")), api.profileUpdates)
        assertEquals("Dana", viewModel.displayName)
        assertEquals(ProfileEditState.Idle, viewModel.profileState)
    }

    @Test
    fun avatar_uploadUsesTheNewPhotoAndRemoveClearsIt() {
        viewModel.refresh()
        val photo = byteArrayOf(9, 9)

        viewModel.uploadAvatar(photo) {}

        assertEquals(1, api.uploads)
        assertTrue(viewModel.avatar.contentEquals(photo))
        assertEquals(1, api.downloads.size)
        viewModel.removeAvatar {}
        assertNull(viewModel.avatar)
    }

    @Test
    fun refresh_loadsRunHistory() {
        viewModel.refresh()

        assertEquals(listOf(11, 10), viewModel.runs.map { it.id })
        assertEquals(7, viewModel.runs.first().sharedRouteId)
    }

    @Test
    fun refresh_failingRunHistoryShowsTheError() {
        api.failRuns = true

        viewModel.refresh()

        assertEquals(UiText.Resource(R.string.error_network), viewModel.error)
        assertTrue(viewModel.runs.isEmpty())
    }

    @Test
    fun reset_clearsRunHistory() {
        viewModel.refresh()

        viewModel.reset()

        assertTrue(viewModel.runs.isEmpty())
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
        var failRuns = false
        val renameRequests = mutableListOf<RenameRouteRequest>()
        val deletedIds = mutableListOf<Int>()
        private val routes = listOf(route(1), route(2), route(3))

        var profile = ProfileData("runner", "runner@test.com", displayName = "Yewon", statusMessage = "Easy runs", avatarUrl = "api/v1/users/1/avatar/?v=1")
        val profileUpdates = mutableListOf<ProfileUpdateRequest>()
        val downloads = mutableListOf<String>()
        var uploads = 0

        override suspend fun myProfile() = ApiResponse(status = "success", data = profile)

        override suspend fun updateProfile(body: ProfileUpdateRequest): ApiResponse<ProfileData> {
            profileUpdates += body
            profile = profile.copy(displayName = body.displayName, statusMessage = body.statusMessage)
            return ApiResponse(status = "success", data = profile)
        }

        override suspend fun uploadAvatar(avatar: MultipartBody.Part): ApiResponse<ProfileData> {
            uploads++
            profile = profile.copy(avatarUrl = "api/v1/users/1/avatar/?v=2")
            return ApiResponse(status = "success", data = profile)
        }

        override suspend fun deleteAvatar(): ApiResponse<ProfileData> {
            profile = profile.copy(avatarUrl = null)
            return ApiResponse(status = "success", data = profile)
        }

        override suspend fun download(url: String): ResponseBody {
            downloads += url
            return byteArrayOf(1, 2, 3).toResponseBody()
        }

        override suspend fun savedRoutes() = routes

        override suspend fun runs(): ApiResponse<List<RunHistoryItem>> {
            if (failRuns) throw IOException("offline")
            return ApiResponse(status = "success", data = listOf(run(11, sharedRouteId = 7), run(10)))
        }

        private fun run(id: Int, sharedRouteId: Int? = null) = RunHistoryItem(
            id = id,
            routeId = id * 10,
            distanceMeters = 3010,
            durationSeconds = 1200,
            finishedAt = "2026-11-02T01:00:00Z",
            routeDistance = 3000,
            sharedRouteId = sharedRouteId,
        )

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
