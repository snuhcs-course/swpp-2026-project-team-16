package com.example.runtime.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.data.remote.FollowUser
import com.example.runtime.data.remote.UserProfile
import com.example.runtime.ui.board.PostCard
import com.example.runtime.ui.common.LocalAppMessages
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.placeholder
import com.example.runtime.ui.community.SharedRouteCard
import com.example.runtime.ui.group.GroupCard
import com.example.runtime.ui.mypage.ProfileAvatar
import com.example.runtime.ui.theme.Spacing

private const val TAB_ROUTES = 0
private const val TAB_GROUPS = 1
private const val TAB_POSTS = 2

@Composable
fun UserProfileScreen(
    viewModel: UserProfileViewModel,
    username: String,
    isLoggedIn: Boolean,
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    onRouteClick: (Int) -> Unit,
    onGroupClick: (Int) -> Unit,
    onPostClick: (Int) -> Unit,
    onLogInRequired: () -> Unit,
) {
    var selectedTab by rememberSaveable(username) { mutableIntStateOf(TAB_ROUTES) }
    val messages = LocalAppMessages.current
    val followError = viewModel.followError?.asString()

    LaunchedEffect(username) { viewModel.load(username) }
    LaunchedEffect(followError) { followError?.let { messages?.show(it) } }
    BackHandler(onBack = onBack)

    val profile = viewModel.profile?.takeIf { it.username == username }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
            }
            Text(username, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        if (profile == null) {
            viewModel.error?.let {
                Text(
                    it.asString(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(Spacing.m),
                )
            } ?: Box(
                modifier = Modifier.padding(Spacing.m).fillMaxWidth().height(140.dp).placeholder(MaterialTheme.shapes.large),
            )
            return@Column
        }

        ProfileHeader(
            profile = profile,
            avatar = viewModel.avatar,
            isFollowWorking = viewModel.isFollowWorking,
            onFollow = { if (isLoggedIn) viewModel.toggleFollow() else onLogInRequired() },
            onOpenList = viewModel::openList,
        )

        TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.background) {
            Tab(
                selected = selectedTab == TAB_ROUTES,
                onClick = { selectedTab = TAB_ROUTES },
                text = { Text(stringResource(R.string.profile_tab_routes, profile.sharedRoutes.size)) },
            )
            Tab(
                selected = selectedTab == TAB_GROUPS,
                onClick = { selectedTab = TAB_GROUPS },
                text = { Text(stringResource(R.string.profile_tab_groups, profile.groups.size)) },
            )
            Tab(
                selected = selectedTab == TAB_POSTS,
                onClick = { selectedTab = TAB_POSTS },
                text = { Text(stringResource(R.string.profile_tab_posts, profile.posts.size)) },
            )
        }

        val empty = when (selectedTab) {
            TAB_ROUTES -> profile.sharedRoutes.isEmpty()
            TAB_GROUPS -> profile.groups.isEmpty()
            else -> profile.posts.isEmpty()
        }
        if (empty) {
            Text(
                stringResource(
                    when (selectedTab) {
                        TAB_ROUTES -> R.string.profile_no_routes
                        TAB_GROUPS -> R.string.profile_no_groups
                        else -> R.string.profile_no_posts
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(Spacing.l),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
                contentPadding = PaddingValues(Spacing.m),
                modifier = Modifier.fillMaxSize(),
            ) {
                when (selectedTab) {
                    TAB_ROUTES -> items(profile.sharedRoutes, key = { "route-${it.id}" }) { route ->
                        SharedRouteCard(route = route, onClick = { onRouteClick(route.id) })
                    }
                    TAB_GROUPS -> items(profile.groups, key = { "group-${it.id}" }) { group ->
                        GroupCard(group = group, onClick = { onGroupClick(group.id) })
                    }
                    else -> items(profile.posts, key = { "post-${it.id}" }) { post ->
                        PostCard(post = post, onClick = { onPostClick(post.id) })
                    }
                }
            }
        }
    }

    viewModel.listType?.let { type ->
        FollowListSheet(
            type = type,
            users = viewModel.listUsers,
            onDismiss = viewModel::closeList,
            onSelectType = viewModel::openList,
            onOpenUser = { user ->
                viewModel.closeList()
                if (user.username != username) onOpenUser(user.username)
            },
        )
    }
}

@Composable
private fun ProfileHeader(
    profile: UserProfile,
    avatar: ByteArray?,
    isFollowWorking: Boolean,
    onFollow: () -> Unit,
    onOpenList: (FollowListType) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(bytes = avatar, initial = profile.displayName.ifBlank { profile.username }, size = 64.dp)
            Spacer(Modifier.width(Spacing.m))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    profile.displayName.ifBlank { profile.username },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (profile.displayName.isNotBlank()) {
                    Text(
                        profile.username,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), modifier = Modifier.padding(top = Spacing.xs)) {
                    FollowCount(stringResource(R.string.profile_followers), profile.followerCount) { onOpenList(FollowListType.FOLLOWERS) }
                    FollowCount(stringResource(R.string.profile_following), profile.followingCount) { onOpenList(FollowListType.FOLLOWING) }
                }
            }
        }
        if (profile.statusMessage.isNotBlank()) {
            Spacer(Modifier.height(Spacing.s))
            Text(profile.statusMessage, style = MaterialTheme.typography.bodyMedium)
        }
        if (!profile.isMe) {
            Spacer(Modifier.height(Spacing.m))
            val label = stringResource(if (profile.isFollowing) R.string.action_unfollow else R.string.action_follow)
            if (profile.isFollowing) {
                OutlinedButton(onClick = onFollow, enabled = !isFollowWorking, modifier = Modifier.fillMaxWidth()) { Text(label) }
            } else {
                Button(onClick = onFollow, enabled = !isFollowWorking, modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
        }
        Spacer(Modifier.height(Spacing.s))
    }
}

@Composable
private fun FollowCount(label: String, count: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
    ) {
        Text(count.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollowListSheet(
    type: FollowListType,
    users: List<FollowUser>?,
    onDismiss: () -> Unit,
    onSelectType: (FollowListType) -> Unit,
    onOpenUser: (FollowUser) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        TabRow(selectedTabIndex = type.ordinal, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Tab(
                selected = type == FollowListType.FOLLOWERS,
                onClick = { onSelectType(FollowListType.FOLLOWERS) },
                text = { Text(stringResource(R.string.profile_followers)) },
            )
            Tab(
                selected = type == FollowListType.FOLLOWING,
                onClick = { onSelectType(FollowListType.FOLLOWING) },
                text = { Text(stringResource(R.string.profile_following)) },
            )
        }
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 480.dp)) {
            when {
                users == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                users.isEmpty() -> Text(
                    stringResource(if (type == FollowListType.FOLLOWERS) R.string.profile_no_followers else R.string.profile_no_following),
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
                                .clickable { onOpenUser(user) }
                                .padding(horizontal = Spacing.m, vertical = Spacing.s),
                        ) {
                            ProfileAvatar(bytes = null, initial = user.displayName.ifBlank { user.username }, size = 40.dp)
                            Spacer(Modifier.width(Spacing.m))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.displayName.ifBlank { user.username }, style = MaterialTheme.typography.titleSmall)
                                if (user.displayName.isNotBlank()) {
                                    Text(
                                        user.username,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (user.isMe) {
                                Text(
                                    stringResource(R.string.profile_me),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else if (user.isFollowing) {
                                Text(
                                    stringResource(R.string.profile_following_badge),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.l))
    }
}
