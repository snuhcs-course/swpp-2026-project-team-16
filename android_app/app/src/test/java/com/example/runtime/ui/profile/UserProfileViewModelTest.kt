package com.example.runtime.ui.profile

import com.example.runtime.data.remote.ApiResponse
import com.example.runtime.data.remote.BlockState
import com.example.runtime.data.remote.BoardPost
import com.example.runtime.data.remote.FollowState
import com.example.runtime.data.remote.FollowUser
import com.example.runtime.data.remote.UserProfile
import com.example.runtime.data.repository.SocialRepository
import com.example.runtime.testing.FakeApiService
import com.example.runtime.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class UserProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val api = SocialApi()
    private val viewModel by lazy { UserProfileViewModel(SocialRepository(api)) }

    @Test
    fun load_showsTheProfile() {
        viewModel.load("star")

        assertEquals("star", viewModel.profile?.username)
        assertEquals(3, viewModel.profile?.followerCount)
    }

    @Test
    fun toggleFollow_followsThenUnfollows() {
        viewModel.load("star")

        viewModel.toggleFollow()
        assertEquals(listOf("follow:star"), api.calls)
        assertEquals(true, viewModel.profile?.isFollowing)
        assertEquals(4, viewModel.profile?.followerCount)

        viewModel.toggleFollow()
        assertEquals(listOf("follow:star", "unfollow:star"), api.calls)
        assertEquals(false, viewModel.profile?.isFollowing)
        assertEquals(3, viewModel.profile?.followerCount)
    }

    @Test
    fun toggleFollow_failureKeepsTheStateAndShowsTheError() {
        viewModel.load("star")
        api.failFollow = true

        viewModel.toggleFollow()

        assertEquals(false, viewModel.profile?.isFollowing)
        assertNotNull(viewModel.followError)
    }

    @Test
    fun cannotFollowMyself() {
        viewModel.load("me")

        viewModel.toggleFollow()

        assertEquals(emptyList<String>(), api.calls)
    }

    @Test
    fun toggleBlock_blocksThenUnblocksAndReportsTheChange() {
        api.followingStar = true
        viewModel.load("star")
        val changes = mutableListOf<Pair<String, Boolean>>()

        viewModel.toggleBlock { username, blocked -> changes += username to blocked }

        assertEquals(listOf("block:star"), api.calls)
        assertEquals(true, viewModel.profile?.isBlocked)
        assertEquals(false, viewModel.profile?.isFollowing)
        assertEquals(2, viewModel.profile?.followerCount)
        assertTrue(viewModel.profile?.posts.orEmpty().isEmpty())

        viewModel.toggleBlock { username, blocked -> changes += username to blocked }

        assertEquals(listOf("block:star", "unblock:star"), api.calls)
        assertEquals(false, viewModel.profile?.isBlocked)
        assertEquals(listOf(1), viewModel.profile?.posts?.map { it.id })
        assertEquals(listOf("star" to true, "star" to false), changes)
    }

    @Test
    fun toggleBlock_failureKeepsTheStateAndShowsTheError() {
        viewModel.load("star")
        api.failBlock = true
        var changed = false

        viewModel.toggleBlock { _, _ -> changed = true }

        assertEquals(false, viewModel.profile?.isBlocked)
        assertNotNull(viewModel.followError)
        assertEquals(false, changed)
        assertEquals(false, viewModel.isBlockWorking)
    }

    @Test
    fun cannotBlockMyself() {
        viewModel.load("me")

        viewModel.toggleBlock()

        assertEquals(emptyList<String>(), api.calls)
    }

    @Test
    fun openList_loadsFollowersAndFollowing() {
        viewModel.load("star")

        viewModel.openList(FollowListType.FOLLOWERS)
        assertEquals(listOf("fan"), viewModel.listUsers?.map { it.username })

        viewModel.openList(FollowListType.FOLLOWING)
        assertEquals(listOf("idol"), viewModel.listUsers?.map { it.username })

        viewModel.closeList()
        assertNull(viewModel.listType)
    }

    private class SocialApi : FakeApiService() {
        var failFollow = false
        var failBlock = false
        var followingStar = false
        val calls = mutableListOf<String>()
        private var followers = 3
        private var following = false
        private var blocked = false

        override suspend fun userProfile(username: String): ApiResponse<UserProfile> =
            ApiResponse(
                status = "success",
                data = UserProfile(
                    username = username,
                    isMe = username == "me",
                    followerCount = followers,
                    isFollowing = following || followingStar,
                    isBlocked = blocked,
                    posts = if (blocked) emptyList() else listOf(BoardPost(id = 1, title = "Hi", body = "b", createdAt = "2026-11-01T00:00:00Z")),
                ),
            )

        override suspend fun block(username: String): ApiResponse<BlockState> {
            if (failBlock) throw IOException("offline")
            calls += "block:$username"
            blocked = true
            if (followingStar) followers -= 1
            followingStar = false
            following = false
            return ApiResponse(status = "success", data = BlockState(followers, 0, false, true))
        }

        override suspend fun unblock(username: String): ApiResponse<BlockState> {
            calls += "unblock:$username"
            blocked = false
            return ApiResponse(status = "success", data = BlockState(followers, 0, false, false))
        }

        override suspend fun follow(username: String): ApiResponse<FollowState> {
            if (failFollow) throw IOException("offline")
            calls += "follow:$username"
            followers += 1
            following = true
            return ApiResponse(status = "success", data = FollowState(followers, 0, true))
        }

        override suspend fun unfollow(username: String): ApiResponse<FollowState> {
            calls += "unfollow:$username"
            followers -= 1
            following = false
            return ApiResponse(status = "success", data = FollowState(followers, 0, false))
        }

        override suspend fun followers(username: String) =
            ApiResponse(status = "success", data = listOf(FollowUser("fan")))

        override suspend fun following(username: String) =
            ApiResponse(status = "success", data = listOf(FollowUser("idol")))
    }
}
