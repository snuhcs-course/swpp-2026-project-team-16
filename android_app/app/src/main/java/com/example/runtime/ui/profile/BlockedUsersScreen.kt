package com.example.runtime.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.mypage.ProfileAvatar
import com.example.runtime.ui.theme.Spacing

@Composable
fun BlockedUsersScreen(
    viewModel: BlockedUsersViewModel,
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    onUnblocked: (String) -> Unit,
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
            Text(stringResource(R.string.blocked_users_title), style = MaterialTheme.typography.titleMedium)
        }
        Text(
            stringResource(R.string.blocked_users_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.m),
        )
        viewModel.error?.let {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
            ) {
                Text(it.asString(), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::load) { Text(stringResource(R.string.action_retry)) }
            }
        }
        val users = viewModel.users
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                users == null && viewModel.error == null -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
                users == null -> Unit
                users.isEmpty() -> Text(
                    stringResource(R.string.blocked_users_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(contentPadding = PaddingValues(vertical = Spacing.s)) {
                    items(users, key = { it.username }) { user ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenUser(user.username) }
                                .padding(horizontal = Spacing.m, vertical = Spacing.s),
                        ) {
                            ProfileAvatar(bytes = null, initial = user.displayName.ifBlank { user.username }, size = 40.dp)
                            Spacer(Modifier.width(Spacing.m))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    user.displayName.ifBlank { user.username },
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (user.displayName.isNotBlank()) {
                                    Text(
                                        user.username,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { viewModel.unblock(user.username, onUnblocked) },
                                enabled = user.username !in viewModel.unblocking,
                            ) { Text(stringResource(R.string.action_unblock)) }
                        }
                    }
                }
            }
        }
    }
}
