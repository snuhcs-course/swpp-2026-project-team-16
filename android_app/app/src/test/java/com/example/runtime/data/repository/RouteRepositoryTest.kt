package com.example.runtime.data.repository

import com.example.runtime.data.remote.ApiResponse
import com.example.runtime.data.remote.ElevationCoordinate
import com.example.runtime.data.remote.ElevationData
import com.example.runtime.data.remote.ElevationLookupRequest
import com.example.runtime.testing.FakeApiService
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

    @Test
    fun getElevations_usesBackendAndPreservesElevationOrder() {
        val api = ElevationApi()
        val repository = RouteRepository(api)

        val result = kotlinx.coroutines.runBlocking {
            repository.getElevations(listOf(37.46 to 126.952, 37.461 to 126.953))
        }

        assertEquals(listOf(101.0, 102.0), result.getOrThrow())
        assertEquals(
            listOf(
                ElevationCoordinate(latitude = 37.46, longitude = 126.952),
                ElevationCoordinate(latitude = 37.461, longitude = 126.953),
            ),
            api.request?.coordinates,
        )
    }

    private class ElevationApi : FakeApiService() {
        var request: ElevationLookupRequest? = null

        override suspend fun getElevations(body: ElevationLookupRequest): ApiResponse<ElevationData> {
            request = body
            return ApiResponse(status = "success", data = ElevationData(listOf(101.0, 102.0)))
        }
    }
}
