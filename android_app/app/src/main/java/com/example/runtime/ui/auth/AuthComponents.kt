package com.example.runtime.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runtime.R
import com.example.runtime.ui.common.LanguageMenu
import com.example.runtime.ui.common.UiText
import com.example.runtime.ui.common.asString
import kotlinx.coroutines.launch

@Composable
fun AuthLayout(
    title: String,
    error: UiText?,
    content: @Composable ColumnScope.() -> Unit,
) {
    var showLanguageMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.app_name), fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(32.dp))
            content()
            error?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it.asString(), color = MaterialTheme.colorScheme.error)
            }
        }
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            IconButton(onClick = { showLanguageMenu = true }) {
                Icon(painterResource(R.drawable.ic_language), contentDescription = stringResource(R.string.language))
            }
            LanguageMenu(expanded = showLanguageMenu, onDismiss = { showLanguageMenu = false })
        }
    }
}

@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String) {
    var visible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    painterResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                    contentDescription = stringResource(if (visible) R.string.hide_password else R.string.show_password)
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun PrimaryAuthButton(text: String, isLoading: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(text)
        }
    }
}

@Composable
fun OrDivider() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.or),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
fun GoogleSignInButton(
    authViewModel: AuthViewModel,
    enabled: Boolean,
    onSuccess: () -> Unit,
    onSignUpRequired: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRequesting by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = {
            isRequesting = true
            authViewModel.clearError()
            scope.launch {
                when (val result = requestGoogleCredential(context)) {
                    is GoogleCredentialResult.Success ->
                        authViewModel.googleLogin(result.idToken, result.email, onSuccess, onSignUpRequired)
                    is GoogleCredentialResult.Failure -> authViewModel.showError(result.message)
                    GoogleCredentialResult.Cancelled -> Unit
                }
                isRequesting = false
            }
        },
        enabled = enabled && !isRequesting,
        modifier = Modifier.fillMaxWidth()
    ) {
        Image(painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(stringResource(R.string.continue_with_google), color = MaterialTheme.colorScheme.onSurface)
    }
}
