package com.example.runtime.ui.group

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.data.remote.InvitableFriend
import com.example.runtime.ui.mypage.ProfileAvatar
import com.example.runtime.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteFriendsSheet(
    friends: List<InvitableFriend>?,
    invitingUsername: String?,
    onInvite: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = Spacing.m)) {
            Text(stringResource(R.string.invite_friends_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.invite_friends_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 480.dp)) {
            when {
                friends == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                friends.isEmpty() -> Text(
                    stringResource(R.string.invite_friends_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.l)
                )
                else -> LazyColumn(contentPadding = PaddingValues(vertical = Spacing.s)) {
                    items(friends, key = { it.username }) { friend ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s)
                        ) {
                            ProfileAvatar(bytes = null, initial = friend.displayName.ifBlank { friend.username }, size = 40.dp)
                            Spacer(Modifier.width(Spacing.m))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(friend.displayName.ifBlank { friend.username }, style = MaterialTheme.typography.titleSmall)
                                if (friend.displayName.isNotBlank()) {
                                    Text(
                                        friend.username,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            when {
                                friend.isMember -> Text(
                                    stringResource(R.string.invite_status_member),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                friend.isInvited -> Text(
                                    stringResource(R.string.invite_status_invited),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                invitingUsername == friend.username ->
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                else -> FilledTonalButton(
                                    onClick = { onInvite(friend.username) },
                                    enabled = invitingUsername == null
                                ) { Text(stringResource(R.string.action_invite)) }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.l))
    }
}
