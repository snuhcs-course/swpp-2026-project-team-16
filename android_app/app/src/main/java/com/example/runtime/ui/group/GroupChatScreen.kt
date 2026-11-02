package com.example.runtime.ui.group

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.data.remote.GroupMessage
import com.example.runtime.ui.common.EmptyState
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.parseInstant
import com.example.runtime.ui.theme.Spacing
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val MESSAGE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun GroupChatScreen(groupsViewModel: GroupsViewModel, groupId: Int, onBack: () -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val messages = groupsViewModel.messages

    DisposableEffect(groupId) {
        groupsViewModel.openChat(groupId)
        onDispose { groupsViewModel.closeChat() }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    groupsViewModel.detail?.takeIf { it.id == groupId }?.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.action_group_chat),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty() && groupsViewModel.chatError == null) {
                EmptyState(
                    icon = R.drawable.ic_chat,
                    title = stringResource(R.string.group_chat_empty_title),
                    message = stringResource(R.string.group_chat_empty_message),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    contentPadding = PaddingValues(Spacing.m),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(message)
                    }
                }
            }
        }
        groupsViewModel.chatError?.let {
            Text(
                it.asString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Spacing.m)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(Spacing.s)
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(MAX_MESSAGE_LENGTH) },
                placeholder = { Text(stringResource(R.string.group_chat_placeholder)) },
                maxLines = 4,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(Spacing.s))
            FilledIconButton(
                onClick = { groupsViewModel.send(groupId, draft) { draft = "" } },
                enabled = draft.isNotBlank() && !groupsViewModel.isSending,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(painterResource(R.drawable.ic_send), contentDescription = stringResource(R.string.action_send))
            }
        }
    }
}

@Composable
private fun MessageBubble(message: GroupMessage) {
    val time = parseInstant(message.createdAt)?.atZone(ZoneId.systemDefault())?.format(MESSAGE_TIME_FORMAT).orEmpty()
    Column(
        horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (!message.isMine) {
            Text(
                message.author,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.xs, bottom = 2.dp)
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            if (message.isMine) TimeLabel(time)
            Surface(
                color = if (message.isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (message.isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (message.isMine) 18.dp else 4.dp,
                    bottomEnd = if (message.isMine) 4.dp else 18.dp
                ),
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
            if (!message.isMine) TimeLabel(time)
        }
    }
}

@Composable
private fun TimeLabel(time: String) {
    Text(
        time,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.xs)
    )
}
