package com.example.runtime.ui.notifications

import com.example.runtime.data.remote.ApiResponse
import com.example.runtime.data.remote.AppNotification
import com.example.runtime.data.remote.DeleteAccountRequest
import com.example.runtime.data.remote.NotificationPage
import com.example.runtime.data.remote.ReadNotificationRequest
import com.example.runtime.data.remote.UnreadCount
import com.example.runtime.data.repository.NotificationRepository
import com.example.runtime.testing.FakeApiService
import com.example.runtime.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class NotificationsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val api = NotificationApi()
    private val viewModel by lazy { NotificationsViewModel(NotificationRepository(api)) }

    @Test
    fun load_andMarkOneRead() {
        viewModel.load()
        assertEquals(2, viewModel.unreadCount)

        viewModel.markRead(1)

        assertEquals(listOf(ReadNotificationRequest(1)), api.reads)
        assertTrue(viewModel.items!!.first { it.id == 1 }.read)
        assertFalse(viewModel.items!!.first { it.id == 2 }.read)
        assertEquals(1, viewModel.unreadCount)
    }

    @Test
    fun toggle_savesOneKindAndRollsBackOnFailure() {
        viewModel.loadSettings()

        viewModel.toggle(NotificationKind.POST_LIKE, false)
        assertEquals(mapOf("post_like" to false), api.updates.single())
        assertEquals(false, viewModel.settings?.get("post_like"))

        api.failUpdate = true
        viewModel.toggle(NotificationKind.FOLLOW, false)
        assertEquals(true, viewModel.settings?.get("follow"))
        assertNotNull(viewModel.settingsError)
    }

    @Test
    fun setAll_turnsEveryKindOff() {
        viewModel.loadSettings()

        viewModel.setAll(false)

        assertEquals(NotificationKind.entries.map { it.apiValue }.toSet(), api.updates.single().keys)
        assertTrue(viewModel.settings!!.values.none { it })
    }

    @Test
    fun deleteAccount_sendsThePassword() {
        var deleted = false

        viewModel.deleteAccount("secret") { deleted = true }

        assertTrue(deleted)
        assertEquals(DeleteAccountRequest("secret"), api.deletes.single())
    }

    private class NotificationApi : FakeApiService() {
        var failUpdate = false
        val reads = mutableListOf<ReadNotificationRequest>()
        val updates = mutableListOf<Map<String, Boolean>>()
        val deletes = mutableListOf<DeleteAccountRequest>()
        private var settings = NotificationKind.entries.associate { it.apiValue to true }

        override suspend fun notifications() = ApiResponse(
            status = "success",
            data = NotificationPage(
                unreadCount = 2,
                items = listOf(
                    AppNotification(1, "follow", "New follower", "bob started following you.", createdAt = "2026-11-01T00:00:00Z"),
                    AppNotification(2, "post_like", "New like", "bob liked your post.", createdAt = "2026-11-01T00:00:00Z"),
                ),
            ),
        )

        override suspend fun readNotifications(body: ReadNotificationRequest): ApiResponse<UnreadCount> {
            reads += body
            return ApiResponse(status = "success", data = UnreadCount(1))
        }

        override suspend fun notificationSettings() = ApiResponse(status = "success", data = settings)

        override suspend fun updateNotificationSettings(body: Map<String, Boolean>): ApiResponse<Map<String, Boolean>> {
            if (failUpdate) throw IOException("offline")
            updates += body
            settings = settings + body
            return ApiResponse(status = "success", data = settings)
        }

        override suspend fun deleteAccount(body: DeleteAccountRequest): ApiResponse<Unit> {
            deletes += body
            return ApiResponse(status = "success")
        }
    }
}
