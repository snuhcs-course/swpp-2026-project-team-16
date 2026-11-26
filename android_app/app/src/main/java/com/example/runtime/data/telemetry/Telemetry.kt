package com.example.runtime.data.telemetry

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import com.example.runtime.BuildConfig
import com.example.runtime.data.remote.ApiClient
import com.example.runtime.data.remote.TelemetryBatchRequest
import com.example.runtime.data.remote.TelemetryEventDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject
import kotlin.system.exitProcess

/** Consent-based, allow-listed telemetry. Payloads never include user input or location. */
object Telemetry {
    private const val PREFS = "product_telemetry"
    private const val CONSENT_KEY = "consent"
    private const val PROMPTED_KEY = "consent_prompted"
    private const val QUEUE_KEY = "queue"
    private const val ANR_CURSOR_KEY = "last_seen_process_exit_timestamp"
    private const val ANR_CURSOR_INITIALIZED_KEY = "process_exit_cursor_initialized"
    private const val MAX_QUEUE = 200
    private const val BATCH_SIZE = 25
    private const val MAX_HISTORICAL_EXITS = 32
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    @Volatile private var appContext: Context? = null
    @Volatile private var appSessionTracked = false

    fun initialize(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (!isEnabled(app)) clearQueue(app)
        installCrashHandler(app)
        captureHistoricalAnrs(app)
        if (isEnabled(app)) {
            trackAppSessionIfNeeded(app)
            flush()
        }
    }

    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(CONSENT_KEY, false)

    fun hasShownConsentPrompt(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(PROMPTED_KEY, false)

    fun markConsentPromptShown(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(PROMPTED_KEY, true).commit()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val app = context.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(CONSENT_KEY, enabled).commit()
        if (enabled) {
            trackAppSessionIfNeeded(app)
            flush()
        } else clearQueue(app)
    }

    fun track(
        context: Context,
        eventName: String,
        outcome: String,
        durationMs: Long? = null,
        failureCategory: String? = null,
    ) {
        val app = context.applicationContext
        if (!isEnabled(app)) return
        val allowedEvents = setOf("route_generation", "place_search", "authentication", "pace_plan", "run_session", "run_report", "live_run_coaching", "app_session", "app_anr", "app_crash")
        val allowedOutcomes = setOf("started", "succeeded", "failed", "completed", "anr", "crashed")
        val allowedCategories = setOf("network", "server", "validation", "no_results", "permission", "unknown")
        if (eventName !in allowedEvents || outcome !in allowedOutcomes || (failureCategory != null && failureCategory !in allowedCategories)) return
        val event = TelemetryEventDto(
            event_name = eventName,
            outcome = outcome,
            duration_ms = durationMs?.coerceIn(0, 600_000),
            failure_category = failureCategory,
            app_version = BuildConfig.VERSION_NAME.take(32),
        )
        scope.launch {
            mutex.withLock {
                if (!isEnabled(app)) return@withLock
                enqueue(app, event)
                sendPendingLocked(app)
            }
        }
    }

    fun track(
        eventName: String,
        outcome: String,
        durationMs: Long? = null,
        failureCategory: String? = null,
    ) {
        appContext?.let { track(it, eventName, outcome, durationMs, failureCategory) }
    }

    fun startTime(): Long = SystemClock.elapsedRealtime()

    fun failureCategory(error: Throwable): String = when (error) {
        is IOException -> "network"
        is HttpException -> if (error.code() in 400..499) "validation" else "server"
        else -> "unknown"
    }

    fun flush() {
        val app = appContext ?: return
        if (!isEnabled(app)) return
        scope.launch {
            mutex.withLock { sendPendingLocked(app) }
        }
    }

    private fun enqueue(context: Context, event: TelemetryEventDto) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val queue = runCatching { JSONArray(prefs.getString(QUEUE_KEY, "[]")) }.getOrElse { JSONArray() }
        queue.put(JSONObject().apply {
            put("event_name", event.event_name)
            put("outcome", event.outcome)
            put("duration_ms", event.duration_ms ?: JSONObject.NULL)
            put("failure_category", event.failure_category ?: JSONObject.NULL)
            put("app_version", event.app_version)
        })
        while (queue.length() > MAX_QUEUE) queue.remove(0)
        prefs.edit().putString(QUEUE_KEY, queue.toString()).commit()
    }

    private suspend fun sendPendingLocked(context: Context) {
        if (!isEnabled(context)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        while (isEnabled(context)) {
            val queue = runCatching { JSONArray(prefs.getString(QUEUE_KEY, "[]")) }.getOrElse { JSONArray() }
            if (queue.length() == 0) return
            val count = minOf(BATCH_SIZE, queue.length())
            val batch = (0 until count).map { index ->
                val item = queue.getJSONObject(index)
                TelemetryEventDto(
                    event_name = item.getString("event_name"),
                    outcome = item.getString("outcome"),
                    duration_ms = item.optLong("duration_ms").takeUnless { item.isNull("duration_ms") },
                    failure_category = item.optString("failure_category").takeUnless { item.isNull("failure_category") || it.isBlank() },
                    app_version = item.getString("app_version"),
                )
            }
            try {
                ApiClient.telemetryApi.sendTelemetry(TelemetryBatchRequest(batch))
            } catch (_: Exception) {
                return // Keep queued events for a later foreground session or event.
            }
            val current = runCatching { JSONArray(prefs.getString(QUEUE_KEY, "[]")) }.getOrElse { JSONArray() }
            val remaining = JSONArray()
            for (index in count until current.length()) remaining.put(current.get(index))
            prefs.edit().putString(QUEUE_KEY, remaining.toString()).commit()
        }
    }

    private fun clearQueue(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(QUEUE_KEY).commit()
    }

    private fun trackAppSessionIfNeeded(context: Context) {
        if (!appSessionTracked && isEnabled(context)) {
            appSessionTracked = true
            track(context, "app_session", "started")
        }
    }

    /** Android reports ANR process exits on the next launch; store only the coarse event and app version. */
    private fun captureHistoricalAnrs(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val manager = context.getSystemService(ActivityManager::class.java)
            val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_HISTORICAL_EXITS)
            val newestTimestamp = exits.maxOfOrNull { it.timestamp } ?: 0L
            val initialized = prefs.getBoolean(ANR_CURSOR_INITIALIZED_KEY, false)
            val lastSeenTimestamp = prefs.getLong(ANR_CURSOR_KEY, 0L)

            // Establish a baseline on first launch after this change; don't upload old exit records.
            if (!initialized) {
                prefs.edit()
                    .putLong(ANR_CURSOR_KEY, newestTimestamp)
                    .putBoolean(ANR_CURSOR_INITIALIZED_KEY, true)
                    .commit()
                return
            }

            if (isEnabled(context)) {
                exits.asSequence()
                    .filter { it.timestamp > lastSeenTimestamp && it.reason == ApplicationExitInfo.REASON_ANR }
                    .sortedBy { it.timestamp }
                    .forEach {
                        enqueue(
                            context,
                            TelemetryEventDto(
                                event_name = "app_anr",
                                outcome = "anr",
                                app_version = BuildConfig.VERSION_NAME.take(32),
                            ),
                        )
                    }
            }
            if (newestTimestamp > lastSeenTimestamp) {
                prefs.edit().putLong(ANR_CURSOR_KEY, newestTimestamp).commit()
            }
        }
    }

    private fun installCrashHandler(context: Context) {
        val current = Thread.getDefaultUncaughtExceptionHandler()
        if (current is TelemetryUncaughtExceptionHandler) return
        Thread.setDefaultUncaughtExceptionHandler(TelemetryUncaughtExceptionHandler(context, current))
    }

    private class TelemetryUncaughtExceptionHandler(
        private val context: Context,
        private val delegate: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            if (isEnabled(context)) {
                // Deliberately omit exception text and stack trace; only a generic crash signal is stored.
                runCatching {
                    enqueue(context, TelemetryEventDto("app_crash", "crashed", failure_category = "unknown", app_version = BuildConfig.VERSION_NAME.take(32)))
                }
            }
            if (delegate != null) {
                delegate.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }
}
