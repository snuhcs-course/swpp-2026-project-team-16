package com.example.runtime.ui.mypage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.ui.common.AccentButton
import com.example.runtime.ui.common.EmptyState
import com.example.runtime.ui.common.PreferenceButtons
import com.example.runtime.ui.common.LocalAppMessages
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.formatDistance
import com.example.runtime.ui.common.placeholder
import com.example.runtime.ui.common.routeTitle
import com.example.runtime.ui.common.sharedElement
import com.example.runtime.ui.theme.Spacing

// 7. My Page Screen (화면 7)
@Composable
fun MyPageScreen(
    myPageViewModel: MyPageViewModel,
    onRouteClick: (Int) -> Unit,
    onCreateRoute: () -> Unit,
    onLogout: () -> Unit,
) {
    var showAccountMenu by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var revealedRouteId by remember { mutableStateOf<Int?>(null) }
    val messages = LocalAppMessages.current
    val haptic = LocalHapticFeedback.current
    val deletedMessage = stringResource(R.string.route_deleted_message)

    LaunchedEffect(Unit) {
        myPageViewModel.refresh()
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.m)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.m, bottom = Spacing.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(start = Spacing.s)) {
                Text(
                    stringResource(R.string.nav_my_page),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val username = myPageViewModel.username
                if (username == null) {
                    Box(modifier = Modifier.padding(top = Spacing.xs).size(width = 120.dp, height = 24.dp).placeholder())
                } else {
                    Text(username, style = MaterialTheme.typography.headlineSmall)
                }
            }
            PreferenceButtons()
            Box {
                IconButton(onClick = { showAccountMenu = true }) {
                    Icon(
                        painterResource(R.drawable.ic_account_circle),
                        contentDescription = stringResource(R.string.menu)
                    )
                }
                DropdownMenu(expanded = showAccountMenu, onDismissRequest = { showAccountMenu = false }) {
                    Column(modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s)) {
                        Text(myPageViewModel.username ?: "", style = MaterialTheme.typography.titleSmall)
                        myPageViewModel.email?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_log_out), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showAccountMenu = false
                            showLogoutDialog = true
                        }
                    )
                }
            }
        }
        myPageViewModel.error?.let {
            Text(
                it.asString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.s)
            )
        }
        when {
            myPageViewModel.isLoading && myPageViewModel.routes.isEmpty() -> Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
                modifier = Modifier.padding(top = Spacing.s)
            ) {
                repeat(3) { Box(modifier = Modifier.fillMaxWidth().height(112.dp).placeholder(MaterialTheme.shapes.medium)) }
            }
            myPageViewModel.routes.isEmpty() && myPageViewModel.error == null -> EmptyState(
                icon = R.drawable.ic_directions_run,
                title = stringResource(R.string.no_saved_routes),
                message = stringResource(R.string.no_saved_routes_message),
                modifier = Modifier.fillMaxSize()
            ) {
                AccentButton(
                    text = stringResource(R.string.action_create_route),
                    icon = R.drawable.ic_route,
                    onClick = onCreateRoute,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            else -> {
                Text(
                    stringResource(R.string.saved_routes_count, myPageViewModel.routes.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.s, top = Spacing.s, bottom = Spacing.xs)
                )
                LazyColumn(contentPadding = PaddingValues(bottom = Spacing.m)) {
                    items(myPageViewModel.routes, key = { it.id }) { route ->
                        SwipeToDeleteItem(
                            revealed = revealedRouteId == route.id,
                            onRevealChange = { revealed ->
                                revealedRouteId = if (revealed) route.id else revealedRouteId.takeUnless { it == route.id }
                            },
                            onDelete = {
                                revealedRouteId = null
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                myPageViewModel.deleteFromList(route.id)
                                messages?.show(deletedMessage)
                            },
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Card(
                                onClick = {
                                    if (revealedRouteId != null) revealedRouteId = null else onRouteClick(route.id)
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(Spacing.m)) {
                                    val defaultTitle = routeTitle(route.startName, route.endName, route.distance)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = route.name.ifBlank { defaultTitle },
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).sharedElement("route-title-${route.id}")
                                        )
                                        Spacer(modifier = Modifier.width(Spacing.s))
                                        DistanceBadge(formatDistance(route.distance))
                                    }
                                    if (route.name.isNotBlank()) {
                                        Text(
                                            text = defaultTitle,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(Spacing.s))
                                    Text(
                                        text = route.briefing,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(Spacing.s))
                                    Text(
                                        text = route.savedAt.take(10),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
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
                }) {
                    Text(stringResource(R.string.action_log_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
fun DistanceBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = Spacing.xs)
        )
    }
}
