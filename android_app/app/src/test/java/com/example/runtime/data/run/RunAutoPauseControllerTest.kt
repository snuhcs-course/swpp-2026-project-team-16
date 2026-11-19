package com.example.runtime.data.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunAutoPauseControllerTest {
    @Test
    fun pausesAfterTwoSecondsBelowPointFiveKmh() {
        val controller = RunAutoPauseController()

        assertNull(controller.update(0.5, 100L))
        assertNull(controller.update(0.5, 1_900L))
        assertEquals(RunAutoPauseController.Transition.PAUSED, controller.update(0.5, 2_100L))
    }

    @Test
    fun movementBeforeTheDwellTimeResetsThePauseTimer() {
        val controller = RunAutoPauseController()

        assertNull(controller.update(0.2, 100L))
        assertNull(controller.update(0.8, 2_000L))
        assertNull(controller.update(0.2, 2_100L))
        assertNull(controller.update(0.2, 3_900L))
        assertEquals(RunAutoPauseController.Transition.PAUSED, controller.update(0.2, 4_100L))
    }

    @Test
    fun resumesAfterTwoSecondsAtOrAboveOneKmh() {
        val controller = RunAutoPauseController()
        assertNull(controller.update(0.0, 0L))
        assertEquals(RunAutoPauseController.Transition.PAUSED, controller.update(0.0, 2_000L))

        assertNull(controller.update(1.0, 2_100L))
        assertNull(controller.update(1.0, 4_000L))
        assertEquals(RunAutoPauseController.Transition.RESUMED, controller.update(1.0, 4_100L))
    }
}
