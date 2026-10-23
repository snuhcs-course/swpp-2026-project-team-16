package com.example.runtime.ui.route

import com.example.runtime.R
import com.example.runtime.data.remote.ApiResponse
import com.example.runtime.data.remote.ElevationRequest
import com.example.runtime.data.remote.ElevationResponse
import com.example.runtime.data.remote.GenerateRouteRequest
import com.example.runtime.data.remote.GeneratedRouteData
import com.example.runtime.data.remote.GeoJsonLineString
import com.example.runtime.data.remote.OpenElevationApiService
import com.example.runtime.data.remote.RouteBody
import com.example.runtime.data.remote.RouteOption
import com.example.runtime.data.remote.SaveRouteRequest
import com.example.runtime.data.remote.SaveRouteResponse
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
    private val viewModel by lazy { RouteViewModel(RouteRepository(api, NoElevation)) }

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
        val requests = mutableListOf<GenerateRouteRequest>()
        val savedIds = mutableListOf<Int>()

        override suspend fun generateRoute(body: GenerateRouteRequest): ApiResponse<GeneratedRouteData> {
            requests += body
            if (failGenerate) throw IOException("offline")
            return ApiResponse(status = "success", data = response)
        }

        override suspend fun saveRoute(body: SaveRouteRequest): SaveRouteResponse {
            if (failSave) throw IOException("offline")
            savedIds += requireNotNull(body.temporaryRouteId)
            return SaveRouteResponse(status = "success")
        }
    }

    private object NoElevation : OpenElevationApiService {
        override suspend fun getElevation(request: ElevationRequest): ElevationResponse = throw IOException("offline")
    }
}
