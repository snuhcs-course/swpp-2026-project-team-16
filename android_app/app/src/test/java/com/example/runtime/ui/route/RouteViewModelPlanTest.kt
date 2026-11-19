package com.example.runtime.ui.route

import com.example.runtime.data.remote.ApiResponse
import com.example.runtime.data.remote.RunPlan
import com.example.runtime.data.remote.RunPlanRequest
import com.example.runtime.data.run.PaceAlertSettings
import com.example.runtime.data.repository.CoachRepository
import com.example.runtime.data.repository.CommunityRepository
import com.example.runtime.data.repository.RouteRepository
import com.example.runtime.testing.FakeApiService
import com.example.runtime.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class RouteViewModelPlanTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val api = PlanApi()
    private val viewModel by lazy { RouteViewModel(RouteRepository(api), CommunityRepository(api), CoachRepository(api)) }

    @Test
    fun requestPlan_sendsTheGoalAndCurrentDistanceAndAppliesThePlan() {
        viewModel.distanceInput = "3"
        viewModel.onPaceMakerEnabledChange(true)
        viewModel.onGoalTextChange("  천천히 회복 러닝  ")
        viewModel.updatePaceRange(360f..420f)
        viewModel.onAlertOnSlowChange(true)

        viewModel.requestPlan()

        assertEquals(RunPlanRequest("천천히 회복 러닝", 3000, 360, 420, true, false), api.requests.single())
        assertEquals(PLAN, viewModel.runPlan)
        assertEquals("5.5", viewModel.distanceInput)
        assertTrue(RoutePreference.FLAT in viewModel.preferences)
        assertEquals(PlanState.Idle, viewModel.planState)
    }

    @Test
    fun applyPlan_replacesAConflictingPreference() {
        viewModel.togglePreference(RoutePreference.HILLS)

        viewModel.applyPlan(PLAN.copy(distanceMeters = 6000))

        assertEquals("6", viewModel.distanceInput)
        assertEquals(setOf(RoutePreference.FLAT), viewModel.preferences)
    }

    @Test
    fun requestPlan_failureKeepsTheOldInputs() {
        api.fail = true
        viewModel.distanceInput = "3"
        viewModel.onPaceMakerEnabledChange(true)
        viewModel.onGoalTextChange("천천히")

        viewModel.requestPlan()

        assertTrue(viewModel.planState is PlanState.Failed)
        assertNull(viewModel.runPlan)
        assertEquals("3", viewModel.distanceInput)
    }

    @Test
    fun clearPlan_andResetForgetThePlan() {
        viewModel.applyPlan(PLAN)
        viewModel.clearPlan()
        assertNull(viewModel.runPlan)

        viewModel.onGoalTextChange("천천히")
        viewModel.applyPlan(PLAN)
        viewModel.reset()
        assertNull(viewModel.runPlan)
        assertEquals("", viewModel.goalText)
    }

    @Test
    fun freeRunCanUseTheSelectedPaceAlertSettingsWithoutCreatingARoutePlan() {
        viewModel.onPaceMakerEnabledChange(true)
        viewModel.updatePaceRange(330f..410f)
        viewModel.onAlertOnSlowChange(true)

        assertEquals(
            PaceAlertSettings(330, 410, alertOnSlow = true, alertOnFast = false),
            viewModel.paceAlertSettingsForFreeRun(),
        )
        assertNull(viewModel.runPlan)
    }

    @Test
    fun paceRangeSnapsToTenSecondStepsAcrossTheTwoToTenMinuteRange() {
        viewModel.updatePaceRange(123f..596f)

        assertEquals(120, viewModel.paceMinSeconds)
        assertEquals(600, viewModel.paceMaxSeconds)
    }

    @Test
    fun addsASectionInTheFirstOpenPartOfThePlan() {
        viewModel.applyPlan(
            PLAN.copy(
                distanceMeters = 5000,
                paceSegments = listOf(com.example.runtime.data.remote.PaceSegment(1000, 2000, 330, 390)),
            ),
        )

        assertTrue(viewModel.addPaceSegment())
        assertEquals(
            listOf(0 to 1000, 1000 to 2000),
            viewModel.runPlan?.paceSegments?.map { it.startMeters to it.endMeters },
        )
    }

    private class PlanApi : FakeApiService() {
        var fail = false
        val requests = mutableListOf<RunPlanRequest>()

        override suspend fun planRun(body: RunPlanRequest): ApiResponse<RunPlan> {
            requests += body
            if (fail) throw IOException("offline")
            return ApiResponse(status = "success", data = PLAN)
        }
    }

    private companion object {
        val PLAN = RunPlan("recovery", 5500, 395, 425, routePreferences = listOf("flat"))
    }
}
