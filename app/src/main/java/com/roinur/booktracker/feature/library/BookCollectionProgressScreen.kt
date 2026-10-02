package com.roinur.booktracker

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun BookCollectionProgressScreen(books: List<BookRow>, names: List<String>,
    types: Map<String, BookCollectionType>, sessions: List<BookReadingSessionWithBook>,
    onOpenBook: (Int) -> Unit, onOpenBooks: (String, BookLibraryFilter) -> Unit) {
    var typeName by rememberSaveable { mutableStateOf("") }
    var insightName by rememberSaveable { mutableStateOf<String?>(null) }
    val insightScrollState = rememberSaveable(insightName, saver = ScrollState.Saver) { ScrollState(0) }
    val all = remember(books, names, types) { collectionProgress(books, names, types) }
    val type = BookCollectionType.entries.firstOrNull { it.name == typeName }
    val visible = remember(all, type) { all.filter { type == null || it.type == type } }
    val uniqueBooks = remember(visible) { visible.flatMap { it.books }.distinctBy { it.id } }
    val finished = uniqueBooks.count { it.status == BookStatus.FINISHED }
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val statusColors = mapOf(
        BookStatus.WISHLIST to colors.onSurfaceVariant.copy(alpha = 0.55f),
        BookStatus.READING to colors.primary,
        BookStatus.PAUSED to if (dark) Color(0xFFFFC36D) else Color(0xFF9B5D07),
        BookStatus.FINISHED to if (dark) Color(0xFF78D6A3) else Color(0xFF24764B)
    )
    val shape = RoundedCornerShape(8.dp)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("$finished", style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold, color = colors.primary)
                    Text("/ ${uniqueBooks.size}", Modifier.padding(bottom = 4.dp),
                        style = MaterialTheme.typography.titleLarge, color = colors.onSurfaceVariant)
                }
                Text("Books finished", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${visible.size}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(if (visible.size == 1) "Collection" else "Collections",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        // A short rule makes the summary part of the page, rather than another dashboard card.
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(null, BookCollectionType.THEME, BookCollectionType.TYPE, BookCollectionType.PUBLISHER,
                BookCollectionType.OTHER).forEach { option ->
                val label = option?.label ?: "All"
                val selected = type == option
                Box(Modifier.weight((label.length + 3).toFloat()).heightIn(min = 40.dp)
                    .clip(shape)
                    .background(if (selected) colors.primaryContainer else Color.Transparent)
                    .border(1.dp, if (selected) colors.primary else colors.outline, shape)
                    .fittedClickable(shape) { typeName = option?.name ?: "" }
                    .padding(horizontal = 4.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                        color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BookStatus.entries.forEach { status ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(statusColors.getValue(status)))
                    Text(status.label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
            }
        }
        Text("Tap a collection for insights", style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (visible.isEmpty()) item {
                Text("No collections yet", Modifier.padding(vertical = 32.dp),
                    style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            }
            items(visible, key = { it.name.lowercase(java.util.Locale.ROOT) }) { group ->
                CollectionProgressRow(group, statusColors) { insightName = group.name }
            }
        }
    }
    all.firstOrNull { it.name == insightName }?.let { group ->
        BookCollectionInsightsDialog(group, sessions, insightScrollState, onOpenBook,
            onOpenCollection = { insightName = null; onOpenBooks(group.name, BookLibraryFilter.ALL) },
            onDismiss = { insightName = null })
    }
}

@Composable
private fun CollectionProgressRow(group: CollectionProgress, statusColors: Map<BookStatus, Color>,
                                  onInsights: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    val cover = group.books.firstOrNull { it.status == BookStatus.READING } ?: group.books.firstOrNull()
    val progress by animateFloatAsState(group.pageFraction, tween(300), label = "collectionPages")
    Column(Modifier.fillMaxWidth().fittedCombinedClickable(shape,
        onClick = onInsights, onLongClick = onInsights)
        .padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()
            .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (cover != null) BookCoverImage(cover.coverUrl, cover.title, Modifier.size(36.dp, 52.dp).clip(RoundedCornerShape(4.dp)))
            else Icon(painterResource(R.drawable.ic_collections_24), null, Modifier.size(36.dp), colors.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(group.type.label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${group.finished} / ${group.books.size}", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                Text("finished", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.ic_chevron_right_24), null, Modifier.size(18.dp), colors.primary)
        }
        Row(Modifier.fillMaxWidth().height(36.dp).clip(shape), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (group.books.isEmpty()) Box(Modifier.fillMaxSize().background(colors.outlineVariant.copy(alpha = 0.35f)))
            BookStatus.entries.forEach { status ->
                val count = group.counts.getValue(status)
                if (count > 0) {
                    Box(Modifier.weight(count.toFloat()).fillMaxHeight()
                        .semantics { contentDescription = "${group.name}: $count ${status.label}" }
                        .fittedCombinedClickable(RoundedCornerShape(4.dp),
                            onClick = onInsights, onLongClick = onInsights),
                        contentAlignment = Alignment.Center) {
                        BoxWithConstraints(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(4.dp))
                            .background(statusColors.getValue(status).copy(alpha = 0.22f))) {
                            if (maxWidth >= 26.dp) Text("$count", Modifier.align(Alignment.Center),
                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                                color = if (status == BookStatus.WISHLIST) colors.onSurfaceVariant else statusColors.getValue(status))
                        }
                    }
                }
            }
        }
        if (group.totalPages > 0L) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${group.readPages} / ${group.totalPages} pages", Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${(group.pageFraction * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = colors.primary)
            }
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(colors.outlineVariant.copy(alpha = 0.3f))) {
                if (progress > 0f) Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(colors.primary))
            }
        }
        if (group.unknownPageCounts > 0) Text("${group.unknownPageCounts} ${if (group.unknownPageCounts == 1) "book without a page count" else "books without page counts"}",
            style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(top = 12.dp), color = colors.outlineVariant.copy(alpha = 0.45f))
    }
}
