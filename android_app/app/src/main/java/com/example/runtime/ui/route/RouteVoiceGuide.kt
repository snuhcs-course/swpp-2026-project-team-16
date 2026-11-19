package com.example.runtime.ui.route

import kotlin.math.roundToInt

/** A one-time voice cue derived from the planned route and current matched progress. */
data class RouteVoiceCue(
    val kind: Kind,
    val direction: RouteTurnDirection? = null,
    val distanceMeters: Int? = null,
) {
    enum class Kind { START, OFF_ROUTE, REJOINED, TURN_APPROACHING, TURN_NOW, ARRIVED }
}

data class RouteUphillSection(val startMeters: Int, val endMeters: Int)

class UphillVoiceGuide(sections: List<RouteUphillSection>) {
    private val sections = sections.sortedBy { it.startMeters }
    private var sectionIndex = 0

    /** Returns the upcoming hill's approximate distance once, within the 200 m warning window. */
    fun update(progressMeters: Double): Int? {
        if (!progressMeters.isFinite()) return null
        while (sectionIndex < sections.size && progressMeters > sections[sectionIndex].endMeters) {
            sectionIndex++
        }
        val next = sections.getOrNull(sectionIndex) ?: return null
        val distanceToHill = next.startMeters - progressMeters
        if (distanceToHill < 0.0 || distanceToHill > WARNING_DISTANCE_METERS) return null
        sectionIndex++
        return ((distanceToHill / 10.0).roundToInt() * 10).coerceAtLeast(10)
    }

    private companion object {
        const val WARNING_DISTANCE_METERS = 200.0
    }
}

class RouteVoiceGuide(
    private val turns: List<RouteTurn>,
    private val routeLengthMeters: Double,
) {
    private var started = false
    private var wasOffRoute = false
    private var turnIndex = 0
    private var approachAnnounced = false
    private var turnAnnounced = false
    private var arrived = false

    fun update(progressMeters: Double, isTracking: Boolean, isPaused: Boolean, isOffRoute: Boolean): RouteVoiceCue? {
        if (!isTracking || isPaused || routeLengthMeters <= 0.0 || !progressMeters.isFinite()) return null
        if (!started) {
            started = true
            return RouteVoiceCue(RouteVoiceCue.Kind.START)
        }

        if (isOffRoute != wasOffRoute) {
            wasOffRoute = isOffRoute
            return RouteVoiceCue(
                if (isOffRoute) RouteVoiceCue.Kind.OFF_ROUTE else RouteVoiceCue.Kind.REJOINED,
            )
        }
        if (isOffRoute || arrived) return null

        if (progressMeters >= routeLengthMeters - ARRIVAL_NOTICE_METERS) {
            arrived = true
            return RouteVoiceCue(RouteVoiceCue.Kind.ARRIVED)
        }

        while (turnIndex < turns.size && progressMeters > turns[turnIndex].distanceAlongMeters + TURN_NOW_DISTANCE_METERS) {
            turnIndex++
            approachAnnounced = false
            turnAnnounced = false
        }
        val turn = turns.getOrNull(turnIndex) ?: return null
        val distanceToTurn = turn.distanceAlongMeters - progressMeters
        if (distanceToTurn <= TURN_NOW_DISTANCE_METERS && !turnAnnounced) {
            turnAnnounced = true
            return RouteVoiceCue(RouteVoiceCue.Kind.TURN_NOW, turn.direction)
        }
        if (distanceToTurn <= TURN_APPROACH_DISTANCE_METERS && distanceToTurn > TURN_NOW_DISTANCE_METERS && !approachAnnounced) {
            approachAnnounced = true
            return RouteVoiceCue(
                RouteVoiceCue.Kind.TURN_APPROACHING,
                turn.direction,
                distanceToTurn.roundToNearestTenMeters(),
            )
        }
        return null
    }

    private fun Double.roundToNearestTenMeters(): Int =
        ((this / 10.0).roundToInt() * 10).coerceAtLeast(10)

    private companion object {
        const val TURN_APPROACH_DISTANCE_METERS = 100.0
        const val TURN_NOW_DISTANCE_METERS = 20.0
        const val ARRIVAL_NOTICE_METERS = 15.0
    }
}
