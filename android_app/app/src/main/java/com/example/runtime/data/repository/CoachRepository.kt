package com.example.runtime.data.repository

import com.example.runtime.data.remote.ApiClient
import com.example.runtime.data.remote.ApiService
import com.example.runtime.data.remote.RunPlan
import com.example.runtime.data.remote.RunPlanRequest
import com.example.runtime.data.remote.RunReport
import com.example.runtime.data.remote.RunningStyle

class CoachRepository(private val api: ApiService = ApiClient.api) {

    suspend fun planRun(text: String, distanceMeters: Int?): RunPlan =
        api.planRun(RunPlanRequest(text, distanceMeters)).requireData()

    suspend fun runReport(runId: Int): RunReport = api.runReport(runId).requireData()

    suspend fun runningStyle(): RunningStyle = api.runningStyle().requireData()

    suspend fun saveRunningStyle(style: RunningStyle): RunningStyle = api.updateRunningStyle(style).requireData()
}
