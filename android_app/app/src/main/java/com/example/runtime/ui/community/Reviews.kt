package com.example.runtime.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.data.remote.RouteReview
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.doneKeyboardOptions
import com.example.runtime.ui.common.hideKeyboardActions
import com.example.runtime.ui.theme.Spacing
import com.example.runtime.ui.theme.StarYellow
import java.util.Locale
import kotlin.math.floor

private const val MAX_COMMENT_LENGTH = 500

fun formatRating(rating: Double): String = String.format(Locale.US, "%.1f", rating)

@Composable
fun Stars(rating: Double, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val full = floor(rating).toInt()
    val half = rating - full >= 0.5
    val description = stringResource(R.string.star_rating_description, formatRating(rating))
    Row(modifier = modifier.semantics { contentDescription = description }) {
        repeat(5) { index ->
            val icon = when {
                index < full -> R.drawable.ic_star_filled
                index == full && half -> R.drawable.ic_star_half
                else -> R.drawable.ic_star
            }
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = if (icon == R.drawable.ic_star) MaterialTheme.colorScheme.outline else StarYellow,
                modifier = Modifier.size(size)
            )
        }
    }
}

@Composable
fun RatingLabel(average: Double?, count: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(R.drawable.ic_star_filled),
            contentDescription = null,
            tint = if (average != null) StarYellow else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(Spacing.xs))
        if (average == null) {
            Text(
                stringResource(R.string.no_reviews_yet),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(formatRating(average), style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(
                "($count)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RatingSummary(average: Double?, count: Int, counts: Map<String, Int>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(112.dp)) {
            Text(average?.let(::formatRating) ?: "–", style = MaterialTheme.typography.displayLarge)
            Stars(average ?: 0.0)
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                pluralStringResource(R.plurals.review_count, count, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(Spacing.m))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            (5 downTo 1).forEach { stars ->
                val starCount = counts[stars.toString()] ?: 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stars.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(12.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.s))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        if (count > 0 && starCount > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(starCount.toFloat() / count)
                                    .clip(CircleShape)
                                    .background(StarYellow)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(Spacing.s))
                    Text(
                        starCount.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ReviewItem(review: RouteReview, onEdit: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    var showMenu by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth().padding(vertical = Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(review.author, style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Stars(review.rating.toDouble(), size = 14.dp)
                    Spacer(modifier = Modifier.width(Spacing.s))
                    Text(
                        review.createdAt.take(10) + if (review.edited) " (" + stringResource(R.string.review_edited) + ")" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (review.isMine) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.menu))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_edit)) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
        if (review.comment.isNotBlank()) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(review.comment, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ReviewDialog(
    initialRating: Int,
    initialComment: String,
    isEditing: Boolean,
    submitState: ReviewSubmitState,
    onDismiss: () -> Unit,
    onSubmit: (Int, String) -> Unit,
) {
    var rating by rememberSaveable { mutableIntStateOf(initialRating) }
    var comment by rememberSaveable { mutableStateOf(initialComment) }
    val isSubmitting = submitState == ReviewSubmitState.Submitting

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isEditing) R.string.review_dialog_title_edit else R.string.review_dialog_title_new)) },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    (1..5).forEach { star ->
                        val description = stringResource(R.string.star_rating_description, star.toString())
                        Icon(
                            painterResource(if (star <= rating) R.drawable.ic_star_filled else R.drawable.ic_star),
                            contentDescription = description,
                            tint = if (star <= rating) StarYellow else MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable(enabled = !isSubmitting) { rating = star }
                                .padding(Spacing.xs)
                        )
                    }
                }
                if (rating == 0) {
                    Text(
                        stringResource(R.string.review_need_rating),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.m))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(MAX_COMMENT_LENGTH) },
                    label = { Text(stringResource(R.string.label_review_comment)) },
                    minLines = 3,
                    keyboardOptions = doneKeyboardOptions(),
                    keyboardActions = hideKeyboardActions(),
                    enabled = !isSubmitting,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                (submitState as? ReviewSubmitState.Failed)?.let {
                    Spacer(modifier = Modifier.height(Spacing.s))
                    Text(it.message.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(rating, comment) }, enabled = rating in 1..5 && !isSubmitting) {
                Text(stringResource(if (isSubmitting) R.string.saving else R.string.action_post_review))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
