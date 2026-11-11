package com.example.runtime.ui.calendar

import com.example.runtime.data.remote.RunHistoryItem
import com.example.runtime.data.remote.RunningGroup
import com.example.runtime.ui.common.parseInstant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class DistanceLevel(val minMeters: Int, val maxMeters: Int?, val alpha: Float)

val DISTANCE_LEVELS = listOf(
    DistanceLevel(0, 5000, 0.3f),
    DistanceLevel(5000, 10000, 0.55f),
    DistanceLevel(10000, 20000, 0.8f),
    DistanceLevel(20000, null, 1f),
)

fun distanceLevel(meters: Int): DistanceLevel? =
    if (meters <= 0) null else DISTANCE_LEVELS.last { meters >= it.minMeters }

fun monthCells(month: YearMonth): List<LocalDate?> {
    val leading = month.atDay(1).dayOfWeek.value % 7
    val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val cells = List<LocalDate?>(leading) { null } + days
    val trailing = (7 - cells.size % 7) % 7
    return cells + List(trailing) { null }
}

val WEEK_DAYS = listOf(
    DayOfWeek.SUNDAY,
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
)

fun RunHistoryItem.localDate(zone: ZoneId): LocalDate? = parseInstant(finishedAt)?.atZone(zone)?.toLocalDate()

fun RunningGroup.localDate(zone: ZoneId): LocalDate? = parseInstant(meetAt)?.atZone(zone)?.toLocalDate()

fun runsByDay(runs: List<RunHistoryItem>, zone: ZoneId): Map<LocalDate, List<RunHistoryItem>> =
    runs.mapNotNull { run -> run.localDate(zone)?.let { it to run } }.groupBy({ it.first }, { it.second })

fun groupsByDay(groups: List<RunningGroup>, zone: ZoneId): Map<LocalDate, List<RunningGroup>> =
    groups.mapNotNull { group -> group.localDate(zone)?.let { it to group } }.groupBy({ it.first }, { it.second })
