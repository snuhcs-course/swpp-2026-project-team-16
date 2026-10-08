package com.example.runtime.ui.route

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.data.remote.GeoJsonLineString
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
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
fun RouteMap(lines: List<GeoJsonLineString>, modifier: Modifier = Modifier) {
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
