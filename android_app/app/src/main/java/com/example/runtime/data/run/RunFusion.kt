package com.example.runtime.data.run

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class GpsFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val timeMs: Long,
    val bearingDegrees: Double? = null,
    val speedMps: Double? = null,
)

class RunFusion(stepsAvailable: Boolean) {
    var usesSteps = stepsAvailable
        private set

    var distanceMeters = 0.0
        private set
    var strideMeters = DEFAULT_STRIDE_METERS
        private set
    var headingOffsetRadians: Double? = null
        private set

    private var originLatitude = 0.0
    private var originLongitude = 0.0
    private var metersPerDegreeLongitude = METERS_PER_DEGREE
    private var x = 0.0
    private var y = 0.0
    private var variance = 0.0
    private var hasPosition = false
    private var lastFixTimeMs = 0L
    private var predictedSinceFix = false

    private var deviceYaw: Double? = null
    private val stepTimes = ArrayDeque<Long>()
    private var gpsSpeedMps = 0.0

    private var lastGoodFix: GpsFix? = null
    private var windowGpsMeters = 0.0
    private var windowSteps = 0

    val latitude: Double?
        get() = if (hasPosition) originLatitude + y / METERS_PER_DEGREE else null

    val longitude: Double?
        get() = if (hasPosition) originLongitude + x / metersPerDegreeLongitude else null

    fun speedMps(nowMs: Long): Double {
        if (!usesSteps) return gpsSpeedMps
        while (stepTimes.isNotEmpty() && nowMs - stepTimes.first() > SPEED_WINDOW_MS) stepTimes.removeFirst()
        if (stepTimes.size < MIN_STEPS_FOR_SPEED) return 0.0
        val seconds = (nowMs - stepTimes.first()).coerceAtLeast(1L) / 1000.0
        return (stepTimes.size - 1) * strideMeters / seconds
    }

    fun onHeading(yawRadians: Double) {
        deviceYaw = yawRadians
    }

    fun onStep(timeMs: Long) {
        if (!usesSteps) return
        distanceMeters += strideMeters
        windowSteps++
        stepTimes.addLast(timeMs)

        val yaw = deviceYaw
        val offset = headingOffsetRadians
        if (hasPosition && yaw != null && offset != null) {
            val heading = yaw + offset
            x += strideMeters * sin(heading)
            y += strideMeters * cos(heading)
            variance += STEP_VARIANCE_M2
            predictedSinceFix = true
        }
    }

    fun onGps(fix: GpsFix) {
        if (fix.accuracyMeters > MAX_ACCURACY_METERS) return
        val measurementVariance = fix.accuracyMeters * fix.accuracyMeters

        if (!hasPosition) {
            originLatitude = fix.latitude
            originLongitude = fix.longitude
            metersPerDegreeLongitude = METERS_PER_DEGREE * cos(Math.toRadians(fix.latitude))
            x = 0.0
            y = 0.0
            variance = measurementVariance
            hasPosition = true
        } else {
            if (!predictedSinceFix) {
                val seconds = ((fix.timeMs - lastFixTimeMs).coerceAtLeast(0L)) / 1000.0
                variance += seconds * UNPREDICTED_VARIANCE_PER_SECOND
            }
            val (gx, gy) = toLocal(fix.latitude, fix.longitude)
            val gain = variance / (variance + measurementVariance)
            x += gain * (gx - x)
            y += gain * (gy - y)
            variance *= 1 - gain
        }
        lastFixTimeMs = fix.timeMs
        predictedSinceFix = false

        if (fix.accuracyMeters <= GOOD_ACCURACY_METERS) useGoodFix(fix)
    }

    fun pause() {
        lastGoodFix = null
        windowGpsMeters = 0.0
        windowSteps = 0
        stepTimes.clear()
        gpsSpeedMps = 0.0
    }

    private fun useGoodFix(fix: GpsFix) {
        val previous = lastGoodFix
        if (previous == null) {
            lastGoodFix = fix
            return
        }

        val seconds = (fix.timeMs - previous.timeMs) / 1000.0
        val meters = distanceBetween(previous, fix)
        val speed = if (seconds > 0) meters / seconds else 0.0
        if (meters < MIN_GPS_DELTA_METERS) {
            if (seconds > MAX_STATIONARY_SECONDS) lastGoodFix = fix
            return
        }
        lastGoodFix = fix
        if (speed > MAX_RUNNING_SPEED_MPS) return

        if (usesSteps && windowSteps == 0 && windowGpsMeters + meters >= STEPLESS_METERS) {
            usesSteps = false
            distanceMeters += windowGpsMeters
            windowGpsMeters = 0.0
        }

        if (!usesSteps) {
            distanceMeters += meters
            gpsSpeedMps = if (gpsSpeedMps == 0.0) speed else gpsSpeedMps * 0.7 + speed * 0.3
            return
        }

        windowGpsMeters += meters
        if (windowGpsMeters >= CALIBRATION_METERS && windowSteps >= CALIBRATION_STEPS) {
            val measured = (windowGpsMeters / windowSteps).coerceIn(MIN_STRIDE_METERS, MAX_STRIDE_METERS)
            strideMeters = strideMeters * (1 - STRIDE_LEARNING_RATE) + measured * STRIDE_LEARNING_RATE
            windowGpsMeters = 0.0
            windowSteps = 0
        }

        val yaw = deviceYaw ?: return
        val bearing = fix.bearingDegrees ?: Math.toDegrees(atan2(
            (fix.longitude - previous.longitude) * metersPerDegreeLongitude,
            (fix.latitude - previous.latitude) * METERS_PER_DEGREE,
        ))
        if (speed < MIN_SPEED_FOR_HEADING_MPS) return
        val measuredOffset = wrap(Math.toRadians(bearing) - yaw)
        val current = headingOffsetRadians
        headingOffsetRadians = if (current == null) {
            measuredOffset
        } else {
            wrap(current + wrap(measuredOffset - current) * HEADING_LEARNING_RATE)
        }
    }

    private fun toLocal(latitude: Double, longitude: Double): Pair<Double, Double> =
        (longitude - originLongitude) * metersPerDegreeLongitude to (latitude - originLatitude) * METERS_PER_DEGREE

    private fun distanceBetween(a: GpsFix, b: GpsFix): Double {
        val kx = METERS_PER_DEGREE * cos(Math.toRadians((a.latitude + b.latitude) / 2))
        return hypot((b.longitude - a.longitude) * kx, (b.latitude - a.latitude) * METERS_PER_DEGREE)
    }

    private fun wrap(angle: Double): Double {
        var value = angle
        while (value > PI) value -= 2 * PI
        while (value <= -PI) value += 2 * PI
        return value
    }

    companion object {
        const val DEFAULT_STRIDE_METERS = 1.0
        private const val METERS_PER_DEGREE = 111_320.0
        private const val MAX_ACCURACY_METERS = 50.0
        private const val GOOD_ACCURACY_METERS = 15.0
        private const val MIN_GPS_DELTA_METERS = 1.5
        private const val MAX_STATIONARY_SECONDS = 10.0
        private const val MAX_RUNNING_SPEED_MPS = 8.0
        private const val MIN_SPEED_FOR_HEADING_MPS = 1.5
        private const val STEP_VARIANCE_M2 = 1.0
        private const val UNPREDICTED_VARIANCE_PER_SECOND = 9.0
        private const val CALIBRATION_METERS = 50.0
        private const val CALIBRATION_STEPS = 30
        private const val STEPLESS_METERS = 100.0
        private const val MIN_STRIDE_METERS = 0.4
        private const val MAX_STRIDE_METERS = 2.0
        private const val STRIDE_LEARNING_RATE = 0.3
        private const val HEADING_LEARNING_RATE = 0.2
        private const val SPEED_WINDOW_MS = 5_000L
        private const val MIN_STEPS_FOR_SPEED = 3
    }
}

fun yawFromRotationMatrix(r: FloatArray): Double? {
    val forwardEast = r[1].toDouble()
    val forwardNorth = r[4].toDouble()
    if (hypot(forwardEast, forwardNorth) >= MIN_HORIZONTAL_COMPONENT) return atan2(forwardEast, forwardNorth)
    val backEast = -r[2].toDouble()
    val backNorth = -r[5].toDouble()
    if (hypot(backEast, backNorth) >= MIN_HORIZONTAL_COMPONENT) return atan2(backEast, backNorth)
    return null
}

private const val MIN_HORIZONTAL_COMPONENT = 0.5
