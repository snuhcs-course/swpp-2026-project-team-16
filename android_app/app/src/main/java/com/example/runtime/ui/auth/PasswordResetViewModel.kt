package com.example.runtime.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runtime.R
import com.example.runtime.data.repository.PasswordResetRepository
import com.example.runtime.ui.common.UiText
import com.example.runtime.ui.common.toUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class PasswordResetStep { EMAIL, CODE, DONE }

data class PasswordResetUiState(
    val step: PasswordResetStep = PasswordResetStep.EMAIL,
    val email: String = "",
    val isLoading: Boolean = false,
    val error: UiText? = null,
    val notice: UiText? = null,
)

class PasswordResetViewModel(
    private val repository: PasswordResetRepository = PasswordResetRepository(),
) : ViewModel() {

    var uiState by mutableStateOf(PasswordResetUiState())
        private set

    fun sendCode(email: String) {
        val trimmed = email.trim()
        val error = when {
            trimmed.isEmpty() -> R.string.error_email_required
            !EMAIL_PATTERN.matches(trimmed) -> R.string.error_email_invalid
            else -> null
        }
        if (error != null) {
            uiState = uiState.copy(error = UiText.Resource(error), notice = null)
            return
        }
        request(trimmed, notice = null)
    }

    fun resendCode() {
        request(uiState.email, notice = UiText.Resource(R.string.reset_password_code_resent))
    }

    fun confirm(code: String, newPassword: String, passwordConfirm: String) {
        val trimmedCode = code.trim()
        val error = when {
            trimmedCode.isEmpty() || newPassword.isEmpty() || passwordConfirm.isEmpty() -> R.string.error_all_fields_required
            !CODE_PATTERN.matches(trimmedCode) -> R.string.error_reset_code_format
            newPassword.length < MIN_PASSWORD_LENGTH -> R.string.error_password_short
            newPassword != passwordConfirm -> R.string.error_password_mismatch
            else -> null
        }
        if (error != null) {
            uiState = uiState.copy(error = UiText.Resource(error), notice = null)
            return
        }
        val email = uiState.email
        submit {
            repository.confirm(email, trimmedCode, newPassword)
            uiState = PasswordResetUiState(step = PasswordResetStep.DONE, email = email)
        }
    }

    fun changeEmail() {
        if (uiState.isLoading) return
        uiState = PasswordResetUiState(email = uiState.email)
    }

    fun reset() {
        uiState = PasswordResetUiState()
    }

    private fun request(email: String, notice: UiText?) {
        submit {
            repository.requestCode(email)
            uiState = uiState.copy(step = PasswordResetStep.CODE, email = email, isLoading = false, notice = notice)
        }
    }

    private fun submit(block: suspend () -> Unit) {
        if (uiState.isLoading) return
        uiState = uiState.copy(isLoading = true, error = null, notice = null)
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, error = e.toUiText())
            }
        }
    }

    companion object {
        private const val MIN_PASSWORD_LENGTH = 8
        private val EMAIL_PATTERN = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
        private val CODE_PATTERN = Regex("\\d{6}")
    }
}
