package com.example.runtime.ui.auth

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.runtime.data.auth.TokenStorage
import com.example.runtime.data.remote.ApiClient
import com.example.runtime.data.remote.AuthData
import com.example.runtime.data.remote.LoginRequest
import com.example.runtime.data.remote.RegisterRequest
import com.example.runtime.data.remote.TokenStore
import com.example.runtime.data.remote.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenStorage = TokenStorage(application)

    var uiState by mutableStateOf(AuthUiState())
        private set

    fun login(username: String, password: String, onSuccess: () -> Unit) {
        authenticate(onSuccess) { ApiClient.api.login(LoginRequest(username, password)).data }
    }

    fun register(username: String, email: String, password: String, onSuccess: () -> Unit) {
        authenticate(onSuccess) { ApiClient.api.register(RegisterRequest(username, email, password)).data }
    }

    fun clearError() {
        uiState = uiState.copy(error = null)
    }

    fun restoreSession(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val token = tokenStorage.load()
            if (token == null) {
                onResult(false)
                return@launch
            }
            TokenStore.token = token
            val valid = try {
                ApiClient.api.myProfile()
                true
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    TokenStore.token = null
                    tokenStorage.clear()
                }
                false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            onResult(valid)
        }
    }

    private fun authenticate(onSuccess: () -> Unit, request: suspend () -> AuthData?) {
        if (uiState.isLoading) return
        uiState = AuthUiState(isLoading = true)
        viewModelScope.launch {
            try {
                val data = request() ?: error("Empty response from server.")
                TokenStore.token = data.token
                tokenStorage.save(data.token)
                uiState = AuthUiState()
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = AuthUiState(error = e.toUserMessage())
            }
        }
    }
}
