package com.example.runtime.ui.group

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.runtime.R
import com.example.runtime.data.remote.GroupInvite
import com.example.runtime.ui.common.EmptyState
import com.example.runtime.ui.common.LocalAppMessages
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.theme.Spacing

@Composable
fun GroupInvitesScreen(
    groupsViewModel: GroupsViewModel,
    onBack: () -> Unit,
    onOpenGroup: (Int) -> Unit,
) {
    val messages = LocalAppMessages.current
    val joinedMessage = stringResource(R.string.group_joined_message)
    val error = groupsViewModel.inviteError?.asString()

    LaunchedEffect(Unit) { groupsViewModel.loadInvites() }
    LaunchedEffect(error) { error?.let { messages?.show(it) } }
    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
            }
            Text(stringResource(R.string.group_invites_title), style = MaterialTheme.typography.titleMedium)
        }
        when {
            !groupsViewModel.invitesLoaded -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            groupsViewModel.invites.isEmpty() -> EmptyState(
                icon = R.drawable.ic_groups,
                title = stringResource(R.string.group_invites_empty),
                message = stringResource(R.string.group_invites_empty_message),
                modifier = Modifier.fillMaxSize(),
            )
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
                contentPadding = PaddingValues(Spacing.m),
            ) {
                items(groupsViewModel.invites, key = { it.id }) { invite ->
                    InviteItem(
                        invite = invite,
                        isWorking = groupsViewModel.inviteActionId == invite.id,
                        onOpen = { onOpenGroup(invite.group.id) },
                        onAccept = {
                            groupsViewModel.acceptInvite(invite) { groupId ->
                                messages?.show(joinedMessage)
                                onOpenGroup(groupId)
                            }
                        },
                        onDecline = { groupsViewModel.declineInvite(invite) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InviteItem(
    invite: GroupInvite,
    isWorking: Boolean,
    onOpen: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            stringResource(R.string.group_invited_by, invite.inviter),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GroupCard(group = invite.group, onClick = onOpen)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            OutlinedButton(onClick = onDecline, enabled = !isWorking, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_decline_invite))
            }
            Button(
                onClick = onAccept,
                enabled = !isWorking && invite.group.status == GROUP_OPEN,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    stringResource(
                        when (invite.group.status) {
                            GROUP_OPEN -> R.string.action_accept_invite
                            GROUP_FULL -> R.string.group_status_full
                            else -> R.string.group_status_closed
                        }
                    )
                )
            }
        }
    }
}
