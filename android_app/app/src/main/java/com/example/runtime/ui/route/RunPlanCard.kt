package com.example.runtime.ui.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.data.remote.RunPlan
import com.example.runtime.ui.common.formatDistance

@Composable
fun RunPlanCard(
    plan: RunPlan,
    title: String,
    modifier: Modifier = Modifier,
    extraRows: @Composable ColumnScope.() -> Unit = {},
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
