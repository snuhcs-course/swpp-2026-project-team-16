package com.example.runtime.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteRepositoryTest {
    private val repository = RouteRepository()

    @Test
    fun calculateSlopes_usesActualSegmentDistance() {
        val slopes = repository.calculateSlopes(
            elevations = listOf(100.0, 110.0),
            distancesMeters = listOf(200.0),
        )

        assertEquals(5.0, slopes.single(), 0.0001)
    }

    @Test
    fun calculateSlopes_returnsNegativeSlopeForDownhill() {
        val slopes = repository.calculateSlopes(
            elevations = listOf(110.0, 100.0),
            distancesMeters = listOf(200.0),
        )

        assertEquals(-5.0, slopes.single(), 0.0001)
    }

    @Test
    fun calculateSlopes_returnsZeroForFlatRoute() {
        val slopes = repository.calculateSlopes(
            elevations = listOf(100.0, 100.0),
            distancesMeters = listOf(200.0),
        )

        assertEquals(0.0, slopes.single(), 0.0001)
    }

    @Test
    fun calculateSlopes_smoothsAnIsolatedSlopeSpike() {
        val slopes = repository.calculateSlopes(
            elevations = listOf(0.0, 2.0, 4.0, 24.0, 26.0, 28.0),
            distancesMeters = listOf(100.0, 100.0, 100.0, 100.0, 100.0),
        )

        assertEquals(listOf(2.0, 2.0, 2.0, 2.0, 2.0), slopes)
    }
}