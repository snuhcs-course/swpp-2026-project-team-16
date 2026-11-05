package com.example.runtime.ui.route

import com.example.runtime.R
import com.example.runtime.data.remote.ApiResponse
import com.example.runtime.data.remote.ElevationData
import com.example.runtime.data.remote.ElevationLookupRequest
import com.example.runtime.data.remote.GenerateRouteRequest
import com.example.runtime.data.remote.GeneratedRouteData
import com.example.runtime.data.remote.GeoJsonLineString
import com.example.runtime.data.remote.GeoJsonPoint
import com.example.runtime.data.remote.OpenElevationApiService
import com.example.runtime.data.remote.Place
import com.example.runtime.data.remote.RouteBody
import com.example.runtime.data.remote.RouteOption
import com.example.runtime.data.remote.SaveRouteRequest
import com.example.runtime.data.remote.SaveRouteResponse
import com.example.runtime.data.remote.ShareRouteRequest
import com.example.runtime.data.remote.SharedRoute
import com.example.runtime.data.repository.CommunityRepository
import com.example.runtime.data.repository.RouteRepository
import com.example.runtime.testing.FakeApiService
import com.example.runtime.testing.MainDispatcherRule
import com.example.runtime.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class RouteViewModelOptionsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val api = RouteApi()
    private val viewModel by lazy { RouteViewModel(RouteRepository(api), CommunityRepository(api)) }

    @Test
    fun share_sendsTheGeneratedRouteAndRemembersTheSharedId() {
        api.response = generated(option(1, 3010), option(2, 3062))
        generate()
        var sharedId: Int? = null

        viewModel.share("  Morning loop  ", " flat ") { sharedId = it }

        val request = api.shareRequests.single()
        assertEquals(1, request.temporaryRouteId)
        assertNull(request.routeId)
        assertEquals("Morning loop", request.title)
        assertEquals("flat", request.description)
        assertEquals(41, sharedId)
        assertEquals(41, viewModel.sharedRouteId)
        assertEquals(ShareState.Idle, viewModel.shareState)

        viewModel.selectOption(1)
        assertNull(viewModel.sharedRouteId)
    }

    @Test
    fun share_usesTheSavedRouteIdWhenAlreadySaved() {
        api.response = generated(option(1, 3010))
        api.savedRouteId = 9
        generate()
        viewModel.save()

        viewModel.share("Loop", "") {}

        val request = api.shareRequests.single()
        assertEquals(9, request.routeId)
        assertNull(request.temporaryRouteId)
    }

    @Test
    fun share_ignoresBlankTitlesAndRepeatShares() {
        api.response = generated(option(1, 3010))
        generate()

        viewModel.share("   ", "") {}
        assertTrue(api.shareRequests.isEmpty())

        viewModel.share("Loop", "") {}
        viewModel.share("Loop", "") {}
        assertEquals(1, api.shareRequests.size)
    }

    @Test
    fun share_failureCanBeRetried() {
        api.response = generated(option(1, 3010))
        generate()
        api.failShare = true

        viewModel.share("Loop", "") {}
        assertEquals(ShareState.Failed(UiText.Resource(R.string.error_network)), viewModel.shareState)
        assertNull(viewModel.sharedRouteId)

        viewModel.clearShareError()
        assertEquals(ShareState.Idle, viewModel.shareState)
        api.failShare = false
        viewModel.share("Loop", "") {}
        assertEquals(42, viewModel.sharedRouteId)
    }

    @Test
    fun generate_showsAllOptionsAndSelectsTheFirst() {
        api.response = generated(option(1, 3010), option(2, 3062), option(3, 2600))

        assertTrue(generate())

        assertEquals(listOf(1, 2, 3), viewModel.options.map { it.temporaryRouteId })
        assertEquals(0, viewModel.selectedOption)
        assertEquals(1, viewModel.generated?.temporaryRouteId)
        assertEquals(3000, api.requests.single().distance)
    }

    @Test
    fun generate_sendsNoPreferencesByDefault() {
        api.response = generated(option(1, 3010))

        generate()

        assertEquals(emptyList<String>(), api.requests.single().preferences)
        assertEquals("", api.requests.single().preferenceText)
    }

    @Test
    fun generate_sendsSelectedPreferencesAndText() {
        api.response = generated(option(1, 3010))
        viewModel.togglePreference(RoutePreference.EXACT_DISTANCE)
        viewModel.togglePreference(RoutePreference.FEW_SIGNALS)
        viewModel.onPreferenceTextChange("  riverside please  ")

        generate()

        assertEquals(listOf("few_signals", "exact_distance"), api.requests.single().preferences)
        assertEquals("riverside please", api.requests.single().preferenceText)
    }

    @Test
    fun togglePreference_flatAndHillsReplaceEachOther() {
        viewModel.togglePreference(RoutePreference.FLAT)
        viewModel.togglePreference(RoutePreference.FEW_SIGNALS)
        viewModel.togglePreference(RoutePreference.HILLS)
        assertEquals(setOf(RoutePreference.HILLS, RoutePreference.FEW_SIGNALS), viewModel.preferences)

        viewModel.togglePreference(RoutePreference.HILLS)
        assertEquals(setOf(RoutePreference.FEW_SIGNALS), viewModel.preferences)
    }

    @Test
    fun onPreferenceTextChange_limitsTheLength() {
        viewModel.onPreferenceTextChange("a".repeat(MAX_PREFERENCE_TEXT_LENGTH + 20))

        assertEquals(MAX_PREFERENCE_TEXT_LENGTH, viewModel.preferenceText.length)
    }

    @Test
    fun generate_withoutOptions_usesTheSingleRoute() {
        api.response = GeneratedRouteData(temporaryRouteId = 7, route = body(5000))

        assertTrue(generate())

        assertEquals(listOf(7), viewModel.options.map { it.temporaryRouteId })
        assertEquals(7, viewModel.generated?.temporaryRouteId)
    }

    @Test
    fun generate_failure_reportsTheError() {
        api.failGenerate = true

        assertFalse(generate())

        assertEquals(UiText.Resource(R.string.error_network), viewModel.error)
        assertNull(viewModel.generated)
    }

    @Test
    fun selectOption_switchesRouteAndIgnoresInvalidIndex() {
        api.response = generated(option(1, 3010), option(2, 3062))
        generate()

        viewModel.selectOption(1)
        assertEquals(1, viewModel.selectedOption)
        assertEquals(2, viewModel.generated?.temporaryRouteId)

        viewModel.selectOption(5)
        assertEquals(1, viewModel.selectedOption)
        assertEquals(2, viewModel.generated?.temporaryRouteId)
    }

    @Test
    fun save_isTrackedPerOption() {
        api.response = generated(option(1, 3010), option(2, 3062))
        generate()

        viewModel.selectOption(1)
        viewModel.save()
        assertEquals(SaveState.Saved, viewModel.saveState)
        assertEquals(listOf(2), api.savedIds)

        viewModel.selectOption(0)
        assertEquals(SaveState.Idle, viewModel.saveState)

        viewModel.selectOption(1)
        viewModel.save()
        assertEquals(listOf(2), api.savedIds)
    }

    @Test
    fun save_failureCanBeRetried() {
        api.response = generated(option(1, 3010))
        generate()
        api.failSave = true

        viewModel.save()
        assertEquals(SaveState.Failed(UiText.Resource(R.string.error_network)), viewModel.saveState)

        api.failSave = false
        viewModel.save()
        assertEquals(SaveState.Saved, viewModel.saveState)
        assertEquals(listOf(1), api.savedIds)
    }

    @Test
    fun reset_clearsOptionsAndSaveState() {
        api.response = generated(option(1, 3010), option(2, 3062))
        generate()
        viewModel.selectOption(1)
        viewModel.save()

        viewModel.reset()

        assertTrue(viewModel.options.isEmpty())
        assertEquals(0, viewModel.selectedOption)
        assertNull(viewModel.generated)
        assertEquals(SaveState.Idle, viewModel.saveState)
    }

    @Test
    fun prepare_requiresAStartPoint() {
        viewModel.distanceInput = "3"

        assertFalse(viewModel.prepare("en"))
        assertEquals(UiText.Resource(R.string.error_start_point_required), viewModel.error)
    }

    @Test
    fun prepare_rejectsTypedButUnselectedEndPoint() {
        viewModel.selectPoint(viewModel.startField, "Start", 126.952, 37.46)
        viewModel.onPlaceInputChange(viewModel.endField, "G")
        viewModel.distanceInput = "3"

        assertFalse(viewModel.prepare("en"))
        assertEquals(UiText.Resource(R.string.error_end_point_not_selected), viewModel.error)
    }

    @Test
    fun prepare_rejectsDistancesOf100mOrLess() {
        viewModel.selectPoint(viewModel.startField, "Start", 126.952, 37.46)
        for (input in listOf("", "abc", "0.1", "-2")) {
            viewModel.distanceInput = input
            assertFalse(input, viewModel.prepare("en"))
            assertEquals(UiText.Resource(R.string.error_distance_min), viewModel.error)
        }
    }

    @Test
    fun selectPoint_usesTheRealPlaceNameAndKeepsThePickedPoint() {
        api.response = generated(option(1, 3010))
        api.reversePlace = Place("국립민속박물관", "서울특별시 종로구 삼청로 37", GeoJsonPoint.of(126.97, 37.58))

        assertTrue(generate())

        val start = viewModel.startField.selected!!
        assertEquals("국립민속박물관", start.name)
        assertEquals("국립민속박물관", viewModel.startField.input)
        assertEquals(GeoJsonPoint.of(126.952, 37.46), start.point)
        assertEquals("국립민속박물관", api.requests.single().startingPointName)
        assertEquals(listOf(37.46 to 126.952), api.reverseRequests)
    }

    @Test
    fun selectPoint_keepsTheFallbackNameWhenLookupFails() {
        api.response = generated(option(1, 3010))
        api.failReverse = true

        assertTrue(generate())

        assertEquals("Start", viewModel.startField.selected?.name)
        assertEquals("Start", api.requests.single().startingPointName)
        assertFalse(viewModel.startField.isSearching)
    }

    private fun generate(): Boolean {
        viewModel.selectPoint(viewModel.startField, "Start", 126.952, 37.46)
        viewModel.distanceInput = "3"
        assertTrue(viewModel.prepare("en"))
        var succeeded = false
        viewModel.generate(onSuccess = { succeeded = true }, onFailure = { succeeded = false })
        return succeeded
    }

    private fun body(distance: Int) = RouteBody(
        distance = distance,
        briefing = "Briefing for $distance m",
        route = listOf(GeoJsonLineString(listOf(listOf(126.952, 37.46), listOf(126.953, 37.461)))),
    )

    private fun option(id: Int, distance: Int) = RouteOption(id, body(distance))

    private fun generated(vararg options: RouteOption) = GeneratedRouteData(
        temporaryRouteId = options.first().temporaryRouteId,
        route = options.first().route,
        options = options.toList(),
    )

    private class RouteApi : FakeApiService() {
        var response: GeneratedRouteData? = null
        var failGenerate = false
        var failSave = false
        var savedRouteId: Int? = null
        var failShare = false
        val shareRequests = mutableListOf<ShareRouteRequest>()
        var reversePlace: Place? = null
        var failReverse = false
        val requests = mutableListOf<GenerateRouteRequest>()
        val savedIds = mutableListOf<Int>()
        val reverseRequests = mutableListOf<Pair<Double, Double>>()

        override suspend fun generateRoute(body: GenerateRouteRequest): ApiResponse<GeneratedRouteData> {
            requests += body
            if (failGenerate) throw IOException("offline")
            return ApiResponse(status = "success", data = response)
        }

        override suspend fun getElevations(body: ElevationLookupRequest): ApiResponse<ElevationData> =
            throw IOException("offline")
        override suspend fun reversePlace(latitude: Double, longitude: Double, language: String): ApiResponse<Place> {
            reverseRequests += latitude to longitude
            if (failReverse) throw IOException("offline")
            return ApiResponse(status = "success", data = reversePlace)
        }

        override suspend fun saveRoute(body: SaveRouteRequest): SaveRouteResponse {
            if (failSave) throw IOException("offline")
            savedIds += requireNotNull(body.temporaryRouteId)
            return SaveRouteResponse(status = "success", routeId = savedRouteId)
        }

        override suspend fun shareRoute(body: ShareRouteRequest): ApiResponse<SharedRoute> {
            shareRequests += body
            if (failShare) throw IOException("offline")
            val shared = SharedRoute(
                id = 40 + shareRequests.size,
                routeId = body.routeId ?: 77,
                title = body.title,
                sharedBy = "runner",
                sharedAt = "2026-11-05T00:00:00Z",
                distance = 3010,
            )
            return ApiResponse(status = "success", data = shared)
        }
    }

}
