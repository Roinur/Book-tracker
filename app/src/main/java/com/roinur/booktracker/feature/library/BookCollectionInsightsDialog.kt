package com.roinur.booktracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
internal fun BookCollectionInsightsDialog(group: CollectionProgress,
    sessions: List<BookReadingSessionWithBook>, scrollState: ScrollState, onOpenBook: (Int) -> Unit,
    onOpenCollection: () -> Unit, onDismiss: () -> Unit) {
    val insight = remember(group, sessions) { CollectionInsights(group, sessions.map { it.session }) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    val cover = group.books.firstOrNull { it.status == BookStatus.READING } ?: group.books.firstOrNull()
    fun decimal(value: Double?, suffix: String, places: Int = 1) =
        value?.let { String.format(Locale.getDefault(), "%.${places}f", it) + suffix } ?: "—"
    fun time(seconds: Long?) = seconds?.let { if (it in 1..59) "<1m" else bookFormatCompactHours(it) } ?: "—"
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(max = maxHeight * 0.92f)
                .clip(shape).background(colors.surfaceContainerLow).border(1.dp, colors.outlineVariant, shape)
                .padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (cover != null) BookCoverImage(cover.coverUrl, cover.title,
                        Modifier.size(42.dp, 62.dp).clip(RoundedCornerShape(4.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(group.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${group.type.label} · Insights", style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant)
                    }
                    TextButton(onClick = onDismiss, shape = shape, contentPadding = PaddingValues(6.dp)) { Text("Close") }
                }
                HorizontalDivider(color = colors.outlineVariant)
                Column(Modifier.weight(1f, fill = false).verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(painterResource(R.drawable.ic_clock_24), null, Modifier.size(22.dp), colors.primary)
                            Text("Time to finish", style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
                        }
                        Text(when {
                            group.books.isEmpty() -> "No books yet"
                            insight.unfinishedBooks.isEmpty() -> "Collection finished"
                            insight.remainingPages == 0L && insight.missingRemainingCounts == 0 -> "No unread pages"
                            insight.remainingPages == 0L -> "Page counts needed"
                            insight.remainingSeconds != null -> "~${time(insight.remainingSeconds)}"
                            else -> "Not enough timed reading"
                        }, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold, color = colors.primary)
                        if (insight.unfinishedBooks.isNotEmpty()) Text(
                            "${insight.remainingPages} ${if (insight.remainingPages == 1L) "page" else "pages"} · ${insight.unfinishedBooks.size} ${if (insight.unfinishedBooks.size == 1) "book" else "books"} left" +
                                if (insight.estimatedSessions != null && insight.remainingPages > 0) " · ~${insight.estimatedSessions} sessions" else "",
                            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        if (insight.remainingPages > 0 && insight.estimatePagesPerHour != null) Text(
                            if (insight.usesOverallPace) "Estimate uses your overall pace: ${decimal(insight.estimatePagesPerHour, " pages/h")}."
                            else "Based on ${insight.timedSessionCount} timed ${if (insight.timedSessionCount == 1) "session" else "sessions"} in this collection.",
                            style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        if (insight.missingRemainingCounts > 0) Text(
                            "Estimate excludes ${insight.missingRemainingCounts} ${if (insight.missingRemainingCounts == 1) "book" else "books"} without page counts.",
                            style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CollectionInsightMetric("Read time", time(insight.totalReadingSeconds), Modifier.weight(1f))
                        CollectionInsightMetric("Read pages", "${insight.totalReadPages}", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CollectionInsightMetric("Collection pace", decimal(insight.pagesPerHour, " pages/h"), Modifier.weight(1f))
                        CollectionInsightMetric("Minutes per page", decimal(insight.minutesPerPage, "", 2), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CollectionInsightMetric("Average session", time(insight.averageSessionSeconds), Modifier.weight(1f))
                        CollectionInsightMetric("Pages per session", decimal(insight.averageSessionPages, ""), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CollectionInsightMetric("Books finished", "${group.finished} / ${group.books.size}", Modifier.weight(1f))
                        CollectionInsightMetric("Average rating", decimal(insight.averageRating, " / 5"), Modifier.weight(1f),
                            detail = "${insight.ratedBooks.size} rated")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CollectionInsightMetric("Average book length", insight.averageBookPages?.let { "${it.roundToInt()} pages" } ?: "—", Modifier.weight(1f))
                        CollectionInsightMetric("Logged sessions", "${insight.loggedSessionCount}", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CollectionInsightMetric("Reading days", "${insight.daysRead}", Modifier.weight(1f))
                        CollectionInsightMetric("Last read", insight.lastRead?.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())) ?: "—", Modifier.weight(1f))
                    }
                    insight.largestBook?.let { largest ->
                        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
                        CollectionInsightMetric("Longest book", largest.title, Modifier.fillMaxWidth(), "${largest.pageCount} pages")
                    }
                    if (insight.nextToFinish.isNotEmpty()) {
                        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
                        Text("Closest to finishing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        insight.nextToFinish.forEach { book ->
                            val left = book.pageCount - book.currentPage.coerceAtLeast(0)
                            val seconds = insight.estimatePagesPerHour?.let { ceil(left * 3600.0 / it).toLong() }
                            Row(Modifier.fillMaxWidth().fittedClickable(shape) { onOpenBook(book.id) }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                BookCoverImage(book.coverUrl, book.title, Modifier.size(34.dp, 48.dp).clip(RoundedCornerShape(4.dp)))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(book.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("$left ${if (left == 1) "page" else "pages"} left" + (seconds?.let { " · ~${time(it)}" } ?: ""),
                                        style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                                }
                                Icon(painterResource(R.drawable.ic_chevron_right_24), null, Modifier.size(20.dp), colors.primary)
                            }
                        }
                    }
                }
                OutlinedButton(onClick = onOpenCollection, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = shape) {
                    Icon(painterResource(R.drawable.ic_collections_24), null, Modifier.size(20.dp), tint = colors.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("View books")
                }
            }
        }
    }
}

@Composable
private fun CollectionInsightMetric(label: String, value: String, modifier: Modifier, detail: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (detail != null) Text(detail, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
