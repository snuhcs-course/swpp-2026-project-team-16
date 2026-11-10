package com.example.runtime.data.repository

import com.example.runtime.data.remote.ApiClient
import com.example.runtime.data.remote.ApiService
import com.example.runtime.data.remote.CreateGroupRequest
import com.example.runtime.data.remote.GroupMessage
import com.example.runtime.data.remote.JoinGroupRequest
import com.example.runtime.data.remote.RunningGroup
import com.example.runtime.data.remote.SendMessageRequest

class GroupRepository(private val api: ApiService = ApiClient.api) {

    suspend fun groups(filter: String, latitude: Double? = null, longitude: Double? = null, radiusKm: Double? = null): List<RunningGroup> =
        api.groups(filter, latitude, longitude, radiusKm).data.orEmpty()

    suspend fun group(groupId: Int): RunningGroup = api.group(groupId).requireData()

    suspend fun create(request: CreateGroupRequest): RunningGroup = api.createGroup(request).requireData()

    suspend fun delete(groupId: Int) {
        api.deleteGroup(groupId)
    }

    suspend fun join(groupId: Int, password: String? = null): RunningGroup =
        api.joinGroup(groupId, JoinGroupRequest(password)).requireData()

    suspend fun leave(groupId: Int): RunningGroup = api.leaveGroup(groupId).requireData()

    suspend fun messages(groupId: Int, after: Int?): List<GroupMessage> = api.groupMessages(groupId, after).data.orEmpty()

    suspend fun send(groupId: Int, text: String): GroupMessage =
        api.sendGroupMessage(groupId, SendMessageRequest(text)).requireData()
}
