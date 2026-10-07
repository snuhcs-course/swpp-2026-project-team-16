package com.example.runtime.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.ui.common.LanguageMenu
import com.example.runtime.ui.common.asString

// 3. Login Screen (화면 3)
@Composable
fun LoginScreen(authViewModel: AuthViewModel, onLoginSuccess: () -> Unit) {
    var id by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var isRegister by remember { mutableStateOf(false) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    val uiState = authViewModel.uiState

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            IconButton(onClick = { showLanguageMenu = true }) {
                Icon(
                    painterResource(R.drawable.ic_language),
                    contentDescription = stringResource(R.string.language)
                )
            }
            LanguageMenu(expanded = showLanguageMenu, onDismiss = { showLanguageMenu = false })
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            OutlinedTextField(
                value = id,
                onValueChange = { id = it },
                label = { Text(stringResource(R.string.label_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (isRegister) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.label_email)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text(stringResource(R.string.label_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            uiState.error?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it.asString(), color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    if (isRegister) {
                        authViewModel.register(id, email, pass, onLoginSuccess)
                    } else {
                        authViewModel.login(id, pass, onLoginSuccess)
                    }
                },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(if (isRegister) R.string.action_sign_up else R.string.action_log_in))
                }
            }
            TextButton(
                onClick = {
                    isRegister = !isRegister
                    authViewModel.clearError()
                },
                enabled = !uiState.isLoading
            ) {
                Text(stringResource(if (isRegister) R.string.switch_to_log_in else R.string.switch_to_sign_up))
            }
        }
    }
}
