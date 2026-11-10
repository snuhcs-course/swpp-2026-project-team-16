package com.example.runtime.ui.group

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.data.remote.RunningGroup
import com.example.runtime.ui.common.formatDateTime
import com.example.runtime.ui.common.formatDistance
import com.example.runtime.ui.common.parseInstant
import com.example.runtime.ui.mypage.DistanceBadge
import com.example.runtime.ui.theme.Spacing
import java.time.Duration
import java.time.Instant

const val GROUP_OPEN = "open"
const val GROUP_FULL = "full"

@Composable
fun GroupStatusBadge(group: RunningGroup) {
    val (label, container, content) = when {
        group.status == GROUP_OPEN && group.closingSoon -> Triple(
            R.string.group_status_closing_soon,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        group.status == GROUP_OPEN -> Triple(
            R.string.group_status_open,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        group.status == GROUP_FULL -> Triple(
            R.string.group_status_full,
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> Triple(
            R.string.group_status_closed,
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    StatusPill(stringResource(label), container, content)
}

@Composable
private fun StatusPill(text: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.extraLarge) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = Spacing.xs))
    }
}

@Composable
fun timeLeftText(deadline: String, now: Instant = Instant.now()): String {
    val end = parseInstant(deadline) ?: return deadline
    val left = Duration.between(now, end)
    return when {
        left.isNegative || left.isZero -> stringResource(R.string.group_deadline_passed)
        left.toDays() >= 1 -> left.toDays().toInt().let { pluralStringResource(R.plurals.group_days_left, it, it) }
        left.toHours() >= 1 -> left.toHours().toInt().let { pluralStringResource(R.plurals.group_hours_left, it, it) }
        else -> left.toMinutes().toInt().coerceAtLeast(1).let { pluralStringResource(R.plurals.group_minutes_left, it, it) }
    }
}

@Composable
fun MemberProgress(group: RunningGroup, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(
            progress = { group.memberCount.toFloat() / group.capacity },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier.weight(1f).height(6.dp)
        )
        Spacer(modifier = Modifier.width(Spacing.s))
        Text(
            stringResource(R.string.group_member_count, group.memberCount, group.capacity),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun GroupInfoRow(icon: Int, text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(Spacing.s))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun GroupCard(group: RunningGroup, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (group.isPrivate) {
                    PrivateGroupIcon()
                    Spacer(modifier = Modifier.width(Spacing.xs))
                }
                Text(
                    group.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(Spacing.s))
                GroupStatusBadge(group)
            }
            Spacer(modifier = Modifier.height(Spacing.s))
            GroupInfoRow(R.drawable.ic_schedule, formatDateTime(group.meetAt))
            Spacer(modifier = Modifier.height(Spacing.xs))
            GroupInfoRow(
                R.drawable.ic_my_location,
                listOfNotNull(
                    group.startName.ifBlank { null },
                    group.distanceFromMe?.let { stringResource(R.string.group_distance_from_me, formatDistance(it)) }
                ).joinToString(", ").ifBlank { "–" }
            )
            Spacer(modifier = Modifier.height(Spacing.m))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DistanceBadge(formatDistance(group.routeDistance))
                Spacer(modifier = Modifier.width(Spacing.s))
                if (group.status == GROUP_OPEN) {
                    Text(
                        stringResource(R.string.group_join_until, timeLeftText(group.joinDeadline)),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (group.closingSoon) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.s))
            MemberProgress(group)
        }
    }
}

@Composable
fun CreateGroupButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Icon(painterResource(R.drawable.ic_groups), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(Spacing.s))
        Text(stringResource(R.string.action_create_group_with_route), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun PrivateGroupIcon(modifier: Modifier = Modifier) {
    Icon(
        painterResource(R.drawable.ic_lock),
        contentDescription = stringResource(R.string.group_visibility_private),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(18.dp)
    )
}
