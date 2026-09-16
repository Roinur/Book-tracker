package com.roinur.booktracker

import androidx.compose.material3.Text

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max

@Composable
internal fun BookGraphDialog(
    book: BookRow,
    sessions: List<BookReadingSessionRow>,
    onDismiss: () -> Unit,
    onPointSelected: (DailyActivityPoint) -> Unit
) {
    var rangeName by rememberSaveable { mutableStateOf(BookStatsRange.MONTH.name) }
    var metricName by rememberSaveable { mutableStateOf(BookGraphMetric.PAGES.name) }
    val range = runCatching { BookStatsRange.valueOf(rangeName) }.getOrDefault(BookStatsRange.MONTH)
    val metric = runCatching { BookGraphMetric.valueOf(metricName) }.getOrDefault(BookGraphMetric.PAGES)
    val points = remember(sessions, metric) {
        bookDailyActivity(sessions, pages = metric == BookGraphMetric.PAGES)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.82f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(14.dp)
                .animateContentSize(animationSpec = tween(220, easing = FastOutSlowInEasing)),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Graph", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(book.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                items(BookStatsRange.entries, key = { it.name }) { option ->
                    FilterChip(selected = range == option, onClick = { rangeName = option.name }, label = { Text(option.label) })
                }
            }
            BookLineChart(
                title = if (metric == BookGraphMetric.PAGES) "Pages read" else "Minutes read",
                points = points,
                range = range,
                metric = metric,
                onPointSelected = onPointSelected,
                metricSelector = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { metricName = BookGraphMetric.PAGES.name }) { Text("Pages", color = if (metric == BookGraphMetric.PAGES) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                        TextButton(onClick = { metricName = BookGraphMetric.TIME.name }) { Text("Time", color = if (metric == BookGraphMetric.TIME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            )
            Text(
                "${sessions.size} ${if (sessions.size == 1) "session" else "sessions"} - ${bookFormatDuration(sessions.sumOf { it.durationSeconds })} - ${sessions.sumOf { it.pagesRead }} pages",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
