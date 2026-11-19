package com.example.runtime.data.run

data class RunSplitMilestone(val kilometer: Int, val elapsedSeconds: Long)

/** Records each crossed whole-kilometer mark once and provides its cumulative time for spoken splits. */
internal class RunSplitTracker {
    private var completedKilometers = 0

    fun update(distanceMeters: Double, elapsedSeconds: Long): List<RunSplitMilestone> {
        if (!distanceMeters.isFinite() || distanceMeters < 0.0) return emptyList()
        val completed = (distanceMeters / METERS_PER_KILOMETER).toInt()
        if (completed <= completedKilometers) return emptyList()
        val newMilestones = ((completedKilometers + 1)..completed).map { kilometer ->
            RunSplitMilestone(kilometer, elapsedSeconds)
        }
        completedKilometers = completed
        return newMilestones
    }

    fun reset() {
        completedKilometers = 0
    }

    private companion object {
        const val METERS_PER_KILOMETER = 1000.0
    }
}
