package com.example.runtime.data.run

import android.Manifest
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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.runtime.MainActivity
import com.example.runtime.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RunMetrics(
    val isTracking: Boolean = false,
    val isPaused: Boolean = false,
    val distanceMeters: Double = 0.0,
    val elapsedSeconds: Long = 0L,
    val speedMetersPerSecond: Double = 0.0,
    val latitude: Double? = null,
    val longitude: Double? = null,
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

    internal fun resetTrail() {
        mutableTrail.value = emptyList()
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
    private var isPaused = false
    private var lastNotificationUpdateMs = 0L

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_STEP_DETECTOR -> {
                    fusion.onStep(event.timestamp / NANOS_PER_MILLI)
                    publishMetrics()
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
            ACTION_START -> startRun()
            ACTION_PAUSE -> pauseRun()
            ACTION_RESUME -> resumeRun()
            ACTION_STOP -> stopRun()
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startRun() {
        if (RunTrackingState.metrics.value.isTracking) return

        elapsedBeforeResumeMs = 0L
        resumedAtMs = SystemClock.elapsedRealtime()
        isPaused = false
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
        if (isPaused) return
        fusion.onGps(
            GpsFix(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble() else UNKNOWN_ACCURACY_METERS,
                timeMs = location.elapsedRealtimeNanos / NANOS_PER_MILLI,
                bearingDegrees = location.bearing.toDouble().takeIf { location.hasBearing() && location.hasSpeed() },
                speedMps = location.speed.toDouble().takeIf { location.hasSpeed() },
            ),
        )
        publishMetrics(locationError = false)
    }

    private fun pauseRun() {
        if (!RunTrackingState.metrics.value.isTracking || isPaused) return
        elapsedBeforeResumeMs += SystemClock.elapsedRealtime() - resumedAtMs
        isPaused = true
        removeLocationUpdates()
        stopSensors()
        fusion.pause()
        publishMetrics()
        updateNotification()
    }

    private fun resumeRun() {
        if (!RunTrackingState.metrics.value.isTracking || !isPaused) return
        isPaused = false
        resumedAtMs = SystemClock.elapsedRealtime()
        startLocationUpdates()
        startSensors()
        publishMetrics()
        updateNotification()
    }

    private fun stopRun() {
        if (RunTrackingState.metrics.value.isTracking && !isPaused) {
            elapsedBeforeResumeMs += SystemClock.elapsedRealtime() - resumedAtMs
        }
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
    ) {
        val latitude = fusion.latitude
        val longitude = fusion.longitude
        if (isTracking && !isPaused && latitude != null && longitude != null) {
            RunTrackingState.appendTrail(latitude, longitude)
        }
        val elapsedMs = elapsedBeforeResumeMs +
            if (isTracking && !isPaused) SystemClock.elapsedRealtime() - resumedAtMs else 0L
        RunTrackingState.update(
            RunMetrics(
                isTracking = isTracking,
                isPaused = isPaused,
                distanceMeters = fusion.distanceMeters,
                elapsedSeconds = elapsedMs / 1000L,
                speedMetersPerSecond = if (isPaused) 0.0 else fusion.speedMps(SystemClock.elapsedRealtime()),
                latitude = fusion.latitude ?: RunTrackingState.metrics.value.latitude,
                longitude = fusion.longitude ?: RunTrackingState.metrics.value.longitude,
                locationError = locationError,
            ),
        )
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
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
            Intent(this, MainActivity::class.java),
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
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.example.runtime.run.START"
        const val ACTION_PAUSE = "com.example.runtime.run.PAUSE"
        const val ACTION_RESUME = "com.example.runtime.run.RESUME"
        const val ACTION_STOP = "com.example.runtime.run.STOP"

        private const val NOTIFICATION_CHANNEL_ID = "run_tracking"
        private const val NOTIFICATION_ID = 4102
        private const val TIMER_INTERVAL_MS = 1_000L
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 5_000L
        private const val LOCATION_INTERVAL_MS = 1_000L
        private const val FASTEST_LOCATION_INTERVAL_MS = 500L
        private const val MAX_LAST_LOCATION_AGE_NANOS = 30_000_000_000L
        private const val UNKNOWN_ACCURACY_METERS = 30.0
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
