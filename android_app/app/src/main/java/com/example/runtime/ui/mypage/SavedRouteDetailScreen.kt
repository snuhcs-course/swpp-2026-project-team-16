package com.example.runtime.ui.mypage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runtime.R
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.formatDistance
import com.example.runtime.ui.common.routeTitle
import com.example.runtime.ui.route.FullScreenRouteMap
import com.example.runtime.ui.route.RouteMap

@Composable
fun SavedRouteDetailScreen(myPageViewModel: MyPageViewModel, routeId: Int, onBack: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showFullMap by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    LaunchedEffect(routeId) {
        myPageViewModel.loadDetail(routeId)
    }
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val route = myPageViewModel.detail
        if (route == null) {
            myPageViewModel.detailError?.let {
                Text(it.asString(), color = MaterialTheme.colorScheme.error)
            } ?: CircularProgressIndicator()
            return@Column
        }
        val defaultTitle = routeTitle(route.startName, route.endName, route.distance)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(route.name.ifBlank { defaultTitle }, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (route.name.isNotBlank()) {
                    Text(defaultTitle, color = Color.Gray, fontSize = 13.sp)
                }
            }
            IconButton(onClick = { showRenameDialog = true }, enabled = !myPageViewModel.isRenaming) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.rename_route))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            RouteMap(
                lines = route.route,
                modifier = Modifier.fillMaxSize(),
                onClick = { showFullMap = true }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.distance_value, formatDistance(route.distance)),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.saved_date, route.savedAt.take(10)), color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.llm_briefing), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(route.briefing)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(onClick = onBack) {
                Text(stringResource(R.string.action_back))
            }
            Button(
                onClick = { showDeleteDialog = true },
                enabled = !myPageViewModel.isDeleting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(if (myPageViewModel.isDeleting) R.string.deleting else R.string.action_delete))
            }
        }
        myPageViewModel.detailError?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it.asString(), color = MaterialTheme.colorScheme.error)
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
            onSave = { name -> myPageViewModel.rename(routeId, name) { showRenameDialog = false } }
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
                    myPageViewModel.delete(routeId, onBack)
                }) {
                    Text(stringResource(R.string.action_delete))
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
