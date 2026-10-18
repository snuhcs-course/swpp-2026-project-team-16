package com.example.runtime.ui.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.runtime.BuildConfig
import com.example.runtime.R
import com.example.runtime.ui.common.UiText
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

sealed interface GoogleCredentialResult {
    data class Success(val idToken: String, val email: String) : GoogleCredentialResult
    data object Cancelled : GoogleCredentialResult
    data class Failure(val message: UiText) : GoogleCredentialResult
}

suspend fun requestGoogleCredential(context: Context): GoogleCredentialResult {
    val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
    if (clientId.isBlank()) {
        return GoogleCredentialResult.Failure(UiText.Resource(R.string.error_google_not_configured))
    }

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
        .build()

    return try {
        val credential = CredentialManager.create(context).getCredential(context, request).credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            GoogleCredentialResult.Success(google.idToken, google.id)
        } else {
            GoogleCredentialResult.Failure(UiText.Resource(R.string.error_google_sign_in_failed))
        }
    } catch (e: GetCredentialCancellationException) {
        GoogleCredentialResult.Cancelled
    } catch (e: NoCredentialException) {
        GoogleCredentialResult.Failure(UiText.Resource(R.string.error_google_no_account))
    } catch (e: GetCredentialException) {
        Log.e("GoogleCredential", "Google sign-in failed", e)
        GoogleCredentialResult.Failure(UiText.Resource(R.string.error_google_sign_in_failed))
    } catch (e: GoogleIdTokenParsingException) {
        Log.e("GoogleCredential", "Invalid Google ID token response", e)
        GoogleCredentialResult.Failure(UiText.Resource(R.string.error_google_sign_in_failed))
    }
}
