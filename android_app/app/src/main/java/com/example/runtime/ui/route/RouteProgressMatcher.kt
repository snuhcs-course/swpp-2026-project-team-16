package com.example.runtime.ui.route

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

data class RouteProgress(
    val distanceAlongMeters: Double,
    val distanceFromRouteMeters: Double,
    val routeLengthMeters: Double,
)

enum class RouteTurnDirection { LEFT, RIGHT }

data class RouteTurn(
    val distanceAlongMeters: Double,
    val direction: RouteTurnDirection,
)

/** Finds clear bends in the generated route geometry for simple visual turn prompts. */
class RouteTurnGuide(points: List<RouteCoordinate>) {
    private val route = points.distinctConsecutive()
    private val cumulative = buildList {
        add(0.0)
        for (index in 0 until route.lastIndex) {
            add(last() + distanceBetweenMeters(
                route[index].latitude to route[index].longitude,
                route[index + 1].latitude to route[index + 1].longitude,
            ))
        }
    }

    val turns: List<RouteTurn> = findTurns()

    fun nextTurn(progressMeters: Double): RouteTurn? =
        turns.firstOrNull { it.distanceAlongMeters >= progressMeters + MIN_TURN_NOTICE_METERS }

    private fun findTurns(): List<RouteTurn> {
        val total = cumulative.lastOrNull() ?: return emptyList()
        if (route.size < 3 || total < LOOK_AROUND_METERS * 2) return emptyList()

        val candidates = mutableListOf<RouteTurn>()
        var distance = LOOK_AROUND_METERS
        while (distance < total - LOOK_AROUND_METERS) {
            val before = pointAt(distance - LOOK_AROUND_METERS)
            val after = pointAt(distance + LOOK_AROUND_METERS)
            val headingChange = signedHeadingChange(before, pointAt(distance), after)
            if (kotlin.math.abs(headingChange) >= TURN_ANGLE_THRESHOLD_DEGREES) {
                val candidate = RouteTurn(
                    distanceAlongMeters = distance,
                    direction = if (headingChange > 0) RouteTurnDirection.RIGHT else RouteTurnDirection.LEFT,
                )
                val previous = candidates.lastOrNull()
                if (previous != null && candidate.distanceAlongMeters - previous.distanceAlongMeters < TURN_MERGE_DISTANCE_METERS) {
                    if (kotlin.math.abs(headingChange) > TURN_ANGLE_THRESHOLD_DEGREES + 20) {
                        candidates[candidates.lastIndex] = candidate
                    }
                } else {
                    candidates.add(candidate)
                }
            }
            distance += TURN_SCAN_INTERVAL_METERS
        }
        return candidates
    }

    private fun pointAt(distanceMeters: Double): RouteCoordinate {
        val target = distanceMeters.coerceIn(0.0, cumulative.last())
        val segment = (0 until cumulative.lastIndex).firstOrNull {
            cumulative[it + 1] >= target
        } ?: route.lastIndex - 1
        val segmentLength = cumulative[segment + 1] - cumulative[segment]
        val fraction = if (segmentLength <= 0.0) 0.0 else {
            ((target - cumulative[segment]) / segmentLength).coerceIn(0.0, 1.0)
        }
        val start = route[segment]
        val end = route[segment + 1]
        return RouteCoordinate(
            latitude = start.latitude + (end.latitude - start.latitude) * fraction,
            longitude = start.longitude + (end.longitude - start.longitude) * fraction,
        )
    }

    private fun signedHeadingChange(
        before: RouteCoordinate,
        pivot: RouteCoordinate,
        after: RouteCoordinate,
    ): Double {
        val incoming = bearingDegrees(before, pivot)
        val outgoing = bearingDegrees(pivot, after)
        return ((outgoing - incoming + 540.0) % 360.0) - 180.0
    }

    private fun bearingDegrees(from: RouteCoordinate, to: RouteCoordinate): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLongitude = Math.toRadians(to.longitude - from.longitude)
        val y = kotlin.math.sin(deltaLongitude) * kotlin.math.cos(lat2)
        val x = kotlin.math.cos(lat1) * kotlin.math.sin(lat2) -
            kotlin.math.sin(lat1) * kotlin.math.cos(lat2) * kotlin.math.cos(deltaLongitude)
        return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360.0) % 360.0
    }

    private fun List<RouteCoordinate>.distinctConsecutive(): List<RouteCoordinate> {
        val result = mutableListOf<RouteCoordinate>()
        forEach { point ->
            val previous = result.lastOrNull()
            if (previous == null || distanceBetweenMeters(
                    previous.latitude to previous.longitude,
                    point.latitude to point.longitude,
                ) > 0.1
            ) result.add(point)
        }
        return result
    }

    companion object {
        private const val LOOK_AROUND_METERS = 12.0
        private const val TURN_SCAN_INTERVAL_METERS = 5.0
        private const val TURN_ANGLE_THRESHOLD_DEGREES = 50.0
        private const val TURN_MERGE_DISTANCE_METERS = 30.0
        private const val MIN_TURN_NOTICE_METERS = 8.0
    }
}

/** Matches GPS fixes to a forward window on a route, so a loop does not snap to its finish at the start. */
class RouteProgressMatcher(points: List<RouteCoordinate>) {
    private val points = points.distinctConsecutive()
    private val cumulativeDistances = buildList {
        add(0.0)
        for (index in 0 until this@RouteProgressMatcher.points.lastIndex) {
            add(last() + distanceBetweenMeters(
                this@RouteProgressMatcher.points[index].latitude to this@RouteProgressMatcher.points[index].longitude,
                this@RouteProgressMatcher.points[index + 1].latitude to this@RouteProgressMatcher.points[index + 1].longitude,
            ))
        }
    }

    val routeLengthMeters: Double = cumulativeDistances.lastOrNull() ?: 0.0
    private var progressMeters = 0.0

    fun update(latitude: Double, longitude: Double): RouteProgress {
        if (points.size < 2 || routeLengthMeters <= 0.0) {
            return RouteProgress(0.0, Double.POSITIVE_INFINITY, routeLengthMeters)
        }

        val minDistance = max(0.0, progressMeters - BACKTRACK_WINDOW_METERS)
        val maxDistance = min(routeLengthMeters, progressMeters + LOOKAHEAD_WINDOW_METERS)
        var bestDistanceFromRoute = Double.POSITIVE_INFINITY
        var bestProgress = progressMeters

        for (index in 0 until points.lastIndex) {
            val segmentStart = cumulativeDistances[index]
            val segmentEnd = cumulativeDistances[index + 1]
            if (segmentEnd < minDistance || segmentStart > maxDistance) continue

            val start = points[index]
            val end = points[index + 1]
            val latitudeScale = METERS_PER_DEGREE
            val longitudeScale = METERS_PER_DEGREE * cos(Math.toRadians(latitude))
            val startX = (start.longitude - longitude) * longitudeScale
            val startY = (start.latitude - latitude) * latitudeScale
            val endX = (end.longitude - longitude) * longitudeScale
            val endY = (end.latitude - latitude) * latitudeScale
            val dx = endX - startX
            val dy = endY - startY
            val lengthSquared = dx * dx + dy * dy
            val fraction = if (lengthSquared <= 0.0) 0.0 else {
                (-(startX * dx + startY * dy) / lengthSquared).coerceIn(0.0, 1.0)
            }
            val projectedX = startX + fraction * dx
            val projectedY = startY + fraction * dy
            val distance = kotlin.math.hypot(projectedX, projectedY)
            val along = segmentStart + (segmentEnd - segmentStart) * fraction

            if (along in minDistance..maxDistance && distance < bestDistanceFromRoute) {
                bestDistanceFromRoute = distance
                bestProgress = along
            }
        }

        progressMeters = max(progressMeters, bestProgress).coerceAtMost(routeLengthMeters)
        return RouteProgress(progressMeters, bestDistanceFromRoute, routeLengthMeters)
    }

    private fun List<RouteCoordinate>.distinctConsecutive(): List<RouteCoordinate> {
        val result = mutableListOf<RouteCoordinate>()
        forEach { point ->
            val previous = result.lastOrNull()
            if (previous == null || distanceBetweenMeters(
                    previous.latitude to previous.longitude,
                    point.latitude to point.longitude,
                ) > 0.1
            ) {
                result.add(point)
            }
        }
        return result
    }

    companion object {
        private const val LOOKAHEAD_WINDOW_METERS = 250.0
        private const val BACKTRACK_WINDOW_METERS = 25.0
        private const val METERS_PER_DEGREE = 111_000.0
    }
}
