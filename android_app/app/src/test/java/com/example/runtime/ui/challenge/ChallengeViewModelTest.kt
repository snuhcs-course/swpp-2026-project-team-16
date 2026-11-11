package com.example.runtime.ui.challenge

import com.example.runtime.data.remote.ApiResponse
import com.example.runtime.data.remote.ChallengeGoals
import com.example.runtime.data.remote.ChallengeStreak
import com.example.runtime.data.remote.ChallengeSummary
import com.example.runtime.data.remote.ChallengeTotals
import com.example.runtime.data.remote.GoalsRequest
import com.example.runtime.data.remote.GoalsResponse
import com.example.runtime.data.repository.ChallengeRepository
import com.example.runtime.testing.FakeApiService
import com.example.runtime.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.ZoneId

class ChallengeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val api = ChallengeApi()
    private val viewModel by lazy { ChallengeViewModel(ChallengeRepository(api), ZoneId.of("Asia/Seoul")) }

    @Test
    fun load_sendsThePhoneTimeZone() {
        viewModel.load()

        assertEquals(listOf("Asia/Seoul"), api.zones)
        assertEquals(3, viewModel.summary?.streak?.current)
    }

    @Test
    fun saveGoals_sendsMetersAndReloads() {
        viewModel.load()
        var closed = false

        viewModel.saveGoals(20.0, 80.5) { closed = true }

        assertEquals(GoalsRequest(20000, 80500), api.goals.single())
        assertTrue(closed)
        assertEquals(2, api.zones.size)
        assertEquals(20000, viewModel.summary?.goals?.weeklyGoalMeters)
    }

    @Test
    fun saveGoals_failureKeepsTheDialogOpen() {
        viewModel.load()
        api.failGoals = true
        var closed = false

        viewModel.saveGoals(10.0, 0.0) { closed = true }

        assertFalse(closed)
        assertNotNull(viewModel.goalError)
    }

    @Test
    fun reset_clearsTheSummary() {
        viewModel.load()
        viewModel.reset()

        assertNull(viewModel.summary)
    }

    private class ChallengeApi : FakeApiService() {
        val zones = mutableListOf<String>()
        val goals = mutableListOf<GoalsRequest>()
        var failGoals = false
        private var weekly = 0

        override suspend fun challenges(timeZone: String): ApiResponse<ChallengeSummary> {
            zones += timeZone
            return ApiResponse(
                status = "success",
                data = ChallengeSummary(
                    goals = ChallengeGoals(weeklyGoalMeters = weekly, weekMeters = 12000),
                    streak = ChallengeStreak(3, 5),
                    totals = ChallengeTotals(10, 52000),
                ),
            )
        }

        override suspend fun updateGoals(body: GoalsRequest): ApiResponse<GoalsResponse> {
            if (failGoals) throw IOException("offline")
            goals += body
            weekly = body.weeklyGoalMeters
            return ApiResponse(status = "success", data = GoalsResponse(body.weeklyGoalMeters, body.monthlyGoalMeters))
        }
    }
}
