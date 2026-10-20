package com.example.runtime.ui.route

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteGeometryUtilsTest {

    @Test
    fun interpolateCoordinate_returnsMidpoint() {
        val result = interpolateCoordinate(
            start = 37.0 to 126.0,
            end = 38.0 to 128.0,
            fraction = 0.5,
        )

        assertEquals(37.5, result.first, 0.000001)
        assertEquals(127.0, result.second, 0.000001)
    }

    @Test
    fun splitRouteGeometryAtIntervals_splitsRouteAndKeepsEndPoint() {
        val earthRadiusMeters = 6_371_000.0
        val latitudeDelta = Math.toDegrees(250.0 / earthRadiusMeters)
        val route = listOf(
            0.0 to 0.0,
            latitudeDelta to 0.0,
        )

        val segments = splitRouteGeometryAtIntervals(
            coordinates = route,
            intervalMeters = 100.0,
        )

        assertEquals(3, segments.size)
        assertEquals(route.last(), segments.last().last())

        assertEquals(
            segments[0].last(),
            segments[1].first(),
        )
        assertEquals(
            segments[1].last(),
            segments[2].first(),
        )

        assertEquals(
            100.0,
            distanceBetweenMeters(segments[0].first(), segments[0].last()),
            0.1,
        )
        assertEquals(
            100.0,
            distanceBetweenMeters(segments[1].first(), segments[1].last()),
            0.1,
        )
        assertEquals(
            50.0,
            distanceBetweenMeters(segments[2].first(), segments[2].last()),
            0.1,
        )
    }
}