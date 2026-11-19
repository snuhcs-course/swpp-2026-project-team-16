package com.example.runtime.data.run

import org.junit.Assert.assertEquals
import org.junit.Test

class RunSplitTrackerTest {
    @Test
    fun recordsEachKilometerOnceWithItsCumulativeTime() {
        val tracker = RunSplitTracker()

        assertEquals(emptyList<RunSplitMilestone>(), tracker.update(999.0, 359L))
        assertEquals(listOf(RunSplitMilestone(1, 360L)), tracker.update(1001.0, 360L))
        assertEquals(emptyList<RunSplitMilestone>(), tracker.update(1500.0, 500L))
        assertEquals(listOf(RunSplitMilestone(2, 688L)), tracker.update(2000.0, 688L))
    }

    @Test
    fun resetStartsAnewSetOfKilometerMilestones() {
        val tracker = RunSplitTracker()
        tracker.update(1000.0, 360L)
        tracker.reset()

        assertEquals(listOf(RunSplitMilestone(1, 10L)), tracker.update(1000.0, 10L))
    }
}
