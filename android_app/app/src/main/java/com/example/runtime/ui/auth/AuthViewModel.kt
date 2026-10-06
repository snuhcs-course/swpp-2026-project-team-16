package com.example.runtime.ui.auth

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.runtime.data.auth.TokenStorage
import com.example.runtime.data.repository.AuthRepository
import com.example.runtime.ui.common.UiText
import com.example.runtime.ui.common.toUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: UiText? = null,
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository(TokenStorage(application))

    var uiState by mutableStateOf(AuthUiState())
        private set

    fun login(username: String, password: String, onSuccess: () -> Unit) {
        authenticate(onSuccess) { repository.login(username, password) }
    }

    fun register(username: String, email: String, password: String, onSuccess: () -> Unit) {
        authenticate(onSuccess) { repository.register(username, email, password) }
    }

    fun clearError() {
        uiState = uiState.copy(error = null)
    }

    fun restoreSession(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(repository.restoreSession())
        }
    }

    private fun authenticate(onSuccess: () -> Unit, request: suspend () -> Unit) {
        if (uiState.isLoading) return
        uiState = AuthUiState(isLoading = true)
        viewModelScope.launch {
            try {
                request()
                uiState = AuthUiState()
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = AuthUiState(error = e.toUiText())
            }
        }
    }
}
