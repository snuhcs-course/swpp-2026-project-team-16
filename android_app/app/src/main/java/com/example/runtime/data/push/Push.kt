package com.example.runtime.data.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.runtime.BuildConfig
import com.example.runtime.MainActivity
import com.example.runtime.R
import com.example.runtime.data.auth.TokenStorage
import com.example.runtime.data.remote.ApiClient
import com.example.runtime.data.remote.DeviceRequest
import com.example.runtime.data.remote.TokenStore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class NotificationTarget(
    val kind: String,
    val notificationId: Int? = null,
    val actor: String? = null,
    val groupId: Int? = null,
    val postId: Int? = null,
    val sharedRouteId: Int? = null,
)

object NotificationNavigation {
    private val mutableTarget = MutableStateFlow<NotificationTarget?>(null)
    val target: StateFlow<NotificationTarget?> = mutableTarget.asStateFlow()

    fun open(target: NotificationTarget) {
        mutableTarget.value = target
    }

    fun consume() {
        mutableTarget.value = null
    }

    fun fromIntent(intent: Intent?): NotificationTarget? {
        val kind = intent?.getStringExtra(EXTRA_KIND) ?: return null
        return NotificationTarget(
            kind = kind,
            notificationId = intent.getStringExtra(EXTRA_NOTIFICATION_ID)?.toIntOrNull(),
            actor = intent.getStringExtra(EXTRA_ACTOR),
            groupId = intent.getStringExtra(EXTRA_GROUP_ID)?.toIntOrNull(),
            postId = intent.getStringExtra(EXTRA_POST_ID)?.toIntOrNull(),
            sharedRouteId = intent.getStringExtra(EXTRA_SHARED_ROUTE_ID)?.toIntOrNull(),
        )
    }

    const val EXTRA_KIND = "kind"
    const val EXTRA_NOTIFICATION_ID = "notification_id"
    const val EXTRA_ACTOR = "actor"
    const val EXTRA_GROUP_ID = "group_id"
    const val EXTRA_POST_ID = "post_id"
    const val EXTRA_SHARED_ROUTE_ID = "shared_route_id"
}

object Push {
    const val CHANNEL_ID = "activity"
    private const val REGISTER_TIMEOUT_MS = 5_000L

    fun initialize(context: Context) {
        createChannel(context)
        if (FirebaseApp.getApps(context).isNotEmpty() || BuildConfig.FIREBASE_APP_ID.isBlank()) return
        val options = FirebaseOptions.Builder()
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
            .build()
        FirebaseApp.initializeApp(context, options)
    }

    fun isConfigured(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    fun canPost(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED && NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_activity),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
    }

    private suspend fun currentToken(): String? = suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            continuation.resume(if (task.isSuccessful) task.result else null)
        }
    }

    suspend fun register(context: Context) {
        if (!isConfigured(context)) return
        withTimeoutOrNull(REGISTER_TIMEOUT_MS) {
            val token = currentToken() ?: return@withTimeoutOrNull
            runCatching { ApiClient.api.registerDevice(DeviceRequest(token)) }
        }
    }

    suspend fun unregister(context: Context) {
        if (!isConfigured(context)) return
        withTimeoutOrNull(REGISTER_TIMEOUT_MS) {
            val token = currentToken() ?: return@withTimeoutOrNull
            runCatching { ApiClient.api.unregisterDevice(DeviceRequest(token)) }
        }
    }

    fun show(context: Context, data: Map<String, String>) {
        if (!canPost(context)) return
        val id = data[NotificationNavigation.EXTRA_NOTIFICATION_ID]?.toIntOrNull() ?: System.currentTimeMillis().toInt()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data.forEach { (key, value) -> putExtra(key, value) }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_route)
            .setContentTitle(data["title"])
            .setContentText(data["body"])
            .setStyle(NotificationCompat.BigTextStyle().bigText(data["body"]))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }
}

class PushMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            if (TokenStore.token == null) TokenStore.token = TokenStorage(applicationContext).load()
            if (TokenStore.token != null) runCatching { ApiClient.api.registerDevice(DeviceRequest(token)) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data[NotificationNavigation.EXTRA_KIND] != null) Push.show(applicationContext, message.data)
    }
}
