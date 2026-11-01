package com.example.runtime.ui.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteProgressMatcherTest {
    @Test
    fun projectsLocationOntoTheRouteAndAdvancesProgress() {
        val matcher = RouteProgressMatcher(
            listOf(
                RouteCoordinate(37.0, 127.0),
                RouteCoordinate(37.0, 127.001),
                RouteCoordinate(37.0, 127.002),
            ),
        )

        val result = matcher.update(37.0, 127.001)

        assertEquals(matcher.routeLengthMeters / 2.0, result.distanceAlongMeters, 2.0)
        assertEquals(0.0, result.distanceFromRouteMeters, 0.5)
    }

    @Test
    fun keepsALoopStartMatchedToTheBeginningInsteadOfTheFinish() {
        val matcher = RouteProgressMatcher(
            listOf(
                RouteCoordinate(37.0, 127.0),
                RouteCoordinate(37.0, 127.001),
                RouteCoordinate(37.001, 127.001),
                RouteCoordinate(37.001, 127.0),
                RouteCoordinate(37.0, 127.0),
            ),
        )

        val result = matcher.update(37.0, 127.0)

        assertTrue(result.distanceAlongMeters < 5.0)
        assertTrue(result.routeLengthMeters > 300.0)
    }

    @Test
    fun reportsDistanceWhenRunnerIsFarFromRoute() {
        val matcher = RouteProgressMatcher(
            listOf(
                RouteCoordinate(37.0, 127.0),
                RouteCoordinate(37.0, 127.002),
            ),
        )

        val result = matcher.update(37.001, 127.001)

        assertTrue(result.distanceFromRouteMeters > 100.0)
    }

    @Test
    fun findsTheNextTurnFromTheGeneratedCourseGeometry() {
        val guide = RouteTurnGuide(
            listOf(
                RouteCoordinate(37.0, 127.0),
                RouteCoordinate(37.001, 127.0),
                RouteCoordinate(37.001, 127.001),
            ),
        )

        val nextTurn = guide.nextTurn(0.0)

        assertTrue(guide.turns.isNotEmpty())
        assertEquals(RouteTurnDirection.RIGHT, nextTurn?.direction)
        assertTrue(nextTurn!!.distanceAlongMeters in 70.0..140.0)
    }
}
