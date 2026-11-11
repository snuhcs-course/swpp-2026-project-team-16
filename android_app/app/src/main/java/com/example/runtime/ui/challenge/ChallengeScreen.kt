package com.example.runtime.ui.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.data.remote.Badge
import com.example.runtime.data.remote.ChallengeSummary
import com.example.runtime.data.remote.PersonalBest
import com.example.runtime.ui.common.AccentButton
import com.example.runtime.ui.common.EmptyState
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.formatDistance
import com.example.runtime.ui.common.formatDuration
import com.example.runtime.ui.common.placeholder
import com.example.runtime.ui.route.formatPaceClock
import com.example.runtime.ui.theme.Charcoal
import com.example.runtime.ui.theme.Lime
import com.example.runtime.ui.theme.Spacing
import java.util.Locale

@Composable
fun ChallengeScreen(viewModel: ChallengeViewModel, isLoggedIn: Boolean, onLogIn: () -> Unit) {
    var showGoalDialog by remember { mutableStateOf(false) }
    var openBadge by remember { mutableStateOf<Badge?>(null) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) viewModel.load() else viewModel.reset()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(
            stringResource(R.string.challenge_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = Spacing.s, top = Spacing.m),
        )
        if (!isLoggedIn) {
            EmptyState(
                icon = R.drawable.ic_trophy,
                title = stringResource(R.string.challenge_log_in_title),
                message = stringResource(R.string.challenge_log_in_message),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.l),
            ) {
                AccentButton(text = stringResource(R.string.action_log_in), onClick = onLogIn)
            }
            return@Column
        }
        viewModel.error?.let {
            Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        val summary = viewModel.summary
        if (summary == null) {
            repeat(3) { Box(modifier = Modifier.fillMaxWidth().height(140.dp).placeholder(MaterialTheme.shapes.large)) }
            return@Column
        }
        GoalsCard(summary, onEdit = {
            viewModel.clearGoalError()
            showGoalDialog = true
        })
        StreakCard(summary)
        SectionTitle(stringResource(R.string.challenge_personal_bests))
        PersonalBests(summary.personalBests)
        SectionTitle(
            stringResource(R.string.challenge_badges, summary.badges.count { it.earned }, summary.badges.size),
        )
        BadgeGrid(summary.badges, onOpen = { openBadge = it })
        Spacer(Modifier.height(Spacing.l))
    }

    if (showGoalDialog) {
        viewModel.summary?.let { summary ->
            GoalDialog(
                weeklyKm = summary.goals.weeklyGoalMeters / 1000.0,
                monthlyKm = summary.goals.monthlyGoalMeters / 1000.0,
                isSaving = viewModel.isSavingGoals,
                error = viewModel.goalError?.asString(),
                onDismiss = { showGoalDialog = false },
                onSave = { weekly, monthly -> viewModel.saveGoals(weekly, monthly) { showGoalDialog = false } },
            )
        }
    }
    openBadge?.let { badge ->
        AlertDialog(
            onDismissRequest = { openBadge = null },
            icon = { BadgeMedal(badge.earned, size = 56) },
            title = { Text(badge.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(badge.description)
                    Text(
                        if (badge.earned) stringResource(R.string.challenge_badge_earned_on, badge.earnedAt.orEmpty())
                        else badgeProgressText(badge),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { openBadge = null }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.s))
}

@Composable
private fun ChallengeCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) { content() }
    }
}

@Composable
private fun GoalsCard(summary: ChallengeSummary, onEdit: () -> Unit) {
    val goals = summary.goals
    ChallengeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.challenge_goals), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onEdit) { Text(stringResource(R.string.challenge_set_goals)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), modifier = Modifier.fillMaxWidth()) {
            GoalRing(stringResource(R.string.challenge_this_week), goals.weekMeters, goals.weeklyGoalMeters, Modifier.weight(1f))
            GoalRing(stringResource(R.string.challenge_this_month), goals.monthMeters, goals.monthlyGoalMeters, Modifier.weight(1f))
        }
        if (goals.weeklyGoalMeters == 0 && goals.monthlyGoalMeters == 0) {
            Text(
                stringResource(R.string.challenge_no_goals),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GoalRing(label: String, doneMeters: Int, goalMeters: Int, modifier: Modifier = Modifier) {
    val fraction = if (goalMeters > 0) (doneMeters.toFloat() / goalMeters).coerceIn(0f, 1f) else 0f
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(112.dp)) {
            CircularProgressIndicator(
                progress = { 1f },
                color = MaterialTheme.colorScheme.surfaceVariant,
                strokeWidth = 10.dp,
                modifier = Modifier.fillMaxSize(),
            )
            CircularProgressIndicator(
                progress = { fraction },
                color = Lime,
                strokeWidth = 10.dp,
                trackColor = androidx.compose.ui.graphics.Color.Transparent,
                modifier = Modifier.fillMaxSize(),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatDistance(doneMeters), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (goalMeters > 0) stringResource(R.string.challenge_goal_of, formatDistance(goalMeters))
                    else stringResource(R.string.challenge_goal_none),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(label, style = MaterialTheme.typography.labelLarge)
        if (goalMeters in 1..doneMeters) {
            Text(stringResource(R.string.challenge_goal_done), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun StreakCard(summary: ChallengeSummary) {
    ChallengeCard {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            StatColumn(stringResource(R.string.challenge_streak_current), stringResource(R.string.challenge_days, summary.streak.current), Modifier.weight(1f))
            StatColumn(stringResource(R.string.challenge_streak_best), stringResource(R.string.challenge_days, summary.streak.best), Modifier.weight(1f))
            StatColumn(stringResource(R.string.challenge_total_runs), stringResource(R.string.challenge_runs, summary.totals.runs), Modifier.weight(1f))
            StatColumn(stringResource(R.string.challenge_total_distance), formatDistance(summary.totals.distanceMeters), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.fillMaxHeight()) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PersonalBests(bests: Map<String, PersonalBest?>) {
    val rows = listOf(
        "longest_run_m" to R.string.challenge_best_longest,
        "fastest_pace_s" to R.string.challenge_best_pace,
        "fastest_1k_s" to R.string.challenge_best_1k,
        "fastest_5k_s" to R.string.challenge_best_5k,
        "best_week_m" to R.string.challenge_best_week,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        rows.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                pair.forEach { (key, label) ->
                    val best = bests[key]
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(modifier = Modifier.padding(Spacing.m)) {
                            Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                best?.let { bestValueText(key, it.value) } ?: stringResource(R.string.challenge_best_none),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(best?.date.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun bestValueText(key: String, value: Int): String = when (key) {
    "fastest_pace_s", "fastest_1k_s" -> "${formatPaceClock(value)} /km"
    "fastest_5k_s" -> formatDuration(value.toLong())
    else -> formatDistance(value)
}

@Composable
private fun BadgeGrid(badges: List<Badge>, onOpen: (Badge) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        badges.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { badge ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable { onOpen(badge) }.padding(Spacing.xs),
                    ) {
                        BadgeMedal(badge.earned, size = 56)
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            badge.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (badge.earned) FontWeight.Bold else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        if (!badge.earned) {
                            LinearProgressIndicator(
                                progress = { badge.progress.toFloat() / badge.target.coerceAtLeast(1) },
                                modifier = Modifier.width(56.dp).padding(top = 4.dp),
                                drawStopIndicator = {},
                            )
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BadgeMedal(earned: Boolean, size: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (earned) Lime else MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Icon(
            painterResource(R.drawable.ic_trophy),
            contentDescription = null,
            tint = if (earned) Charcoal else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size((size / 2).dp),
        )
    }
}

@Composable
private fun badgeProgressText(badge: Badge): String {
    val isDistance = badge.id.startsWith("total_") || badge.id.startsWith("single_")
    return if (isDistance) {
        stringResource(R.string.challenge_badge_progress, formatDistance(badge.progress), formatDistance(badge.target))
    } else {
        stringResource(R.string.challenge_badge_progress, badge.progress.toString(), badge.target.toString())
    }
}

@Composable
private fun GoalDialog(
    weeklyKm: Double,
    monthlyKm: Double,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (Double, Double) -> Unit,
) {
    fun text(km: Double) = if (km <= 0) "" else String.format(Locale.US, "%.1f", km).removeSuffix(".0")
    var weekly by remember { mutableStateOf(text(weeklyKm)) }
    var monthly by remember { mutableStateOf(text(monthlyKm)) }
    fun parse(value: String) = if (value.isBlank()) 0.0 else value.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..1000.0 }
    val weeklyValue = parse(weekly)
    val monthlyValue = parse(monthly)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.challenge_set_goals)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedTextField(
                    value = weekly,
                    onValueChange = { weekly = it },
                    label = { Text(stringResource(R.string.challenge_weekly_goal)) },
                    suffix = { Text(stringResource(R.string.unit_km)) },
                    singleLine = true,
                    isError = weeklyValue == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = monthly,
                    onValueChange = { monthly = it },
                    label = { Text(stringResource(R.string.challenge_monthly_goal)) },
                    suffix = { Text(stringResource(R.string.unit_km)) },
                    singleLine = true,
                    isError = monthlyValue == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.challenge_goal_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (weeklyValue != null && monthlyValue != null) onSave(weeklyValue, monthlyValue) },
                enabled = !isSaving && weeklyValue != null && monthlyValue != null,
            ) { Text(stringResource(if (isSaving) R.string.saving else R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
