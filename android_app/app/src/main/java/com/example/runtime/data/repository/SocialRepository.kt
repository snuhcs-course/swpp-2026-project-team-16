package com.example.runtime.data.repository

import com.example.runtime.data.remote.ApiClient
import com.example.runtime.data.remote.ApiService
import com.example.runtime.data.remote.BlockState
import com.example.runtime.data.remote.BlockedUser
import com.example.runtime.data.remote.FollowState
import com.example.runtime.data.remote.FollowUser
import com.example.runtime.data.remote.UserProfile

class SocialRepository(private val api: ApiService = ApiClient.api) {

    suspend fun profile(username: String): UserProfile = api.userProfile(username).requireData()

    suspend fun follow(username: String): FollowState = api.follow(username).requireData()

    suspend fun unfollow(username: String): FollowState = api.unfollow(username).requireData()

    suspend fun block(username: String): BlockState = api.block(username).requireData()

    suspend fun unblock(username: String): BlockState = api.unblock(username).requireData()

    suspend fun blockedUsers(): List<BlockedUser> = api.blockedUsers().data.orEmpty()

    suspend fun followers(username: String): List<FollowUser> = api.followers(username).data.orEmpty()

    suspend fun following(username: String): List<FollowUser> = api.following(username).data.orEmpty()

    suspend fun avatar(url: String): ByteArray = api.download(url).use { it.bytes() }
}
