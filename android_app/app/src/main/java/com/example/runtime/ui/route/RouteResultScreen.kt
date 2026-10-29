package com.example.runtime.ui.route

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.example.runtime.R
import com.example.runtime.data.remote.GeneratedRouteData
import com.example.runtime.ui.common.AccentButton
import com.example.runtime.ui.common.GlassSurface
import com.example.runtime.ui.common.LocalAppMessages
import com.example.runtime.ui.common.StatItem
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.formatDistance
import com.example.runtime.ui.common.formatKm
import com.example.runtime.ui.common.glassBorderColor
import com.example.runtime.ui.common.glassColor
import com.example.runtime.ui.common.placeholder
import com.example.runtime.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private val CARD_HEIGHT = 340.dp
private val PANEL_HEIGHT = 440.dp

// 6. Route Result Screen (화면 6)
@Composable
fun RouteResultScreen(routeViewModel: RouteViewModel, onSave: () -> Unit) {
    val generated = routeViewModel.generated ?: return
    val saveState = routeViewModel.saveState
    val elevationLoadState = routeViewModel.elevationLoadState
    val routeSlopeSegments = routeViewModel.routeSlopeSegments
    val options = routeViewModel.options.ifEmpty { listOf(generated) }

    var showPanel by rememberSaveable { mutableStateOf(true) }
    val pagerState = rememberPagerState(initialPage = routeViewModel.selectedOption) { options.size }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect(routeViewModel::selectOption)
    }

    SaveFeedback(routeId = generated.temporaryRouteId, saveState = saveState)

    Box(modifier = Modifier.fillMaxSize()) {
        RouteMap(
            lines = generated.route.route,
            modifier = Modifier.fillMaxSize(),
            onClick = { showPanel = !showPanel },
            slopeSegments = routeSlopeSegments,
            contentPadding = PaddingValues(top = 72.dp, bottom = if (showPanel) PANEL_HEIGHT else 0.dp)
        )
        if (elevationLoadState == ElevationLoadState.Success &&
            routeSlopeSegments.isNotEmpty()
        ) {
            SlopeLegend(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(Spacing.m)
            )
        }
        AnimatedVisibility(
            visible = showPanel,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(modifier = Modifier.padding(bottom = Spacing.m)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = Spacing.l),
                        pageSpacing = Spacing.s,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        RouteOptionCard(
                            option = options[page],
                            index = page,
                            count = options.size,
                            isSelected = page == routeViewModel.selectedOption,
                            elevationLoadState = elevationLoadState,
                            elevationSummary = routeViewModel.steepSectionSummary,
                            modifier = Modifier
                                .height(CARD_HEIGHT)
                                .cardTurn(pagerState, page)
                        )
                    }
                    PageArrow(
                        visible = pagerState.currentPage > 0,
                        icon = R.drawable.ic_chevron_left,
                        description = stringResource(R.string.previous_route_option),
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    PageArrow(
                        visible = pagerState.currentPage < options.lastIndex,
                        icon = R.drawable.ic_chevron_right,
                        description = stringResource(R.string.next_route_option),
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
                if (options.size > 1) {
                    PageDots(count = options.size, selected = pagerState.currentPage)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.l),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(
                        onClick = { /* TODO : Share 링크 공유 기능 */ },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = glassColor(),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(painterResource(R.drawable.ic_share), contentDescription = stringResource(R.string.action_share))
                    }
                    Spacer(modifier = Modifier.width(Spacing.s))
                    AccentButton(
                        text = stringResource(
                            when (saveState) {
                                SaveState.Saving -> R.string.saving
                                SaveState.Saved -> R.string.saved
                                else -> R.string.action_save
                            }
                        ),
                        icon = if (saveState == SaveState.Saved) R.drawable.ic_bookmark_added else R.drawable.ic_bookmark,
                        onClick = onSave,
                        enabled = saveState == SaveState.Idle || saveState is SaveState.Failed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PageArrow(
    visible: Boolean,
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .padding(horizontal = Spacing.xs)
                .size(40.dp)
                .border(1.dp, glassBorderColor(), CircleShape)
        ) {
            Icon(painterResource(icon), contentDescription = description)
        }
    }
}

@Composable
private fun SaveFeedback(routeId: Int, saveState: SaveState) {
    val messages = LocalAppMessages.current
    val haptic = LocalHapticFeedback.current
    val savedMessage = stringResource(R.string.route_saved_message)
    val failedMessage = (saveState as? SaveState.Failed)?.message?.asString()
    var previous by remember { mutableStateOf(routeId to saveState) }

    LaunchedEffect(routeId, saveState) {
        val (previousId, previousState) = previous
        previous = routeId to saveState
        if (previousId != routeId || previousState != SaveState.Saving) return@LaunchedEffect
        when (saveState) {
            SaveState.Saved -> {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                messages?.show(savedMessage)
            }
            is SaveState.Failed -> {
                haptic.performHapticFeedback(HapticFeedbackType.Reject)
                failedMessage?.let { messages?.show(it) }
            }
            else -> Unit
        }
    }
}

private fun Modifier.cardTurn(pagerState: PagerState, page: Int): Modifier = graphicsLayer {
    val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
    val fraction = offset.absoluteValue.coerceIn(0f, 1f)
    val scale = lerp(1f, 0.86f, fraction)
    scaleX = scale
    scaleY = scale
    alpha = lerp(1f, 0.55f, fraction)
    rotationY = offset.coerceIn(-1f, 1f) * 18f
    cameraDistance = 16 * density
}

@Composable
private fun RouteOptionCard(
    option: GeneratedRouteData,
    index: Int,
    count: Int,
    isSelected: Boolean,
    elevationLoadState: ElevationLoadState,
    elevationSummary: String,
    modifier: Modifier = Modifier,
) {
    val route = option.route
    GlassSurface(modifier = modifier.fillMaxWidth().semantics { this.selected = isSelected }) {
        Column(modifier = Modifier.fillMaxSize().padding(Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index == 0) {
                    Text(
                        stringResource(R.string.route_option_recommended),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = Spacing.xs)
                    )
                } else {
                    Text(
                        stringResource(R.string.route_option_label, index + 1),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "${index + 1} / $count",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(Spacing.s))
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
            if (route.isShortestPath) {
                Text(
                    stringResource(R.string.shortest_path_notice, formatDistance(route.distance)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(Spacing.m))
            Row {
                StatItem(
                    label = stringResource(R.string.stat_climb),
                    value = route.elevationGainM?.let { stringResource(R.string.value_meters, it) } ?: "–",
                    icon = R.drawable.ic_trending_up,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = stringResource(R.string.stat_traffic_lights),
                    value = route.trafficLightCount.toString(),
                    icon = R.drawable.ic_traffic,
                    modifier = Modifier.weight(1f)
                ) // AI-assited with ChatGPT, 2026-10-16 ; reviewd by Soonyoung
            }
            HorizontalDivider(
                color = glassBorderColor(),
                modifier = Modifier.padding(vertical = Spacing.m)
            )
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                SectionLabel(stringResource(R.string.elevation_information))
                when {
                    !isSelected || elevationLoadState == ElevationLoadState.Loading -> PlaceholderLines()
                    elevationLoadState == ElevationLoadState.Success ->
                        Text(elevationSummary, style = MaterialTheme.typography.bodyMedium)
                    else -> Text(
                        stringResource(R.string.elevation_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.m))
                SectionLabel(stringResource(R.string.llm_briefing))
                Text(route.briefing, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = Spacing.xs)
    )
}

@Composable
private fun PlaceholderLines() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(14.dp).placeholder())
        Box(modifier = Modifier.fillMaxWidth(0.6f).height(14.dp).placeholder())
    }
}

@Composable
private fun PageDots(count: Int, selected: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.m),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = if (index == selected) 18.dp else 6.dp, height = 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    )
            )
        }
    }
}

@Composable
private fun SlopeLegend(modifier: Modifier = Modifier) {
    GlassSurface(modifier = modifier, shape = MaterialTheme.shapes.extraLarge) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            SlopeLegendItem(
                color = steepUphillColor,
                label = stringResource(R.string.slope_legend_steep_uphill),
            )
            SlopeLegendItem(
                color = moderateUphillColor,
                label = stringResource(R.string.slope_legend_moderate_uphill),
            )
            SlopeLegendItem(
                color = levelRouteColor,
                label = stringResource(R.string.slope_legend_level),
            )
            SlopeLegendItem(
                color = downhillColor,
                label = stringResource(R.string.slope_legend_downhill),
            )
        }
    }
}

@Composable
private fun SlopeLegendItem(
    color: Color,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
