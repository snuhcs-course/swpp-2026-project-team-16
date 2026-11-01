package com.example.runtime.ui.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewPathTest {

    private val start = 37.5796 to 126.9770

    @Test
    fun loop_startsAndEndsAtStart() {
        val path = previewPath(start, null, 3_000)

        assertEquals(start.first, path.first().first, 0.000001)
        assertEquals(start.second, path.first().second, 0.000001)
        assertEquals(start.first, path.last().first, 0.000001)
        assertEquals(start.second, path.last().second, 0.000001)
    }

    @Test
    fun loop_lengthIsCloseToRequestedDistance() {
        val path = previewPath(start, null, 3_000)
        val length = path.zipWithNext(::distanceBetweenMeters).sum()

        assertEquals(3_000.0, length, 300.0)
    }

    @Test
    fun loop_radiusIsCappedForLongDistances() {
        val path = previewPath(start, null, 100_000)

        assertTrue(path.all { distanceBetweenMeters(start, it) < 6_000.0 })
    }

    @Test
    fun pointToPoint_goesFromStartToEnd() {
        val end = 37.5704 to 126.9921
        val path = previewPath(start, end, 3_000)

        assertEquals(start.first, path.first().first, 0.000001)
        assertEquals(start.second, path.first().second, 0.000001)
        assertEquals(end.first, path.last().first, 0.000001)
        assertEquals(end.second, path.last().second, 0.000001)
    }
}
