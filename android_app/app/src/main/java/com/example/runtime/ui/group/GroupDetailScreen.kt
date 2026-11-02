package com.example.runtime.ui.group

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.data.remote.GroupRanking
import com.example.runtime.data.remote.RunningGroup
import com.example.runtime.ui.common.AccentButton
import com.example.runtime.ui.common.LocalAppMessages
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.formatDateTime
import com.example.runtime.ui.common.formatDistance
import com.example.runtime.ui.common.formatDuration
import com.example.runtime.ui.common.formatPaceSeconds
import com.example.runtime.ui.common.placeholder
import com.example.runtime.ui.route.FullScreenRouteMap
import com.example.runtime.ui.route.RouteMap
import com.example.runtime.ui.theme.Spacing

@Composable
fun GroupDetailScreen(
    groupsViewModel: GroupsViewModel,
    groupId: Int,
    isLoggedIn: Boolean,
    onBack: () -> Unit,
    onRun: (RunningGroup) -> Unit,
    onOpenChat: () -> Unit,
    onLogInToJoin: () -> Unit,
) {
    var showFullMap by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val messages = LocalAppMessages.current
    val haptic = LocalHapticFeedback.current
    val joinedMessage = stringResource(R.string.group_joined_message)
    val leftMessage = stringResource(R.string.group_left_message)
    val deletedMessage = stringResource(R.string.group_deleted_message)
    val actionError = (groupsViewModel.actionState as? GroupActionState.Failed)?.message?.asString()
    val isWorking = groupsViewModel.actionState == GroupActionState.Working

    LaunchedEffect(groupId) {
        groupsViewModel.loadDetail(groupId)
    }
    LaunchedEffect(actionError) {
        actionError?.let { messages?.show(it) }
    }
    BackHandler(onBack = onBack)

    val group = groupsViewModel.detail?.takeIf { it.id == groupId }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
            }
            if (group == null) {
                Box(modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.size(width = 180.dp, height = 22.dp).placeholder())
                }
            } else {
                Text(
                    group.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (group.isHost) {
                    IconButton(onClick = { showDeleteDialog = true }, enabled = !isWorking) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.action_delete_group))
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.m)
        ) {
            if (group == null) {
                groupsViewModel.detailError?.let {
                    Text(it.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                } ?: Box(modifier = Modifier.fillMaxWidth().height(220.dp).placeholder(MaterialTheme.shapes.large))
                return@Column
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(MaterialTheme.shapes.large)
            ) {
                if (group.route.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().placeholder(MaterialTheme.shapes.large))
                } else {
                    RouteMap(lines = group.route, modifier = Modifier.fillMaxSize(), onClick = { showFullMap = true })
                }
            }
            Spacer(modifier = Modifier.height(Spacing.m))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GroupStatusBadge(group)
                Spacer(modifier = Modifier.width(Spacing.s))
                Text(
                    stringResource(R.string.group_hosted_by, group.host),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(Spacing.m))
            GroupInfoRow(R.drawable.ic_schedule, stringResource(R.string.group_meet_at, formatDateTime(group.meetAt)))
            Spacer(modifier = Modifier.height(Spacing.s))
            GroupInfoRow(
                R.drawable.ic_groups,
                stringResource(R.string.group_join_deadline_at, formatDateTime(group.joinDeadline)) +
                    if (group.status == GROUP_OPEN) " · " + timeLeftText(group.joinDeadline) else ""
            )
            Spacer(modifier = Modifier.height(Spacing.s))
            GroupInfoRow(
                R.drawable.ic_distance,
                listOf(group.startName.ifBlank { null }, formatDistance(group.routeDistance)).filterNotNull().joinToString(" · ")
            )
            if (group.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(Spacing.m))
                Text(group.description, style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(modifier = Modifier.height(Spacing.l))
            Text(stringResource(R.string.group_members_title), style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(Spacing.s))
            MemberProgress(group)
            if (group.members.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.s))
                Text(
                    group.members.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(Spacing.l))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_trophy), contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(Spacing.s))
                Text(stringResource(R.string.group_ranking_title), style = MaterialTheme.typography.titleLarge)
            }
            Text(
                stringResource(R.string.group_ranking_rule),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.s))
            if (group.ranking.isEmpty()) {
                Text(
                    stringResource(R.string.group_ranking_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.s)
                )
            } else {
                group.ranking.forEach { RankingRow(it) }
            }

            if (group.isMember && !group.isHost) {
                Spacer(modifier = Modifier.height(Spacing.m))
                TextButton(
                    onClick = { showLeaveDialog = true },
                    enabled = !isWorking,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_leave_group))
                }
            }
            Spacer(modifier = Modifier.height(Spacing.l))
        }

        if (group != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.m),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (group.isMember) {
                    OutlinedButton(
                        onClick = onOpenChat,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Icon(painterResource(R.drawable.ic_chat), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(Spacing.s))
                        Text(stringResource(R.string.action_group_chat), style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(modifier = Modifier.width(Spacing.s))
                    AccentButton(
                        text = stringResource(R.string.action_run_now),
                        icon = R.drawable.ic_directions_run,
                        onClick = { onRun(group) },
                        enabled = group.route.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    AccentButton(
                        text = stringResource(
                            when {
                                isWorking -> R.string.joining
                                group.status == GROUP_OPEN -> R.string.action_join_group
                                group.status == GROUP_FULL -> R.string.group_status_full
                                else -> R.string.group_status_closed
                            }
                        ),
                        icon = R.drawable.ic_groups,
                        onClick = {
                            if (!isLoggedIn) {
                                onLogInToJoin()
                            } else {
                                groupsViewModel.join {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    messages?.show(joinedMessage)
                                }
                            }
                        },
                        enabled = group.status == GROUP_OPEN && !isWorking,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    val detail = groupsViewModel.detail
    if (showFullMap && detail != null) {
        FullScreenRouteMap(lines = detail.route, onDismiss = { showFullMap = false })
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text(stringResource(R.string.leave_group_title)) },
            text = { Text(stringResource(R.string.leave_group_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveDialog = false
                    groupsViewModel.leave { messages?.show(leftMessage) }
                }) {
                    Text(stringResource(R.string.action_leave_group), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_group_title)) },
            text = { Text(stringResource(R.string.delete_group_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    groupsViewModel.delete {
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
private fun RankingRow(ranking: GroupRanking) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (ranking.isMe) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.s, vertical = Spacing.s)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (ranking.rank <= 3) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Text(
                ranking.rank.toString(),
                style = MaterialTheme.typography.titleSmall,
                color = if (ranking.rank <= 3) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(Spacing.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                ranking.username + if (ranking.isMe) " " + stringResource(R.string.group_ranking_me) else "",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (ranking.isMe) FontWeight.Bold else null
            )
            Text(
                "${formatDistance(ranking.distanceMeters)} · ${formatDuration(ranking.durationSeconds)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(formatPaceSeconds(ranking.paceSecondsPerKm), style = MaterialTheme.typography.titleMedium)
    }
}
