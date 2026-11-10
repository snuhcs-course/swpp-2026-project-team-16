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
    "A verified Google email is required" to R.string.error_google_email_unverified,
    "All fields are required" to R.string.error_all_fields_required,
    "Authentication credentials were not provided." to R.string.error_auth_required,
    "Authentication required." to R.string.error_auth_required,
    "Comment not found." to R.string.error_comment_not_found,
    "Email already exists" to R.string.error_email_exists,
    "Enter a valid email address" to R.string.error_email_invalid,
    "Google authentication is temporarily unavailable." to R.string.error_google_unavailable,
    "Group not found." to R.string.error_group_not_found,
    "Invalid Google credential." to R.string.error_google_invalid,
    "Invalid username or password" to R.string.error_invalid_credentials,
    "Join this group to see the chat." to R.string.error_group_chat_members,
    "Multiple accounts use this email" to R.string.error_google_multiple_accounts,
    "Name must be 30 characters or fewer." to R.string.error_display_name_long,
    "Password must be at least 8 characters" to R.string.error_password_short,
    "Post not found." to R.string.error_post_not_found,
    "Rating must be a whole number from 1 to 5." to R.string.review_need_rating,
    "Route not found." to R.string.error_route_not_found,
    "Run or save this route before reviewing it." to R.string.review_eligibility,
    "Run this route before sharing it." to R.string.error_run_before_share,
    "Status message must be 100 characters or fewer." to R.string.error_status_long,
    "Temporary route not found." to R.string.error_temporary_route_not_found,
    "The image must be 2 MB or smaller." to R.string.error_avatar_too_big,
    "The image must be a JPEG or PNG." to R.string.error_avatar_type,
    "The join deadline must be before the run starts." to R.string.error_group_deadline_after_run,
    "The join deadline must be in the future." to R.string.error_group_deadline_past,
    "The password is incorrect." to R.string.error_group_password_wrong,
    "The password must be 4 to 20 characters." to R.string.error_group_password,
    "This group is full." to R.string.error_group_full,
    "This group is no longer taking members." to R.string.error_group_closed,
    "This route is already shared." to R.string.error_already_shared,
    "User not found." to R.string.error_user_not_found,
    "Username already exists" to R.string.error_username_exists,
    "Username and password are required" to R.string.error_username_password_required,
    "You already joined this group." to R.string.error_group_already_joined,
    "You already reported this comment." to R.string.error_already_reported,
    "You already reported this post." to R.string.error_already_reported,
    "You already reviewed this route." to R.string.error_already_reviewed,
    "You can only change your own post." to R.string.error_not_your_post,
    "You can't leave after the run has started." to R.string.error_group_leave_started,
    "You can't follow yourself." to R.string.error_follow_self,
    "You can't like your own post." to R.string.error_like_own_post,
    "You can't report your own comment." to R.string.error_report_own,
    "You can't report your own post." to R.string.error_report_own,
    "You can't review a route you shared." to R.string.review_own_route,
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
