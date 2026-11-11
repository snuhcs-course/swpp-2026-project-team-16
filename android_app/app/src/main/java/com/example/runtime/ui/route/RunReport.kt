package com.example.runtime.ui.route

import androidx.annotation.StringRes
import com.example.runtime.R
import com.example.runtime.data.remote.RunPlan
import java.util.Locale
import kotlin.math.roundToInt

data class RunResult(
    val distanceMeters: Int,
    val elapsedSeconds: Long,
    val splitTimes: List<Long> = emptyList(),
    val offRouteCount: Int = 0,
    val offRouteSeconds: Long = 0,
    val finishedAtMillis: Long = 0,
    val path: List<List<Double>> = emptyList(),
) {
    val splitDurations: List<Long>
        get() = splitTimes.mapIndexed { index, time ->
            (time - (splitTimes.getOrNull(index - 1) ?: 0L)).coerceAtLeast(1L)
        }
}

enum class SplitStatus { IN_RANGE, FAST, SLOW }

data class SplitPace(
    val km: Int,
    val distanceMeters: Int,
    val paceSeconds: Int,
    val status: SplitStatus?,
    val diffSeconds: Int,
)

enum class RunType(val apiValue: String, @StringRes val label: Int) {
    RECOVERY("recovery", R.string.run_type_recovery),
    EASY("easy", R.string.run_type_easy),
    LONG("long", R.string.run_type_long),
    TEMPO("tempo", R.string.run_type_tempo),
    INTERVAL("interval", R.string.run_type_interval);

    companion object {
        fun from(apiValue: String?): RunType? = entries.firstOrNull { it.apiValue == apiValue }
    }
}

private const val MIN_PARTIAL_SPLIT_METERS = 200
private const val SPLIT_METERS = 1000

fun RunResult.splitPaces(plan: RunPlan?): List<SplitPace> {
    val durations = splitDurations
    val rows = durations.mapIndexed { index, seconds -> Triple(index + 1, SPLIT_METERS, seconds.toInt()) }.toMutableList()
    val remainingMeters = distanceMeters - SPLIT_METERS * durations.size
    val remainingSeconds = elapsedSeconds - durations.sum()
    if (remainingMeters >= MIN_PARTIAL_SPLIT_METERS && remainingSeconds > 0) {
        rows += Triple(
            durations.size + 1,
            remainingMeters,
            (remainingSeconds * SPLIT_METERS.toDouble() / remainingMeters).roundToInt(),
        )
    }
    return rows.map { (km, meters, pace) ->
        val (status, diff) = when {
            plan == null -> null to 0
            pace < plan.paceMinSeconds -> SplitStatus.FAST to plan.paceMinSeconds - pace
            pace > plan.paceMaxSeconds -> SplitStatus.SLOW to pace - plan.paceMaxSeconds
            else -> SplitStatus.IN_RANGE to 0
        }
        SplitPace(km, meters, pace, status, diff)
    }
}

fun RunResult.averagePaceSeconds(): Int? =
    if (distanceMeters >= 100) (elapsedSeconds * 1000.0 / distanceMeters).roundToInt() else null

fun RunPlan.expectedSeconds(): Long = (distanceMeters / 1000.0 * (paceMinSeconds + paceMaxSeconds) / 2.0).roundToInt().toLong()

fun formatPaceClock(seconds: Int): String = String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)

fun RunPlan.paceRangeText(): String = "${formatPaceClock(paceMinSeconds)}–${formatPaceClock(paceMaxSeconds)}"
