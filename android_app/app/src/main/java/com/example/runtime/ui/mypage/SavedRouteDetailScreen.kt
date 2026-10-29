package com.example.runtime.ui.mypage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.ui.common.LocalAppMessages
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.formatKm
import com.example.runtime.ui.common.placeholder
import com.example.runtime.ui.common.routeTitle
import com.example.runtime.ui.common.sharedElement
import com.example.runtime.ui.route.FullScreenRouteMap
import com.example.runtime.ui.route.RouteMap
import com.example.runtime.ui.theme.Spacing

@Composable
fun SavedRouteDetailScreen(myPageViewModel: MyPageViewModel, routeId: Int, onBack: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showFullMap by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    val messages = LocalAppMessages.current
    val haptic = LocalHapticFeedback.current
    val deletedMessage = stringResource(R.string.route_deleted_message)
    val renamedMessage = stringResource(R.string.route_renamed_message)

    LaunchedEffect(routeId) {
        myPageViewModel.loadDetail(routeId)
    }
    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize()) {
        val route = myPageViewModel.detail
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
            }
            Column(modifier = Modifier.weight(1f)) {
                if (route == null) {
                    Box(modifier = Modifier.size(width = 180.dp, height = 22.dp).placeholder())
                } else {
                    val defaultTitle = routeTitle(route.startName, route.endName, route.distance)
                    Text(
                        route.name.ifBlank { defaultTitle },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.sharedElement("route-title-${route.id}")
                    )
                    if (route.name.isNotBlank()) {
                        Text(
                            defaultTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            IconButton(
                onClick = { showRenameDialog = true },
                enabled = route != null && !myPageViewModel.isRenaming
            ) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.rename_route))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.m)
        ) {
            if (route == null) {
                myPageViewModel.detailError?.let {
                    Text(it.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                } ?: run {
                    Box(modifier = Modifier.fillMaxWidth().height(280.dp).placeholder(MaterialTheme.shapes.large))
                    Spacer(modifier = Modifier.height(Spacing.l))
                    Box(modifier = Modifier.size(width = 120.dp, height = 44.dp).placeholder())
                    Spacer(modifier = Modifier.height(Spacing.m))
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp).placeholder(MaterialTheme.shapes.medium))
                }
                return@Column
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(MaterialTheme.shapes.large)
            ) {
                RouteMap(
                    lines = route.route,
                    modifier = Modifier.fillMaxSize(),
                    onClick = { showFullMap = true }
                )
            }
            Spacer(modifier = Modifier.height(Spacing.l))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatKm(route.distance), style = MaterialTheme.typography.displayLarge)
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    stringResource(R.string.unit_km),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Text(
                stringResource(R.string.saved_date, route.savedAt.take(10)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.m))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(Spacing.m)) {
                    Text(
                        stringResource(R.string.llm_briefing),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(route.briefing, style = MaterialTheme.typography.bodyMedium)
                }
            }
            myPageViewModel.detailError?.let {
                Spacer(modifier = Modifier.height(Spacing.s))
                Text(it.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(Spacing.l))
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                enabled = !myPageViewModel.isDeleting,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(Spacing.s))
                Text(
                    stringResource(if (myPageViewModel.isDeleting) R.string.deleting else R.string.action_delete),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(modifier = Modifier.height(Spacing.l))
        }
    }

    val detailRoute = myPageViewModel.detail
    if (showFullMap && detailRoute != null) {
        FullScreenRouteMap(lines = detailRoute.route, onDismiss = { showFullMap = false })
    }

    if (showRenameDialog && detailRoute != null) {
        RenameRouteDialog(
            initialName = detailRoute.name,
            placeholder = routeTitle(detailRoute.startName, detailRoute.endName, detailRoute.distance),
            isSaving = myPageViewModel.isRenaming,
            onDismiss = { showRenameDialog = false },
            onSave = { name ->
                myPageViewModel.rename(routeId, name) {
                    showRenameDialog = false
                    messages?.show(renamedMessage)
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_dialog_title)) },
            text = { Text(stringResource(R.string.delete_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    myPageViewModel.delete(routeId) {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        messages?.show(deletedMessage)
                        onBack()
                    }
                }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun RenameRouteDialog(
    initialName: String,
    placeholder: String,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_route)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_ROUTE_NAME_LENGTH) },
                label = { Text(stringResource(R.string.label_route_name)) },
                placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name) },
                enabled = !isSaving && name.isNotBlank() && name.trim() != initialName
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

private const val MAX_ROUTE_NAME_LENGTH = 255
