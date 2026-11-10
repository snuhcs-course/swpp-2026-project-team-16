package com.example.runtime.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.data.remote.RunHistoryItem
import com.example.runtime.data.remote.RunningGroup
import com.example.runtime.ui.common.AccentButton
import com.example.runtime.ui.common.EmptyState
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.formatDistance
import com.example.runtime.ui.common.formatDuration
import com.example.runtime.ui.common.formatPace
import com.example.runtime.ui.common.parseInstant
import com.example.runtime.ui.common.routeTitle
import com.example.runtime.ui.theme.Charcoal
import com.example.runtime.ui.theme.Lime
import com.example.runtime.ui.theme.Spacing
import com.example.runtime.ui.theme.StarYellow
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    isLoggedIn: Boolean,
    onLogIn: () -> Unit,
    onGroupClick: (Int) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) viewModel.load() else viewModel.reset()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.m),
    ) {
        Text(
            stringResource(R.string.calendar_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = Spacing.s, top = Spacing.m, bottom = Spacing.s),
        )

        if (!isLoggedIn) {
            EmptyState(
                icon = R.drawable.ic_calendar,
                title = stringResource(R.string.calendar_log_in_title),
                message = stringResource(R.string.calendar_log_in_message),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.l),
            ) {
                AccentButton(text = stringResource(R.string.action_log_in), onClick = onLogIn)
            }
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = viewModel::showPreviousMonth) {
                Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.calendar_previous_month))
            }
            Text(
                stringResource(
                    R.string.calendar_month_title,
                    viewModel.month.year,
                    viewModel.month.month.getDisplayName(TextStyle.FULL, locale),
                ),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = viewModel::showNextMonth) {
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.calendar_next_month))
            }
        }
        Text(
            stringResource(R.string.calendar_month_summary, viewModel.monthRunDays, formatDistance(viewModel.monthDistance)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (viewModel.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = Spacing.s))
        }
        viewModel.error?.let {
            Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(Spacing.m))

        Row(modifier = Modifier.fillMaxWidth()) {
            WEEK_DAYS.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        val today = LocalDate.now()
        monthCells(viewModel.month).chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                meters = viewModel.dayDistance(date),
                                hasGroup = viewModel.groupsByDay[date].orEmpty().isNotEmpty(),
                                isToday = date == today,
                                isSelected = date == viewModel.selectedDate,
                                onClick = { viewModel.select(date) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.m))
        Legend()

        Spacer(Modifier.height(Spacing.l))
        DayDetail(
            date = viewModel.selectedDate,
            runs = viewModel.runsByDay[viewModel.selectedDate].orEmpty(),
            groups = viewModel.groupsByDay[viewModel.selectedDate].orEmpty(),
            onGroupClick = onGroupClick,
        )
        Spacer(Modifier.height(Spacing.l))
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    meters: Int,
    hasGroup: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val level = distanceLevel(meters)
    val fill = level?.let { Lime.copy(alpha = it.alpha) } ?: Color.Transparent
    val textColor = if (level != null && level.alpha >= 0.55f) Charcoal else MaterialTheme.colorScheme.onSurface
    val ring = MaterialTheme.colorScheme.onSurface
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).padding(2.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(fill)
                .then(if (isSelected) Modifier.border(2.dp, ring, CircleShape) else Modifier),
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || level != null) FontWeight.Bold else null,
                color = textColor,
            )
        }
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (hasGroup) StarYellow else Color.Transparent),
        )
    }
}

@Composable
private fun Legend() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.calendar_legend_title), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                DISTANCE_LEVELS.forEach { level ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Lime.copy(alpha = level.alpha)),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            levelLabel(level),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(StarYellow))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.calendar_legend_group), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun levelLabel(level: DistanceLevel): String {
    val min = level.minMeters / 1000
    val max = level.maxMeters?.div(1000)
    return when {
        level.minMeters == 0 && max != null -> stringResource(R.string.calendar_legend_up_to, max)
        max == null -> stringResource(R.string.calendar_legend_over, min)
        else -> stringResource(R.string.calendar_legend_range, min, max)
    }
}

@Composable
private fun DayDetail(
    date: LocalDate,
    runs: List<RunHistoryItem>,
    groups: List<RunningGroup>,
    onGroupClick: (Int) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    Text(
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(Modifier.height(Spacing.s))
    if (runs.isEmpty() && groups.isEmpty()) {
        Text(
            stringResource(R.string.calendar_day_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        groups.forEach { group ->
            Card(
                onClick = { onGroupClick(group.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.m)) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(StarYellow))
                    Spacer(Modifier.width(Spacing.s))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(group.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(
                                R.string.calendar_group_time,
                                parseInstant(group.meetAt)?.atZone(zone)?.format(timeFormat).orEmpty(),
                                formatDistance(group.routeDistance),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
        runs.forEach { run ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.m)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Lime.copy(alpha = distanceLevel(run.distanceMeters)?.alpha ?: 0.3f)),
                    )
                    Spacer(Modifier.width(Spacing.s))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            routeTitle(run.startName, run.endName, run.routeDistance),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(
                                R.string.calendar_run_summary,
                                parseInstant(run.finishedAt)?.atZone(zone)?.format(timeFormat).orEmpty(),
                                formatDuration(run.durationSeconds),
                                formatPace(run.distanceMeters / run.durationSeconds.coerceAtLeast(1L).toDouble()),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(formatDistance(run.distanceMeters), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
