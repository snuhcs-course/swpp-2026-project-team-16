package com.example.runtime.ui.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun usesWholeRouteWhenRunnerMovesBeyondTheNormalLookAheadWindow() {
        val matcher = RouteProgressMatcher(
            listOf(RouteCoordinate(37.0, 127.0), RouteCoordinate(37.0, 127.005)),
        )

        val result = matcher.update(37.0, 127.0045, timeMs = 1_000L, accuracyMeters = 5.0)

        assertFalse(result.isOffRoute)
        assertTrue(result.distanceAlongMeters > 390.0)
        assertEquals(0.0, result.distanceFromRouteMeters, 1.0)
    }

    @Test
    fun ignoresPoorAccuracyFixesForOffRouteStateChanges() {
        val matcher = RouteProgressMatcher(
            listOf(RouteCoordinate(37.0, 127.0), RouteCoordinate(37.0, 127.005)),
        )

        matcher.update(37.0, 127.001, 0L, accuracyMeters = 5.0)
        val result = matcher.update(37.001, 127.001, 10_000L, accuracyMeters = 40.0)

        assertFalse(result.isOffRoute)
    }

    @Test
    fun allowsForGpsAccuracyBeforeMarkingRunnerOffRoute() {
        val matcher = RouteProgressMatcher(
            listOf(RouteCoordinate(37.0, 127.0), RouteCoordinate(37.0, 127.005)),
        )

        matcher.update(37.0, 127.001, timeMs = 0L, accuracyMeters = 5.0)
        // This fix is about 50 m from the route, but the phone reports 40 m
        // accuracy. The offset can still be explained by location uncertainty.
        val result = matcher.update(37.00045, 127.001, timeMs = 10_000L, accuracyMeters = 40.0)

        assertFalse(result.isOffRoute)
    }

    @Test
    fun flagsOffRouteOnlyAfterStayingAwayLongEnough() {
        val matcher = RouteProgressMatcher(
            listOf(
                RouteCoordinate(37.0, 127.0),
                RouteCoordinate(37.0, 127.005),
            ),
        )

        matcher.update(37.0, 127.001, 0L)
        assertFalse(matcher.update(37.001, 127.001, 1_000L).isOffRoute)
        assertFalse(matcher.update(37.001, 127.0012, 3_000L).isOffRoute)
        assertTrue(matcher.update(37.001, 127.0014, 5_000L).isOffRoute)
    }

    @Test
    fun ignoresABriefGpsJumpAwayFromTheRoute() {
        val matcher = RouteProgressMatcher(
            listOf(
                RouteCoordinate(37.0, 127.0),
                RouteCoordinate(37.0, 127.005),
            ),
        )

        matcher.update(37.0, 127.001, 0L)
        matcher.update(37.001, 127.001, 1_000L)
        matcher.update(37.0, 127.0011, 2_000L)
        val result = matcher.update(37.001, 127.0012, 6_000L)

        assertFalse(result.isOffRoute)
    }

    @Test
    fun keepsProgressWhileOffRouteAndResumesWhenRejoiningAhead() {
        val matcher = RouteProgressMatcher(
            listOf(
                RouteCoordinate(37.0, 127.0),
                RouteCoordinate(37.0, 127.01),
            ),
        )

        matcher.update(37.0, 127.001, 0L)
        val before = matcher.update(37.0, 127.001, 500L).distanceAlongMeters
        matcher.update(37.002, 127.002, 1_000L)
        val offRoute = matcher.update(37.002, 127.006, 6_000L)

        assertTrue(offRoute.isOffRoute)
        assertEquals(before, offRoute.distanceAlongMeters, 0.5)

        val rejoined = matcher.update(37.0, 127.006, 7_000L)

        assertFalse(rejoined.isOffRoute)
        assertTrue(rejoined.distanceAlongMeters > before + 400.0)
    }

    @Test
    fun rejoiningALoopNearTheFinishDoesNotRestartFromTheStart() {
        val matcher = RouteProgressMatcher(
            listOf(
                RouteCoordinate(37.0, 127.0),
                RouteCoordinate(37.0, 127.001),
                RouteCoordinate(37.001, 127.001),
                RouteCoordinate(37.001, 127.0),
                RouteCoordinate(37.0, 127.0),
            ),
        )
        listOf(
            37.0 to 127.0005,
            37.0 to 127.001,
            37.0005 to 127.001,
            37.001 to 127.001,
            37.001 to 127.0005,
            37.001 to 127.0,
            37.0005 to 127.0,
        ).forEachIndexed { index, (latitude, longitude) ->
            matcher.update(latitude, longitude, index * 1_000L)
        }
        matcher.update(37.0005, 126.999, 10_000L)
        assertTrue(matcher.update(37.0005, 126.999, 16_000L).isOffRoute)

        val rejoined = matcher.update(37.00002, 127.00008, 20_000L)

        assertFalse(rejoined.isOffRoute)
        assertTrue(rejoined.distanceAlongMeters > rejoined.routeLengthMeters - 20.0)
    }

    @Test
    fun continuesFromTheCurrentPositionAfterALocationGap() {
        val matcher = RouteProgressMatcher(
            listOf(
                RouteCoordinate(37.0, 127.0),
                RouteCoordinate(37.0, 127.002),
                RouteCoordinate(37.0, 127.006),
            ),
        )
        matcher.update(37.0, 127.0001, 0L)

        val afterGap = matcher.update(37.0, 127.004, 60_000L)

        assertFalse(afterGap.isOffRoute)
        assertTrue(afterGap.distanceAlongMeters > 340.0)
    }

    @Test
    fun resumesGuidanceWhenRejoiningBehindTheLastMatchedPoint() {
        val matcher = RouteProgressMatcher(
            listOf(
                RouteCoordinate(37.0, 127.0),
                RouteCoordinate(37.0, 127.01),
            ),
        )

        // Advance in steps within the matcher's normal 250 m look-ahead window.
        matcher.update(37.0, 127.001, 0L)
        matcher.update(37.0, 127.003, 500L)
        val laterProgress = matcher.update(37.0, 127.005, 1_000L).distanceAlongMeters
        matcher.update(37.002, 127.006, 2_000L)
        val offRoute = matcher.update(37.002, 127.006, 6_500L)
        assertTrue(offRoute.isOffRoute)

        val rejoinedBehind = matcher.update(37.0, 127.0025, 7_000L)

        assertFalse(rejoinedBehind.isOffRoute)
        assertTrue(rejoinedBehind.distanceAlongMeters < laterProgress - 100.0)
        assertTrue(rejoinedBehind.distanceFromRouteMeters <= RouteProgressMatcher.REJOIN_DISTANCE_METERS)
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

    @Test
    fun identifiesAUturnFromTheGeneratedCourseGeometry() {
        val guide = RouteTurnGuide(
            listOf(
                RouteCoordinate(37.0, 127.0),
                RouteCoordinate(37.001, 127.0),
                RouteCoordinate(37.0, 127.00001),
            ),
        )

        assertTrue(guide.turns.any { it.direction == RouteTurnDirection.UTURN })
    }

    @Test
    fun splitsTheRouteAtTheRunnerProgressForMapRendering() {
        val route = listOf(
            RouteCoordinate(37.0, 127.0),
            RouteCoordinate(37.0, 127.002),
        )
        val passed = sliceRoute(route, 0.0, 100.0)
        val remaining = sliceRoute(route, 100.0, 220.0)

        assertEquals(127.0, passed.first().longitude, 0.000001)
        assertEquals(passed.last().longitude, remaining.first().longitude, 0.000001)
        assertEquals(
            100.0,
            distanceBetweenMeters(
                route.first().latitude to route.first().longitude,
                passed.last().latitude to passed.last().longitude,
            ),
            1.0,
        )
        assertTrue(routeDirectionChunks(route, 100.0).isNotEmpty())
    }

    @Test
    fun voiceGuideAnnouncesTurnAroundOneHundredMetersAndAgainNearTheTurnOnlyOnce() {
        val turn = RouteTurn(200.0, RouteTurnDirection.LEFT)
        val guide = RouteVoiceGuide(listOf(turn), routeLengthMeters = 500.0)

        assertEquals(RouteVoiceCue.Kind.START, guide.update(0.0, true, false, false)?.kind)
        val approach = guide.update(100.0, true, false, false)
        assertEquals(RouteVoiceCue.Kind.TURN_APPROACHING, approach?.kind)
        assertEquals(RouteTurnDirection.LEFT, approach?.direction)
        assertEquals(100, approach?.distanceMeters)
        assertEquals(RouteVoiceCue.Kind.TURN_NOW, guide.update(185.0, true, false, false)?.kind)
        assertEquals(null, guide.update(190.0, true, false, false))
        assertEquals(null, guide.update(205.0, true, false, false))
    }

    @Test
    fun voiceGuideAnnouncesLeavingAndRejoiningTheRouteAndArrival() {
        val guide = RouteVoiceGuide(emptyList(), routeLengthMeters = 500.0)

        guide.update(0.0, true, false, false)
        assertEquals(RouteVoiceCue.Kind.OFF_ROUTE, guide.update(20.0, true, false, true)?.kind)
        assertEquals(null, guide.update(30.0, true, false, true))
        assertEquals(RouteVoiceCue.Kind.REJOINED, guide.update(40.0, true, false, false)?.kind)
        assertEquals(RouteVoiceCue.Kind.ARRIVED, guide.update(490.0, true, false, false)?.kind)
        assertEquals(null, guide.update(495.0, true, false, false))
    }

    @Test
    fun voiceGuideDoesNotGiveRouteInstructionsDuringFreeTracking() {
        val guide = RouteVoiceGuide(emptyList(), routeLengthMeters = 0.0)

        assertEquals(null, guide.update(0.0, true, false, false))
    }

    @Test
    fun uphillGuideWarnsWithinTwoHundredMetersOnlyOncePerSection() {
        val guide = UphillVoiceGuide(
            listOf(
                RouteUphillSection(500, 1000),
                RouteUphillSection(1800, 2300),
            ),
        )

        assertEquals(null, guide.update(250.0))
        assertEquals(200, guide.update(300.0))
        assertEquals(null, guide.update(350.0))
        assertEquals(200, guide.update(1600.0))
    }
}
