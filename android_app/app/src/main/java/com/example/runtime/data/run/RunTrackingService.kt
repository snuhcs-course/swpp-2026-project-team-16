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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

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

object RunTrackingState {
    private val mutableMetrics = MutableStateFlow(RunMetrics())
    val metrics: StateFlow<RunMetrics> = mutableMetrics.asStateFlow()

    internal fun update(metrics: RunMetrics) {
        mutableMetrics.value = metrics
    }
}

class RunTrackingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var locationManager: LocationManager
    private var listener: LocationListener? = null
    private var lastLocation: Location? = null
    private var distanceMeters = 0.0
    private var elapsedBeforeResumeMs = 0L
    private var resumedAtMs = 0L
    private var smoothedSpeedMps = 0.0
    private var isPaused = false
    private var lastNotificationUpdateMs = 0L

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

        distanceMeters = 0.0
        elapsedBeforeResumeMs = 0L
        resumedAtMs = SystemClock.elapsedRealtime()
        smoothedSpeedMps = 0.0
        isPaused = false
        lastLocation = null

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
        startLocationUpdates()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    @Suppress("MissingPermission")
    private fun startLocationUpdates() {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation && !hasCoarseLocation) {
            RunTrackingState.update(RunTrackingState.metrics.value.copy(locationError = true))
            return
        }

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) {
            RunTrackingState.update(RunTrackingState.metrics.value.copy(locationError = true))
            return
        }

        val newListener = object : LocationListener {
            override fun onLocationChanged(location: Location) = acceptLocation(location)
        }
        listener = newListener
        try {
            locationManager.requestLocationUpdates(
                provider,
                LOCATION_INTERVAL_MS,
                MIN_LOCATION_INTERVAL_METERS,
                newListener,
                Looper.getMainLooper(),
            )
        } catch (_: SecurityException) {
            RunTrackingState.update(RunTrackingState.metrics.value.copy(locationError = true))
        }
    }

    private fun acceptLocation(location: Location) {
        if (location.hasAccuracy() && location.accuracy > MAX_LOCATION_ACCURACY_METERS) return

        val previous = lastLocation
        var acceptedSpeed = 0.0
        if (previous != null && !isPaused) {
            val seconds = (location.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1_000_000_000.0
            val deltaMeters = previous.distanceTo(location).toDouble()
            val measuredSpeed = if (seconds > 0.0) deltaMeters / seconds else 0.0

            if (deltaMeters >= MIN_DISTANCE_DELTA_METERS && measuredSpeed <= MAX_RUNNING_SPEED_MPS) {
                distanceMeters += deltaMeters
                acceptedSpeed = measuredSpeed
            }
        }

        lastLocation = location
        val sensorSpeed = if (location.hasSpeed()) location.speed.toDouble() else acceptedSpeed
        val usableSpeed = sensorSpeed.takeIf { it in MIN_DISPLAY_SPEED_MPS..MAX_RUNNING_SPEED_MPS } ?: 0.0
        smoothedSpeedMps = if (usableSpeed == 0.0) {
            smoothedSpeedMps * SPEED_SMOOTHING_DECAY
        } else if (smoothedSpeedMps == 0.0) {
            usableSpeed
        } else {
            smoothedSpeedMps * SPEED_SMOOTHING_OLD_WEIGHT + usableSpeed * SPEED_SMOOTHING_NEW_WEIGHT
        }

        publishMetrics(
            latitude = location.latitude,
            longitude = location.longitude,
            locationError = false,
        )
    }

    private fun pauseRun() {
        if (!RunTrackingState.metrics.value.isTracking || isPaused) return
        elapsedBeforeResumeMs += SystemClock.elapsedRealtime() - resumedAtMs
        isPaused = true
        removeLocationUpdates()
        lastLocation = null
        smoothedSpeedMps = 0.0
        publishMetrics()
        updateNotification()
    }

    private fun resumeRun() {
        if (!RunTrackingState.metrics.value.isTracking || !isPaused) return
        isPaused = false
        resumedAtMs = SystemClock.elapsedRealtime()
        lastLocation = null
        startLocationUpdates()
        publishMetrics()
        updateNotification()
    }

    private fun stopRun() {
        if (RunTrackingState.metrics.value.isTracking && !isPaused) {
            elapsedBeforeResumeMs += SystemClock.elapsedRealtime() - resumedAtMs
        }
        removeLocationUpdates()
        handler.removeCallbacks(ticker)
        publishMetrics(isTracking = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publishMetrics(
        latitude: Double? = RunTrackingState.metrics.value.latitude,
        longitude: Double? = RunTrackingState.metrics.value.longitude,
        locationError: Boolean = RunTrackingState.metrics.value.locationError,
        isTracking: Boolean = RunTrackingState.metrics.value.isTracking,
    ) {
        val elapsedMs = elapsedBeforeResumeMs +
            if (isTracking && !isPaused) SystemClock.elapsedRealtime() - resumedAtMs else 0L
        RunTrackingState.update(
            RunMetrics(
                isTracking = isTracking,
                isPaused = isPaused,
                distanceMeters = distanceMeters,
                elapsedSeconds = elapsedMs / 1000L,
                speedMetersPerSecond = smoothedSpeedMps,
                latitude = latitude,
                longitude = longitude,
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
        listener?.let { locationManager.removeUpdates(it) }
        listener = null
    }

    override fun onDestroy() {
        removeLocationUpdates()
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
        private const val MIN_LOCATION_INTERVAL_METERS = 1f
        private const val MAX_LOCATION_ACCURACY_METERS = 50f
        private const val MIN_DISTANCE_DELTA_METERS = 1.5
        private const val MIN_DISPLAY_SPEED_MPS = 0.5
        private const val MAX_RUNNING_SPEED_MPS = 8.0
        private const val SPEED_SMOOTHING_OLD_WEIGHT = 0.7
        private const val SPEED_SMOOTHING_NEW_WEIGHT = 0.3
        private const val SPEED_SMOOTHING_DECAY = 0.7
    }
}
