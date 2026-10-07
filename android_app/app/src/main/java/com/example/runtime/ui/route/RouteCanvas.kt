package com.example.runtime.ui.route

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.runtime.data.remote.GeoJsonLineString
import kotlin.math.cos
import kotlin.math.min

@Composable
fun RouteCanvas(lines: List<GeoJsonLineString>, modifier: Modifier = Modifier) {
    val points = lines.flatMap { it.coordinates }.filter { it.size >= 2 }
    val lineColor = MaterialTheme.colorScheme.primary
    val startColor = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas

        val minLon = points.minOf { it[0] }
        val maxLon = points.maxOf { it[0] }
        val minLat = points.minOf { it[1] }
        val maxLat = points.maxOf { it[1] }
        val lonScale = cos(Math.toRadians((minLat + maxLat) / 2))
        val spanX = ((maxLon - minLon) * lonScale).coerceAtLeast(1e-9)
        val spanY = (maxLat - minLat).coerceAtLeast(1e-9)

        val padding = 16.dp.toPx()
        val scale = min((size.width - 2 * padding) / spanX, (size.height - 2 * padding) / spanY)
        val offsetX = (size.width - spanX * scale) / 2
        val offsetY = (size.height - spanY * scale) / 2

        fun toOffset(coordinate: List<Double>) = Offset(
            x = (offsetX + (coordinate[0] - minLon) * lonScale * scale).toFloat(),
            y = (offsetY + (maxLat - coordinate[1]) * scale).toFloat(),
        )

        lines.forEach { line ->
            val path = Path()
            line.coordinates.filter { it.size >= 2 }.forEachIndexed { index, coordinate ->
                val point = toOffset(coordinate)
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        drawCircle(color = startColor, radius = 6.dp.toPx(), center = toOffset(points.first()))
    }
}
