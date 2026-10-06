package com.example.runtime.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.runtime.R
import com.example.runtime.data.remote.EmptyResponseException
import com.example.runtime.data.remote.serverMessage
import retrofit2.HttpException
import java.io.IOException

sealed interface UiText {
    data class Resource(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plain(val value: String) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Resource -> stringResource(id, *args.toTypedArray())
    is UiText.Plain -> value
}

private val serverMessages = mapOf(
    "All fields are required" to R.string.error_all_fields_required,
    "Authentication credentials were not provided." to R.string.error_auth_required,
    "Authentication required." to R.string.error_auth_required,
    "Invalid username or password" to R.string.error_invalid_credentials,
    "Route not found." to R.string.error_route_not_found,
    "Temporary route not found." to R.string.error_temporary_route_not_found,
    "Username already exists" to R.string.error_username_exists,
    "Username and password are required" to R.string.error_username_password_required,
)

private fun serverText(message: String): UiText =
    serverMessages[message]?.let { UiText.Resource(it) } ?: UiText.Plain(message)

fun Throwable.toUiText(): UiText = when (this) {
    is HttpException -> serverMessage()?.let(::serverText)
        ?: UiText.Resource(R.string.error_request_failed, listOf(code()))
    is IOException -> UiText.Resource(R.string.error_network)
    is EmptyResponseException -> serverMessage?.let(::serverText)
        ?: UiText.Resource(R.string.error_empty_response)
    else -> UiText.Resource(R.string.error_unknown)
}
