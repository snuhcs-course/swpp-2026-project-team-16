package com.example.runtime.ui.auth

import android.app.Application
import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.runtime.R
import com.example.runtime.data.auth.GuestTrialStorage
import com.example.runtime.data.auth.TokenStorage
import com.example.runtime.data.repository.AuthRepository
import com.example.runtime.ui.common.UiText
import com.example.runtime.ui.common.toUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: UiText? = null,
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository(TokenStorage(application))
    private val guestTrialStorage = GuestTrialStorage(application)

    var uiState by mutableStateOf(AuthUiState())
        private set

    var isLoggedIn by mutableStateOf(false)
        private set

    var isGuestTrialUsed by mutableStateOf(false)
        private set

    fun markGuestTrialUsed() {
        if (isLoggedIn || isGuestTrialUsed) return
        isGuestTrialUsed = true
        viewModelScope.launch { guestTrialStorage.markTrialUsed() }
    }

    fun login(username: String, password: String, onSuccess: () -> Unit) {
        if (username.isBlank() || password.isEmpty()) {
            uiState = AuthUiState(error = UiText.Resource(R.string.error_username_password_required))
            return
        }
        authenticate(onSuccess) { repository.login(username.trim(), password) }
    }

    fun register(username: String, email: String, password: String, passwordConfirm: String, onSuccess: () -> Unit) {
        val error = when {
            username.isBlank() || email.isBlank() || password.isEmpty() || passwordConfirm.isEmpty() ->
                R.string.error_all_fields_required
            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> R.string.error_email_invalid
            password.length < MIN_PASSWORD_LENGTH -> R.string.error_password_short
            password != passwordConfirm -> R.string.error_password_mismatch
            else -> null
        }
        if (error != null) {
            uiState = AuthUiState(error = UiText.Resource(error))
            return
        }
        authenticate(onSuccess) { repository.register(username.trim(), email.trim(), password) }
    }

    fun googleLogin(idToken: String, email: String, onSuccess: () -> Unit, onSignUpRequired: (String) -> Unit) {
        authenticate(
            onSuccess = onSuccess,
            onNotFound = {
                uiState = AuthUiState(error = UiText.Resource(R.string.google_account_not_registered))
                onSignUpRequired(email)
            },
        ) { repository.googleLogin(idToken) }
    }

    fun showError(message: UiText) {
        uiState = AuthUiState(error = message)
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            val token = repository.clearSession()
            uiState = AuthUiState()
            isLoggedIn = false
            onDone()
            token?.let { repository.revokeToken(it) }
        }
    }

    fun onSessionExpired(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.clearSession()
            uiState = AuthUiState(error = UiText.Resource(R.string.error_auth_required))
            isLoggedIn = false
            onDone()
        }
    }

    fun clearError() {
        uiState = uiState.copy(error = null)
    }

    fun restoreSession(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            isGuestTrialUsed = guestTrialStorage.isTrialUsed()
            isLoggedIn = repository.restoreSession()
            onResult(isLoggedIn)
        }
    }

    private fun authenticate(
        onSuccess: () -> Unit,
        onNotFound: (() -> Unit)? = null,
        request: suspend () -> Unit,
    ) {
        if (uiState.isLoading) return
        uiState = AuthUiState(isLoading = true)
        viewModelScope.launch {
            try {
                request()
                uiState = AuthUiState()
                isLoggedIn = true
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (onNotFound != null && e is HttpException && e.code() == HTTP_NOT_FOUND) {
                    onNotFound()
                } else {
                    uiState = AuthUiState(error = e.toUiText())
                }
            }
        }
    }

    companion object {
        private const val MIN_PASSWORD_LENGTH = 8
        private const val HTTP_NOT_FOUND = 404
    }
}
