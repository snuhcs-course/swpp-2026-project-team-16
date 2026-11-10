package com.example.runtime.ui.route

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runtime.R
import com.example.runtime.data.remote.RunPlan
import com.example.runtime.ui.common.formatDistance
import com.example.runtime.ui.common.formatDuration
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

@Composable
fun RunReportScreen(
    result: RunResult,
    plan: RunPlan?,
    isLoggedIn: Boolean,
    recordState: RunRecordState,
    reportState: ReportState,
    onRetryReport: () -> Unit,
    onPlanNextRun: (RunPlan) -> Unit,
    onDone: () -> Unit,
    shareAction: @Composable () -> Unit,
) {
    val splits = remember(result, plan) { result.splitPaces(plan) }
    val runType = RunType.from(plan?.runType)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (result.finishedAtMillis > 0) {
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(Date(result.finishedAtMillis)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (runType != null) stringResource(R.string.report_title_typed, stringResource(runType.label))
                else stringResource(R.string.report_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (plan != null && runType != null) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        stringResource(
                            R.string.report_plan_pill,
                            formatDistance(plan.distanceMeters),
                            stringResource(runType.label),
                            plan.paceRangeText(),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
        }

        ReportStats(result, plan, splits)

        if (splits.isNotEmpty()) {
            ReportCard(title = stringResource(R.string.report_pace_chart)) {
                ChartLegend(showBand = plan != null)
                PaceChart(splits = splits, plan = plan)
                Text(
                    stringResource(R.string.report_chart_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ReportCard(title = stringResource(R.string.report_splits)) {
                SplitList(splits)
            }
        }

        ReportCard(title = stringResource(R.string.report_coach_comment)) {
            CoachComment(isLoggedIn, recordState, reportState, onRetryReport)
        }

        ReportCard(title = stringResource(R.string.report_route)) {
            PlanRow(
                stringResource(R.string.report_off_route),
                if (result.offRouteCount == 0) stringResource(R.string.report_off_route_none)
                else stringResource(R.string.report_off_route_value, result.offRouteCount, formatDuration(result.offRouteSeconds)),
            )
        }

        (reportState as? ReportState.Loaded)?.report?.recommendation?.let { recommendation ->
            RunPlanCard(
                plan = recommendation.plan,
                title = stringResource(R.string.report_next_run),
                extraRows = {
                    PlanRow(
                        stringResource(R.string.report_next_when),
                        if (recommendation.restDays == 0) stringResource(R.string.report_next_tomorrow)
                        else stringResource(R.string.report_next_after_rest, recommendation.restDays),
                    )
                },
                footer = {
                    Text(recommendation.reason, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = { onPlanNextRun(recommendation.plan) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.report_plan_next_run))
                    }
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) { shareAction() }
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_done))
        }
    }
}

@Composable
private fun ReportStats(result: RunResult, plan: RunPlan?, splits: List<SplitPace>) {
    val average = result.averagePaceSeconds()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = stringResource(R.string.run_metric_distance),
                value = formatDistance(result.distanceMeters),
                caption = plan?.let { stringResource(R.string.report_target, formatDistance(it.distanceMeters)) },
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.run_metric_time),
                value = formatDuration(result.elapsedSeconds),
                caption = plan?.let { stringResource(R.string.report_expected, formatDuration(it.expectedSeconds())) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = stringResource(R.string.report_average_pace),
                value = average?.let(::formatPaceClock) ?: stringResource(R.string.run_pace_unavailable),
                caption = plan?.let { stringResource(R.string.report_target, it.paceRangeText()) },
                modifier = Modifier.weight(1f),
            )
            val judged = splits.filter { it.status != null }
            if (plan != null && judged.isNotEmpty()) {
                StatTile(
                    label = stringResource(R.string.report_in_range),
                    value = "${judged.count { it.status == SplitStatus.IN_RANGE }} / ${judged.size}",
                    caption = stringResource(R.string.report_in_range_caption),
                    modifier = Modifier.weight(1f),
                )
            } else {
                val fastest = splits.minByOrNull { it.paceSeconds }
                StatTile(
                    label = stringResource(R.string.report_fastest_split),
                    value = fastest?.let { formatPaceClock(it.paceSeconds) } ?: stringResource(R.string.run_pace_unavailable),
                    caption = fastest?.let { stringResource(R.string.report_split_km, it.km) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, caption: String?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                caption.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReportCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ChartLegend(showBand: Boolean) {
    val line = MaterialTheme.colorScheme.primary
    val band = MaterialTheme.colorScheme.primaryContainer
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showBand) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(14.dp, 10.dp)) {
                    drawRoundRect(band.copy(alpha = BAND_ALPHA), cornerRadius = CornerRadius(3.dp.toPx()))
                }
                Spacer(Modifier.width(5.dp))
                Text(stringResource(R.string.report_legend_target), style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(14.dp, 10.dp)) {
                drawLine(line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx())
            }
            Spacer(Modifier.width(5.dp))
            Text(stringResource(R.string.report_legend_actual), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PaceChart(splits: List<SplitPace>, plan: RunPlan?) {
    val lineColor = MaterialTheme.colorScheme.primary
    val bandColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = BAND_ALPHA)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val tooltipBackground = MaterialTheme.colorScheme.inverseSurface
    val tooltipText = MaterialTheme.colorScheme.inverseOnSurface
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = labelColor)
    val tooltipStyle = TextStyle(fontSize = 12.sp, color = tooltipText, fontWeight = FontWeight.SemiBold)
    var selected by remember(splits) { mutableStateOf<Int?>(null) }
    val description = splits.joinToString(", ") { "${it.km}km ${formatPaceClock(it.paceSeconds)}" }

    val values = splits.map { it.paceSeconds } + listOfNotNull(plan?.paceMinSeconds, plan?.paceMaxSeconds)
    val yMin = (floor((values.min() - 10) / GRID_STEP.toDouble()) * GRID_STEP).toInt()
    val yMax = (ceil((values.max() + 10) / GRID_STEP.toDouble()) * GRID_STEP).toInt().coerceAtLeast(yMin + GRID_STEP)
    val step = GRID_STEP * ((yMax - yMin) / GRID_STEP / MAX_GRID_LINES + 1)

    Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = description }
                .pointerInput(splits) {
                    detectTapGestures { tap ->
                        val left = LEFT_AXIS.toPx()
                        val width = size.width - left
                        val index = splits.indices.minByOrNull { i ->
                            abs(left + (i + 0.5f) * width / splits.size - tap.x)
                        }
                        selected = if (selected == index) null else index
                    }
                },
        ) {
            val left = LEFT_AXIS.toPx()
            val top = 12.dp.toPx()
            val bottom = size.height - 22.dp.toPx()
            val width = size.width - left
            fun x(i: Int) = left + (i + 0.5f) * width / splits.size
            fun y(pace: Int) = top + (pace - yMin).toFloat() / (yMax - yMin) * (bottom - top)

            var tick = yMin
            while (tick <= yMax) {
                drawLine(gridColor, Offset(left, y(tick)), Offset(size.width, y(tick)), strokeWidth = 1.dp.toPx())
                val text = measurer.measure(formatPaceClock(tick), labelStyle)
                drawText(text, topLeft = Offset(left - text.size.width - 6.dp.toPx(), y(tick) - text.size.height / 2f))
                tick += step
            }
            plan?.let {
                drawRoundRect(
                    bandColor,
                    topLeft = Offset(left, y(it.paceMinSeconds)),
                    size = Size(width, y(it.paceMaxSeconds) - y(it.paceMinSeconds)),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
            splits.forEachIndexed { i, split ->
                val text = measurer.measure("${split.km}km", labelStyle)
                drawText(text, topLeft = Offset(x(i) - text.size.width / 2f, size.height - text.size.height))
            }
            if (splits.size > 1) {
                val path = Path().apply {
                    splits.forEachIndexed { i, split ->
                        if (i == 0) moveTo(x(i), y(split.paceSeconds)) else lineTo(x(i), y(split.paceSeconds))
                    }
                }
                drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            splits.forEachIndexed { i, split ->
                val center = Offset(x(i), y(split.paceSeconds))
                drawCircle(surface, radius = 6.5.dp.toPx(), center = center)
                drawCircle(lineColor, radius = 4.5.dp.toPx(), center = center)
            }
            selected?.let { i ->
                val split = splits[i]
                val text = measurer.measure("${split.km}km ${formatPaceClock(split.paceSeconds)} /km", tooltipStyle)
                val padding = 6.dp.toPx()
                val boxWidth = text.size.width + padding * 2
                val boxHeight = text.size.height + padding * 2
                val boxLeft = (x(i) - boxWidth / 2).coerceIn(0f, size.width - boxWidth)
                val boxTop = (y(split.paceSeconds) - boxHeight - 10.dp.toPx()).coerceAtLeast(0f)
                drawRoundRect(tooltipBackground, Offset(boxLeft, boxTop), Size(boxWidth, boxHeight), CornerRadius(8.dp.toPx()))
                drawText(text, topLeft = Offset(boxLeft + padding, boxTop + padding))
            }
        }
    }
}

@Composable
private fun SplitList(splits: List<SplitPace>) {
    val fastest = splits.minOf { it.paceSeconds }
    val slowest = splits.maxOf { it.paceSeconds }
    Column {
        splits.forEach { split ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (split.distanceMeters < 1000) formatDistance(split.distanceMeters) else stringResource(R.string.report_split_km, split.km),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(52.dp),
                )
                Text(formatPaceClock(split.paceSeconds), fontWeight = FontWeight.SemiBold, modifier = Modifier.width(44.dp))
                val relative = if (slowest == fastest) 1f else 0.35f + 0.65f * (slowest - split.paceSeconds) / (slowest - fastest).toFloat()
                LinearProgressIndicator(
                    progress = { relative },
                    modifier = Modifier.weight(1f).height(6.dp),
                    drawStopIndicator = {},
                )
                split.status?.let { SplitStatusPill(it, split.diffSeconds) }
            }
        }
    }
}

@Composable
private fun SplitStatusPill(status: SplitStatus, diffSeconds: Int) {
    val (text, container, content) = when (status) {
        SplitStatus.IN_RANGE -> Triple(
            stringResource(R.string.report_status_in_range),
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        SplitStatus.FAST -> Triple(
            stringResource(R.string.report_status_fast, diffSeconds),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        SplitStatus.SLOW -> Triple(
            stringResource(R.string.report_status_slow, diffSeconds),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = content) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
private fun CoachComment(
    isLoggedIn: Boolean,
    recordState: RunRecordState,
    reportState: ReportState,
    onRetry: () -> Unit,
) {
    when {
        !isLoggedIn -> Text(
            stringResource(R.string.report_log_in_for_coach),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        reportState is ReportState.Loaded -> Text(reportState.report.comment, style = MaterialTheme.typography.bodyMedium)
        reportState == ReportState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.report_comment_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
        recordState is RunRecordState.Failed -> Text(
            stringResource(R.string.report_record_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                stringResource(R.string.report_comment_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val LEFT_AXIS = 40.dp
private const val GRID_STEP = 15
private const val MAX_GRID_LINES = 5
private const val BAND_ALPHA = 0.45f
