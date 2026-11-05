package com.example.runtime.ui.community

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.formatDistance
import com.example.runtime.ui.route.RunRecordState
import com.example.runtime.ui.route.RunViewModel
import com.example.runtime.ui.route.ShareState
import com.example.runtime.ui.theme.Spacing

private const val MAX_TITLE_LENGTH = 60
private const val MAX_DESCRIPTION_LENGTH = 300

@Composable
fun RunShareAction(
    runViewModel: RunViewModel,
    isLoggedIn: Boolean,
    onShared: (Int) -> Unit,
    onOpenShared: (Int) -> Unit,
) {
    var showShareDialog by rememberSaveable { mutableStateOf(false) }

    when (val record = runViewModel.recordState) {
        RunRecordState.Idle -> if (!isLoggedIn) {
            Text(
                stringResource(R.string.run_log_in_to_share),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RunRecordState.Saving -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        is RunRecordState.Failed -> TextButton(onClick = runViewModel::retryRecord) {
            Text(stringResource(R.string.run_record_retry))
        }
        is RunRecordState.Saved -> if (record.sharedRouteId == null) {
            TextButton(onClick = { showShareDialog = true }) {
                Text(stringResource(R.string.action_share_to_community))
            }
        } else {
            TextButton(onClick = { onOpenShared(record.sharedRouteId) }) {
                Text(stringResource(R.string.action_view_in_community))
            }
        }
    }

    if (showShareDialog) {
        ShareRouteDialog(
            defaultTitle = runViewModel.target?.let { target ->
                if (target.startName.isBlank()) stringResource(R.string.default_share_title_no_start, formatDistance(target.distanceMeters))
                else stringResource(R.string.default_share_title, target.startName, formatDistance(target.distanceMeters))
            }.orEmpty(),
            shareState = runViewModel.shareState,
            onDismiss = { showShareDialog = false },
            onShare = { title, description ->
                runViewModel.share(title, description) { sharedRouteId ->
                    showShareDialog = false
                    onShared(sharedRouteId)
                }
            }
        )
    }
}

@Composable
fun ShareRouteDialog(
    defaultTitle: String,
    shareState: ShareState,
    onDismiss: () -> Unit,
    onShare: (String, String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(defaultTitle.take(MAX_TITLE_LENGTH)) }
    var description by rememberSaveable { mutableStateOf("") }
    val isSharing = shareState == ShareState.Sharing

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_dialog_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.share_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.m))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(MAX_TITLE_LENGTH) },
                    label = { Text(stringResource(R.string.label_share_title)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.s))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(MAX_DESCRIPTION_LENGTH) },
                    label = { Text(stringResource(R.string.label_share_description)) },
                    minLines = 3,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                (shareState as? ShareState.Failed)?.let {
                    Spacer(modifier = Modifier.height(Spacing.s))
                    Text(it.message.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onShare(title, description) }, enabled = !isSharing && title.isNotBlank()) {
                Text(stringResource(if (isSharing) R.string.sharing else R.string.action_share))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSharing) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
