package com.example.runtime.ui.route

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal const val MIN_COORDINATE_DISTANCE_METERS = 0.1

internal fun splitRouteGeometryAtIntervals(
    coordinates: List<Pair<Double, Double>>,
    intervalMeters: Double,
): List<List<Pair<Double, Double>>> {
    if (coordinates.size < 2 || intervalMeters <= 0.0) return emptyList()

    val cleanCoordinates = mutableListOf<Pair<Double, Double>>()
    coordinates.forEach { point ->
        if (
            cleanCoordinates.isEmpty() ||
            distanceBetweenMeters(cleanCoordinates.last(), point) >
            MIN_COORDINATE_DISTANCE_METERS
        ) {
            cleanCoordinates.add(point)
        }
    }

    if (cleanCoordinates.size < 2) return emptyList()

    val result = mutableListOf<List<Pair<Double, Double>>>()
    var currentCoordinates = mutableListOf(cleanCoordinates.first())
    var distanceInCurrentSegment = 0.0

    for (index in 0 until cleanCoordinates.lastIndex) {
        var edgeStart = cleanCoordinates[index]
        val edgeEnd = cleanCoordinates[index + 1]
        var remainingEdgeDistance = distanceBetweenMeters(edgeStart, edgeEnd)

        while (remainingEdgeDistance > 0.001) {
            val distanceToBoundary = intervalMeters - distanceInCurrentSegment

            if (remainingEdgeDistance + 1e-6 >= distanceToBoundary) {
                val fraction =
                    (distanceToBoundary / remainingEdgeDistance).coerceIn(0.0, 1.0)
                val boundary = interpolateCoordinate(edgeStart, edgeEnd, fraction)
                currentCoordinates.add(boundary)

                if (currentCoordinates.size >= 2) {
                    result.add(currentCoordinates.toList())
                }

                currentCoordinates = mutableListOf(boundary)
                distanceInCurrentSegment = 0.0
                edgeStart = boundary
                remainingEdgeDistance = distanceBetweenMeters(edgeStart, edgeEnd)
            } else {
                currentCoordinates.add(edgeEnd)
                distanceInCurrentSegment += remainingEdgeDistance
                remainingEdgeDistance = 0.0
            }
        }
    }

    if (currentCoordinates.size >= 2) {
        result.add(currentCoordinates.toList())
    }

    return result
}

internal fun interpolateCoordinate(
    start: Pair<Double, Double>,
    end: Pair<Double, Double>,
    fraction: Double,
): Pair<Double, Double> {
    val latitude = start.first + (end.first - start.first) * fraction
    val longitude = start.second + (end.second - start.second) * fraction
    return latitude to longitude
}

internal fun distanceBetweenMeters(
    first: Pair<Double, Double>,
    second: Pair<Double, Double>,
): Double {
    val earthRadiusMeters = 6_371_000.0

    val firstLatitude = Math.toRadians(first.first)
    val secondLatitude = Math.toRadians(second.first)
    val latitudeDifference = Math.toRadians(second.first - first.first)
    val longitudeDifference = Math.toRadians(second.second - first.second)

    val haversine =
        sin(latitudeDifference / 2) * sin(latitudeDifference / 2) +
                cos(firstLatitude) *
                cos(secondLatitude) *
                sin(longitudeDifference / 2) *
                sin(longitudeDifference / 2)

    return 2 * earthRadiusMeters * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
}