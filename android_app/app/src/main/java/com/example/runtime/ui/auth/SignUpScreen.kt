package com.example.runtime.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.runtime.R

@Composable
fun SignUpScreen(
    authViewModel: AuthViewModel,
    initialEmail: String,
    onSignUpSuccess: () -> Unit,
    onLogInClick: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable(initialEmail) { mutableStateOf(initialEmail) }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    val uiState = authViewModel.uiState

    fun goToLogIn() {
        authViewModel.clearError()
        onLogInClick()
    }

    BackHandler(onBack = ::goToLogIn)

    AuthLayout(title = stringResource(R.string.title_sign_up), error = uiState.error) {
        AuthTextField(value = name, onValueChange = { name = it }, label = stringResource(R.string.label_name))
        Spacer(modifier = Modifier.height(12.dp))
        AuthTextField(
            value = email,
            onValueChange = { email = it },
            label = stringResource(R.string.label_email),
            keyboardType = KeyboardType.Email
        )
        Spacer(modifier = Modifier.height(12.dp))
        PasswordField(value = password, onValueChange = { password = it }, label = stringResource(R.string.label_password))
        Spacer(modifier = Modifier.height(12.dp))
        PasswordField(
            value = passwordConfirm,
            onValueChange = { passwordConfirm = it },
            label = stringResource(R.string.label_password_confirm)
        )
        Spacer(modifier = Modifier.height(24.dp))
        PrimaryAuthButton(
            text = stringResource(R.string.action_sign_up),
            isLoading = uiState.isLoading,
            onClick = { authViewModel.register(name, email, password, passwordConfirm, onSignUpSuccess) }
        )
        OrDivider()
        GoogleSignInButton(
            authViewModel = authViewModel,
            enabled = !uiState.isLoading,
            onSuccess = onSignUpSuccess,
            onSignUpRequired = { email = it }
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = ::goToLogIn, enabled = !uiState.isLoading) {
            Text(stringResource(R.string.switch_to_log_in))
        }
    }
}
