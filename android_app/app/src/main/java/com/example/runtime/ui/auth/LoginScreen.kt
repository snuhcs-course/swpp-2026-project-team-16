package com.example.runtime.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.runtime.R

// 3. Login Screen (화면 3)
@Composable
fun LoginScreen(authViewModel: AuthViewModel, onLoginSuccess: () -> Unit, onSignUpClick: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val uiState = authViewModel.uiState

    AuthLayout(title = stringResource(R.string.title_log_in), error = uiState.error) {
        AuthTextField(value = name, onValueChange = { name = it }, label = stringResource(R.string.label_name))
        Spacer(modifier = Modifier.height(12.dp))
        PasswordField(value = password, onValueChange = { password = it }, label = stringResource(R.string.label_password))
        Spacer(modifier = Modifier.height(24.dp))
        PrimaryAuthButton(
            text = stringResource(R.string.action_log_in),
            isLoading = uiState.isLoading,
            onClick = { authViewModel.login(name, password, onLoginSuccess) }
        )
        OrDivider()
        GoogleSignInButton(enabled = !uiState.isLoading)
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = {
                authViewModel.clearError()
                onSignUpClick()
            },
            enabled = !uiState.isLoading
        ) {
            Text(stringResource(R.string.switch_to_sign_up))
        }
    }
}
