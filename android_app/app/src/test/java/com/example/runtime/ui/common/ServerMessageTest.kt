package com.example.runtime.ui.common

import com.example.runtime.R
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class ServerMessageTest {

    @Test
    fun mapsKnownAuthMessages() {
        val cases = mapOf(
            "Email already exists" to R.string.error_email_exists,
            "Enter a valid email address" to R.string.error_email_invalid,
            "Password must be at least 8 characters" to R.string.error_password_short,
            "Username already exists" to R.string.error_username_exists,
            "Invalid Google credential." to R.string.error_google_invalid,
            "A verified Google email is required" to R.string.error_google_email_unverified,
            "Multiple accounts use this email" to R.string.error_google_multiple_accounts,
            "Authentication credentials were not provided." to R.string.error_auth_required,
        )
        for ((message, resource) in cases) {
            assertEquals(message, UiText.Resource(resource), httpError(400, message).toUiText())
        }
    }

    @Test
    fun keepsUnknownMessagesAsText() {
        assertEquals(UiText.Plain("Something new"), httpError(400, "Something new").toUiText())
    }

    @Test
    fun usesStatusCodeWhenThereIsNoMessage() {
        val error = HttpException(Response.error<Unit>(502, "".toResponseBody(null)))
        assertEquals(UiText.Resource(R.string.error_request_failed, listOf(502)), error.toUiText())
    }

    @Test
    fun mapsNetworkErrors() {
        assertEquals(UiText.Resource(R.string.error_network), IOException("offline").toUiText())
    }

    private fun httpError(code: Int, message: String) = HttpException(
        Response.error<Unit>(
            code,
            """{"status": "error", "message": "$message"}""".toResponseBody("application/json".toMediaType()),
        )
    )
}
