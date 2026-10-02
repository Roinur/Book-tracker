package com.roinur.booktracker

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlin.math.min

internal data class ReadingMapCamera(val zoom: Float, val pan: Offset)

internal fun readingMapFocusCamera(
    graph: BookReadingMap, viewport: IntSize, points: List<Offset>, density: Float
): ReadingMapCamera {
    if (viewport.width == 0 || viewport.height == 0 || points.isEmpty()) return ReadingMapCamera(1f, Offset.Zero)
    val fit = min(viewport.width / (graph.maxX - graph.minX), viewport.height / (graph.maxY - graph.minY)) * 0.92f
    // Keep the focused cluster between the controls and the selection preview.
    val top = min(104f * density, viewport.height * 0.22f)
    val bottom = min(190f * density, viewport.height * 0.34f)
    val padding = 44f * density
    val left = points.minOf { it.x }; val right = points.maxOf { it.x }
    val upper = points.minOf { it.y }; val lower = points.maxOf { it.y }
    val zoom = min(
        (viewport.width - padding * 2).coerceAtLeast(1f) / ((right - left).coerceAtLeast(100f) * fit),
        (viewport.height - top - bottom - padding * 2).coerceAtLeast(1f) / ((lower - upper).coerceAtLeast(100f) * fit)
    ).coerceIn(0.65f, 4f)
    val target = Offset(viewport.width / 2f, (top + viewport.height - bottom) / 2f)
    val center = Offset((left + right) / 2f, (upper + lower) / 2f)
    val graphCenter = Offset((graph.minX + graph.maxX) / 2f, (graph.minY + graph.maxY) / 2f)
    return ReadingMapCamera(zoom, target - Offset(viewport.width / 2f, viewport.height / 2f) - (center - graphCenter) * fit * zoom)
}

/** Label alternatives are checked in priority order; crowded labels wait for zooming in. */
internal fun readingMapLabelBounds(
    center: Offset, radius: Float, width: Float, height: Float, gap: Float,
    viewport: Rect, occupied: List<Rect>
): Rect? {
    val candidates = listOf(
        Offset(center.x - width / 2, center.y + radius + gap),
        Offset(center.x - width / 2, center.y - radius - gap - height),
        Offset(center.x + radius + gap, center.y - height / 2),
        Offset(center.x - radius - gap - width, center.y - height / 2)
    )
    return candidates.map { origin ->
        val x = origin.x.coerceIn(viewport.left, (viewport.right - width).coerceAtLeast(viewport.left))
        Rect(Offset(x, origin.y), androidx.compose.ui.geometry.Size(width, height))
    }.firstOrNull { rect ->
        rect.left >= viewport.left && rect.right <= viewport.right &&
            rect.top >= viewport.top && rect.bottom <= viewport.bottom && occupied.none { it.overlaps(rect) }
    }
}
