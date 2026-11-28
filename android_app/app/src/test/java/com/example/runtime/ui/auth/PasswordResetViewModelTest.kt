package com.example.runtime.ui.auth

import com.example.runtime.R
import com.example.runtime.data.remote.ApiResponse
import com.example.runtime.data.remote.PasswordResetConfirmRequest
import com.example.runtime.data.remote.PasswordResetRequest
import com.example.runtime.data.repository.PasswordResetRepository
import com.example.runtime.testing.FakeApiService
import com.example.runtime.testing.MainDispatcherRule
import com.example.runtime.ui.common.UiText
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class PasswordResetViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val api = ResetApi()
    private val viewModel = PasswordResetViewModel(PasswordResetRepository(api))

    @Test
    fun sendCode_movesToCodeStepWithTrimmedEmail() {
        viewModel.sendCode("  runner@test.com ")

        assertEquals(listOf(PasswordResetRequest("runner@test.com")), api.requests)
        assertEquals(PasswordResetStep.CODE, viewModel.uiState.step)
        assertEquals("runner@test.com", viewModel.uiState.email)
        assertFalse(viewModel.uiState.isLoading)
        assertNull(viewModel.uiState.error)
    }

    @Test
    fun sendCode_rejectsBlankAndInvalidEmailWithoutCallingServer() {
        viewModel.sendCode(" ")
        assertEquals(UiText.Resource(R.string.error_email_required), viewModel.uiState.error)

        viewModel.sendCode("not-an-email")
        assertEquals(UiText.Resource(R.string.error_email_invalid), viewModel.uiState.error)

        assertTrue(api.requests.isEmpty())
        assertEquals(PasswordResetStep.EMAIL, viewModel.uiState.step)
    }

    @Test
    fun resendCode_requestsAgainAndShowsNotice() {
        viewModel.sendCode("runner@test.com")

        viewModel.resendCode()

        assertEquals(2, api.requests.size)
        assertEquals(UiText.Resource(R.string.reset_password_code_resent), viewModel.uiState.notice)
        assertEquals(PasswordResetStep.CODE, viewModel.uiState.step)
    }

    @Test
    fun confirm_validatesLocally() {
        viewModel.sendCode("runner@test.com")
        val cases = listOf(
            Triple("", "new-password", "new-password") to R.string.error_all_fields_required,
            Triple("12a456", "new-password", "new-password") to R.string.error_reset_code_format,
            Triple("123456", "short", "short") to R.string.error_password_short,
            Triple("123456", "new-password", "other-password") to R.string.error_password_mismatch,
        )
        for ((input, error) in cases) {
            viewModel.confirm(input.first, input.second, input.third)
            assertEquals(UiText.Resource(error), viewModel.uiState.error)
        }
        assertTrue(api.confirms.isEmpty())
    }

    @Test
    fun confirm_sendsCodeAndFinishes() {
        viewModel.sendCode("runner@test.com")

        viewModel.confirm(" 123456 ", "new-password", "new-password")

        assertEquals(listOf(PasswordResetConfirmRequest("runner@test.com", "123456", "new-password")), api.confirms)
        assertEquals(PasswordResetStep.DONE, viewModel.uiState.step)
        assertNull(viewModel.uiState.error)
    }

    @Test
    fun confirm_showsServerErrorAndStaysOnCodeStep() {
        viewModel.sendCode("runner@test.com")
        api.confirmError = httpError(400, "The code is invalid or has expired.")

        viewModel.confirm("123456", "new-password", "new-password")

        assertEquals(UiText.Resource(R.string.error_reset_code_invalid), viewModel.uiState.error)
        assertEquals(PasswordResetStep.CODE, viewModel.uiState.step)
        assertFalse(viewModel.uiState.isLoading)
    }

    @Test
    fun changeEmail_andReset_goBackToEmailStep() {
        viewModel.sendCode("runner@test.com")

        viewModel.changeEmail()
        assertEquals(PasswordResetStep.EMAIL, viewModel.uiState.step)
        assertEquals("runner@test.com", viewModel.uiState.email)

        viewModel.reset()
        assertEquals(PasswordResetUiState(), viewModel.uiState)
    }

    private fun httpError(code: Int, message: String) = HttpException(
        Response.error<Unit>(
            code,
            """{"status": "error", "message": "$message"}""".toResponseBody("application/json".toMediaType()),
        )
    )

    private class ResetApi : FakeApiService() {
        val requests = mutableListOf<PasswordResetRequest>()
        val confirms = mutableListOf<PasswordResetConfirmRequest>()
        var confirmError: Exception? = null

        override suspend fun requestPasswordReset(body: PasswordResetRequest): ApiResponse<Unit> {
            requests += body
            return ApiResponse(status = "success")
        }

        override suspend fun confirmPasswordReset(body: PasswordResetConfirmRequest): ApiResponse<Unit> {
            confirmError?.let { throw it }
            confirms += body
            return ApiResponse(status = "success")
        }
    }
}
