package com.example.runtime.ui.route

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.runtime.R
import com.example.runtime.data.remote.GeoJsonLineString
import com.example.runtime.data.remote.GeoJsonPoint
import com.example.runtime.data.remote.Place
import com.example.runtime.ui.common.glassColor
import com.example.runtime.ui.theme.Charcoal
import com.example.runtime.ui.theme.LocalDarkTheme
import com.example.runtime.ui.theme.Lime
import com.example.runtime.ui.theme.Spacing
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.compose.CameraPositionState
import com.naver.maps.map.compose.ExperimentalNaverMapApi
import com.naver.maps.map.compose.MapProperties
import com.naver.maps.map.compose.MapType
import com.naver.maps.map.compose.MapUiSettings
import com.naver.maps.map.compose.Marker
import com.naver.maps.map.compose.MarkerState
import com.naver.maps.map.compose.NaverMap
import com.naver.maps.map.compose.NaverMapConstants
import com.naver.maps.map.compose.PathOverlay
import com.naver.maps.map.compose.rememberCameraPositionState
import com.naver.maps.map.util.MarkerIcons
import java.util.Locale

data class RouteCoordinate(
    val latitude: Double,
    val longitude: Double,
)

data class RouteSlopeSegment(
    val coordinates: List<RouteCoordinate>,
    val slopePercent: Double,
)

private data class ColoredRoutePath(
    val coordinates: List<LatLng>,
    val color: Color,
)

private const val STEEP_UPHILL_THRESHOLD = 5.0
private const val MODERATE_UPHILL_THRESHOLD = 2.5
private const val DOWNHILL_THRESHOLD = -2.5

private val MAP_LANGUAGES = setOf("ko", "en")
private val SEOUL = LatLng(37.5666, 126.9784)
private const val DEFAULT_ZOOM = 13.0
private const val SAME_POINT_METERS = 30.0
private const val PREVIEW_DRAW_MS = 1600
private const val ROUTE_DRAW_MS = 1200

@Composable
private fun mapLocale(): Locale? {
    val language = LocalConfiguration.current.locales[0].language
    return if (language in MAP_LANGUAGES) Locale(language) else null
}

@Composable
private fun mapProperties(): MapProperties =
    if (LocalDarkTheme.current) {
        MapProperties(
            mapType = MapType.Navi,
            isNightModeEnabled = true,
            backgroundColor = NaverMapConstants.DefaultBackgroundColorDark,
            backgroundResource = NaverMapConstants.DefaultBackgroundDrawableDark
        )
    } else {
        MapProperties()
    }

private val MapUi = MapUiSettings(
    isCompassEnabled = false,
    isScaleBarEnabled = false,
    isZoomControlEnabled = false,
    isTiltGesturesEnabled = false
)

@Composable
private fun boundsPadding(): Int = with(LocalDensity.current) { 48.dp.roundToPx() }

@OptIn(ExperimentalNaverMapApi::class)
private fun CameraPositionState.fit(points: List<LatLng>, padding: Int) {
    when {
        points.size >= 2 -> move(CameraUpdate.fitBounds(LatLngBounds.from(points), padding))
        points.size == 1 -> move(CameraUpdate.scrollTo(points.first()))
    }
}

@OptIn(ExperimentalNaverMapApi::class)
private suspend fun CameraPositionState.animateFit(points: List<LatLng>, padding: Int) {
    when {
        points.size >= 2 -> animate(CameraUpdate.fitBounds(LatLngBounds.from(points), padding), durationMs = 500)
        points.size == 1 -> animate(CameraUpdate.scrollTo(points.first()), durationMs = 500)
    }
}

@OptIn(ExperimentalNaverMapApi::class)
@Composable
private fun StartEndMarkers(start: LatLng?, end: LatLng?) {
    start?.let {
        Marker(state = MarkerState(position = it), icon = MarkerIcons.GREEN, captionText = stringResource(R.string.marker_start))
    }
    end?.let {
        Marker(state = MarkerState(position = it), icon = MarkerIcons.RED, captionText = stringResource(R.string.marker_end))
    }
}

private fun List<GeoJsonLineString>.toLatLngs(): List<LatLng> =
    flatMap { it.coordinates }
        .filter { it.size >= 2 }
        .map { LatLng(it[1], it[0]) }

@OptIn(ExperimentalNaverMapApi::class)
@Composable
fun RouteMap(
    lines: List<GeoJsonLineString>,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    slopeSegments: List<RouteSlopeSegment> = emptyList(),
    contentPadding: PaddingValues = PaddingValues(),
    ) {
    val points = remember(lines) { lines.toLatLngs() }
    val coloredPaths = remember(slopeSegments) {
        val paths = mutableListOf<ColoredRoutePath>()

        slopeSegments.forEach { segment ->
            val segmentPoints = segment.coordinates.map { coordinate ->
                LatLng(coordinate.latitude, coordinate.longitude)
            }

            if (segmentPoints.size >= 2) {
                val color = slopeColor(segment.slopePercent)
                val previousPath = paths.lastOrNull()

                if (
                    previousPath != null &&
                    previousPath.color == color &&
                    previousPath.coordinates.last() == segmentPoints.first()
                ) {
                    paths[paths.lastIndex] = previousPath.copy(
                        coordinates = previousPath.coordinates + segmentPoints.drop(1),
                    )
                } else {
                    paths.add(
                        ColoredRoutePath(
                            coordinates = segmentPoints,
                            color = color,
                        ),
                    )
                }
            }
        }

        paths
    }

    val start = points.firstOrNull()
    val end = points.lastOrNull()?.takeIf { start != null && it.distanceTo(start) > SAME_POINT_METERS }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition(start ?: SEOUL, DEFAULT_ZOOM)
    }
    val padding = boundsPadding()

    LaunchedEffect(points) {
        cameraPositionState.animateFit(points, padding)
    }

    NaverMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = mapProperties(),
        uiSettings = MapUi,
        locale = mapLocale(),
        contentPadding = contentPadding,
        onMapClick = { _, _ -> onClick?.invoke() },
        onMapLoaded = { cameraPositionState.fit(points, padding) }
    ) {
        if (coloredPaths.isNotEmpty()) {
            coloredPaths.forEach { path ->
                PathOverlay(
                    coords = path.coordinates,
                    width = 6.dp,
                    outlineWidth = 1.dp,
                    color = path.color,
                    outlineColor = Color.White,
                )
            }
        } else if (points.size >= 2) {
            // 고도 정보가 없거나 불러오지 못한 경우 원래대로
            PathOverlay(
                coords = points,
                width = 6.dp,
                outlineWidth = 1.dp,
                color = Lime,
                outlineColor = Charcoal
            )
        }
        StartEndMarkers(start, end)
    }
}

@OptIn(ExperimentalNaverMapApi::class)
@Composable
fun DrawingRouteMap(
    start: GeoJsonPoint,
    end: GeoJsonPoint?,
    distanceMeters: Int,
    route: List<GeoJsonLineString>?,
    onRouteDrawn: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val startPoint = start.toLatLng()
    val endPoint = end?.toLatLng()
    val preview = remember(start, end, distanceMeters) {
        previewPath(
            startPoint.latitude to startPoint.longitude,
            endPoint?.let { it.latitude to it.longitude },
            distanceMeters
        ).map { LatLng(it.first, it.second) }
    }
    val routePoints = remember(route) { route?.toLatLngs()?.takeIf { it.size >= 2 } }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition(startPoint, DEFAULT_ZOOM)
    }
    val padding = boundsPadding()
    val currentOnRouteDrawn by rememberUpdatedState(onRouteDrawn)

    val previewProgress by rememberInfiniteTransition(label = "preview").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PREVIEW_DRAW_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "previewProgress"
    )
    val routeProgress = remember { Animatable(0f) }

    LaunchedEffect(routePoints) {
        if (routePoints == null) return@LaunchedEffect
        cameraPositionState.animateFit(routePoints, padding)
        routeProgress.animateTo(1f, tween(ROUTE_DRAW_MS, easing = LinearEasing))
        currentOnRouteDrawn()
    }

    NaverMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = mapProperties(),
        uiSettings = MapUi.copy(isScrollGesturesEnabled = false, isZoomGesturesEnabled = false),
        locale = mapLocale(),
        contentPadding = contentPadding,
        onMapLoaded = { cameraPositionState.fit(preview + listOfNotNull(endPoint), padding) }
    ) {
        PathOverlay(
            coords = routePoints ?: preview,
            progress = (if (routePoints != null) routeProgress.value else previewProgress).toDouble(),
            width = 6.dp,
            outlineWidth = 1.dp,
            color = Color.Transparent,
            outlineColor = Color.Transparent,
            passedColor = Lime,
            passedOutlineColor = Charcoal
        )
        StartEndMarkers(startPoint, endPoint)
    }
}

@Composable
fun FullScreenRouteMap(
    lines: List<GeoJsonLineString>,
    onDismiss: () -> Unit,
    slopeSegments: List<RouteSlopeSegment> = emptyList(),
    ) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize()) {
            RouteMap(
                lines = lines,
                modifier = Modifier.fillMaxSize(),
                slopeSegments = slopeSegments,
            )
            IconButton(
                onClick = onDismiss,
                colors = IconButtonDefaults.iconButtonColors(containerColor = glassColor()),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(Spacing.m)
                    .clip(CircleShape)
            ) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close))
            }
        }
    }
}

@OptIn(ExperimentalNaverMapApi::class)
@Composable
fun PlacePickerMap(
    start: Place?,
    end: Place?,
    onPick: (LatLng) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val startPoint = start?.point?.toLatLng()
    val endPoint = end?.point?.toLatLng()
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition(startPoint ?: endPoint ?: SEOUL, DEFAULT_ZOOM)
    }
    val padding = boundsPadding()

    LaunchedEffect(startPoint, endPoint) {
        cameraPositionState.animateFit(listOfNotNull(startPoint, endPoint), padding)
    }

    NaverMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = mapProperties(),
        uiSettings = MapUi,
        locale = mapLocale(),
        contentPadding = contentPadding,
        onMapClick = { _, latLng -> onPick(latLng) }
    ) {
        StartEndMarkers(startPoint, endPoint)
    }
}

private fun GeoJsonPoint.toLatLng() = LatLng(coordinates[1], coordinates[0])

internal val steepUphillColor = Color(0xFFE53935)
internal val moderateUphillColor = Color(0xFFFFC107)
internal val levelRouteColor = Color(0xFF43A047)
internal val downhillColor = Color(0xFF1E88E5)
private fun slopeColor(slopePercent: Double): Color = when {
    slopePercent >= STEEP_UPHILL_THRESHOLD -> steepUphillColor
    slopePercent >= MODERATE_UPHILL_THRESHOLD -> moderateUphillColor
    slopePercent >= DOWNHILL_THRESHOLD -> levelRouteColor
    else -> downhillColor
}
