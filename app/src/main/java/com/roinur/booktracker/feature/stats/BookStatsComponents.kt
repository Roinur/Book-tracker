package com.roinur.booktracker

import androidx.compose.material3.Text

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun BookStatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String = ""
) {
    Column(
        modifier = modifier
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (supporting.isNotBlank()) {
            Text(supporting, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun BookStatCell(
    icon: String,
    label: String,
    value: String,
    supporting: String = "",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        BookMiniIcon(
            kind = icon,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (supporting.isNotBlank()) {
            Text(supporting, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Text(" ", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun BookVerticalDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(1.dp)
            .height(66.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    )
}

@Composable
internal fun BookLineChart(
    title: String,
    points: List<DailyActivityPoint>,
    range: BookStatsRange,
    metric: BookGraphMetric,
    onPointSelected: (DailyActivityPoint) -> Unit,
    metricSelector: @Composable () -> Unit,
    anchorDate: LocalDate = LocalDate.now(),
    modifier: Modifier = Modifier
) {
    val visiblePoints = remember(points, range, anchorDate) { bookChartPoints(points, range, anchorDate) }
    var selectedPoint by remember(points, range, metric) { mutableStateOf<DailyActivityPoint?>(null) }
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxValue = visiblePoints.maxOfOrNull { it.pagesRead }?.coerceAtLeast(1) ?: 1

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                selectedPoint?.let { point ->
                    val unit = if (metric == BookGraphMetric.TIME) "minutes" else "pages"
                    Text(
                        "${point.date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))}: ${point.pagesRead} $unit",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            metricSelector()
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(visiblePoints, range, metric) {
                    detectTapGestures { offset ->
                        if (visiblePoints.isEmpty()) return@detectTapGestures
                        val left = 44f
                        val right = size.width - 28f
                        val width = (right - left).coerceAtLeast(1f)
                        val step = if (visiblePoints.size <= 1) width else width / (visiblePoints.size - 1).toFloat()
                        val index = ((offset.x - left) / step).roundToInt().coerceIn(0, visiblePoints.lastIndex)
                        selectedPoint = visiblePoints[index]
                        onPointSelected(visiblePoints[index])
                    }
                }
        ) {
            val left = 44f
            val right = size.width - 28f
            val top = 8f
            val bottom = size.height - 34f
            val width = (right - left).coerceAtLeast(1f)
            val height = (bottom - top).coerceAtLeast(1f)
            repeat(4) { index ->
                val y = top + height * index / 3f
                drawLine(grid, Offset(left, y), Offset(right, y), strokeWidth = 1f)
                val label = ((maxValue * (3 - index)) / 3f).roundToInt().toString()
                drawContext.canvas.nativeCanvas.drawText(label, 0f, y + 5f, android.graphics.Paint().apply {
                    color = labelColor.toArgb()
                    textSize = 24f
                    isAntiAlias = true
                })
            }
            val step = if (visiblePoints.size <= 1) width else width / (visiblePoints.size - 1).toFloat()
            val offsets = visiblePoints.mapIndexed { index, point ->
                val x = left + step * index
                val y = bottom - (point.pagesRead.toFloat() / maxValue.toFloat()) * height
                Offset(x, y)
            }
            val fillPath = Path().apply {
                offsets.firstOrNull()?.let { first ->
                    moveTo(first.x, bottom)
                    offsets.forEach { lineTo(it.x, it.y) }
                    lineTo(offsets.last().x, bottom)
                    close()
                }
            }
            drawPath(fillPath, primary.copy(alpha = 0.20f))
            offsets.zipWithNext().forEach { (a, b) ->
                drawLine(primary, a, b, strokeWidth = 5f, cap = StrokeCap.Round)
            }
            offsets.forEachIndexed { index, offset ->
                if (selectedPoint?.date == visiblePoints[index].date) {
                    drawCircle(primary.copy(alpha = 0.24f), radius = 16f, center = offset)
                    drawCircle(primary, radius = 9f, center = offset)
                } else {
                    drawCircle(primary, radius = 7f, center = offset)
                }
            }
            visiblePoints.forEachIndexed { index, point ->
                val labelStride = when (range) {
                    BookStatsRange.WEEK -> 1
                    BookStatsRange.MONTH -> 5
                    BookStatsRange.YEAR -> 2
                    BookStatsRange.ALL_TIME -> (visiblePoints.size / 5).coerceAtLeast(1)
                }
                if (index % labelStride != 0 && index != visiblePoints.lastIndex) return@forEachIndexed
                val x = left + step * index
                val label = point.date.format(
                    DateTimeFormatter.ofPattern(
                        if (range == BookStatsRange.YEAR) "MMM" else "MMM d",
                        Locale.US
                    )
                )
                val paint = android.graphics.Paint().apply {
                    color = labelColor.toArgb()
                    textSize = 23f
                    isAntiAlias = true
                }
                val labelWidth = paint.measureText(label)
                val labelX = (x - labelWidth / 2f).coerceIn(0f, size.width - labelWidth)
                drawContext.canvas.nativeCanvas.drawText(label, labelX, size.height - 4f, paint)
            }
        }
    }
}
