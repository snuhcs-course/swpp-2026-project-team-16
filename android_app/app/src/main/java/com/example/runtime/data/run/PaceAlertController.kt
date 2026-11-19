package com.example.runtime.data.run

import kotlin.math.roundToInt

data class PaceAlertSegment(
    val startMeters: Int,
    val endMeters: Int,
    val paceMinSeconds: Int,
    val paceMaxSeconds: Int,
)

data class PaceAlertSettings(
    val paceMinSeconds: Int,
    val paceMaxSeconds: Int,
    val alertOnSlow: Boolean,
    val alertOnFast: Boolean,
    val segments: List<PaceAlertSegment> = emptyList(),
)

enum class PaceAlert { TOO_SLOW, TOO_FAST }

/** Emits no more than one pace warning in each planned phase (or each km without phases). */
internal class PaceAlertController(
    private val confirmDurationMs: Long = DEFAULT_CONFIRM_DURATION_MS,
) {
    private var settings: PaceAlertSettings? = null
    private var activeSegmentKey: Int? = null
    private var alertedForSegment = false
    private var slowSinceMs: Long? = null
    private var fastSinceMs: Long? = null

    fun configure(settings: PaceAlertSettings?) {
        this.settings = settings?.takeIf { it.alertOnSlow || it.alertOnFast }
        resetSegment()
    }

    fun reset() {
        settings = null
        resetSegment()
    }

    fun update(
        distanceMeters: Double,
        speedMetersPerSecond: Double,
        nowMs: Long,
        isPaused: Boolean,
    ): PaceAlert? {
        val plan = settings ?: return null
        if (distanceMeters < 0.0 || !distanceMeters.isFinite()) return null

        val distance = distanceMeters.toInt()
        val customIndex = plan.segments.indexOfFirst { distance >= it.startMeters && distance < it.endMeters }
        // When no custom phase covers this distance, a new default phase starts each kilometer.
        val segmentKey = if (customIndex >= 0) customIndex else -((distance / METERS_PER_KILOMETER).toInt() + 1)
        if (segmentKey != activeSegmentKey) {
            activeSegmentKey = segmentKey
            alertedForSegment = false
            slowSinceMs = null
            fastSinceMs = null
        }
        if (alertedForSegment) return null

        if (isPaused || !speedMetersPerSecond.isFinite() || speedMetersPerSecond < MIN_ALERT_SPEED_MPS) {
            slowSinceMs = null
            fastSinceMs = null
            return null
        }

        val target = plan.segments.getOrNull(customIndex)
        val paceMin = target?.paceMinSeconds ?: plan.paceMinSeconds
        val paceMax = target?.paceMaxSeconds ?: plan.paceMaxSeconds
        val paceSecondsPerKm = (METERS_PER_KILOMETER / speedMetersPerSecond).roundToInt()

        val tooSlow = plan.alertOnSlow && paceSecondsPerKm >= paceMax + ALERT_DEVIATION_SECONDS
        val tooFast = plan.alertOnFast && paceSecondsPerKm <= paceMin - ALERT_DEVIATION_SECONDS
        if (!tooSlow) slowSinceMs = null
        if (!tooFast) fastSinceMs = null

        if (tooSlow) {
            val since = slowSinceMs ?: nowMs.also { slowSinceMs = it }
            if (nowMs - since >= confirmDurationMs) {
                alertedForSegment = true
                return PaceAlert.TOO_SLOW
            }
        }
        if (tooFast) {
            val since = fastSinceMs ?: nowMs.also { fastSinceMs = it }
            if (nowMs - since >= confirmDurationMs) {
                alertedForSegment = true
                return PaceAlert.TOO_FAST
            }
        }
        return null
    }

    private fun resetSegment() {
        activeSegmentKey = null
        alertedForSegment = false
        slowSinceMs = null
        fastSinceMs = null
    }

    private companion object {
        const val METERS_PER_KILOMETER = 1000.0
        const val MIN_ALERT_SPEED_MPS = 0.5
        const val ALERT_DEVIATION_SECONDS = 30
        const val DEFAULT_CONFIRM_DURATION_MS = 10_000L
    }
}
