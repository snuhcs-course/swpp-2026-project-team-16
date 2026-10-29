package com.example.runtime.ui.route

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val METERS_PER_DEGREE = 111_320.0
private const val PREVIEW_POINTS = 64
private const val PREVIEW_WAVE = 0.12
private const val MAX_PREVIEW_RADIUS_METERS = 2_500.0

internal fun previewPath(
    start: Pair<Double, Double>,
    end: Pair<Double, Double>?,
    distanceMeters: Int,
): List<Pair<Double, Double>> {
    fun offset(northMeters: Double, eastMeters: Double): Pair<Double, Double> {
        val latitude = start.first + northMeters / METERS_PER_DEGREE
        val longitude = start.second + eastMeters / (METERS_PER_DEGREE * cos(Math.toRadians(start.first)))
        return latitude to longitude
    }

    if (end != null && distanceBetweenMeters(start, end) > 1.0) {
        val north = (end.first - start.first) * METERS_PER_DEGREE
        val east = (end.second - start.second) * METERS_PER_DEGREE * cos(Math.toRadians(start.first))
        return (0..PREVIEW_POINTS).map { index ->
            val t = index.toDouble() / PREVIEW_POINTS
            val wave = PREVIEW_WAVE * sin(2 * PI * t)
            offset(north * t - east * wave, east * t + north * wave)
        }
    }

    val radius = (distanceMeters / (2 * PI)).coerceIn(1.0, MAX_PREVIEW_RADIUS_METERS)
    return (0..PREVIEW_POINTS).map { index ->
        val angle = 2 * PI * index / PREVIEW_POINTS
        val r = radius * (1 + PREVIEW_WAVE * sin(3 * angle))
        offset(radius - r * cos(angle), r * sin(angle))
    }
}
