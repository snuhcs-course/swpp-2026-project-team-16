package com.example.runtime.ui.route

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runtime.R
import com.example.runtime.data.remote.GeneratedRouteData
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.formatDistance
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

// 6. Route Result Screen (화면 6)
@Composable
fun RouteResultScreen(routeViewModel: RouteViewModel, onSave: () -> Unit) {
    val generated = routeViewModel.generated ?: return
    val saveState = routeViewModel.saveState
    val elevationSummary = routeViewModel.steepSectionSummary
    val elevationLoadState = routeViewModel.elevationLoadState
    val routeSlopeSegments = routeViewModel.routeSlopeSegments

    var showFullMap by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (routeViewModel.options.size > 1) {
            RouteOptionCards(
                options = routeViewModel.options,
                selected = routeViewModel.selectedOption,
                onSelect = routeViewModel::selectOption
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        // 지도
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            RouteMap(
                lines = generated.route.route,
                modifier = Modifier.fillMaxSize(),
                onClick = { showFullMap = true },
                slopeSegments = routeSlopeSegments,
            )
        }
        if (elevationLoadState == ElevationLoadState.Success &&
            routeSlopeSegments.isNotEmpty()
        ) {
            SlopeLegend()
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.distance_value, formatDistance(generated.route.distance)),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        if (generated.route.isShortestPath) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.shortest_path_notice, formatDistance(generated.route.distance)),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }

        Text(
            text = generated.route.trafficLightCount?.let { count ->
                stringResource(R.string.traffic_light_count, count)
            } ?: stringResource(R.string.traffic_light_count_unknown),
            fontSize = 16.sp
        ) // AI-assited with ChatGPT, 2026-10-30 ; reviewd by Soonyoung

        Spacer(modifier = Modifier.height(8.dp))

        // elevation information
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    stringResource(R.string.elevation_information),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                when (elevationLoadState) {
                    ElevationLoadState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }

                    ElevationLoadState.Success -> {
                        Text(elevationSummary)
                    }

                    ElevationLoadState.Error -> {
                        Text(stringResource(R.string.elevation_unavailable))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // LLM Briefing
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.llm_briefing), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(generated.route.briefing)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { /* TODO : Share 링크 공유 기능 */ }) {
                Text(stringResource(R.string.action_share))
            }
            Button(
                onClick = onSave,
                enabled = saveState == SaveState.Idle || saveState is SaveState.Failed
            ) {
                Text(
                    stringResource(
                        when (saveState) {
                            SaveState.Saving -> R.string.saving
                            SaveState.Saved -> R.string.saved
                            else -> R.string.action_save
                        }
                    )
                )
            }
        }
        if (saveState is SaveState.Failed) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(saveState.message.asString(), color = MaterialTheme.colorScheme.error)
        }
    }

    if (showFullMap) {
        FullScreenRouteMap(
            lines = generated.route.route,
            onDismiss = { showFullMap = false },
            slopeSegments = routeSlopeSegments,
            )
    }
}

@Composable
private fun SlopeLegend() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SlopeLegendItem(
                modifier = Modifier.weight(1f),
                color = steepUphillColor,
                label = stringResource(R.string.slope_legend_steep_uphill),
            )
            SlopeLegendItem(
                modifier = Modifier.weight(1f),
                color = moderateUphillColor,
                label = stringResource(R.string.slope_legend_moderate_uphill),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SlopeLegendItem(
                modifier = Modifier.weight(1f),
                color = levelRouteColor,
                label = stringResource(R.string.slope_legend_level),
            )
            SlopeLegendItem(
                modifier = Modifier.weight(1f),
                color = downhillColor,
                label = stringResource(R.string.slope_legend_downhill),
            )
        }
    }
}

@Composable
private fun SlopeLegendItem(
    modifier: Modifier = Modifier,
    color: Color,
    label: String,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(label, fontSize = 11.sp)
    }
}

@Composable
private fun RouteOptionCards(options: List<GeneratedRouteData>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selected
            val route = option.route
            OutlinedCard(
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f).semantics { this.selected = isSelected },
                border = BorderStroke(
                    if (isSelected) 2.dp else 1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                ),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (index == 0) stringResource(R.string.route_option_recommended) else "",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(formatDistance(route.distance), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        route.elevationGainM?.let { stringResource(R.string.route_option_climb, it) }
                            ?: stringResource(R.string.route_option_climb_unknown),
                        fontSize = 13.sp
                    )
                    Text(
                        text = route.trafficLightCount?.let { count ->
                            stringResource(R.string.route_option_traffic_lights, count)
                        } ?: stringResource(R.string.route_option_traffic_lights_unknown),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
