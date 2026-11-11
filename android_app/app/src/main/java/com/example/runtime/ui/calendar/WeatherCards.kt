package com.example.runtime.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.data.remote.WeatherBriefing
import com.example.runtime.ui.common.placeholder
import com.example.runtime.ui.theme.Spacing
import kotlin.math.roundToInt

private val AIR_GOOD = Color(0xFF3D8BFD)
private val AIR_MODERATE = Color(0xFF2EAD5B)
private val AIR_BAD = Color(0xFFF0A020)

@Composable
fun WeatherCards(state: WeatherState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    when (state) {
        WeatherState.Loading -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = modifier.fillMaxWidth()) {
            repeat(2) {
                Box(modifier = Modifier.weight(1f).height(150.dp).placeholder(MaterialTheme.shapes.large))
            }
        }
        WeatherState.Failed -> Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s)) {
                Text(
                    stringResource(R.string.weather_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
        is WeatherState.Loaded -> Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        ) {
            WeatherCard(state.briefing, state.isNearMe, Modifier.weight(1f).fillMaxHeight())
            AirCard(state.briefing, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun BriefingCard(title: String, caption: String?, modifier: Modifier, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                caption?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
private fun WeatherCard(briefing: WeatherBriefing, isNearMe: Boolean, modifier: Modifier) {
    BriefingCard(
        title = stringResource(R.string.weather_title),
        caption = stringResource(if (isNearMe) R.string.weather_near_me else R.string.weather_default_city),
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.weather_degrees, briefing.temperature.roundToInt()),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(Spacing.s))
            Text(
                briefing.conditionLabel,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Text(
            listOfNotNull(
                briefing.apparentTemperature?.let { stringResource(R.string.weather_feels_like, it.roundToInt()) },
                briefing.precipitationProbability?.let { stringResource(R.string.weather_precipitation, it) },
            ).joinToString(", "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(briefing.weatherTip, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AirCard(briefing: WeatherBriefing, modifier: Modifier) {
    BriefingCard(title = stringResource(R.string.air_title), caption = null, modifier = modifier) {
        if (briefing.airGrade == null || briefing.airLabel == null) {
            Text(
                stringResource(R.string.air_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@BriefingCard
        }
        val color = when (briefing.airGrade) {
            "good" -> AIR_GOOD
            "moderate" -> AIR_MODERATE
            "bad" -> AIR_BAD
            else -> MaterialTheme.colorScheme.error
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(Spacing.s))
            Text(
                briefing.airLabel,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
        Text(
            listOfNotNull(
                briefing.pm10?.let { stringResource(R.string.air_pm10, it) },
                briefing.pm25?.let { stringResource(R.string.air_pm25, it) },
            ).joinToString(", "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        briefing.airTip?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
