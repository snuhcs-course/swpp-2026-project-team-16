package com.example.runtime.ui.route

import com.example.runtime.data.remote.RunPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunReportTest {
    private val plan = RunPlan("recovery", 5000, 375, 405)

    @Test
    fun splitDurationsComeFromCumulativeTimes() {
        val result = RunResult(5000, 1868, splitTimes = listOf(392, 773, 1139, 1497))

        assertEquals(listOf(392L, 381L, 366L, 358L), result.splitDurations)
    }

    @Test
    fun comparesEachSplitWithThePlan() {
        val result = RunResult(5000, 1868, splitTimes = listOf(392, 773, 1139, 1497, 1868))

        val splits = result.splitPaces(plan)

        assertEquals(listOf(392, 381, 366, 358, 371), splits.map { it.paceSeconds })
        assertEquals(
            listOf(SplitStatus.IN_RANGE, SplitStatus.IN_RANGE, SplitStatus.FAST, SplitStatus.FAST, SplitStatus.FAST),
            splits.map { it.status },
        )
        assertEquals(listOf(0, 0, 9, 17, 4), splits.map { it.diffSeconds })
    }

    @Test
    fun aLongLeftoverBecomesANormalizedPartialSplit() {
        val result = RunResult(2500, 930, splitTimes = listOf(360, 730))

        val splits = result.splitPaces(plan)

        assertEquals(3, splits.size)
        assertEquals(500, splits.last().distanceMeters)
        assertEquals(400, splits.last().paceSeconds)
        assertEquals(SplitStatus.IN_RANGE, splits.last().status)
    }

    @Test
    fun aShortLeftoverIsDropped() {
        assertEquals(1, RunResult(1100, 400, splitTimes = listOf(360)).splitPaces(null).size)
    }

    @Test
    fun withoutAPlanSplitsHaveNoStatus() {
        val splits = RunResult(2000, 760, splitTimes = listOf(380, 760)).splitPaces(null)

        assertEquals(listOf(null, null), splits.map { it.status })
    }

    @Test
    fun averagePaceNeedsSomeDistance() {
        assertEquals(374, RunResult(5000, 1868).averagePaceSeconds())
        assertNull(RunResult(50, 30).averagePaceSeconds())
    }

    @Test
    fun formatsPaceAndPlanRange() {
        assertEquals("6:05", formatPaceClock(365))
        assertEquals("6:15–6:45", plan.paceRangeText())
        assertEquals(1950L, plan.expectedSeconds())
    }
}
