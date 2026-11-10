package com.example.runtime.ui.notifications

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.data.remote.AppNotification
import com.example.runtime.ui.board.timeAgoText
import com.example.runtime.ui.common.EmptyState
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.theme.Spacing

@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    onBack: () -> Unit,
    onOpen: (AppNotification) -> Unit,
    onOpenSettings: () -> Unit,
) {
    LaunchedEffect(Unit) { viewModel.load() }
    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
            }
            Text(
                stringResource(R.string.notifications_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (viewModel.unreadCount > 0) {
                TextButton(onClick = { viewModel.markRead(null) }) { Text(stringResource(R.string.notifications_read_all)) }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings_title))
            }
        }
        viewModel.error?.let {
            Text(it.asString(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = Spacing.m))
        }
        val items = viewModel.items
        when {
            items == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            items.isEmpty() -> EmptyState(
                icon = R.drawable.ic_notifications,
                title = stringResource(R.string.notifications_empty),
                message = stringResource(R.string.notifications_empty_message),
                modifier = Modifier.fillMaxSize(),
            )
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { item ->
                    NotificationRow(item) {
                        if (!item.read) viewModel.markRead(item.id)
                        onOpen(item)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(item: AppNotification, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (item.read) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.m, vertical = Spacing.s + 4.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(if (item.read) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(Spacing.s))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                item.body,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (item.read) null else FontWeight.SemiBold,
            )
            Text(
                timeAgoText(item.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
