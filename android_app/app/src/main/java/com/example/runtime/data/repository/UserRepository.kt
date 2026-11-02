package com.example.runtime.data.repository

import com.example.runtime.data.remote.ApiClient
import com.example.runtime.data.remote.ApiService
import com.example.runtime.data.remote.ProfileData
import com.example.runtime.data.remote.ProfileUpdateRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class UserRepository(private val api: ApiService = ApiClient.api) {

    suspend fun profile(): ProfileData = api.myProfile().requireData()

    suspend fun updateProfile(displayName: String, statusMessage: String): ProfileData =
        api.updateProfile(ProfileUpdateRequest(displayName, statusMessage)).requireData()

    suspend fun uploadAvatar(jpeg: ByteArray): ProfileData =
        api.uploadAvatar(
            MultipartBody.Part.createFormData("avatar", "avatar.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
        ).requireData()

    suspend fun deleteAvatar(): ProfileData = api.deleteAvatar().requireData()

    suspend fun avatar(url: String): ByteArray = api.download(url).use { it.bytes() }
}
