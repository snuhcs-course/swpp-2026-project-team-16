package com.example.runtime.ui.notifications

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.runtime.R
import com.example.runtime.data.push.Push
import com.example.runtime.ui.auth.PasswordField
import com.example.runtime.ui.common.AppLanguage
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.currentAppLanguage
import com.example.runtime.ui.common.setAppLanguage
import com.example.runtime.ui.theme.Spacing
import com.example.runtime.ui.theme.ThemeMode
import com.example.runtime.ui.theme.ThemeStorage
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: NotificationsViewModel,
    hasPassword: Boolean,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onAccountDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var phoneAllowed by remember { mutableStateOf(Push.canPost(context)) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadSettings() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) phoneAllowed = Push.canPost(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
            }
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.m),
        ) {
            Text(stringResource(R.string.settings_display), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Spacing.s))
            ThemeSetting()
            Spacer(Modifier.height(Spacing.s))
            LanguageSetting()

            Spacer(Modifier.height(Spacing.l))
            Text(stringResource(R.string.settings_notifications), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.settings_notifications_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!phoneAllowed) {
                Spacer(Modifier.height(Spacing.s))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(Spacing.m)) {
                        Text(stringResource(R.string.settings_phone_off), style = MaterialTheme.typography.bodyMedium)
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                )
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) { Text(stringResource(R.string.settings_open_phone_settings)) }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.s))
            val settings = viewModel.settings
            when {
                settings == null && viewModel.settingsError == null -> CircularProgressIndicator(
                    modifier = Modifier.padding(Spacing.l).align(Alignment.CenterHorizontally),
                )
                settings == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        viewModel.settingsError!!.asString(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::loadSettings) { Text(stringResource(R.string.action_retry)) }
                }
                else -> {
                    ToggleRow(
                        title = stringResource(R.string.settings_all_notifications),
                        description = null,
                        checked = NotificationKind.entries.all { settings[it.apiValue] != false },
                        onChange = viewModel::setAll,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NotificationKind.entries.forEach { kind ->
                        ToggleRow(
                            title = stringResource(kind.label),
                            description = stringResource(kind.description),
                            checked = settings[kind.apiValue] != false,
                            onChange = { viewModel.toggle(kind, it) },
                        )
                    }
                    viewModel.settingsError?.let {
                        Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(Modifier.height(Spacing.l))
            Text(stringResource(R.string.settings_account), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Spacing.s))
            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_log_out)) }
            Spacer(Modifier.height(Spacing.s))
            OutlinedButton(
                onClick = {
                    viewModel.clearDeleteError()
                    showDeleteDialog = true
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_delete_account)) }
            Spacer(Modifier.height(Spacing.l))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.log_out_dialog_title)) },
            text = { Text(stringResource(R.string.log_out_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) { Text(stringResource(R.string.action_log_out)) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showDeleteDialog) {
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!viewModel.isDeleting) showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_account_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(stringResource(R.string.delete_account_message))
                    if (hasPassword) {
                        PasswordField(
                            value = password,
                            onValueChange = { password = it },
                            label = stringResource(R.string.delete_account_password),
                        )
                    }
                    viewModel.deleteError?.let {
                        Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteAccount(password.takeIf { hasPassword }, onAccountDeleted) },
                    enabled = !viewModel.isDeleting && (!hasPassword || password.isNotEmpty()),
                ) {
                    Text(stringResource(R.string.action_delete_account), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }, enabled = !viewModel.isDeleting) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ToggleRow(title: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            description?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ThemeSetting() {
    val context = LocalContext.current
    val storage = remember(context) { ThemeStorage(context.applicationContext) }
    val current by storage.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val scope = rememberCoroutineScope()
    ChoiceSetting(
        title = stringResource(R.string.theme),
        options = ThemeMode.entries,
        selected = current,
        label = { stringResource(it.label) },
        onSelect = { mode -> scope.launch { storage.setThemeMode(mode) } },
    )
}

@Composable
private fun LanguageSetting() {
    val context = LocalContext.current
    val current = context.currentAppLanguage()
    ChoiceSetting(
        title = stringResource(R.string.language),
        options = AppLanguage.entries,
        selected = current,
        label = { stringResource(it.label) },
        onSelect = { language -> if (language != current) context.setAppLanguage(language) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceSetting(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = label(selected),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(title) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
