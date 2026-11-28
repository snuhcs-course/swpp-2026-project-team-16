package com.example.runtime.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.example.runtime.R
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.theme.Spacing

@Composable
fun PasswordResetScreen(
    viewModel: PasswordResetViewModel,
    onBackToLogin: () -> Unit,
) {
    val uiState = viewModel.uiState

    fun leave() {
        viewModel.reset()
        onBackToLogin()
    }

    BackHandler(onBack = ::leave)

    AuthLayout(title = stringResource(R.string.title_reset_password)) {
        when (uiState.step) {
            PasswordResetStep.EMAIL -> EmailStep(uiState, viewModel)
            PasswordResetStep.CODE -> CodeStep(uiState, viewModel)
            PasswordResetStep.DONE -> {
                Hint(stringResource(R.string.reset_password_done))
                Spacer(modifier = Modifier.height(Spacing.l))
                PrimaryAuthButton(
                    text = stringResource(R.string.action_log_in),
                    isLoading = false,
                    onClick = ::leave
                )
            }
        }
        if (uiState.step != PasswordResetStep.DONE) {
            Spacer(modifier = Modifier.height(Spacing.s))
            TextButton(onClick = ::leave, enabled = !uiState.isLoading) {
                Text(stringResource(R.string.action_back_to_log_in))
            }
        }
    }
}

@Composable
private fun EmailStep(uiState: PasswordResetUiState, viewModel: PasswordResetViewModel) {
    var email by rememberSaveable { mutableStateOf(uiState.email) }

    Hint(stringResource(R.string.reset_password_email_hint))
    Spacer(modifier = Modifier.height(Spacing.m))
    AuthTextField(
        value = email,
        onValueChange = { email = it },
        label = stringResource(R.string.label_email),
        keyboardType = KeyboardType.Email
    )
    Spacer(modifier = Modifier.height(Spacing.l))
    PrimaryAuthButton(
        text = stringResource(R.string.action_send_code),
        isLoading = uiState.isLoading,
        error = uiState.error,
        onClick = { viewModel.sendCode(email) }
    )
}

@Composable
private fun CodeStep(uiState: PasswordResetUiState, viewModel: PasswordResetViewModel) {
    var code by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }

    Hint(stringResource(R.string.reset_password_code_hint, uiState.email))
    uiState.notice?.let {
        Spacer(modifier = Modifier.height(Spacing.s))
        Text(
            it.asString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
    Spacer(modifier = Modifier.height(Spacing.m))
    AuthTextField(
        value = code,
        onValueChange = { value -> code = value.filter(Char::isDigit).take(CODE_LENGTH) },
        label = stringResource(R.string.label_reset_code),
        keyboardType = KeyboardType.NumberPassword
    )
    Spacer(modifier = Modifier.height(Spacing.m))
    PasswordField(value = password, onValueChange = { password = it }, label = stringResource(R.string.label_new_password))
    Spacer(modifier = Modifier.height(Spacing.m))
    PasswordField(
        value = passwordConfirm,
        onValueChange = { passwordConfirm = it },
        label = stringResource(R.string.label_new_password_confirm)
    )
    Spacer(modifier = Modifier.height(Spacing.l))
    PrimaryAuthButton(
        text = stringResource(R.string.action_change_password),
        isLoading = uiState.isLoading,
        error = uiState.error,
        onClick = { viewModel.confirm(code, password, passwordConfirm) }
    )
    Spacer(modifier = Modifier.height(Spacing.s))
    TextButton(onClick = viewModel::resendCode, enabled = !uiState.isLoading) {
        Text(stringResource(R.string.action_resend_code))
    }
    TextButton(onClick = viewModel::changeEmail, enabled = !uiState.isLoading) {
        Text(stringResource(R.string.action_use_other_email))
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

private const val CODE_LENGTH = 6
