package com.example.runtime.ui.route

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.data.run.RunMetrics
import com.example.runtime.ui.common.formatDistance
import com.example.runtime.ui.common.formatDuration
import com.example.runtime.ui.theme.Spacing
import kotlin.math.roundToInt

@Composable
fun RunOngoingBar(metrics: RunMetrics, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = 10.dp)
        ) {
            Icon(painterResource(R.drawable.ic_directions_run), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(Spacing.s))
            Text(
                stringResource(
                    if (metrics.isPaused) R.string.run_ongoing_paused else R.string.run_ongoing,
                    formatDistance(metrics.distanceMeters.roundToInt()),
                    formatDuration(metrics.elapsedSeconds)
                ),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            Text(stringResource(R.string.run_ongoing_open), style = MaterialTheme.typography.labelLarge)
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}
