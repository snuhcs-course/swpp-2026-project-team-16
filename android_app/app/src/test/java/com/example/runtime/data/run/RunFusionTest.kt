package com.example.runtime.data.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos

class RunFusionTest {

    private val lat0 = 37.46
    private val lon0 = 126.952
    private val metersPerDegree = 111_320.0
    private val metersPerDegreeLon = metersPerDegree * cos(Math.toRadians(lat0))

    private fun fix(
        north: Double,
        east: Double = 0.0,
        seconds: Double,
        accuracy: Double = 5.0,
        bearing: Double? = null,
    ) = GpsFix(
        latitude = lat0 + north / metersPerDegree,
        longitude = lon0 + east / metersPerDegreeLon,
        accuracyMeters = accuracy,
        timeMs = (seconds * 1000).toLong(),
        bearingDegrees = bearing,
    )

    private fun RunFusion.north() = (latitude!! - lat0) * metersPerDegree
    private fun RunFusion.east() = (longitude!! - lon0) * metersPerDegreeLon

    @Test
    fun withoutStepsDistanceComesFromAccurateGps() {
        val fusion = RunFusion(stepsAvailable = false)
        fusion.onGps(fix(0.0, seconds = 0.0))
        fusion.onGps(fix(3.0, seconds = 1.0))
        fusion.onGps(fix(6.0, seconds = 2.0))
        fusion.onGps(fix(30.0, seconds = 3.0, accuracy = 40.0))

        assertEquals(6.0, fusion.distanceMeters, 0.1)
        assertEquals(3.0, fusion.speedMps(3_000), 0.5)
    }

    @Test
    fun smallMovementsBetweenFrequentFixesStillAddUp() {
        val fusion = RunFusion(stepsAvailable = false)
        for (tick in 0..20) fusion.onGps(fix(north = tick * 1.0, seconds = tick * 0.5))

        assertEquals(19.5, fusion.distanceMeters, 1.6)
    }

    @Test
    fun standingStillDoesNotAddDistance() {
        val fusion = RunFusion(stepsAvailable = false)
        for (tick in 0..60) fusion.onGps(fix(north = if (tick % 2 == 0) 0.0 else 1.0, seconds = tick.toDouble()))

        assertEquals(0.0, fusion.distanceMeters, 0.0)
    }

    @Test
    fun withStepsDistanceIgnoresGpsJitter() {
        val fusion = RunFusion(stepsAvailable = true)
        fusion.onGps(fix(0.0, seconds = 0.0))
        fusion.onGps(fix(8.0, east = 5.0, seconds = 1.0))
        repeat(10) { fusion.onStep(1_000L + it * 500L) }

        assertEquals(10 * RunFusion.DEFAULT_STRIDE_METERS, fusion.distanceMeters, 0.001)
    }

    @Test
    fun speedComesFromCadenceAndStride() {
        val fusion = RunFusion(stepsAvailable = true)
        repeat(9) { fusion.onStep(it * 500L) }

        assertEquals(2.0, fusion.speedMps(4_000), 0.01)
        assertEquals(0.0, fusion.speedMps(20_000), 0.0)
    }

    @Test
    fun learnsStrideFromAccurateGps() {
        val fusion = RunFusion(stepsAvailable = true)
        for (second in 0..60) {
            fusion.onGps(fix(north = second * 3.0, seconds = second.toDouble()))
            fusion.onStep(second * 1000L + 250)
            fusion.onStep(second * 1000L + 750)
        }

        assertTrue(fusion.strideMeters > 1.2)
        assertTrue(fusion.strideMeters < 1.5)
    }

    @Test
    fun learnsTheAngleBetweenPhoneAndRunningDirection() {
        val fusion = RunFusion(stepsAvailable = true)
        fusion.onHeading(0.0)
        for (second in 0..10) {
            fusion.onGps(fix(north = 0.0, east = second * 3.0, seconds = second.toDouble(), bearing = 90.0))
        }

        assertEquals(PI / 2, fusion.headingOffsetRadians!!, 0.01)

        val before = fusion.east()
        repeat(5) { fusion.onStep(11_000L + it * 300L) }
        assertEquals(before + 5 * fusion.strideMeters, fusion.east(), 0.01)
    }

    @Test
    fun stepsDoNotMoveThePositionBeforeTheDirectionIsKnown() {
        val fusion = RunFusion(stepsAvailable = true)
        fusion.onHeading(0.0)
        fusion.onGps(fix(0.0, seconds = 0.0))
        repeat(5) { fusion.onStep(it * 300L) }

        assertNull(fusion.headingOffsetRadians)
        assertEquals(0.0, fusion.north(), 0.001)
    }

    @Test
    fun inaccurateFixesMoveThePositionLessThanAccurateOnes() {
        val rough = RunFusion(stepsAvailable = false)
        rough.onGps(fix(0.0, seconds = 0.0, accuracy = 3.0))
        rough.onGps(fix(20.0, seconds = 0.1, accuracy = 40.0))

        val sharp = RunFusion(stepsAvailable = false)
        sharp.onGps(fix(0.0, seconds = 0.0, accuracy = 3.0))
        sharp.onGps(fix(20.0, seconds = 0.1, accuracy = 3.0))

        assertTrue(rough.north() < 2.0)
        assertTrue(sharp.north() > 8.0)
    }

    @Test
    fun positionKeepsFollowingGpsOverTime() {
        val fusion = RunFusion(stepsAvailable = false)
        for (second in 0..60) fusion.onGps(fix(north = 0.0, seconds = second.toDouble()))
        fusion.onGps(fix(north = 30.0, seconds = 70.0))

        assertTrue(fusion.north() > 20.0)
    }

    @Test
    fun ignoresVeryInaccurateFixes() {
        val fusion = RunFusion(stepsAvailable = false)
        fusion.onGps(fix(0.0, seconds = 0.0, accuracy = 80.0))
        assertNull(fusion.latitude)
    }

    @Test
    fun fallsBackToGpsWhenTheStepSensorStaysSilent() {
        val fusion = RunFusion(stepsAvailable = true)
        for (second in 0..40) fusion.onGps(fix(north = second * 3.0, seconds = second.toDouble()))

        assertFalse(fusion.usesSteps)
        assertEquals(120.0, fusion.distanceMeters, 1.0)
    }

    @Test
    fun pauseDoesNotCountTheGapAsDistance() {
        val fusion = RunFusion(stepsAvailable = false)
        fusion.onGps(fix(0.0, seconds = 0.0))
        fusion.onGps(fix(3.0, seconds = 1.0))
        fusion.pause()
        fusion.onGps(fix(200.0, seconds = 100.0))
        fusion.onGps(fix(203.0, seconds = 101.0))

        assertEquals(6.0, fusion.distanceMeters, 0.1)
        assertNotNull(fusion.latitude)
    }

    @Test
    fun yawFollowsThePhoneTopWhenFlatAndTheBackWhenUpright() {
        val flatFacingNorth = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        assertEquals(0.0, yawFromRotationMatrix(flatFacingNorth)!!, 1e-6)

        val uprightScreenFacingWest = floatArrayOf(0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f)
        assertEquals(-PI / 2, yawFromRotationMatrix(uprightScreenFacingWest)!!, 1e-6)
    }
}
