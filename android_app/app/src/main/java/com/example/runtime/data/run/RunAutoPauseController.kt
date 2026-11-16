package com.example.runtime.data.run

/** Adds hysteresis and a dwell time so noisy GPS speeds do not toggle auto-pause. */
internal class RunAutoPauseController(
    private val lowSpeedKmh: Double = LOW_SPEED_KMH,
    private val highSpeedKmh: Double = HIGH_SPEED_KMH,
    private val pauseAfterMs: Long = PAUSE_AFTER_MS,
    private val resumeAfterMs: Long = RESUME_AFTER_MS,
) {
    enum class Transition { PAUSED, RESUMED }

    private var paused = false
    private var belowThresholdSinceMs: Long? = null
    private var aboveThresholdSinceMs: Long? = null

    fun update(speedKmh: Double?, nowMs: Long): Transition? {
        if (speedKmh == null || !speedKmh.isFinite()) {
            resetDwell()
            return null
        }

        if (!paused) {
            aboveThresholdSinceMs = null
            if (speedKmh > lowSpeedKmh) {
                belowThresholdSinceMs = null
                return null
            }
            val since = belowThresholdSinceMs ?: nowMs.also { belowThresholdSinceMs = it }
            if (nowMs - since >= pauseAfterMs) {
                paused = true
                belowThresholdSinceMs = null
                return Transition.PAUSED
            }
            return null
        }

        belowThresholdSinceMs = null
        if (speedKmh < highSpeedKmh) {
            aboveThresholdSinceMs = null
            return null
        }
        val since = aboveThresholdSinceMs ?: nowMs.also { aboveThresholdSinceMs = it }
        if (nowMs - since >= resumeAfterMs) {
            paused = false
            aboveThresholdSinceMs = null
            return Transition.RESUMED
        }
        return null
    }

    fun reset(paused: Boolean = false) {
        this.paused = paused
        resetDwell()
    }

    private fun resetDwell() {
        belowThresholdSinceMs = null
        aboveThresholdSinceMs = null
    }

    companion object {
        const val LOW_SPEED_KMH = 0.5
        const val HIGH_SPEED_KMH = 1.0
        const val PAUSE_AFTER_MS = 3_000L
        const val RESUME_AFTER_MS = 2_000L
    }
}
