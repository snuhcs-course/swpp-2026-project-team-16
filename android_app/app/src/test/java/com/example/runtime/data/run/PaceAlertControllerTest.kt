package com.example.runtime.data.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaceAlertControllerTest {

    @Test
    fun warnsAfterThePaceLeavesTheSelectedBandForTenSeconds() {
        val controller = PaceAlertController()
        controller.configure(PaceAlertSettings(330, 390, alertOnSlow = true, alertOnFast = false))
        val speedThirtySecondsOutsideTheBand = 1000.0 / 420.0

        assertNull(controller.update(2100.0, speedThirtySecondsOutsideTheBand, 0L, isPaused = false))
        assertNull(controller.update(2200.0, speedThirtySecondsOutsideTheBand, 9_999L, isPaused = false))
        assertEquals(PaceAlert.TOO_SLOW, controller.update(2300.0, speedThirtySecondsOutsideTheBand, 10_000L, isPaused = false))
    }

    @Test
    fun emitsEachWarningTypeOnlyOnceUntilTheNextKilometer() {
        val controller = PaceAlertController(confirmDurationMs = 0L)
        controller.configure(PaceAlertSettings(330, 390, alertOnSlow = true, alertOnFast = true))
        val slowSpeed = 1000.0 / 420.0

        assertEquals(PaceAlert.TOO_SLOW, controller.update(2100.0, slowSpeed, 1_000L, isPaused = false))
        val fastSpeed = 1000.0 / 300.0
        assertEquals(PaceAlert.TOO_FAST, controller.update(2400.0, fastSpeed, 2_000L, isPaused = false))
        assertNull(controller.update(2600.0, slowSpeed, 3_000L, isPaused = false))
        assertNull(controller.update(2800.0, fastSpeed, 4_000L, isPaused = false))
        assertEquals(PaceAlert.TOO_SLOW, controller.update(3000.0, slowSpeed, 5_000L, isPaused = false))
    }

    @Test
    fun slowWarningRequiresAtLeastThreeKmhMovement() {
        val controller = PaceAlertController(confirmDurationMs = 0L)
        controller.configure(PaceAlertSettings(330, 390, alertOnSlow = true, alertOnFast = false))

        assertNull(controller.update(2100.0, 2.9 / 3.6, 1_000L, isPaused = false))
        assertEquals(PaceAlert.TOO_SLOW, controller.update(2200.0, 3.0 / 3.6, 2_000L, isPaused = false))
    }

    @Test
    fun warnsOnlyWhenThirtySecondsBeyondTheFastEdge() {
        val controller = PaceAlertController(confirmDurationMs = 0L)
        controller.configure(PaceAlertSettings(330, 390, alertOnSlow = false, alertOnFast = true))

        assertNull(controller.update(1200.0, 1000.0 / 315.0, 1_000L, isPaused = false))
        assertEquals(PaceAlert.TOO_FAST, controller.update(1300.0, 1000.0 / 300.0, 2_000L, isPaused = false))
    }

    @Test
    fun customPhaseUsesItsOwnPaceBandAndPauseDoesNotTriggerAWarning() {
        val controller = PaceAlertController(confirmDurationMs = 0L)
        controller.configure(
            PaceAlertSettings(
                paceMinSeconds = 350,
                paceMaxSeconds = 390,
                alertOnSlow = true,
                alertOnFast = false,
                segments = listOf(PaceAlertSegment(2000, 3000, 300, 360)),
            ),
        )

        assertNull(controller.update(2500.0, 1000.0 / 400.0, 1_000L, isPaused = true))
        assertEquals(PaceAlert.TOO_SLOW, controller.update(2500.0, 1000.0 / 400.0, 2_000L, isPaused = false))
    }
}
