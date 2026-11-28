package com.example.runtime.ui.profile

import com.example.runtime.data.remote.ApiResponse
import com.example.runtime.data.remote.BlockState
import com.example.runtime.data.remote.BlockedUser
import com.example.runtime.data.repository.SocialRepository
import com.example.runtime.testing.FakeApiService
import com.example.runtime.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class BlockedUsersViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val api = BlockApi()
    private val viewModel by lazy { BlockedUsersViewModel(SocialRepository(api)) }

    @Test
    fun load_showsBlockedUsers() {
        viewModel.load()

        assertEquals(listOf("troll", "spammer"), viewModel.users?.map { it.username })
        assertNull(viewModel.error)
    }

    @Test
    fun load_failureShowsTheError() {
        api.failList = true

        viewModel.load()

        assertNull(viewModel.users)
        assertNotNull(viewModel.error)
    }

    @Test
    fun unblock_removesTheUserAndReportsIt() {
        viewModel.load()
        val unblocked = mutableListOf<String>()

        viewModel.unblock("troll") { unblocked += it }

        assertEquals(listOf("unblock:troll"), api.calls)
        assertEquals(listOf("spammer"), viewModel.users?.map { it.username })
        assertEquals(listOf("troll"), unblocked)
        assertEquals(emptySet<String>(), viewModel.unblocking)
    }

    @Test
    fun unblock_failureKeepsTheUser() {
        viewModel.load()
        api.failUnblock = true
        var reported = false

        viewModel.unblock("troll") { reported = true }

        assertEquals(listOf("troll", "spammer"), viewModel.users?.map { it.username })
        assertNotNull(viewModel.error)
        assertEquals(false, reported)
    }

    @Test
    fun reset_clearsTheList() {
        viewModel.load()

        viewModel.reset()

        assertNull(viewModel.users)
    }

    private class BlockApi : FakeApiService() {
        var failList = false
        var failUnblock = false
        val calls = mutableListOf<String>()

        override suspend fun blockedUsers(): ApiResponse<List<BlockedUser>> {
            if (failList) throw IOException("offline")
            return ApiResponse(
                status = "success",
                data = listOf(BlockedUser("troll", displayName = "Troll"), BlockedUser("spammer")),
            )
        }

        override suspend fun unblock(username: String): ApiResponse<BlockState> {
            if (failUnblock) throw IOException("offline")
            calls += "unblock:$username"
            return ApiResponse(status = "success", data = BlockState(0, 0, false, false))
        }
    }
}
