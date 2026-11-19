package com.example.runtime.ui.route

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runtime.R
import com.example.runtime.data.remote.RunPlan
import com.example.runtime.data.run.PaceAlertSettings
import com.example.runtime.data.run.PaceAlertSegment
import com.example.runtime.data.run.RunTrackingService
import com.example.runtime.data.run.RunTrackingState
import com.example.runtime.data.run.TrailPoint
import com.example.runtime.ui.common.formatDistance
import com.example.runtime.ui.common.formatDuration
import com.example.runtime.ui.common.formatPace
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraAnimation
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.compose.ExperimentalNaverMapApi
import com.naver.maps.map.compose.ArrowheadPathOverlay
import com.naver.maps.map.compose.Marker
import com.naver.maps.map.compose.MarkerState
import com.naver.maps.map.compose.NaverMap
import com.naver.maps.map.compose.PathOverlay
import com.naver.maps.map.compose.rememberCameraPositionState
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RunNavigationScreen(
    routePoints: List<RouteCoordinate>,
    routeProgress: RouteProgress,
    isOffRoute: Boolean,
    offRouteTotals: () -> Pair<Int, Long>,
    routeDistanceMeters: Int,
    plan: RunPlan? = null,
    paceAlertSettings: PaceAlertSettings? = null,
    uphillSections: List<RouteUphillSection> = emptyList(),
    onClose: () -> Unit,
    onRunFinished: (RunResult) -> Unit = {},
    isFree: Boolean = false,
    isMakingReturnRoute: Boolean = false,
    onMakeReturnRoute: (from: RouteCoordinate, to: RouteCoordinate, distanceMeters: Int) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    val metrics by RunTrackingState.metrics.collectAsState()
    val trail by RunTrackingState.trail.collectAsState()
    val splitTimes by RunTrackingState.splitTimes.collectAsState()
    val turnGuide = remember(routePoints) { RouteTurnGuide(routePoints) }
    val voiceGuide = remember(routePoints) { RouteVoiceGuide(turnGuide.turns, turnGuide.routeLengthMeters) }
    val uphillVoiceGuide = remember(uphillSections) { UphillVoiceGuide(uphillSections) }
    var showFinishConfirmation by remember { mutableStateOf(false) }
    var permissionError by remember { mutableStateOf(false) }
    var showReturnDialog by remember { mutableStateOf(false) }
    val hasRoute = routePoints.size >= 2

    LaunchedEffect(routeProgress.distanceAlongMeters, metrics.isTracking, metrics.isPaused, isOffRoute) {
        val cue = voiceGuide.update(
            progressMeters = routeProgress.distanceAlongMeters,
            isTracking = metrics.isTracking,
            isPaused = metrics.isPaused,
            isOffRoute = isOffRoute,
        ) ?: return@LaunchedEffect
        val messageId = when (cue.kind) {
            RouteVoiceCue.Kind.START -> R.string.run_voice_started
            RouteVoiceCue.Kind.OFF_ROUTE -> R.string.run_voice_off_route
            RouteVoiceCue.Kind.REJOINED -> R.string.run_voice_rejoined
            RouteVoiceCue.Kind.ARRIVED -> R.string.run_voice_arrived
            RouteVoiceCue.Kind.TURN_APPROACHING -> when (cue.direction) {
                RouteTurnDirection.LEFT -> R.string.run_voice_turn_approaching_left
                RouteTurnDirection.RIGHT -> R.string.run_voice_turn_approaching_right
                RouteTurnDirection.UTURN -> R.string.run_voice_turn_approaching_uturn
                null -> return@LaunchedEffect
            }
            RouteVoiceCue.Kind.TURN_NOW -> when (cue.direction) {
                RouteTurnDirection.LEFT -> R.string.run_voice_turn_now_left
                RouteTurnDirection.RIGHT -> R.string.run_voice_turn_now_right
                RouteTurnDirection.UTURN -> R.string.run_voice_turn_now_uturn
                null -> return@LaunchedEffect
            }
        }
        RunTrackingService.speakNavigationCue(context, messageId, cue.distanceMeters)
    }

    LaunchedEffect(routeProgress.distanceAlongMeters, metrics.isTracking, metrics.isPaused, isOffRoute) {
        if (!metrics.isTracking || metrics.isPaused || isOffRoute) return@LaunchedEffect
        val distanceToUphill = uphillVoiceGuide.update(routeProgress.distanceAlongMeters) ?: return@LaunchedEffect
        RunTrackingService.speakNavigationCue(context, R.string.run_voice_uphill_approaching, distanceToUphill)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            permissionError = false
            val startIntent = Intent(context, RunTrackingService::class.java)
                .setAction(RunTrackingService.ACTION_START)
            val alertSettings = paceAlertSettings ?: plan?.takeIf { it.alertOnSlow || it.alertOnFast }?.let {
                PaceAlertSettings(
                    paceMinSeconds = it.paceMinSeconds,
                    paceMaxSeconds = it.paceMaxSeconds,
                    alertOnSlow = it.alertOnSlow,
                    alertOnFast = it.alertOnFast,
                    segments = it.paceSegments.map { segment ->
                        PaceAlertSegment(segment.startMeters, segment.endMeters, segment.paceMinSeconds, segment.paceMaxSeconds)
                    },
                )
            }
            if (alertSettings != null) {
                startIntent
                    .putExtra(RunTrackingService.EXTRA_PACE_MIN_SECONDS, alertSettings.paceMinSeconds)
                    .putExtra(RunTrackingService.EXTRA_PACE_MAX_SECONDS, alertSettings.paceMaxSeconds)
                    .putExtra(RunTrackingService.EXTRA_PACE_ALERT_ON_SLOW, alertSettings.alertOnSlow)
                    .putExtra(RunTrackingService.EXTRA_PACE_ALERT_ON_FAST, alertSettings.alertOnFast)
                    .putExtra(RunTrackingService.EXTRA_PACE_SEGMENT_STARTS, alertSettings.segments.map { it.startMeters }.toIntArray())
                    .putExtra(RunTrackingService.EXTRA_PACE_SEGMENT_ENDS, alertSettings.segments.map { it.endMeters }.toIntArray())
                    .putExtra(RunTrackingService.EXTRA_PACE_SEGMENT_MINS, alertSettings.segments.map { it.paceMinSeconds }.toIntArray())
                    .putExtra(RunTrackingService.EXTRA_PACE_SEGMENT_MAXES, alertSettings.segments.map { it.paceMaxSeconds }.toIntArray())
            }
            context.startForegroundService(startIntent)
        } else {
            permissionError = true
        }
    }

    fun requestStart() {
        val requestedPermissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        permissionLauncher.launch(requestedPermissions)
    }

    fun sendServiceAction(action: String) {
        context.startService(Intent(context, RunTrackingService::class.java).setAction(action))
    }

    val averagePace = if (metrics.distanceMeters >= 20.0) {
        formatPace(metrics.distanceMeters / metrics.elapsedSeconds.coerceAtLeast(1L))
    } else {
        stringResource(R.string.run_pace_unavailable)
    }
    val currentLatitude = metrics.latitude
    val currentLongitude = metrics.longitude
    val currentLocation = if (currentLatitude != null && currentLongitude != null) {
        LatLng(currentLatitude, currentLongitude)
    } else {
        null
    }

    BackHandler(onBack = onClose)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            RunningRouteMap(
                points = routePoints,
                trail = trail,
                currentLocation = currentLocation,
                headingDegrees = metrics.headingDegrees,
                isOffRoute = isOffRoute,
                progressMeters = routeProgress.distanceAlongMeters,
                modifier = Modifier.fillMaxSize(),
            )
            FilledIconButton(
                onClick = onClose,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
            ) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.run_close))
            }
        }

        val totalRouteMeters = routeDistanceMeters.toDouble().coerceAtLeast(1.0)
        val completedMeters = (routeProgress.distanceAlongMeters /
            routeProgress.routeLengthMeters.coerceAtLeast(1.0) * totalRouteMeters)
            .coerceIn(0.0, totalRouteMeters)
        val progress = (completedMeters / totalRouteMeters).toFloat()
        val remainingMeters = (routeDistanceMeters - completedMeters.roundToInt()).coerceAtLeast(0)
        val nextTurn = turnGuide.nextTurn(routeProgress.distanceAlongMeters)

        if (metrics.isAutoPaused) {
            Text(
                text = stringResource(R.string.run_auto_paused),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (!hasRoute) {
            Text(
                text = when {
                    metrics.locationError -> stringResource(R.string.run_location_unavailable)
                    metrics.latitude == null -> stringResource(R.string.run_waiting_for_gps)
                    metrics.isTracking -> stringResource(R.string.run_free_tracking)
                    else -> stringResource(R.string.run_free_ready)
                },
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (isOffRoute) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(
                            R.string.run_off_route_distance,
                            formatDistance(routeProgress.distanceFromRouteMeters.roundToInt()),
                        ),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.run_off_route),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            Text(
                text = when {
                    metrics.locationError -> stringResource(R.string.run_location_unavailable)
                    metrics.latitude == null -> stringResource(R.string.run_waiting_for_gps)
                    nextTurn != null -> stringResource(
                        when (nextTurn.direction) {
                            RouteTurnDirection.LEFT -> R.string.run_turn_left
                            RouteTurnDirection.RIGHT -> R.string.run_turn_right
                            RouteTurnDirection.UTURN -> R.string.run_turn_uturn
                        },
                        formatDistance((nextTurn.distanceAlongMeters - routeProgress.distanceAlongMeters).roundToInt()),
                    )
                    progress >= NEAR_FINISH_PROGRESS -> stringResource(R.string.run_near_finish)
                    else -> stringResource(R.string.run_follow_route)
                },
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (hasRoute) {
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(
                    R.string.run_route_progress,
                    formatDistance(completedMeters.roundToInt()),
                    formatDistance(remainingMeters),
                ),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.End),
            )
        }
        if (isFree && metrics.isTracking && currentLocation != null && trail.isNotEmpty()) {
            OutlinedButton(
                onClick = { showReturnDialog = true },
                enabled = !isMakingReturnRoute,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        when {
                            isMakingReturnRoute -> R.string.run_return_route_making
                            hasRoute -> R.string.run_return_route_again
                            else -> R.string.run_return_route
                        }
                    )
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RunMetricCard(
                label = stringResource(R.string.run_metric_distance),
                value = formatDistance(metrics.distanceMeters.roundToInt()),
                modifier = Modifier.weight(1f),
            )
            RunMetricCard(
                label = stringResource(R.string.run_metric_time),
                value = formatDuration(metrics.elapsedSeconds),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RunMetricCard(
                label = stringResource(R.string.run_metric_speed),
                value = String.format(Locale.US, "%.1f km/h", metrics.speedMetersPerSecond * 3.6),
                modifier = Modifier.weight(1f),
            )
            RunMetricCard(
                label = stringResource(R.string.run_metric_pace),
                value = formatPace(metrics.speedMetersPerSecond),
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            stringResource(R.string.run_average_pace, averagePace),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        if (!metrics.isTracking) {
            Button(onClick = ::requestStart, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.run_start))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        sendServiceAction(
                            if (metrics.isPaused) RunTrackingService.ACTION_RESUME
                            else RunTrackingService.ACTION_PAUSE,
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(if (metrics.isPaused) R.string.run_resume else R.string.run_pause))
                }
                Button(
                    onClick = { showFinishConfirmation = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.run_finish))
                }
            }
        }
    }

    if (showReturnDialog) {
        val start = trail.firstOrNull()
        val here = currentLocation
        if (start == null || here == null) {
            showReturnDialog = false
        } else {
            val straight = distanceBetweenMeters(here.latitude to here.longitude, start.latitude to start.longitude)
            ReturnRouteDialog(
                suggestedKm = suggestedReturnKm(straight),
                onDismiss = { showReturnDialog = false },
                onConfirm = { km ->
                    showReturnDialog = false
                    onMakeReturnRoute(
                        RouteCoordinate(here.latitude, here.longitude),
                        RouteCoordinate(start.latitude, start.longitude),
                        (km * 1000).roundToInt(),
                    )
                },
            )
        }
    }

    if (permissionError) {
        AlertDialog(
            onDismissRequest = { permissionError = false },
            title = { Text(stringResource(R.string.run_permission_title)) },
            text = { Text(stringResource(R.string.run_permission_message)) },
            confirmButton = {
                TextButton(onClick = { permissionError = false }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }

    if (showFinishConfirmation) {
        AlertDialog(
            onDismissRequest = { showFinishConfirmation = false },
            title = { Text(stringResource(R.string.run_finish_title)) },
            text = { Text(stringResource(R.string.run_finish_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFinishConfirmation = false
                        val (offRouteCount, offRouteSeconds) = offRouteTotals()
                        sendServiceAction(RunTrackingService.ACTION_STOP)
                        val result = RunResult(
                            distanceMeters = metrics.distanceMeters.roundToInt(),
                            elapsedSeconds = metrics.elapsedSeconds,
                            splitTimes = splitTimes,
                            offRouteCount = offRouteCount,
                            offRouteSeconds = offRouteSeconds,
                            finishedAtMillis = System.currentTimeMillis(),
                            path = if (isFree) trail.map { listOf(it.longitude, it.latitude) } else emptyList(),
                        )
                        onRunFinished(result)
                    },
                ) { Text(stringResource(R.string.run_finish)) }
            },
            dismissButton = {
                TextButton(onClick = { showFinishConfirmation = false }) {
                    Text(stringResource(R.string.run_continue))
                }
            },
        )
    }

}

@Composable
private fun RunMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalNaverMapApi::class)
@Composable
private fun RunningRouteMap(
    points: List<RouteCoordinate>,
    trail: List<TrailPoint>,
    currentLocation: LatLng?,
    headingDegrees: Double?,
    isOffRoute: Boolean,
    progressMeters: Double,
    modifier: Modifier = Modifier,
) {
    val initialPosition = points.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
        ?: LatLng(37.5666, 126.9784)
    val initialHeading = points.getOrNull(1)?.let { next ->
        bearingDegrees(initialPosition.latitude, initialPosition.longitude, next.latitude, next.longitude)
    } ?: 0.0
    val cameraState = rememberCameraPositionState {
        position = CameraPosition(initialPosition, RUN_MAP_ZOOM, RUN_MAP_TILT, initialHeading)
    }
    LaunchedEffect(currentLocation, headingDegrees) {
        currentLocation?.let { location ->
            cameraState.move(
                CameraUpdate.toCameraPosition(
                    CameraPosition(location, RUN_MAP_ZOOM, RUN_MAP_TILT, headingDegrees ?: initialHeading),
                ).animate(CameraAnimation.Easing, CAMERA_FOLLOW_ANIMATION_MS),
            )
        }
    }

    Box(modifier = modifier) {
        NaverMap(cameraPositionState = cameraState, modifier = Modifier.matchParentSize()) {
            if (points.size >= 2) {
                val totalMeters = points.zipWithNext().sumOf { (a, b) ->
                    distanceBetweenMeters(a.latitude to a.longitude, b.latitude to b.longitude)
                }
                val splitAt = progressMeters.coerceIn(0.0, totalMeters)
                sliceRoute(points, 0.0, splitAt).takeIf { it.size >= 2 }?.let { passed ->
                    PathOverlay(
                        coords = passed.map { LatLng(it.latitude, it.longitude) },
                        width = 7.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                    )
                }
                sliceRoute(points, splitAt, totalMeters).takeIf { it.size >= 2 }?.let { remaining ->
                    PathOverlay(
                        coords = remaining.map { LatLng(it.latitude, it.longitude) },
                        width = 7.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                routeDirectionChunks(points, splitAt).forEach { chunk ->
                    ArrowheadPathOverlay(
                        coords = chunk.map { LatLng(it.latitude, it.longitude) },
                        width = 7.dp,
                        headSizeRatio = 2f,
                        color = MaterialTheme.colorScheme.primary,
                        outlineWidth = 0.dp,
                        outlineColor = Color.Transparent,
                    )
                }
                Marker(state = MarkerState(position = initialPosition), captionText = stringResource(R.string.marker_start))
                points.lastOrNull()?.let { last ->
                    Marker(
                        state = MarkerState(position = LatLng(last.latitude, last.longitude)),
                        captionText = stringResource(R.string.marker_end),
                    )
                }
            }
            if (trail.size >= 2) {
                PathOverlay(
                    coords = trail.map { LatLng(it.latitude, it.longitude) },
                    width = 4.dp,
                    color = if (isOffRoute) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                    outlineWidth = 0.dp,
                )
            }
            currentLocation?.let { location ->
                Marker(
                    state = MarkerState(position = location),
                    captionText = stringResource(R.string.run_you),
                    iconTintColor = if (isOffRoute) MaterialTheme.colorScheme.error else Color.Transparent,
                )
            }
        }
        RoundedCornerMask(
            radius = MAP_CORNER_RADIUS,
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
private fun RoundedCornerMask(radius: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cornerRadius = CornerRadius(radius.toPx())
        val mask = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            addRoundRect(RoundRect(Rect(Offset.Zero, size), cornerRadius))
        }
        drawPath(mask, color)
    }
}

internal fun List<RouteCoordinate>.distinctConsecutivePoints(): List<RouteCoordinate> =
    fold(mutableListOf()) { result, point ->
        val previous = result.lastOrNull()
        if (previous == null || distanceBetweenMeters(
                previous.latitude to previous.longitude,
                point.latitude to point.longitude,
            ) > 0.1
        ) result.add(point)
        result
    }

private val MAP_CORNER_RADIUS = 18.dp
private const val RUN_MAP_ZOOM = 18.5
private const val RUN_MAP_TILT = 45.0
private const val CAMERA_FOLLOW_ANIMATION_MS = 800L
private const val NEAR_FINISH_PROGRESS = 0.95f

private fun bearingDegrees(fromLatitude: Double, fromLongitude: Double, toLatitude: Double, toLongitude: Double): Double {
    val lat1 = Math.toRadians(fromLatitude)
    val lat2 = Math.toRadians(toLatitude)
    val deltaLongitude = Math.toRadians(toLongitude - fromLongitude)
    val y = kotlin.math.sin(deltaLongitude) * kotlin.math.cos(lat2)
    val x = kotlin.math.cos(lat1) * kotlin.math.sin(lat2) -
        kotlin.math.sin(lat1) * kotlin.math.cos(lat2) * kotlin.math.cos(deltaLongitude)
    return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360.0) % 360.0
}

fun suggestedReturnKm(straightMeters: Double): Double {
    val km = straightMeters * RETURN_DETOUR_FACTOR / 1000.0
    return (Math.round(km * 2) / 2.0).coerceAtLeast(MIN_RETURN_KM)
}

private const val RETURN_DETOUR_FACTOR = 1.4
private const val MIN_RETURN_KM = 1.0

@Composable
private fun ReturnRouteDialog(suggestedKm: Double, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var input by remember { mutableStateOf(String.format(Locale.US, "%.1f", suggestedKm).removeSuffix(".0")) }
    val km = input.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= MIN_RETURN_KM && it <= 42.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.run_return_route_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.run_return_route_message))
                androidx.compose.material3.OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    suffix = { Text(stringResource(R.string.unit_km)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                    ),
                    isError = km == null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { km?.let(onConfirm) }, enabled = km != null) {
                Text(stringResource(R.string.run_return_route_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
