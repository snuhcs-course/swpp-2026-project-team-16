package com.example.runtime.ui.route

import com.example.runtime.data.remote.GeoJsonLineString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteShareLinkTest {

    @Test
    fun usesFirstAndLastPointsAsOriginAndDestination() {
        val url = googleMapsRouteUrl(listOf(GeoJsonLineString(listOf(listOf(126.95, 37.46), listOf(126.96, 37.47)))))

        assertEquals(
            "https://www.google.com/maps/dir/?api=1&origin=37.460000,126.950000&destination=37.470000,126.960000&travelmode=walking",
            url
        )
    }

    @Test
    fun joinsLinesAndLimitsWaypoints() {
        val first = GeoJsonLineString((0 until 10).map { listOf(127.0 + it * 0.001, 37.0) })
        val second = GeoJsonLineString((10 until 20).map { listOf(127.0 + it * 0.001, 37.0) })

        val url = googleMapsRouteUrl(listOf(first, second))!!

        val waypoints = url.substringAfter("&waypoints=").substringBefore("&").split("%7C")
        assertEquals(8, waypoints.size)
        assertEquals("37.000000,127.019000", url.substringAfter("&destination=").substringBefore("&"))
    }

    @Test
    fun returnsNullWithoutEnoughPoints() {
        assertNull(googleMapsRouteUrl(emptyList()))
        assertNull(googleMapsRouteUrl(listOf(GeoJsonLineString(listOf(listOf(127.0, 37.0))))))
    }
}
