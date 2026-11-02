package com.example.runtime.ui.common

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

fun formatKm(meters: Int): String =
    "%.2f".format(Locale.US, meters / 1000.0).trimEnd('0').trimEnd('.')

fun formatDistance(meters: Int): String = formatKm(meters) + "km"

fun routeTitle(startName: String, endName: String, meters: Int): String {
    val distance = formatDistance(meters)
    if (startName.isBlank()) return distance
    return "$startName -> ${endName.ifBlank { startName }} [$distance]"
}

fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    val remainder = seconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainder)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, remainder)
    }
}

fun formatPace(speedMetersPerSecond: Double): String {
    if (speedMetersPerSecond < 0.3) return "--:--"
    val secondsPerKm = (1000.0 / speedMetersPerSecond).roundToInt().coerceAtMost(3599)
    return String.format(Locale.US, "%d:%02d /km", secondsPerKm / 60, secondsPerKm % 60)
}

fun formatPaceSeconds(secondsPerKm: Int): String =
    String.format(Locale.US, "%d:%02d /km", secondsPerKm / 60, secondsPerKm % 60)

fun parseInstant(value: String): Instant? =
    try {
        OffsetDateTime.parse(value).toInstant()
    } catch (e: DateTimeParseException) {
        null
    }

fun formatDateTime(instant: Instant): String =
    instant.atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.getDefault()))

fun formatDateTime(value: String): String = parseInstant(value)?.let(::formatDateTime) ?: value
