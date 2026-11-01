package com.example.runtime.ui.route

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runtime.R
import com.example.runtime.data.remote.GeoJsonLineString
import com.example.runtime.data.run.RunTrackingService
import com.example.runtime.data.run.RunTrackingState
import com.example.runtime.ui.common.formatDistance
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.compose.ExperimentalNaverMapApi
import com.naver.maps.map.compose.Marker
import com.naver.maps.map.compose.MarkerState
import com.naver.maps.map.compose.NaverMap
import com.naver.maps.map.compose.PathOverlay
import com.naver.maps.map.compose.rememberCameraPositionState
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RunNavigationScreen(
    lines: List<GeoJsonLineString>,
    routeDistanceMeters: Int,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val metrics by RunTrackingState.metrics.collectAsState()
    val routePoints = remember(lines) {
        lines.flatMap { line ->
            line.coordinates.mapNotNull { coordinate ->
                if (coordinate.size >= 2) RouteCoordinate(coordinate[1], coordinate[0]) else null
            }
        }.distinctConsecutivePoints()
    }
    val progressMatcher = remember(routePoints) { RouteProgressMatcher(routePoints) }
    val turnGuide = remember(routePoints) { RouteTurnGuide(routePoints) }
    var routeProgress by remember { mutableStateOf(RouteProgress(0.0, Double.POSITIVE_INFINITY, progressMatcher.routeLengthMeters)) }
    var showFinishConfirmation by remember { mutableStateOf(false) }
    var showRunSummary by remember { mutableStateOf(false) }
    var permissionError by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            permissionError = false
            context.startForegroundService(
                Intent(context, RunTrackingService::class.java).setAction(RunTrackingService.ACTION_START),
            )
        } else {
            permissionError = true
        }
    }

    LaunchedEffect(metrics.latitude, metrics.longitude) {
        val latitude = metrics.latitude
        val longitude = metrics.longitude
        if (latitude != null && longitude != null) {
            routeProgress = progressMatcher.update(latitude, longitude)
        }
    }

    fun requestStart() {
        val requestedPermissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
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

    BackHandler {
        if (showRunSummary || !metrics.isTracking) onFinish()
        else showFinishConfirmation = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RunningRouteMap(
            points = routePoints,
            currentLocation = currentLocation,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        val totalRouteMeters = routeDistanceMeters.toDouble().coerceAtLeast(1.0)
        val completedMeters = (routeProgress.distanceAlongMeters /
            routeProgress.routeLengthMeters.coerceAtLeast(1.0) * totalRouteMeters)
            .coerceIn(0.0, totalRouteMeters)
        val progress = (completedMeters / totalRouteMeters).toFloat()
        val remainingMeters = (routeDistanceMeters - completedMeters.roundToInt()).coerceAtLeast(0)
        val nextTurn = turnGuide.nextTurn(routeProgress.distanceAlongMeters)

        Text(
            text = when {
                metrics.locationError -> stringResource(R.string.run_location_unavailable)
                metrics.latitude == null -> stringResource(R.string.run_waiting_for_gps)
                routeProgress.distanceFromRouteMeters > OFF_ROUTE_DISTANCE_METERS ->
                    stringResource(R.string.run_off_route)
                nextTurn != null -> stringResource(
                    if (nextTurn.direction == RouteTurnDirection.LEFT) R.string.run_turn_left
                    else R.string.run_turn_right,
                    formatDistance((nextTurn.distanceAlongMeters - routeProgress.distanceAlongMeters).roundToInt()),
                )
                progress >= NEAR_FINISH_PROGRESS -> stringResource(R.string.run_near_finish)
                else -> stringResource(R.string.run_follow_route)
            },
            color = if (
                metrics.latitude != null &&
                routeProgress.distanceFromRouteMeters > OFF_ROUTE_DISTANCE_METERS
            ) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

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
                        sendServiceAction(RunTrackingService.ACTION_STOP)
                        showRunSummary = true
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

    if (showRunSummary) {
        AlertDialog(
            onDismissRequest = { onFinish() },
            title = { Text(stringResource(R.string.run_summary_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.run_summary_distance, formatDistance(metrics.distanceMeters.roundToInt())))
                    Text(stringResource(R.string.run_summary_time, formatDuration(metrics.elapsedSeconds)))
                    Text(stringResource(R.string.run_average_pace, averagePace))
                }
            },
            confirmButton = {
                TextButton(onClick = onFinish) { Text(stringResource(R.string.action_done)) }
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
    currentLocation: LatLng?,
    modifier: Modifier = Modifier,
) {
    val initialPosition = points.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
        ?: LatLng(37.5666, 126.9784)
    val cameraState = rememberCameraPositionState {
        position = CameraPosition(initialPosition, 16.0)
    }
    LaunchedEffect(currentLocation) {
        currentLocation?.let { cameraState.move(CameraUpdate.scrollTo(it)) }
    }

    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), tonalElevation = 2.dp) {
        Box {
            NaverMap(cameraPositionState = cameraState) {
                if (points.size >= 2) {
                    PathOverlay(
                        coords = points.map { LatLng(it.latitude, it.longitude) },
                        width = 7.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Marker(state = MarkerState(position = initialPosition), captionText = stringResource(R.string.marker_start))
                    points.lastOrNull()?.let { last ->
                        Marker(
                            state = MarkerState(position = LatLng(last.latitude, last.longitude)),
                            captionText = stringResource(R.string.marker_end),
                        )
                    }
                }
                currentLocation?.let { location ->
                    Marker(
                        state = MarkerState(position = location),
                        captionText = stringResource(R.string.run_you),
                    )
                }
            }
        }
    }
}

private fun List<RouteCoordinate>.distinctConsecutivePoints(): List<RouteCoordinate> =
    fold(mutableListOf()) { result, point ->
        val previous = result.lastOrNull()
        if (previous == null || distanceBetweenMeters(
                previous.latitude to previous.longitude,
                point.latitude to point.longitude,
            ) > 0.1
        ) result.add(point)
        result
    }

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    val remainder = seconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainder)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, remainder)
    }
}

private fun formatPace(speedMetersPerSecond: Double): String {
    if (speedMetersPerSecond < 0.3) return "--:--"
    val secondsPerKm = (1000.0 / speedMetersPerSecond).roundToInt().coerceAtMost(3599)
    return String.format(Locale.US, "%d:%02d /km", secondsPerKm / 60, secondsPerKm % 60)
}

private const val OFF_ROUTE_DISTANCE_METERS = 40.0
private const val NEAR_FINISH_PROGRESS = 0.95f
