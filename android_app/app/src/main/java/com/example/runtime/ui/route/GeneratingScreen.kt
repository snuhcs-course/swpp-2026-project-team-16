package com.example.runtime.ui.route

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.ui.common.GlassSurface
import com.example.runtime.ui.theme.Spacing
import kotlinx.coroutines.delay

private val GENERATING_STEPS = listOf(
    R.string.generating_step_route,
    R.string.generating_step_elevation,
    R.string.generating_step_briefing,
)
private val STEP_DELAYS_MS = listOf(2_500L, 4_000L)

// 5. Generating Screen (화면 5)
@Composable
fun GeneratingScreen(routeViewModel: RouteViewModel, onComplete: () -> Unit, onFailure: () -> Unit) {
    var routeReady by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        routeViewModel.generate(onSuccess = { routeReady = true }, onFailure = onFailure)
    }
    LaunchedEffect(Unit) {
        STEP_DELAYS_MS.forEach {
            delay(it)
            if (!routeReady) step++
        }
    }
    LaunchedEffect(routeReady) {
        if (routeReady) step = GENERATING_STEPS.size
    }

    val start = routeViewModel.pendingStart ?: return
    Box(modifier = Modifier.fillMaxSize()) {
        DrawingRouteMap(
            start = start.point,
            end = routeViewModel.pendingEnd?.point,
            distanceMeters = routeViewModel.pendingDistanceMeters ?: 0,
            route = if (routeReady) routeViewModel.generated?.route?.route else null,
            onRouteDrawn = onComplete,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 48.dp, bottom = 240.dp)
        )
        GlassSurface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(Spacing.m)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(Spacing.l)) {
                Text(stringResource(R.string.generating_route), style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(Spacing.m))
                GENERATING_STEPS.forEachIndexed { index, label ->
                    GeneratingStep(
                        label = stringResource(label),
                        state = when {
                            index < step -> StepState.Done
                            index == step -> StepState.Active
                            else -> StepState.Pending
                        }
                    )
                    if (index < GENERATING_STEPS.lastIndex) Spacer(modifier = Modifier.height(Spacing.m))
                }
            }
        }
    }
}

private enum class StepState { Done, Active, Pending }

@Composable
private fun GeneratingStep(label: String, state: StepState) {
    val accent = MaterialTheme.colorScheme.primaryContainer
    val ring = MaterialTheme.colorScheme.primary
    val pulse by rememberInfiniteTransition(label = "step").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "stepPulse"
    )
    val textColor by animateColorAsState(
        if (state == StepState.Pending) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        label = "stepText"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .then(
                    when (state) {
                        StepState.Done -> Modifier.background(accent)
                        StepState.Active -> Modifier.border(2.dp, ring, CircleShape)
                        StepState.Pending -> Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                StepState.Done -> Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                StepState.Active -> Box(
                    modifier = Modifier
                        .size(10.dp)
                        .graphicsLayer { alpha = pulse }
                        .clip(CircleShape)
                        .background(ring)
                )
                StepState.Pending -> Unit
            }
        }
        Spacer(modifier = Modifier.width(Spacing.m))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = textColor)
    }
}
