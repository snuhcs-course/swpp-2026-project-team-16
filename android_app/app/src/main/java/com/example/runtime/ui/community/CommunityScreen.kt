package com.example.runtime.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.data.remote.SharedRoute
import com.example.runtime.ui.common.EmptyState
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.formatDistance
import com.example.runtime.ui.common.placeholder
import com.example.runtime.ui.common.sharedElement
import com.example.runtime.ui.mypage.DistanceBadge
import com.example.runtime.ui.theme.Spacing

private const val TAB_ROUTES = 0
private const val TAB_CREWS = 1

@Composable
fun CommunityScreen(communityViewModel: CommunityViewModel, onRouteClick: (Int) -> Unit) {
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_ROUTES) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.nav_community),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = Spacing.l, top = Spacing.m, bottom = Spacing.s)
        )
        PrimaryTabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.background) {
            Tab(
                selected = selectedTab == TAB_ROUTES,
                onClick = { selectedTab = TAB_ROUTES },
                text = { Text(stringResource(R.string.community_tab_routes), style = MaterialTheme.typography.titleSmall) }
            )
            Tab(
                selected = selectedTab == TAB_CREWS,
                onClick = { selectedTab = TAB_CREWS },
                text = { Text(stringResource(R.string.community_tab_crews), style = MaterialTheme.typography.titleSmall) }
            )
        }
        when (selectedTab) {
            TAB_ROUTES -> SharedRoutesTab(communityViewModel, onRouteClick)
            else -> EmptyState(
                icon = R.drawable.ic_groups,
                title = stringResource(R.string.crews_coming_soon_title),
                message = stringResource(R.string.crews_coming_soon_message),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun SharedRoutesTab(communityViewModel: CommunityViewModel, onRouteClick: (Int) -> Unit) {
    LaunchedEffect(Unit) {
        communityViewModel.refresh()
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.m)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            modifier = Modifier.padding(top = Spacing.m)
        ) {
            RouteSort.entries.forEach { sort ->
                FilterChip(
                    selected = communityViewModel.sort == sort,
                    onClick = { communityViewModel.changeSort(sort) },
                    label = { Text(stringResource(sort.label)) },
                    shape = MaterialTheme.shapes.extraLarge
                )
            }
        }
        communityViewModel.error?.let {
            Text(
                it.asString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(Spacing.s)
            )
        }
        when {
            communityViewModel.isLoading && communityViewModel.routes.isEmpty() -> Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
                modifier = Modifier.padding(top = Spacing.m)
            ) {
                repeat(3) { Box(modifier = Modifier.fillMaxWidth().height(120.dp).placeholder(MaterialTheme.shapes.medium)) }
            }
            communityViewModel.routes.isEmpty() && communityViewModel.error == null -> EmptyState(
                icon = R.drawable.ic_route,
                title = stringResource(R.string.no_shared_routes),
                message = stringResource(R.string.no_shared_routes_message),
                modifier = Modifier.fillMaxSize()
            )
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
                contentPadding = PaddingValues(vertical = Spacing.m)
            ) {
                items(communityViewModel.routes, key = { it.id }) { route ->
                    SharedRouteCard(route = route, onClick = { onRouteClick(route.id) })
                }
            }
        }
    }
}

@Composable
private fun SharedRouteCard(route: SharedRoute, onClick: () -> Unit) {
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
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.ic_distance),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.m))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        route.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.sharedElement("shared-title-${route.id}")
                    )
                    Text(
                        stringResource(R.string.shared_by, route.sharedBy, route.sharedAt.take(10)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.s))
                DistanceBadge(formatDistance(route.distance))
            }
            if (route.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(Spacing.s))
                Text(
                    route.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(Spacing.s))
            RatingLabel(average = route.ratingAverage, count = route.reviewCount)
            Spacer(modifier = Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_directions_run),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    pluralStringResource(R.plurals.run_count, route.runCount, route.runCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (route.startName.isNotBlank()) {
                    Text(
                        " · " + route.startName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
