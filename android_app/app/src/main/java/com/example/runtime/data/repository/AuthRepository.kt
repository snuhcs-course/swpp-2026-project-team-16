package com.example.runtime.data.repository

import android.util.Log
import com.example.runtime.data.auth.TokenStorage
import com.example.runtime.data.remote.ApiClient
import com.example.runtime.data.remote.ApiService
import com.example.runtime.data.remote.AuthData
import com.example.runtime.data.remote.GoogleLoginRequest
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
        api.login(LoginRequest(username, password)).requireData().also { saveSession(it) }

    suspend fun register(username: String, email: String, password: String, displayName: String = ""): AuthData =
        api.register(RegisterRequest(username, email, password, displayName)).requireData().also { saveSession(it) }

    suspend fun googleLogin(idToken: String): AuthData =
        api.googleLogin(GoogleLoginRequest(idToken)).requireData().also { saveSession(it) }

    suspend fun restoreSession(): Boolean {
        val token = tokenStorage.load() ?: return false
        TokenStore.token = token
        TokenStore.username = tokenStorage.loadUsername()
        return try {
            api.myProfile().data?.username?.let { username ->
                TokenStore.username = username
                tokenStorage.saveUsername(username)
            }
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

    private suspend fun saveSession(auth: AuthData) {
        TokenStore.token = auth.token
        TokenStore.username = auth.username
        tokenStorage.save(auth.token)
        tokenStorage.saveUsername(auth.username)
    }

    suspend fun clearSession(): String? {
        val token = TokenStore.token
        clearToken()
        return token
    }

    suspend fun revokeToken(token: String) {
        try {
            api.logout("Token $token")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AuthRepository", "Server logout failed", e)
        }
    }

    private suspend fun clearToken() {
        TokenStore.token = null
        TokenStore.username = null
        tokenStorage.clear()
    }
}
