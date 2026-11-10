package com.example.runtime.data.repository

import com.example.runtime.data.remote.ApiClient
import com.example.runtime.data.remote.ApiService
import com.example.runtime.data.remote.DeleteAccountRequest
import com.example.runtime.data.remote.NotificationPage
import com.example.runtime.data.remote.ReadNotificationRequest

class NotificationRepository(private val api: ApiService = ApiClient.api) {

    suspend fun notifications(): NotificationPage = api.notifications().requireData()

    suspend fun markRead(id: Int? = null): Int = api.readNotifications(ReadNotificationRequest(id)).requireData().unreadCount

    suspend fun settings(): Map<String, Boolean> = api.notificationSettings().requireData()

    suspend fun updateSettings(changes: Map<String, Boolean>): Map<String, Boolean> =
        api.updateNotificationSettings(changes).requireData()

    suspend fun deleteAccount(password: String?) {
        api.deleteAccount(DeleteAccountRequest(password))
    }
}
