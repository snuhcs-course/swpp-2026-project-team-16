package com.example.runtime.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.example.runtime.R
import com.example.runtime.ui.theme.Spacing

@Composable
fun SignUpScreen(
    authViewModel: AuthViewModel,
    onSignUpSuccess: () -> Unit,
    onLogInClick: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    val uiState = authViewModel.uiState

    fun goToLogIn() {
        authViewModel.clearError()
        onLogInClick()
    }

    BackHandler(onBack = ::goToLogIn)

    AuthLayout(title = stringResource(R.string.title_sign_up)) {
        AuthTextField(value = name, onValueChange = { name = it }, label = stringResource(R.string.label_name))
        Spacer(modifier = Modifier.height(Spacing.m))
        AuthTextField(
            value = nickname,
            onValueChange = { nickname = it.take(MAX_NICKNAME_LENGTH).replace("\n", "") },
            label = stringResource(R.string.label_nickname_optional)
        )
        Text(
            stringResource(R.string.nickname_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs)
        )
        Spacer(modifier = Modifier.height(Spacing.m))
        AuthTextField(
            value = email,
            onValueChange = { email = it },
            label = stringResource(R.string.label_email),
            keyboardType = KeyboardType.Email
        )
        Spacer(modifier = Modifier.height(Spacing.m))
        PasswordField(value = password, onValueChange = { password = it }, label = stringResource(R.string.label_password))
        Spacer(modifier = Modifier.height(Spacing.m))
        PasswordField(
            value = passwordConfirm,
            onValueChange = { passwordConfirm = it },
            label = stringResource(R.string.label_password_confirm)
        )
        Spacer(modifier = Modifier.height(Spacing.m))
        Text(
            stringResource(R.string.signup_activity_notice),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(Spacing.m))
        PrimaryAuthButton(
            text = stringResource(R.string.action_sign_up),
            isLoading = uiState.isLoading,
            error = uiState.error,
            onClick = { authViewModel.register(name, email, password, passwordConfirm, onSignUpSuccess, nickname) }
        )
        OrDivider()
        GoogleSignInButton(
            authViewModel = authViewModel,
            enabled = !uiState.isLoading,
            onSuccess = onSignUpSuccess
        )
        Spacer(modifier = Modifier.height(Spacing.s))
        TextButton(onClick = ::goToLogIn, enabled = !uiState.isLoading) {
            Text(stringResource(R.string.switch_to_log_in))
        }
    }
}

private const val MAX_NICKNAME_LENGTH = 30
