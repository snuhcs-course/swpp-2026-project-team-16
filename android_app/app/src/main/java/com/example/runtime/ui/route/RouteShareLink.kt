package com.example.runtime.ui.route

import com.example.runtime.data.remote.GeoJsonLineString
import java.util.Locale

private const val MAPS_DIRECTIONS_URL = "https://www.google.com/maps/dir/?api=1"
private const val MAX_WAYPOINTS = 8

fun googleMapsRouteUrl(lines: List<GeoJsonLineString>): String? {
    val points = lines.flatMap { it.coordinates }
        .filter { it.size >= 2 && it[0].isFinite() && it[1].isFinite() }
    if (points.size < 2) return null

    val interior = points.subList(1, points.lastIndex)
    val waypoints = if (interior.size <= MAX_WAYPOINTS) {
        interior
    } else {
        (1..MAX_WAYPOINTS).map { i -> interior[i * interior.size / (MAX_WAYPOINTS + 1)] }
    }

    return buildString {
        append(MAPS_DIRECTIONS_URL)
        append("&origin=").append(latLng(points.first()))
        append("&destination=").append(latLng(points.last()))
        if (waypoints.isNotEmpty()) {
            append("&waypoints=").append(waypoints.joinToString("%7C") { latLng(it) })
        }
        append("&travelmode=walking")
    }
}

private fun latLng(coordinate: List<Double>): String =
    "%.6f,%.6f".format(Locale.US, coordinate[1], coordinate[0])
