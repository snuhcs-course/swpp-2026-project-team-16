package com.example.runtime.ui.route

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.compose.CameraPositionState
import com.naver.maps.map.compose.ExperimentalNaverMapApi
import com.naver.maps.map.compose.Marker
import com.naver.maps.map.compose.MarkerState
import com.naver.maps.map.compose.NaverMap
import com.naver.maps.map.compose.PathOverlay
import com.naver.maps.map.compose.rememberCameraPositionState
import com.naver.maps.map.util.MarkerIcons
import java.util.Locale

private val MAP_LANGUAGES = setOf("ko", "en")
private val SEOUL = LatLng(37.5666, 126.9784)
private const val DEFAULT_ZOOM = 13.0
private const val SAME_POINT_METERS = 30.0

@Composable
private fun mapLocale(): Locale? {
    val language = LocalConfiguration.current.locales[0].language
    return if (language in MAP_LANGUAGES) Locale(language) else null
}

@Composable
private fun boundsPadding(): Int = with(LocalDensity.current) { 64.dp.roundToPx() }

@OptIn(ExperimentalNaverMapApi::class)
private fun CameraPositionState.fit(points: List<LatLng>, padding: Int) {
    when {
        points.size >= 2 -> move(CameraUpdate.fitBounds(LatLngBounds.from(points), padding))
        points.size == 1 -> move(CameraUpdate.scrollTo(points.first()))
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

@OptIn(ExperimentalNaverMapApi::class)
@Composable
fun RouteMap(lines: List<GeoJsonLineString>, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val points = remember(lines) {
        lines.flatMap { it.coordinates }
            .filter { it.size >= 2 }
            .map { LatLng(it[1], it[0]) }
    }
    val start = points.firstOrNull()
    val end = points.lastOrNull()?.takeIf { start != null && it.distanceTo(start) > SAME_POINT_METERS }
    val cameraPositionState = rememberCameraPositionState()
    val padding = boundsPadding()

    LaunchedEffect(points) {
        cameraPositionState.fit(points, padding)
    }

    NaverMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        locale = mapLocale(),
        onMapClick = { _, _ -> onClick?.invoke() },
        onMapLoaded = { cameraPositionState.fit(points, padding) }
    ) {
        if (points.size >= 2) {
            PathOverlay(
                coords = points,
                width = 5.dp,
                outlineWidth = 1.dp,
                color = MaterialTheme.colorScheme.primary,
                outlineColor = Color.White
            )
        }
        StartEndMarkers(start, end)
    }
}

@Composable
fun FullScreenRouteMap(lines: List<GeoJsonLineString>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize()) {
            RouteMap(lines = lines, modifier = Modifier.fillMaxSize())
            IconButton(
                onClick = onDismiss,
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clip(CircleShape)
            ) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close))
            }
        }
    }
}

@OptIn(ExperimentalNaverMapApi::class)
@Composable
fun PlacePickerMap(start: Place?, end: Place?, onPick: (LatLng) -> Unit, modifier: Modifier = Modifier) {
    val startPoint = start?.point?.toLatLng()
    val endPoint = end?.point?.toLatLng()
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition(startPoint ?: endPoint ?: SEOUL, DEFAULT_ZOOM)
    }
    val padding = boundsPadding()

    LaunchedEffect(startPoint, endPoint) {
        cameraPositionState.fit(listOfNotNull(startPoint, endPoint), padding)
    }

    NaverMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        locale = mapLocale(),
        onMapClick = { _, latLng -> onPick(latLng) }
    ) {
        StartEndMarkers(startPoint, endPoint)
    }
}

private fun GeoJsonPoint.toLatLng() = LatLng(coordinates[1], coordinates[0])
