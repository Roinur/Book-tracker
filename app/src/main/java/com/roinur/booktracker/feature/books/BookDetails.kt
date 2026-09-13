package com.roinur.booktracker

import androidx.compose.material3.Text

import android.app.DatePickerDialog
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

@Composable
internal fun BookDetailCard(
    onCompletionDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    book: BookRow,
    active: Boolean,
    activeStartedAtMs: Long,
    activePausedAtMs: Long,
    activePausedTotalMs: Long,
    nowMs: Long,
    notes: List<BookNote>,
    sessions: List<BookReadingSessionRow>,
    onStart: () -> Unit,
    onStatusChange: (BookStatus) -> Unit,
    onRating: (Int) -> Unit,
    onRatingPreview: (Int) -> Unit,
    onRatingCancel: () -> Unit,
    onPickCover: () -> Unit,
    onAuthorSelected: (String) -> Unit,
    onEdit: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenSessions: () -> Unit,
    onAddManual: () -> Unit,
    onOpenGraph: () -> Unit
) {
    val liveSeconds = if (active) {
        bookActiveElapsedSeconds(activeStartedAtMs, nowMs, activePausedAtMs, activePausedTotalMs)
    } else {
        0L
    }
    val totalSeconds = book.readingSeconds + liveSeconds
    val sessionPages = sessions.sumOf { it.pagesRead.coerceAtLeast(0) }
    val bookPagesPerHour = if (totalSeconds > 0L) {
        ((if (sessionPages > 0) sessionPages else book.currentPage.coerceAtLeast(0)).toFloat() * 3600f) / totalSeconds.toFloat()
    } else {
        0f
    }
    val bookCollections = remember(book.collections) { splitBookCollections(book.collections) }
    var showCollectionsDialog by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(220, easing = FastOutSlowInEasing)),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        BookPanel(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.96f),
            contentPadding = PaddingValues(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .width(112.dp)
                        .height(166.dp)
                        .fittedClickable(RoundedCornerShape(8.dp), onClick = onPickCover)
                ) {
                    BookCover(book = book, modifier = Modifier.fillMaxSize())
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(166.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            book.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(book.authors.ifBlank { "Unknown author" }, modifier = Modifier.fittedClickable(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)) { if (book.authors.isNotBlank()) onAuthorSelected(book.authors.trim()) }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (book.pageCount > 0) "${book.currentPage.coerceAtMost(book.pageCount)} / ${book.pageCount} pages" else "${book.currentPage} pages",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            Text(
                                text = bookFormatDuration(totalSeconds),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        LinearProgressIndicator(
                            progress = { book.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(99.dp))
                        )
                    }
                    Button(
                        onClick = onStart,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        if (active) {
                            Text("Resume · p.${book.currentPage} · ${bookFormatDuration(liveSeconds)}")
                        } else {
                            BookButtonLabel(R.drawable.ic_book_24, "Continue reading")
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Reading status", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (book.status == BookStatus.FINISHED) {
                    val pickerColors = MaterialTheme.colorScheme
    val context = LocalContext.current
                    val date = runCatching { Instant.parse(book.finishedAt).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrDefault(LocalDate.now())
                    Text("Finished ${date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))}",
                        modifier = Modifier.fittedClickable { DatePickerDialog(bookPickerContext(context, pickerColors), { _, y, m, d -> onCompletionDate(LocalDate.of(y, m + 1, d)) }, date.year, date.monthValue - 1, date.dayOfMonth).showBookStyle(pickerColors) }.padding(4.dp),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.fillMaxWidth()) {
                BookStatus.entries.forEachIndexed { index, status ->
                    val selected = book.status == status
                    val shape = when (index) {
                        0 -> RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)
                        BookStatus.entries.lastIndex -> RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)
                        else -> RoundedCornerShape(0.dp)
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, shape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                            .fittedClickable(shape, enabled = !active) { onStatusChange(status) }
                    ) {
                        Text(
                            status.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            BookPanel(
                modifier = Modifier.weight(1f).height(120.dp),
                contentPadding = PaddingValues(9.dp),
                contentSpacing = 0.dp
            ) {
                Spacer(Modifier.weight(1f))
                BookMetricLabel(R.drawable.ic_book_24, "Pages read")
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (book.pageCount > 0) "${book.currentPage} / ${book.pageCount}" else "${book.currentPage}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                LinearProgressIndicator(
                    progress = { book.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(99.dp))
                )
                Spacer(Modifier.weight(1f))
            }
            BookPanel(
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp),
                contentPadding = PaddingValues(9.dp),
                contentSpacing = 0.dp
            ) {
                Spacer(Modifier.weight(1f))
                BookMetricLabel(R.drawable.ic_clock_24, "Read time")
                Spacer(Modifier.weight(1f))
                Text(
                    text = bookFormatCompactHours(totalSeconds),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (bookPagesPerHour > 0f) String.format(Locale.US, "%.1f pages/h", bookPagesPerHour) else "- pages/h",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = run {
                        val valid = sessions.filter { it.pagesRead > 0 && it.durationSeconds > 0 }.take(20)
                        val averagePages = if (valid.isEmpty()) 0.0 else valid.map { it.pagesRead }.average()
                        val left = (book.pageCount - book.currentPage).coerceAtLeast(0)
                        val passes = if (averagePages > 0) kotlin.math.ceil(left / averagePages).toInt() else 0
                        "${bookEstimateRemaining(book, totalSeconds)} left" + if (passes > 0 && book.status != BookStatus.FINISHED) " · ~$passes sessions" else ""
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
            }
        }

        BookPanel(
            modifier = Modifier.height(142.dp),
            contentPadding = PaddingValues(9.dp),
            contentSpacing = 3.dp
        ) {
            Text("Book details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_scan_24), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("ISBN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(book.isbn.ifBlank { "-" }, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { showCollectionsDialog = true },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painterResource(R.drawable.ic_bookmark_24), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("Collections", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(book.collections.ifBlank { "-" }, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(painterResource(R.drawable.ic_chevron_right_24), "Show all collections", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            BookInteractiveRating(
                rating = book.rating,
                onRating = onRating,
                onRatingPreview = onRatingPreview,
                onRatingCancel = onRatingCancel
            )
        }
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().height(40.dp)) { BookButtonLabel(R.drawable.ic_edit_24, "Edit book") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onOpenNotes, modifier = Modifier.weight(1f).height(40.dp)) {
                BookButtonLabel(R.drawable.ic_note_24, "Notes (${notes.size})")
            }
            OutlinedButton(onClick = onOpenSessions, modifier = Modifier.weight(1f).height(40.dp)) {
                BookButtonLabel(R.drawable.ic_clock_24, "Sessions (${sessions.size})")
            }
        }
        OutlinedButton(onClick = onAddManual, modifier = Modifier.fillMaxWidth().height(40.dp)) {
            BookButtonLabel(R.drawable.ic_add_24, "Add note or session")
        }
        Button(onClick = onOpenGraph, modifier = Modifier.fillMaxWidth().height(46.dp)) {
            BookButtonLabel(R.drawable.ic_chart_24, "Show graph")
        }
    }
    if (showCollectionsDialog) {
        AlertDialog(
            shape = RoundedCornerShape(8.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
            onDismissRequest = { showCollectionsDialog = false },
            title = { Text("Collections") },
            text = {
                if (bookCollections.isEmpty()) {
                    Text("No collections")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(bookCollections) { collection ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_bookmark_24),
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(collection, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCollectionsDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
internal fun BookMetricLabel(iconRes: Int, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(iconRes), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun BookButtonLabel(iconRes: Int, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(iconRes), null, modifier = Modifier.size(18.dp))
        Text(label, maxLines = 1)
    }
}

@Composable
internal fun BookInteractiveRating(
    rating: Int,
    onRating: (Int) -> Unit,
    onRatingPreview: (Int) -> Unit,
    onRatingCancel: () -> Unit
) {
    var confirmClear by remember { mutableStateOf(false) }

    var widthPx by remember { mutableStateOf(1f) }
    var dragRating by remember { mutableStateOf(0) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { widthPx = it.size.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(widthPx) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        dragRating = mapDragPositionToRating(offset.x, widthPx)
                        onRatingPreview(dragRating)
                    },
                    onDrag = { change, _ ->
                        dragRating = mapDragPositionToRating(change.position.x, widthPx)
                        onRatingPreview(dragRating)
                        change.consume()
                    },
                    onDragEnd = {
                        if (dragRating > 0) onRating(dragRating)
                        dragRating = 0
                        onRatingCancel()
                    },
                    onDragCancel = {
                        dragRating = 0
                        onRatingCancel()
                    }
                )
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        (1..5).forEach { star ->
            Text(
                text = if (star <= rating) "★" else "☆",
                style = MaterialTheme.typography.headlineSmall,
                color = if (star <= rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .fittedClickable(RoundedCornerShape(6.dp)) { onRating(star) }
                    .padding(vertical = 2.dp),
                textAlign = TextAlign.Center
            )
        }
        TextButton(onClick = { confirmClear = true }) {
            Text("Clear")
        }
    }
    if (confirmClear) AlertDialog(
            shape = RoundedCornerShape(8.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),onDismissRequest = { confirmClear = false }, title = { Text("Clear rating?") },
        confirmButton = { TextButton(onClick = { confirmClear = false; onRating(0) }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
}

@Composable
internal fun BookSessionNotesList(notes: List<BookNote>, onOpenNote: (BookNote) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Reading notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${notes.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (notes.isEmpty()) {
            Text("No session notes yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            notes.forEach { note ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .fittedClickable(RoundedCornerShape(8.dp)) { onOpenNote(note) }
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "${bookFormatIsoDate(note.createdAt)} - page ${note.page} - ${bookFormatDuration(note.durationSeconds)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        note.note.ifBlank { "No note written." },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
