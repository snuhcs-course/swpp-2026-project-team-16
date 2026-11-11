package com.example.runtime.data.repository

import com.example.runtime.data.remote.ApiClient
import com.example.runtime.data.remote.ApiService
import com.example.runtime.data.remote.WeatherBriefing

class WeatherRepository(private val api: ApiService = ApiClient.api) {

    suspend fun briefing(latitude: Double, longitude: Double): WeatherBriefing =
        api.weather(latitude, longitude).requireData()
}
