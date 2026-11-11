package com.example.runtime.ui.calendar

import com.example.runtime.data.remote.ApiResponse
import com.example.runtime.data.remote.RunHistoryItem
import com.example.runtime.data.remote.RunningGroup
import com.example.runtime.data.remote.WeatherBriefing
import com.example.runtime.data.repository.CommunityRepository
import com.example.runtime.data.repository.GroupRepository
import com.example.runtime.data.repository.WeatherRepository
import com.example.runtime.testing.FakeApiService
import com.example.runtime.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class CalendarViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val api = CalendarApi()
    private val viewModel by lazy {
        CalendarViewModel(CommunityRepository(api), GroupRepository(api), WeatherRepository(api), ZoneOffset.UTC, LocalDate.of(2026, 11, 10))
    }

    @Test
    fun load_usesMyRunsAndJoinedGroups() {
        viewModel.load()

        assertEquals(listOf("mine"), api.groupFilters)
        assertEquals(8000, viewModel.dayDistance(LocalDate.of(2026, 11, 3)))
        assertEquals(1, viewModel.groupsByDay[LocalDate.of(2026, 11, 15)]?.size)
    }

    @Test
    fun monthSummaryCountsOnlyTheShownMonth() {
        viewModel.load()

        assertEquals(2, viewModel.monthRunDays)
        assertEquals(13000, viewModel.monthDistance)

        viewModel.showPreviousMonth()
        assertEquals(YearMonth.of(2026, 10), viewModel.month)
        assertEquals(1, viewModel.monthRunDays)
        assertEquals(4000, viewModel.monthDistance)
    }

    @Test
    fun selectingADayInAnotherMonthMovesTheMonth() {
        viewModel.select(LocalDate.of(2026, 12, 2))

        assertEquals(YearMonth.of(2026, 12), viewModel.month)
        assertEquals(LocalDate.of(2026, 12, 2), viewModel.selectedDate)
    }

    @Test
    fun resetClearsTheData() {
        viewModel.load()
        viewModel.reset()

        assertTrue(viewModel.runsByDay.isEmpty())
        assertTrue(viewModel.groupsByDay.isEmpty())
    }

    @Test
    fun loadWeather_usesMyLocationOrFallsBackToSeoul() {
        viewModel.loadWeather(35.1, 129.0)
        assertEquals(35.1 to 129.0, api.weatherQueries.last())
        assertEquals(true, (viewModel.weather as WeatherState.Loaded).isNearMe)

        viewModel.loadWeather(null, null, force = true)
        assertEquals(DEFAULT_LATITUDE to DEFAULT_LONGITUDE, api.weatherQueries.last())
        assertEquals(false, (viewModel.weather as WeatherState.Loaded).isNearMe)
    }

    @Test
    fun loadWeather_failureKeepsTheLastBriefing() {
        viewModel.loadWeather(null, null)
        api.failWeather = true

        viewModel.loadWeather(null, null, force = true)

        assertTrue(viewModel.weather is WeatherState.Loaded)
    }

    @Test
    fun loadWeather_failureWithoutDataShowsFailed() {
        api.failWeather = true

        viewModel.loadWeather(null, null)

        assertEquals(WeatherState.Failed, viewModel.weather)
    }

    @Test
    fun loadWeather_skipsRepeatsForTheSameArea() {
        viewModel.loadWeather(37.5, 127.0, now = 0)
        viewModel.loadWeather(37.505, 127.005, now = 1_000)
        viewModel.loadWeather(null, null, now = 2_000)

        assertEquals(1, api.weatherQueries.size)
    }

    @Test
    fun loadWeather_refinesTheFallbackWithMyLocation() {
        viewModel.loadWeather(null, null, now = 0)
        viewModel.loadWeather(35.1, 129.0, now = 1_000)
        viewModel.loadWeather(37.5, 127.0, force = true, now = 2_000)

        assertEquals(
            listOf(DEFAULT_LATITUDE to DEFAULT_LONGITUDE, 35.1 to 129.0, 37.5 to 127.0),
            api.weatherQueries,
        )
    }

    @Test
    fun cachedWeatherShowsUntilFreshDataArrives() {
        val cached = WeatherState.Loaded(
            WeatherBriefing(temperature = 1.0, condition = "snow", conditionLabel = "Snow", weatherTip = "Cold."),
            isNearMe = true,
        )
        viewModel.showCachedWeather(cached)
        assertEquals(cached, viewModel.weather)

        viewModel.loadWeather(37.5, 127.0)

        assertEquals(9.0, (viewModel.weather as WeatherState.Loaded).briefing.temperature, 0.0)
    }

    private class CalendarApi : FakeApiService() {
        val groupFilters = mutableListOf<String>()
        val weatherQueries = mutableListOf<Pair<Double, Double>>()
        var failWeather = false

        override suspend fun weather(latitude: Double, longitude: Double): ApiResponse<WeatherBriefing> {
            weatherQueries += latitude to longitude
            if (failWeather) throw java.io.IOException("offline")
            return ApiResponse(
                status = "success",
                data = WeatherBriefing(
                    temperature = 9.0,
                    condition = "clear",
                    conditionLabel = "Clear",
                    weatherTip = "Great weather for a run.",
                    pm10 = 20,
                    pm25 = 10,
                    airGrade = "good",
                    airLabel = "Good",
                    airTip = "The air is clean.",
                ),
            )
        }

        override suspend fun runs() = ApiResponse(
            status = "success",
            data = listOf(
                run(1, "2026-11-03T07:00:00Z", 5000),
                run(2, "2026-11-03T19:00:00Z", 3000),
                run(3, "2026-11-08T07:00:00Z", 5000),
                run(4, "2026-10-28T07:00:00Z", 4000),
            ),
        )

        override suspend fun groups(filter: String, latitude: Double?, longitude: Double?, radiusKm: Double?): ApiResponse<List<RunningGroup>> {
            groupFilters += filter
            return ApiResponse(
                status = "success",
                data = listOf(
                    RunningGroup(
                        id = 1,
                        title = "Sunday crew",
                        host = "host",
                        routeId = 1,
                        routeDistance = 5000,
                        capacity = 4,
                        memberCount = 2,
                        meetAt = "2026-11-15T07:00:00Z",
                        joinDeadline = "2026-11-14T07:00:00Z",
                        status = "open",
                    ),
                ),
            )
        }

        private fun run(id: Int, finishedAt: String, meters: Int) = RunHistoryItem(
            id = id,
            routeId = 1,
            distanceMeters = meters,
            durationSeconds = 1800,
            finishedAt = finishedAt,
            routeDistance = meters,
        )
    }
}
