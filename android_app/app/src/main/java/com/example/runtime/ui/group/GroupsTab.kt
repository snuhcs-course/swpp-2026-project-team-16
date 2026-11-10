package com.example.runtime.ui.group

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.ui.common.EmptyState
import com.example.runtime.ui.common.LOCATION_PERMISSIONS
import com.example.runtime.ui.common.LocalAppMessages
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.currentLocation
import com.example.runtime.ui.common.formatKm
import com.example.runtime.ui.common.hasLocationPermission
import com.example.runtime.ui.common.placeholder
import com.example.runtime.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun GroupsTab(
    groupsViewModel: GroupsViewModel,
    isLoggedIn: Boolean,
    onGroupClick: (Int) -> Unit,
    onCreateGroup: () -> Unit,
    onLogInRequired: () -> Unit,
    onOpenInvites: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = LocalAppMessages.current
    val locationUnavailable = stringResource(R.string.error_location_unavailable)
    val locationDenied = stringResource(R.string.error_location_permission)
    var isLocating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        groupsViewModel.refresh()
    }
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) groupsViewModel.loadInvites()
    }

    fun locateAndShowNearby() {
        isLocating = true
        scope.launch {
            val location = context.currentLocation()
            isLocating = false
            if (location == null) messages?.show(locationUnavailable)
            else groupsViewModel.showNearby(location.latitude, location.longitude)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) locateAndShowNearby() else messages?.show(locationDenied)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.m)) {
            if (isLoggedIn && groupsViewModel.invites.isNotEmpty()) {
                InviteBanner(count = groupsViewModel.invites.size, onClick = onOpenInvites)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = Spacing.m)
            ) {
                GroupFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = groupsViewModel.filter == filter,
                        onClick = {
                            when (filter) {
                                GroupFilter.NEARBY -> when {
                                    isLocating -> Unit
                                    context.hasLocationPermission() -> locateAndShowNearby()
                                    else -> permissionLauncher.launch(LOCATION_PERMISSIONS)
                                }
                                GroupFilter.MINE, GroupFilter.FOLLOWING -> if (isLoggedIn) groupsViewModel.changeFilter(filter) else onLogInRequired()
                                else -> groupsViewModel.changeFilter(filter)
                            }
                        },
                        label = { Text(stringResource(filter.label)) },
                        leadingIcon = if (filter == GroupFilter.NEARBY && isLocating) {
                            { CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) }
                        } else null,
                        shape = MaterialTheme.shapes.extraLarge
                    )
                }
            }
            if (groupsViewModel.filter == GroupFilter.NEARBY) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    modifier = Modifier.padding(top = Spacing.s)
                ) {
                    Text(
                        stringResource(R.string.group_radius_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    NEARBY_RADII_KM.forEach { radius ->
                        FilterChip(
                            selected = groupsViewModel.radiusKm == radius,
                            onClick = { groupsViewModel.changeRadius(radius) },
                            label = { Text(stringResource(R.string.group_radius_km, formatKm((radius * 1000).toInt()))) },
                            shape = MaterialTheme.shapes.extraLarge
                        )
                    }
                }
            }
            groupsViewModel.error?.let {
                Text(
                    it.asString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(Spacing.s)
                )
            }
            when {
                groupsViewModel.isLoading && groupsViewModel.groups.isEmpty() -> Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                    modifier = Modifier.padding(top = Spacing.m)
                ) {
                    repeat(3) { Box(modifier = Modifier.fillMaxWidth().height(150.dp).placeholder(MaterialTheme.shapes.medium)) }
                }
                groupsViewModel.groups.isEmpty() && groupsViewModel.error == null -> EmptyState(
                    icon = R.drawable.ic_groups,
                    title = stringResource(
                        when (groupsViewModel.filter) {
                            GroupFilter.MINE -> R.string.no_my_groups
                            GroupFilter.CLOSING_SOON -> R.string.no_closing_soon_groups
                            GroupFilter.NEARBY -> R.string.no_nearby_groups
                            GroupFilter.FOLLOWING -> R.string.no_following_groups
                            else -> R.string.no_groups
                        }
                    ),
                    message = stringResource(R.string.no_groups_message),
                    modifier = Modifier.fillMaxSize()
                )
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                    contentPadding = PaddingValues(top = Spacing.m, bottom = 88.dp)
                ) {
                    items(groupsViewModel.groups, key = { it.id }) { group ->
                        GroupCard(group = group, onClick = { onGroupClick(group.id) })
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { if (isLoggedIn) onCreateGroup() else onLogInRequired() },
            icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
            text = { Text(stringResource(R.string.action_create_group)) },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.m)
        )
    }
}

@Composable
private fun InviteBanner(count: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.m)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s)
        ) {
            Icon(painterResource(R.drawable.ic_groups), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(Spacing.s))
            Text(
                pluralStringResource(R.plurals.group_invite_banner, count, count),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}
