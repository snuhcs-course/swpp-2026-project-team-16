package com.example.runtime.ui.common

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
