package com.example.runtime.ui.auth

import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.runtime.R

@Composable
fun LoginPromptDialog(@StringRes message: Int, onLogIn: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.login_prompt_title)) },
        text = { Text(stringResource(message)) },
        confirmButton = {
            TextButton(onClick = onLogIn) {
                Text(stringResource(R.string.action_log_in))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_not_now))
            }
        }
    )
}
