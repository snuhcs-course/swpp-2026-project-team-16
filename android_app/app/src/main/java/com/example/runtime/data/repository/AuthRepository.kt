package com.example.runtime.data.repository

import com.example.runtime.data.auth.TokenStorage
import com.example.runtime.data.remote.ApiClient
import com.example.runtime.data.remote.ApiService
import com.example.runtime.data.remote.AuthData
import com.example.runtime.data.remote.LoginRequest
import com.example.runtime.data.remote.RegisterRequest
import com.example.runtime.data.remote.TokenStore
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

class AuthRepository(
    private val tokenStorage: TokenStorage,
    private val api: ApiService = ApiClient.api,
) {

    suspend fun login(username: String, password: String): AuthData =
        api.login(LoginRequest(username, password)).requireData().also { saveToken(it.token) }

    suspend fun register(username: String, email: String, password: String): AuthData =
        api.register(RegisterRequest(username, email, password)).requireData().also { saveToken(it.token) }

    suspend fun restoreSession(): Boolean {
        val token = tokenStorage.load() ?: return false
        TokenStore.token = token
        return try {
            api.myProfile()
            true
        } catch (e: HttpException) {
            if (e.code() == 401) clearToken()
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun saveToken(token: String) {
        TokenStore.token = token
        tokenStorage.save(token)
    }

    private suspend fun clearToken() {
        TokenStore.token = null
        tokenStorage.clear()
    }
}
