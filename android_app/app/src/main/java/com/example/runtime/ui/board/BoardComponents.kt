package com.example.runtime.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.data.remote.BoardComment
import com.example.runtime.data.remote.BoardPost
import com.example.runtime.ui.common.formatDateTime
import com.example.runtime.ui.common.parseInstant
import com.example.runtime.ui.theme.Spacing
import java.time.Duration
import java.time.Instant

@Composable
fun timeAgoText(createdAt: String, now: Instant = Instant.now()): String {
    val created = parseInstant(createdAt) ?: return createdAt
    val elapsed = Duration.between(created, now)
    return when {
        elapsed.toMinutes() < 1 -> stringResource(R.string.time_just_now)
        elapsed.toHours() < 1 -> elapsed.toMinutes().toInt().let { pluralStringResource(R.plurals.time_minutes_ago, it, it) }
        elapsed.toDays() < 1 -> elapsed.toHours().toInt().let { pluralStringResource(R.plurals.time_hours_ago, it, it) }
        else -> formatDateTime(createdAt)
    }
}

@Composable
fun postAuthorLabel(post: BoardPost): String = post.author ?: stringResource(R.string.board_anonymous)

@Composable
fun commentAuthorLabel(comment: BoardComment): String = when {
    comment.anonymous && comment.isPostAuthor -> stringResource(R.string.board_anonymous_writer)
    comment.anonymous -> stringResource(R.string.board_anonymous_number, comment.anonymousNumber ?: 0)
    comment.isPostAuthor -> stringResource(R.string.board_named_writer, comment.author.orEmpty())
    else -> comment.author.orEmpty()
}

@Composable
fun CountLabel(icon: Int, count: Int, tint: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(2.dp))
        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
fun PostCard(post: BoardPost, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.m, vertical = Spacing.m)
    ) {
        Text(post.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            post.body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(Spacing.s))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${timeAgoText(post.createdAt)} · ${postAuthorLabel(post)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (post.likeCount > 0) CountLabel(R.drawable.ic_thumb_up, post.likeCount, MaterialTheme.colorScheme.error)
            if (post.commentCount > 0) {
                Spacer(modifier = Modifier.width(Spacing.s))
                CountLabel(R.drawable.ic_chat, post.commentCount, MaterialTheme.colorScheme.tertiary)
            }
            if (post.scrapCount > 0) {
                Spacer(modifier = Modifier.width(Spacing.s))
                CountLabel(R.drawable.ic_bookmark, post.scrapCount, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ReportDialog(title: String, onDismiss: () -> Unit, onReport: (ReportReason) -> Unit) {
    var selected by remember { mutableStateOf<ReportReason?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    stringResource(R.string.report_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.s))
                ReportReason.entries.forEach { reason ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected == reason, onClick = { selected = reason }, role = Role.RadioButton)
                            .padding(vertical = Spacing.xs)
                    ) {
                        RadioButton(selected = selected == reason, onClick = null)
                        Spacer(modifier = Modifier.width(Spacing.s))
                        Text(stringResource(reason.label), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { selected?.let(onReport) }, enabled = selected != null) {
                Text(stringResource(R.string.action_report), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
