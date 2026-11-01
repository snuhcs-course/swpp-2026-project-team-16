package com.example.runtime.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.ui.common.PreferenceButtons
import com.example.runtime.ui.common.UiText
import com.example.runtime.ui.common.accentButtonColors
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun AuthLayout(
    title: String,
    error: UiText?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Spacing.l, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BrandMark()
            Spacer(modifier = Modifier.height(Spacing.s))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(Spacing.l))
            content()
            error?.let {
                Spacer(modifier = Modifier.height(Spacing.m))
                Text(it.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.s)) {
            PreferenceButtons()
        }
    }
}

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_directions_run),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.width(Spacing.s))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
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
        shape = MaterialTheme.shapes.medium,
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
        shape = MaterialTheme.shapes.medium,
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
    Button(
        onClick = onClick,
        enabled = !isLoading,
        colors = accentButtonColors(),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun OrDivider() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.m)
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.or),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.m)
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
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Image(painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(Spacing.s))
        Text(
            stringResource(R.string.continue_with_google),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
