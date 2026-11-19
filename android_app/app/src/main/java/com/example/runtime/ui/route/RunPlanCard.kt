package com.example.runtime.ui.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.data.remote.PaceSegment
import com.example.runtime.data.remote.RunPlan
import com.example.runtime.ui.common.formatDistance
import java.util.Locale

@Composable
fun RunPlanCard(
    plan: RunPlan,
    title: String,
    modifier: Modifier = Modifier,
    extraRows: @Composable ColumnScope.() -> Unit = {},
    onUpdatePaceSegment: ((Int, PaceSegment) -> Boolean)? = null,
    onRemovePaceSegment: ((Int) -> Unit)? = null,
    onAddPaceSegment: (() -> Boolean)? = null,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            extraRows()
            RunType.from(plan.runType)?.let { type ->
                PlanRow(stringResource(R.string.plan_run_type), stringResource(type.label))
            }
            PlanRow(stringResource(R.string.plan_distance), formatDistance(plan.distanceMeters))
            PlanRow(stringResource(R.string.plan_target_pace), stringResource(R.string.pace_range_per_km, plan.paceRangeText()))
            if (plan.alertOnSlow || plan.alertOnFast) {
                val alertLabels = listOfNotNull(
                    if (plan.alertOnSlow) stringResource(R.string.pace_maker_alert_slow) else null,
                    if (plan.alertOnFast) stringResource(R.string.pace_maker_alert_fast) else null,
                )
                PlanRow(stringResource(R.string.pace_maker_alerts), alertLabels.joinToString(", "))
            }
            if (plan.paceSegments.isNotEmpty()) {
                Text(
                    stringResource(R.string.pace_maker_segments_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                plan.paceSegments.forEachIndexed { index, segment ->
                    if (onUpdatePaceSegment != null) {
                        PaceSegmentEditor(
                            index = index,
                            segment = segment,
                            onApply = onUpdatePaceSegment,
                            onRemove = onRemovePaceSegment,
                        )
                    } else {
                        PlanRow(
                            label = stringResource(
                                R.string.pace_segment_distance,
                                formatKm(segment.startMeters),
                                formatKm(segment.endMeters),
                            ),
                            value = "${formatPaceClock(segment.paceMinSeconds)}–${formatPaceClock(segment.paceMaxSeconds)} /km",
                        )
                    }
                }
            }
            if (onAddPaceSegment != null) {
                var addFailed by androidx.compose.runtime.remember(plan.paceSegments, plan.distanceMeters) {
                    androidx.compose.runtime.mutableStateOf(false)
                }
                TextButton(
                    onClick = { addFailed = !onAddPaceSegment() },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text(stringResource(R.string.pace_segment_add))
                }
                if (addFailed) {
                    Text(
                        stringResource(R.string.pace_segment_add_unavailable),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (plan.intensity.isNotBlank()) {
                PlanRow(stringResource(R.string.plan_intensity), plan.intensity)
            }
            if (plan.routePreferences.isNotEmpty()) {
                val labels = plan.routePreferences.mapNotNull { value ->
                    RoutePreference.entries.firstOrNull { it.apiValue == value }
                }.map { stringResource(it.label) }
                if (labels.isNotEmpty()) PlanRow(stringResource(R.string.plan_route), labels.joinToString(", "))
            }
            if (plan.summary.isNotBlank()) {
                Text(
                    plan.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            footer()
        }
    }
}

@Composable
private fun PaceSegmentEditor(
    index: Int,
    segment: PaceSegment,
    onApply: (Int, PaceSegment) -> Boolean,
    onRemove: ((Int) -> Unit)?,
) {
    var startText by androidx.compose.runtime.remember(segment) { androidx.compose.runtime.mutableStateOf(formatKm(segment.startMeters)) }
    var endText by androidx.compose.runtime.remember(segment) { androidx.compose.runtime.mutableStateOf(formatKm(segment.endMeters)) }
    var minPaceText by androidx.compose.runtime.remember(segment) { androidx.compose.runtime.mutableStateOf(formatPaceClock(segment.paceMinSeconds)) }
    var maxPaceText by androidx.compose.runtime.remember(segment) { androidx.compose.runtime.mutableStateOf(formatPaceClock(segment.paceMaxSeconds)) }
    var invalid by androidx.compose.runtime.remember(segment) { androidx.compose.runtime.mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.pace_segment_number, index + 1), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = startText,
                onValueChange = { startText = it; invalid = false },
                label = { Text(stringResource(R.string.pace_segment_start)) },
                suffix = { Text("km") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = endText,
                onValueChange = { endText = it; invalid = false },
                label = { Text(stringResource(R.string.pace_segment_end)) },
                suffix = { Text("km") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = minPaceText,
                onValueChange = { minPaceText = it; invalid = false },
                label = { Text(stringResource(R.string.pace_segment_fast_edge)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = maxPaceText,
                onValueChange = { maxPaceText = it; invalid = false },
                label = { Text(stringResource(R.string.pace_segment_slow_edge)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    val start = parseKm(startText)
                    val end = parseKm(endText)
                    val minPace = parsePace(minPaceText)
                    val maxPace = parsePace(maxPaceText)
                    invalid = start == null || end == null || minPace == null || maxPace == null ||
                        end <= start || minPace > maxPace || !onApply(
                            index,
                            PaceSegment(start!!, end!!, minPace!!, maxPace!!),
                        )
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.pace_segment_apply))
            }
            if (onRemove != null) {
                TextButton(onClick = { onRemove(index) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pace_segment_remove))
                }
            }
        }
        if (invalid) {
            Text(stringResource(R.string.pace_segment_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatKm(meters: Int): String =
    String.format(Locale.US, "%.1f", meters / 1000.0).removeSuffix(".0")

private fun parseKm(value: String): Int? = value.trim().toDoubleOrNull()
    ?.takeIf { it.isFinite() && it >= 0.0 }
    ?.let { (it * 1000).toInt() }

private fun parsePace(value: String): Int? {
    val match = Regex("^(\\d{1,2}):([0-5]\\d)$").matchEntire(value.trim()) ?: return null
    val seconds = match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()
    return seconds.takeIf { it in 180..900 }
}

@Composable
fun PlanRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f),
        )
    }
}
