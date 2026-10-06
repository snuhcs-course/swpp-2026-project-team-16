package com.example.runtime.data.repository

import com.example.runtime.data.remote.ApiClient
import com.example.runtime.data.remote.ApiService
import com.example.runtime.data.remote.ProfileData

class UserRepository(private val api: ApiService = ApiClient.api) {

    suspend fun profile(): ProfileData = api.myProfile().requireData()
}
