package com.example.runtime.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun formatKm_dropsTrailingZeros() {
        assertEquals("3", formatKm(3_000))
        assertEquals("2.5", formatKm(2_500))
        assertEquals("2.95", formatKm(2_954))
    }

    @Test
    fun formatDistance_addsUnit() {
        assertEquals("3.06km", formatDistance(3_060))
    }

    @Test
    fun routeTitle_usesStartAsEndForLoops() {
        assertEquals("A -> A [2km]", routeTitle("A", "", 2_000))
        assertEquals("2km", routeTitle("", "", 2_000))
    }
}
