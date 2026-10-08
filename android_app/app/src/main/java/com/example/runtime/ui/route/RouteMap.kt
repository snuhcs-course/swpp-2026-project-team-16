package com.example.runtime.ui.route

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.runtime.data.remote.GeoJsonLineString
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.compose.ExperimentalNaverMapApi
import com.naver.maps.map.compose.Marker
import com.naver.maps.map.compose.MarkerState
import com.naver.maps.map.compose.NaverMap
import com.naver.maps.map.compose.PathOverlay
import com.naver.maps.map.compose.rememberCameraPositionState
import java.util.Locale

private val MAP_LANGUAGES = setOf("ko", "en")

@OptIn(ExperimentalNaverMapApi::class)
@Composable
fun RouteMap(lines: List<GeoJsonLineString>, modifier: Modifier = Modifier) {
    val points = remember(lines) {
        lines.flatMap { it.coordinates }
            .filter { it.size >= 2 }
            .map { LatLng(it[1], it[0]) }
    }
    val cameraPositionState = rememberCameraPositionState()
    val padding = with(LocalDensity.current) { 32.dp.roundToPx() }
    val language = LocalConfiguration.current.locales[0].language
    val locale = if (language in MAP_LANGUAGES) Locale(language) else null

    NaverMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        locale = locale,
        onMapLoaded = {
            if (points.size >= 2) {
                cameraPositionState.move(CameraUpdate.fitBounds(LatLngBounds.from(points), padding))
            }
        }
    ) {
        if (points.size >= 2) {
            PathOverlay(
                coords = points,
                width = 5.dp,
                outlineWidth = 1.dp,
                color = MaterialTheme.colorScheme.primary,
                outlineColor = Color.White
            )
            Marker(state = MarkerState(position = points.first()))
        }
    }
}
