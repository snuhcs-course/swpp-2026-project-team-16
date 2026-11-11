package com.example.runtime.ui.calendar

import android.content.Context
import com.example.runtime.data.remote.ApiClient
import com.example.runtime.data.remote.WeatherBriefing
import kotlinx.serialization.Serializable

@Serializable
private data class CachedWeather(val briefing: WeatherBriefing, val isNearMe: Boolean, val savedAt: Long)

class WeatherCache(context: Context) {
    private val preferences = context.getSharedPreferences("weather", Context.MODE_PRIVATE)

    fun load(now: Long = System.currentTimeMillis()): WeatherState.Loaded? {
        val text = preferences.getString(KEY, null) ?: return null
        val cached = runCatching { ApiClient.json.decodeFromString(CachedWeather.serializer(), text) }.getOrNull() ?: return null
        if (now - cached.savedAt > MAX_AGE_MS) return null
        return WeatherState.Loaded(cached.briefing, cached.isNearMe)
    }

    fun save(state: WeatherState.Loaded, now: Long = System.currentTimeMillis()) {
        val text = ApiClient.json.encodeToString(CachedWeather.serializer(), CachedWeather(state.briefing, state.isNearMe, now))
        preferences.edit().putString(KEY, text).apply()
    }

    private companion object {
        const val KEY = "last_briefing"
        const val MAX_AGE_MS = 3 * 60 * 60 * 1000L
    }
}
