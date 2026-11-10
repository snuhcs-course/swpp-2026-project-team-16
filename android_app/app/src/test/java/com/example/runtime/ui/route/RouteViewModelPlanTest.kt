package com.example.runtime.ui.route

import com.example.runtime.data.remote.ApiResponse
import com.example.runtime.data.remote.RunPlan
import com.example.runtime.data.remote.RunPlanRequest
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
        viewModel.onGoalTextChange("  천천히 회복 러닝  ")

        viewModel.requestPlan()

        assertEquals(RunPlanRequest("천천히 회복 러닝", 3000), api.requests.single())
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
