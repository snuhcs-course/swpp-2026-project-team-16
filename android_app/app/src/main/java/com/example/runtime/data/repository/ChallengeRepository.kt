package com.example.runtime.data.repository

import com.example.runtime.data.remote.ApiClient
import com.example.runtime.data.remote.ApiService
import com.example.runtime.data.remote.ChallengeSummary
import com.example.runtime.data.remote.GoalsRequest
import com.example.runtime.data.remote.GoalsResponse

class ChallengeRepository(private val api: ApiService = ApiClient.api) {

    suspend fun summary(timeZone: String): ChallengeSummary = api.challenges(timeZone).requireData()

    suspend fun saveGoals(weeklyMeters: Int, monthlyMeters: Int): GoalsResponse =
        api.updateGoals(GoalsRequest(weeklyMeters, monthlyMeters)).requireData()
}
