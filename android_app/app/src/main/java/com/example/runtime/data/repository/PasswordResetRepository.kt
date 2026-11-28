package com.example.runtime.data.repository

import com.example.runtime.data.remote.ApiClient
import com.example.runtime.data.remote.ApiService
import com.example.runtime.data.remote.PasswordResetConfirmRequest
import com.example.runtime.data.remote.PasswordResetRequest

class PasswordResetRepository(private val api: ApiService = ApiClient.api) {

    suspend fun requestCode(email: String) {
        api.requestPasswordReset(PasswordResetRequest(email))
    }

    suspend fun confirm(email: String, code: String, newPassword: String) {
        api.confirmPasswordReset(PasswordResetConfirmRequest(email, code, newPassword))
    }
}
