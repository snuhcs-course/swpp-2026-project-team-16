package com.example.runtime.ui.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runtime.data.remote.RunHistoryItem
import com.example.runtime.data.remote.RunningGroup
import com.example.runtime.data.repository.CommunityRepository
import com.example.runtime.data.repository.GroupRepository
import com.example.runtime.ui.common.UiText
import com.example.runtime.ui.common.toUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class CalendarViewModel(
    private val communityRepository: CommunityRepository = CommunityRepository(),
    private val groupRepository: GroupRepository = GroupRepository(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(),
) : ViewModel() {

    var month by mutableStateOf(YearMonth.from(today))
        private set
    var selectedDate by mutableStateOf(today)
        private set
    var runsByDay by mutableStateOf<Map<LocalDate, List<RunHistoryItem>>>(emptyMap())
        private set
    var groupsByDay by mutableStateOf<Map<LocalDate, List<RunningGroup>>>(emptyMap())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set

    fun load() {
        if (isLoading) return
        isLoading = true
        error = null
        viewModelScope.launch {
            try {
                val runs = async { communityRepository.runs() }
                val groups = async { groupRepository.groups("mine") }
                runsByDay = runsByDay(runs.await(), zone)
                groupsByDay = groupsByDay(groups.await(), zone)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toUiText()
            } finally {
                isLoading = false
            }
        }
    }

    fun showPreviousMonth() {
        month = month.minusMonths(1)
    }

    fun showNextMonth() {
        month = month.plusMonths(1)
    }

    fun select(date: LocalDate) {
        selectedDate = date
        if (YearMonth.from(date) != month) month = YearMonth.from(date)
    }

    fun dayDistance(date: LocalDate): Int = runsByDay[date].orEmpty().sumOf { it.distanceMeters }

    val monthDistance: Int
        get() = runsByDay.filterKeys { YearMonth.from(it) == month }.values.flatten().sumOf { it.distanceMeters }

    val monthRunDays: Int
        get() = runsByDay.keys.count { YearMonth.from(it) == month }

    fun reset() {
        runsByDay = emptyMap()
        groupsByDay = emptyMap()
        error = null
    }
}
