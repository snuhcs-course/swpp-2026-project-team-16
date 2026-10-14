package com.example.runtime.ui.common

import java.util.Locale

fun formatDistance(meters: Int): String =
    "%.2f".format(Locale.US, meters / 1000.0).trimEnd('0').trimEnd('.') + "km"

fun routeTitle(startName: String, endName: String, meters: Int): String {
    val distance = formatDistance(meters)
    if (startName.isBlank()) return distance
    return "$startName -> ${endName.ifBlank { startName }} [$distance]"
}
