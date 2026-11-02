package com.example.runtime.ui.community

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.data.remote.SharedRoute
import com.example.runtime.ui.common.AccentButton
import com.example.runtime.ui.common.LocalAppMessages
import com.example.runtime.ui.common.StatItem
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.formatKm
import com.example.runtime.ui.common.placeholder
import com.example.runtime.ui.common.sharedElement
import com.example.runtime.ui.route.FullScreenRouteMap
import com.example.runtime.ui.route.RouteMap
import com.example.runtime.ui.route.SaveState
import com.example.runtime.ui.theme.Spacing

@Composable
fun SharedRouteDetailScreen(
    communityViewModel: CommunityViewModel,
    sharedRouteId: Int,
    onBack: () -> Unit,
    onRun: (SharedRoute) -> Unit,
    onSave: () -> Unit,
    isLoggedIn: Boolean,
    onLogInToReview: () -> Unit,
) {
    var showFullMap by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showReviewDialog by rememberSaveable { mutableStateOf(false) }
    var deletingReviewId by remember { mutableStateOf<Int?>(null) }
    val reviewPostedMessage = stringResource(R.string.review_posted_message)
    val reviewUpdatedMessage = stringResource(R.string.review_updated_message)
    val reviewDeletedMessage = stringResource(R.string.review_deleted_message)
    val messages = LocalAppMessages.current
    val haptic = LocalHapticFeedback.current
    val savedMessage = stringResource(R.string.route_saved_message)
    val removedMessage = stringResource(R.string.shared_route_removed_message)
    val saveState = communityViewModel.saveState
    val saveFailedMessage = (saveState as? SaveState.Failed)?.message?.asString()

    LaunchedEffect(sharedRouteId) {
        communityViewModel.loadDetail(sharedRouteId)
    }
    var previousSaveState by remember { mutableStateOf(saveState) }
    LaunchedEffect(saveState) {
        val wasSaving = previousSaveState == SaveState.Saving
        previousSaveState = saveState
        if (!wasSaving) return@LaunchedEffect
        when (saveState) {
            SaveState.Saved -> {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                messages?.show(savedMessage)
            }
            is SaveState.Failed -> saveFailedMessage?.let { messages?.show(it) }
            else -> Unit
        }
    }
    BackHandler(onBack = onBack)

    val route = communityViewModel.detail
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
            }
            if (route == null) {
                Box(modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.size(width = 180.dp, height = 22.dp).placeholder())
                }
            } else {
                Text(
                    route.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).sharedElement("shared-title-${route.id}")
                )
                if (route.isMine) {
                    IconButton(onClick = { showDeleteDialog = true }, enabled = !communityViewModel.isDeleting) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.action_unshare))
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.m)
        ) {
            if (route == null) {
                communityViewModel.detailError?.let {
                    Text(it.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                } ?: Box(modifier = Modifier.fillMaxWidth().height(280.dp).placeholder(MaterialTheme.shapes.large))
                return@Column
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(MaterialTheme.shapes.large)
            ) {
                if (route.route.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().placeholder(MaterialTheme.shapes.large))
                } else {
                    RouteMap(
                        lines = route.route,
                        modifier = Modifier.fillMaxSize(),
                        onClick = { showFullMap = true }
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.l))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatKm(route.distance), style = MaterialTheme.typography.displayLarge)
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    stringResource(R.string.unit_km),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Text(
                stringResource(R.string.shared_by, route.sharedBy, route.sharedAt.take(10)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.m))
            Row {
                StatItem(
                    label = stringResource(R.string.stat_runs),
                    value = pluralStringResource(R.plurals.run_count, route.runCount, route.runCount),
                    icon = R.drawable.ic_directions_run,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = stringResource(R.string.stat_start),
                    value = route.startName.ifBlank { "–" },
                    icon = R.drawable.ic_my_location,
                    modifier = Modifier.weight(1f)
                )
            }
            if (route.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(Spacing.m))
                Text(route.description, style = MaterialTheme.typography.bodyLarge)
            }
            if (route.briefing.isNotBlank()) {
                Spacer(modifier = Modifier.height(Spacing.m))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(Spacing.m)) {
                        Text(
                            stringResource(R.string.llm_briefing),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(route.briefing, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            communityViewModel.detailError?.let {
                Spacer(modifier = Modifier.height(Spacing.s))
                Text(it.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(Spacing.l))
            Text(stringResource(R.string.reviews_title), style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(Spacing.m))
            RatingSummary(average = route.ratingAverage, count = route.reviewCount, counts = route.ratingCounts)
            Spacer(modifier = Modifier.height(Spacing.m))
            val hasMyReview = route.myReviewId != null
            when {
                route.isMine -> ReviewHint(stringResource(R.string.review_own_route))
                !isLoggedIn || route.canReview -> OutlinedButton(
                    onClick = { if (isLoggedIn) showReviewDialog = true else onLogInToReview() },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_rate_review), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(Spacing.s))
                    Text(
                        stringResource(if (hasMyReview) R.string.action_edit_review else R.string.action_write_review),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                else -> ReviewHint(stringResource(R.string.review_eligibility))
            }
            communityViewModel.reviews.forEachIndexed { index, review ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ReviewItem(
                    review = review,
                    onEdit = { showReviewDialog = true },
                    onDelete = { deletingReviewId = review.id },
                    modifier = Modifier.padding(top = if (index == 0) Spacing.s else 0.dp)
                )
            }
            Spacer(modifier = Modifier.height(Spacing.l))
        }

        if (route != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.m),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onSave,
                    enabled = saveState == SaveState.Idle || saveState is SaveState.Failed,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Icon(
                        painterResource(if (saveState == SaveState.Saved) R.drawable.ic_bookmark_added else R.drawable.ic_bookmark),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.s))
                    Text(
                        stringResource(
                            when (saveState) {
                                SaveState.Saving -> R.string.saving
                                SaveState.Saved -> R.string.saved
                                else -> R.string.action_save
                            }
                        ),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.s))
                AccentButton(
                    text = stringResource(R.string.action_run_route),
                    icon = R.drawable.ic_directions_run,
                    onClick = { onRun(route) },
                    enabled = route.route.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showFullMap && route != null) {
        FullScreenRouteMap(lines = route.route, onDismiss = { showFullMap = false })
    }

    if (showReviewDialog && route != null) {
        val myReview = communityViewModel.reviews.firstOrNull { it.isMine }
        ReviewDialog(
            initialRating = myReview?.rating ?: 0,
            initialComment = myReview?.comment.orEmpty(),
            isEditing = myReview != null,
            submitState = communityViewModel.reviewSubmitState,
            onDismiss = { showReviewDialog = false },
            onSubmit = { rating, comment ->
                communityViewModel.submitReview(rating, comment) {
                    showReviewDialog = false
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    messages?.show(if (myReview != null) reviewUpdatedMessage else reviewPostedMessage)
                }
            }
        )
    }

    deletingReviewId?.let { reviewId ->
        AlertDialog(
            onDismissRequest = { deletingReviewId = null },
            title = { Text(stringResource(R.string.delete_review_title)) },
            confirmButton = {
                TextButton(onClick = {
                    deletingReviewId = null
                    communityViewModel.deleteReview(reviewId) { messages?.show(reviewDeletedMessage) }
                }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingReviewId = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.unshare_dialog_title)) },
            text = { Text(stringResource(R.string.unshare_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    communityViewModel.delete(sharedRouteId) {
                        messages?.show(removedMessage)
                        onBack()
                    }
                }) {
                    Text(stringResource(R.string.action_unshare), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun ReviewHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Spacing.s)
    )
}
