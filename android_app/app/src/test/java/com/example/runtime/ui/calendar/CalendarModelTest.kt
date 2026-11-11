package com.example.runtime.ui.calendar

import com.example.runtime.data.remote.RunHistoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class CalendarModelTest {

    @Test
    fun monthStartsOnSundayAndFillsWholeWeeks() {
        val cells = monthCells(YearMonth.of(2026, 11))

        assertEquals(LocalDate.of(2026, 11, 1), cells[0])
        assertEquals(35, cells.size)
        assertEquals(LocalDate.of(2026, 11, 30), cells[29])
        assertNull(cells.last())
    }

    @Test
    fun leadingBlanksMatchTheFirstWeekday() {
        val cells = monthCells(YearMonth.of(2026, 10))

        assertEquals(4, cells.indexOf(LocalDate.of(2026, 10, 1)))
        assertEquals(0, cells.size % 7)
    }

    @Test
    fun distanceLevelsFollowTheLegend() {
        assertNull(distanceLevel(0))
        assertEquals(0.3f, distanceLevel(4999)?.alpha)
        assertEquals(0.55f, distanceLevel(5000)?.alpha)
        assertEquals(0.55f, distanceLevel(9999)?.alpha)
        assertEquals(0.8f, distanceLevel(10000)?.alpha)
        assertEquals(0.8f, distanceLevel(19999)?.alpha)
        assertEquals(1f, distanceLevel(20000)?.alpha)
    }

    @Test
    fun runsAreGroupedByLocalDay() {
        val seoul = ZoneId.of("Asia/Seoul")
        val runs = listOf(
            run(1, "2026-11-09T16:30:00Z", 3000),
            run(2, "2026-11-10T01:00:00Z", 2000),
            run(3, "2026-11-09T10:00:00Z", 5000),
        )

        val byDay = runsByDay(runs, seoul)

        assertEquals(listOf(1, 2), byDay[LocalDate.of(2026, 11, 10)]?.map { it.id })
        assertEquals(listOf(3), byDay[LocalDate.of(2026, 11, 9)]?.map { it.id })
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
