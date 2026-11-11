package com.example.runtime.ui.challenge

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runtime.data.remote.ChallengeSummary
import com.example.runtime.data.repository.ChallengeRepository
import com.example.runtime.ui.common.UiText
import com.example.runtime.ui.common.toUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.ZoneId
import kotlin.math.roundToInt

class ChallengeViewModel(
    private val repository: ChallengeRepository = ChallengeRepository(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    var summary by mutableStateOf<ChallengeSummary?>(null)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isSavingGoals by mutableStateOf(false)
        private set
    var goalError by mutableStateOf<UiText?>(null)
        private set

    fun load() {
        if (isLoading) return
        isLoading = true
        error = null
        viewModelScope.launch {
            try {
                summary = repository.summary(zone.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toUiText()
            } finally {
                isLoading = false
            }
        }
    }

    fun saveGoals(weeklyKm: Double, monthlyKm: Double, onSaved: () -> Unit) {
        if (isSavingGoals) return
        isSavingGoals = true
        goalError = null
        viewModelScope.launch {
            try {
                val saved = repository.saveGoals((weeklyKm * 1000).roundToInt(), (monthlyKm * 1000).roundToInt())
                summary = summary?.let {
                    it.copy(goals = it.goals.copy(weeklyGoalMeters = saved.weeklyGoalMeters, monthlyGoalMeters = saved.monthlyGoalMeters))
                }
                onSaved()
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                goalError = e.toUiText()
            } finally {
                isSavingGoals = false
            }
        }
    }

    fun clearGoalError() {
        goalError = null
    }

    fun reset() {
        summary = null
        error = null
    }
}
