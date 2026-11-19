package com.example.runtime.data.run

import android.Manifest
import android.content.Context
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.runtime.MainActivity
import com.example.runtime.R
import com.example.runtime.data.push.NotificationNavigation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.roundToInt

data class RunMetrics(
    val isTracking: Boolean = false,
    val isPaused: Boolean = false,
    val isAutoPaused: Boolean = false,
    val distanceMeters: Double = 0.0,
    val elapsedSeconds: Long = 0L,
    val speedMetersPerSecond: Double = 0.0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val headingDegrees: Double? = null,
    val accuracyMeters: Double? = null,
    val locationError: Boolean = false,
)

data class TrailPoint(val latitude: Double, val longitude: Double)

object RunTrackingState {
    private val mutableMetrics = MutableStateFlow(RunMetrics())
    val metrics: StateFlow<RunMetrics> = mutableMetrics.asStateFlow()

    private val mutableTrail = MutableStateFlow<List<TrailPoint>>(emptyList())
    val trail: StateFlow<List<TrailPoint>> = mutableTrail.asStateFlow()

    internal fun update(metrics: RunMetrics) {
        mutableMetrics.value = metrics
    }

    private val mutableSplitTimes = MutableStateFlow<List<Long>>(emptyList())
    val splitTimes: StateFlow<List<Long>> = mutableSplitTimes.asStateFlow()
    private val splitTracker = RunSplitTracker()

    internal fun resetTrail() {
        mutableTrail.value = emptyList()
        mutableSplitTimes.value = emptyList()
        splitTracker.reset()
    }

    internal fun recordSplits(distanceMeters: Double, elapsedSeconds: Long): List<RunSplitMilestone> {
        val milestones = splitTracker.update(distanceMeters, elapsedSeconds)
        if (milestones.isNotEmpty()) {
            mutableSplitTimes.value = mutableSplitTimes.value + milestones.map { it.elapsedSeconds }
        }
        return milestones
    }

    internal fun appendTrail(latitude: Double, longitude: Double) {
        val last = mutableTrail.value.lastOrNull()
        if (last != null && distanceMeters(last.latitude, last.longitude, latitude, longitude) < MIN_TRAIL_SPACING_METERS) return
        mutableTrail.value = mutableTrail.value + TrailPoint(latitude, longitude)
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val result = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, result)
        return result[0].toDouble()
    }

    private const val MIN_TRAIL_SPACING_METERS = 3.0
}

class RunTrackingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var locationManager: LocationManager
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var sensorManager: SensorManager
    private var platformListener: LocationListener? = null
    private var fusedCallback: LocationCallback? = null
    private var fusion = RunFusion(stepsAvailable = false)
    private val rotationMatrix = FloatArray(9)
    private var elapsedBeforeResumeMs = 0L
    private var resumedAtMs = 0L
    private var isManuallyPaused = false
    private var isAutoPaused = false
    private val isPaused: Boolean get() = isManuallyPaused || isAutoPaused
    private val autoPauseController = RunAutoPauseController()
    private val paceAlertController = PaceAlertController()
    private var textToSpeech: TextToSpeech? = null
    private var textToSpeechReady = false
    private var pendingPaceAlert: PaceAlert? = null
    private val pendingNavigationCues = ArrayDeque<String>()
    private var lastSpeedLocation: Location? = null
    private var latestLocation: Location? = null
    private var headingDegrees: Double? = null
    private var lastNotificationUpdateMs = 0L

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_STEP_DETECTOR -> {
                    if (!isPaused) {
                        fusion.onStep(event.timestamp / NANOS_PER_MILLI)
                        publishMetrics()
                    }
                }
                Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR -> {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    yawFromRotationMatrix(rotationMatrix)?.let(fusion::onHeading)
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    private val ticker = object : Runnable {
        override fun run() {
            publishMetrics()
            val now = SystemClock.elapsedRealtime()
            if (now - lastNotificationUpdateMs >= NOTIFICATION_UPDATE_INTERVAL_MS) {
                updateNotification()
                lastNotificationUpdateMs = now
            }
            handler.postDelayed(this, TIMER_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LocationManager::class.java)
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(SensorManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRun(intent)
            ACTION_PAUSE -> pauseRun()
            ACTION_RESUME -> resumeRun()
            ACTION_STOP -> stopRun()
            ACTION_SPEAK_NAVIGATION -> intent?.let(::speakNavigationCue)
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startRun(intent: Intent) {
        if (RunTrackingState.metrics.value.isTracking) return

        configurePaceAlerts(intent)

        elapsedBeforeResumeMs = 0L
        resumedAtMs = SystemClock.elapsedRealtime()
        isManuallyPaused = false
        isAutoPaused = false
        autoPauseController.reset()
        lastSpeedLocation = null
        latestLocation = null
        headingDegrees = null
        fusion = RunFusion(stepsAvailable = canCountSteps())

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        RunTrackingState.update(RunMetrics(isTracking = true))
        RunTrackingState.resetTrail()
        startLocationUpdates()
        startSensors()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun canCountSteps(): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED
        return permitted && sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
    }

    private fun startSensors() {
        if (fusion.usesSteps) {
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let {
                sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_FASTEST)
            }
        }
        val rotation = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        rotation?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    private fun stopSensors() {
        sensorManager.unregisterListener(sensorListener)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            RunTrackingState.update(RunTrackingState.metrics.value.copy(locationError = true))
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_LOCATION_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach(::acceptLocation)
            }
        }
        fusedCallback = callback
        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener {
                    if (fusedCallback === callback) {
                        fusedCallback = null
                        startPlatformLocationUpdates()
                    }
                }
            fusedClient.lastLocation.addOnSuccessListener { location ->
                if (location != null && fusion.latitude == null &&
                    SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos <= MAX_LAST_LOCATION_AGE_NANOS
                ) {
                    acceptLocation(location)
                }
            }
        } catch (_: SecurityException) {
            RunTrackingState.update(RunTrackingState.metrics.value.copy(locationError = true))
        }
    }

    @Suppress("MissingPermission")
    private fun startPlatformLocationUpdates() {
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) {
            RunTrackingState.update(RunTrackingState.metrics.value.copy(locationError = true))
            return
        }

        val newListener = LocationListener(::acceptLocation)
        platformListener = newListener
        try {
            locationManager.requestLocationUpdates(provider, LOCATION_INTERVAL_MS, 0f, newListener, Looper.getMainLooper())
        } catch (_: SecurityException) {
            RunTrackingState.update(RunTrackingState.metrics.value.copy(locationError = true))
        }
    }

    private fun acceptLocation(location: Location) {
        if (isManuallyPaused) return
        val timeMs = location.elapsedRealtimeNanos / NANOS_PER_MILLI
        latestLocation = Location(location)
        val accuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble() else UNKNOWN_ACCURACY_METERS
        val measuredSpeed = location.speed.toDouble().takeIf { location.hasSpeed() && it.isFinite() && it >= 0.0 }
            ?: speedFromRecentLocation(location, timeMs, accuracyMeters)
        if (measuredSpeed != null && measuredSpeed >= MIN_HEADING_SPEED_MPS) {
            val measuredHeading = location.bearing.toDouble().takeIf { location.hasBearing() }
                ?: lastSpeedLocation?.let { bearingDegrees(it.latitude, it.longitude, location.latitude, location.longitude) }
            if (measuredHeading != null) headingDegrees = measuredHeading
        }
        if (accuracyMeters <= GOOD_LOCATION_ACCURACY_METERS) lastSpeedLocation = Location(location)

        val fix = GpsFix(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = accuracyMeters,
            timeMs = timeMs,
            bearingDegrees = headingDegrees,
            speedMps = measuredSpeed,
        )

        if (isAutoPaused) {
            if (autoPauseController.update(measuredSpeed?.times(METERS_PER_SECOND_TO_KMH), timeMs) == RunAutoPauseController.Transition.RESUMED) {
                isAutoPaused = false
                resumedAtMs = SystemClock.elapsedRealtime()
                fusion.pause()
                fusion.onGps(fix)
                publishMetrics(locationError = false)
                updateNotification()
            } else {
                publishMetrics(locationError = false, location = location)
            }
            return
        }

        fusion.onGps(fix)
        if (autoPauseController.update(measuredSpeed?.times(METERS_PER_SECOND_TO_KMH), timeMs) == RunAutoPauseController.Transition.PAUSED) {
            elapsedBeforeResumeMs += SystemClock.elapsedRealtime() - resumedAtMs
            isAutoPaused = true
            fusion.pause()
            updateNotification()
        }
        publishMetrics(locationError = false)
    }

    private fun pauseRun() {
        if (!RunTrackingState.metrics.value.isTracking || isPaused) return
        elapsedBeforeResumeMs += SystemClock.elapsedRealtime() - resumedAtMs
        isManuallyPaused = true
        autoPauseController.reset()
        removeLocationUpdates()
        stopSensors()
        fusion.pause()
        publishMetrics()
        updateNotification()
    }

    private fun resumeRun() {
        if (!RunTrackingState.metrics.value.isTracking || !isPaused) return
        if (isManuallyPaused) {
            isManuallyPaused = false
            startLocationUpdates()
            startSensors()
        } else {
            isAutoPaused = false
            latestLocation?.let { location ->
                fusion.pause()
                fusion.onGps(gpsFix(location))
            }
        }
        autoPauseController.reset()
        resumedAtMs = SystemClock.elapsedRealtime()
        publishMetrics()
        updateNotification()
    }

    private fun stopRun() {
        if (RunTrackingState.metrics.value.isTracking && !isPaused) {
            elapsedBeforeResumeMs += SystemClock.elapsedRealtime() - resumedAtMs
        }
        isManuallyPaused = false
        isAutoPaused = false
        autoPauseController.reset()
        paceAlertController.reset()
        removeLocationUpdates()
        stopSensors()
        handler.removeCallbacks(ticker)
        publishMetrics(isTracking = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publishMetrics(
        locationError: Boolean = RunTrackingState.metrics.value.locationError,
        isTracking: Boolean = RunTrackingState.metrics.value.isTracking,
        location: Location? = null,
    ) {
        val latitude = fusion.latitude
        val longitude = fusion.longitude
        if (isTracking && !isPaused && latitude != null && longitude != null) {
            RunTrackingState.appendTrail(latitude, longitude)
        }
        val elapsedMs = elapsedBeforeResumeMs +
            if (isTracking && !isPaused) SystemClock.elapsedRealtime() - resumedAtMs else 0L
        if (isTracking) {
            RunTrackingState.recordSplits(fusion.distanceMeters, elapsedMs / 1000L)
                .lastOrNull()
                ?.let(::speakKilometerSplit)
        }
        val speedMetersPerSecond = if (isPaused) 0.0 else fusion.speedMps(SystemClock.elapsedRealtime())
        if (isTracking) {
            paceAlertController.update(
                distanceMeters = fusion.distanceMeters,
                speedMetersPerSecond = speedMetersPerSecond,
                nowMs = SystemClock.elapsedRealtime(),
                isPaused = isPaused,
            )?.let(::speakPaceAlert)
        }
        RunTrackingState.update(
            RunMetrics(
                isTracking = isTracking,
                isPaused = isPaused,
                isAutoPaused = isAutoPaused,
                distanceMeters = fusion.distanceMeters,
                elapsedSeconds = elapsedMs / 1000L,
                speedMetersPerSecond = speedMetersPerSecond,
                latitude = location?.latitude ?: fusion.latitude ?: RunTrackingState.metrics.value.latitude,
                longitude = location?.longitude ?: fusion.longitude ?: RunTrackingState.metrics.value.longitude,
                headingDegrees = headingDegrees,
                accuracyMeters = (location?.takeIf { it.hasAccuracy() } ?: latestLocation?.takeIf { it.hasAccuracy() })
                    ?.accuracy?.toDouble(),
                locationError = locationError,
            ),
        )
    }

    private fun gpsFix(location: Location) = GpsFix(
        latitude = location.latitude,
        longitude = location.longitude,
        accuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble() else UNKNOWN_ACCURACY_METERS,
        timeMs = location.elapsedRealtimeNanos / NANOS_PER_MILLI,
        bearingDegrees = headingDegrees,
        speedMps = location.speed.toDouble().takeIf { location.hasSpeed() },
    )

    private fun speedFromRecentLocation(location: Location, timeMs: Long, accuracyMeters: Double): Double? {
        if (accuracyMeters > GOOD_LOCATION_ACCURACY_METERS) return null
        val previous = lastSpeedLocation ?: return null
        val elapsedMs = timeMs - previous.elapsedRealtimeNanos / NANOS_PER_MILLI
        if (elapsedMs <= 0L) return null
        val result = FloatArray(1)
        Location.distanceBetween(previous.latitude, previous.longitude, location.latitude, location.longitude, result)
        return (result[0].toDouble() / (elapsedMs / 1000.0)).takeIf { it <= MAX_REASONABLE_SPEED_MPS }
    }

    private fun bearingDegrees(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val result = FloatArray(3)
        Location.distanceBetween(fromLat, fromLon, toLat, toLon, result)
        return ((result[1].toDouble() % 360.0) + 360.0) % 360.0
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun configurePaceAlerts(intent: Intent) {
        val alertOnSlow = intent.getBooleanExtra(EXTRA_PACE_ALERT_ON_SLOW, false)
        val alertOnFast = intent.getBooleanExtra(EXTRA_PACE_ALERT_ON_FAST, false)
        val paceMin = intent.getIntExtra(EXTRA_PACE_MIN_SECONDS, -1)
        val paceMax = intent.getIntExtra(EXTRA_PACE_MAX_SECONDS, -1)
        if ((!alertOnSlow && !alertOnFast) || paceMin !in 120..900 || paceMax !in paceMin..900) {
            paceAlertController.configure(null)
            return
        }

        val starts = intent.getIntArrayExtra(EXTRA_PACE_SEGMENT_STARTS) ?: intArrayOf()
        val ends = intent.getIntArrayExtra(EXTRA_PACE_SEGMENT_ENDS) ?: intArrayOf()
        val mins = intent.getIntArrayExtra(EXTRA_PACE_SEGMENT_MINS) ?: intArrayOf()
        val maxes = intent.getIntArrayExtra(EXTRA_PACE_SEGMENT_MAXES) ?: intArrayOf()
        val count = minOf(starts.size, ends.size, mins.size, maxes.size)
        val segments = (0 until count).mapNotNull { index ->
            val start = starts[index]
            val end = ends[index]
            val min = mins[index]
            val max = maxes[index]
            if (start < 0 || end <= start || min !in 120..900 || max !in min..900) null
            else PaceAlertSegment(start, end, min, max)
        }
        paceAlertController.configure(
            PaceAlertSettings(
                paceMinSeconds = paceMin,
                paceMaxSeconds = paceMax,
                alertOnSlow = alertOnSlow,
                alertOnFast = alertOnFast,
                segments = segments,
            ),
        )
        ensureTextToSpeech()
    }

    private fun ensureTextToSpeech() {
        if (textToSpeech != null) return
        textToSpeech = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val locale = resources.configuration.locales[0] ?: Locale.getDefault()
            textToSpeech?.language = locale
            textToSpeechReady = true
            while (pendingNavigationCues.isNotEmpty()) {
                speakQueuedMessage(pendingNavigationCues.removeFirst())
            }
            pendingPaceAlert?.let { alert ->
                pendingPaceAlert = null
                speakPaceAlert(alert)
            }
        }
    }

    private fun speakPaceAlert(alert: PaceAlert) {
        if (!textToSpeechReady) {
            pendingPaceAlert = alert
            return
        }
        val message = getString(
            if (alert == PaceAlert.TOO_SLOW) R.string.run_pace_alert_slow else R.string.run_pace_alert_fast,
        )
        textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "pace-alert-${SystemClock.elapsedRealtime()}")
    }

    private fun speakNavigationCue(intent: Intent) {
        val message = intent.getStringExtra(EXTRA_NAVIGATION_TEXT) ?: run {
            val messageId = intent.getIntExtra(EXTRA_NAVIGATION_MESSAGE_ID, 0)
            if (messageId == 0) return
            val distanceMeters = intent.getIntExtra(EXTRA_NAVIGATION_DISTANCE_METERS, -1).takeIf { it >= 0 }
            if (distanceMeters == null) getString(messageId) else getString(messageId, distanceMeters)
        }
        speakNavigationMessage(message)
    }

    private fun speakKilometerSplit(milestone: RunSplitMilestone) {
        val elapsed = milestone.elapsedSeconds
        val hours = elapsed / 3600
        val minutes = elapsed % 3600 / 60
        val seconds = elapsed % 60
        val duration = if (hours > 0) {
            getString(R.string.run_voice_duration_hours, hours, minutes, seconds)
        } else {
            getString(R.string.run_voice_duration_minutes, minutes, seconds)
        }
        val pacePerKilometer = (elapsed.toDouble() / milestone.kilometer).roundToInt()
        val paceMinutes = pacePerKilometer / 60
        val paceSeconds = pacePerKilometer % 60
        val pace = getString(R.string.run_voice_pace, paceMinutes, paceSeconds)
        val message = getString(R.string.run_voice_kilometer_summary, milestone.kilometer, milestone.kilometer, duration, pace)
        speakNavigationMessage(message)
    }

    private fun speakNavigationMessage(message: String) {
        if (!textToSpeechReady) {
            pendingNavigationCues.addLast(message)
            ensureTextToSpeech()
            return
        }
        speakQueuedMessage(message)
    }

    private fun speakQueuedMessage(message: String) {
        textToSpeech?.speak(message, TextToSpeech.QUEUE_ADD, null, "run-cue-${SystemClock.elapsedRealtime()}")
    }

    private fun buildNotification(): Notification {
        val metrics = RunTrackingState.metrics.value
        val content = getString(
            if (isPaused) R.string.run_notification_paused else R.string.run_notification_active,
            (metrics.distanceMeters / 1000.0).let { String.format(java.util.Locale.US, "%.2f", it) },
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .putExtra(NotificationNavigation.EXTRA_KIND, NotificationNavigation.KIND_RUN)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_route)
            .setContentTitle(getString(R.string.run_notification_title))
            .setContentText(content)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.run_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun removeLocationUpdates() {
        fusedCallback?.let { fusedClient.removeLocationUpdates(it) }
        fusedCallback = null
        platformListener?.let { locationManager.removeUpdates(it) }
        platformListener = null
    }

    override fun onDestroy() {
        removeLocationUpdates()
        stopSensors()
        handler.removeCallbacks(ticker)
        textToSpeech?.shutdown()
        textToSpeech = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.example.runtime.run.START"
        const val ACTION_PAUSE = "com.example.runtime.run.PAUSE"
        const val ACTION_RESUME = "com.example.runtime.run.RESUME"
        const val ACTION_STOP = "com.example.runtime.run.STOP"
        const val ACTION_SPEAK_NAVIGATION = "com.example.runtime.run.SPEAK_NAVIGATION"

        const val EXTRA_NAVIGATION_MESSAGE_ID = "com.example.runtime.run.NAVIGATION_MESSAGE_ID"
        const val EXTRA_NAVIGATION_DISTANCE_METERS = "com.example.runtime.run.NAVIGATION_DISTANCE_METERS"
        const val EXTRA_NAVIGATION_TEXT = "com.example.runtime.run.NAVIGATION_TEXT"

        const val EXTRA_PACE_MIN_SECONDS = "com.example.runtime.run.PACE_MIN_SECONDS"
        const val EXTRA_PACE_MAX_SECONDS = "com.example.runtime.run.PACE_MAX_SECONDS"
        const val EXTRA_PACE_ALERT_ON_SLOW = "com.example.runtime.run.PACE_ALERT_ON_SLOW"
        const val EXTRA_PACE_ALERT_ON_FAST = "com.example.runtime.run.PACE_ALERT_ON_FAST"
        const val EXTRA_PACE_SEGMENT_STARTS = "com.example.runtime.run.PACE_SEGMENT_STARTS"
        const val EXTRA_PACE_SEGMENT_ENDS = "com.example.runtime.run.PACE_SEGMENT_ENDS"
        const val EXTRA_PACE_SEGMENT_MINS = "com.example.runtime.run.PACE_SEGMENT_MINS"
        const val EXTRA_PACE_SEGMENT_MAXES = "com.example.runtime.run.PACE_SEGMENT_MAXES"

        fun speakNavigationCue(context: Context, messageId: Int, distanceMeters: Int? = null) {
            context.startService(
                Intent(context, RunTrackingService::class.java)
                    .setAction(ACTION_SPEAK_NAVIGATION)
                    .putExtra(EXTRA_NAVIGATION_MESSAGE_ID, messageId)
                    .putExtra(EXTRA_NAVIGATION_DISTANCE_METERS, distanceMeters ?: -1),
            )
        }

        fun speakNavigationText(context: Context, message: String) {
            context.startService(
                Intent(context, RunTrackingService::class.java)
                    .setAction(ACTION_SPEAK_NAVIGATION)
                    .putExtra(EXTRA_NAVIGATION_TEXT, message),
            )
        }

        private const val NOTIFICATION_CHANNEL_ID = "run_tracking"
        private const val NOTIFICATION_ID = 4102
        private const val TIMER_INTERVAL_MS = 1_000L
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 5_000L
        private const val LOCATION_INTERVAL_MS = 1_000L
        private const val FASTEST_LOCATION_INTERVAL_MS = 500L
        private const val MAX_LAST_LOCATION_AGE_NANOS = 30_000_000_000L
        private const val UNKNOWN_ACCURACY_METERS = 30.0
        private const val GOOD_LOCATION_ACCURACY_METERS = 20.0
        private const val MIN_HEADING_SPEED_MPS = 1.0
        private const val MAX_REASONABLE_SPEED_MPS = 12.0
        private const val METERS_PER_SECOND_TO_KMH = 3.6
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
