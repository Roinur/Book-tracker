package com.roinur.booktracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.UUID

/** Marks all start/end boundaries, including gaps, without changing the existing bar height. */
@Composable
internal fun BookPartsProgressBar(book: BookRow, modifier: Modifier = Modifier, onOpenParts: (() -> Unit)? = null) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val gap = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(99.dp)
    val interactive = if (book.parts.isNotEmpty() && onOpenParts != null) Modifier
        .semantics { contentDescription = "Parts of ${book.title}, ${book.currentPage} of ${book.pageCount} pages" }
        .fittedClickable(RoundedCornerShape(4.dp), onClick = onOpenParts) else Modifier
    Box(modifier.then(interactive).height(4.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(4.dp).clip(shape)) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = radius)
            if (book.progressFraction > 0) drawRoundRect(accent, size = Size(size.width * book.progressFraction, size.height), cornerRadius = radius)
            if (book.pageCount > 0) book.parts.flatMap { listOf(it.startPage - 1, it.endPage) }.distinct().filter { it > 0 && it < book.pageCount }.forEach { page ->
                val x = size.width * page / book.pageCount
                drawLine(gap, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
            }
        }
    }
}

@Composable
internal fun BookPartsOverview(book: BookRow, sessions: List<BookReadingSessionRow>, fallbackPagesPerHour: Double?, onRating: (BookPart, Int) -> Unit, onDismiss: () -> Unit) {
    val readings = remember(book.parts, sessions) { bookPartReading(book.parts, sessions.filter { it.bookId == book.id }) }
    val timedReadings = remember(book.parts, sessions) { bookPartReading(book.parts,
        bookSessionsWithPageFallback(sessions.filter { it.bookId == book.id }).filter { !it.excludeFromStatistics && it.pagesRead > 0 && it.durationSeconds > 0 }) }
    BookPartsDialogFrame(onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_list_24), null, Modifier.size(24.dp), MaterialTheme.colorScheme.primary)
            Text("Parts", Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onDismiss) { Text("Close") }
        }
        Text(book.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            itemsIndexed(book.parts) { index, part ->
                val read = part.pagesRead(book.currentPage)
                val active = part == book.currentPart()
                val remainingSeconds = bookPartRemainingSeconds(part, book.currentPage, timedReadings[index], fallbackPagesPerHour)
                var displayRating by remember(part.title, part.startPage, part.endPage, part.rating) { mutableStateOf(part.rating) }
                var previewRating by remember { mutableStateOf<Int?>(null) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    BookCoverImage(part.coverUrl.ifBlank { book.coverUrl }, part.title, Modifier.size(60.dp, 90.dp).clip(RoundedCornerShape(8.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(part.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        if (part.completed(book.currentPage)) Icon(painterResource(R.drawable.ic_check_24), "Finished part", Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("p.${part.startPage}–${part.endPage}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$read / ${part.pageCount} pages", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    LinearProgressIndicator(progress = { read.toFloat() / part.pageCount }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(99.dp)))
                    Text(if (part.completed(book.currentPage)) "Finished" else if (active) "Reading · ${part.pageCount - read} pages left" else "Not started",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(bookFormatDuration(readings[index].seconds) + " read" +
                        if (remainingSeconds != null && remainingSeconds > 0) " · ~${bookFormatDuration(remainingSeconds)} left" else "", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    BookInteractiveRating(previewRating ?: displayRating,
                        onRating = { value -> displayRating = value; previewRating = null; onRating(part, value) },
                        onRatingPreview = { previewRating = it }, onRatingCancel = { previewRating = null })
                    }
                }
            }
        }
    }
}

private data class PartDraft(val key: String = UUID.randomUUID().toString(), val title: String = "", val start: String = "", val end: String = "", val coverUrl: String = "", val rating: Int = 0)
private val partsDraftSaver = listSaver<List<PartDraft>, String>(
    save = { rows -> listOf("ratings-v1") + rows.flatMap { listOf(it.key, it.title, it.start, it.end, it.coverUrl, it.rating.toString()) } },
    restore = { values -> if (values.firstOrNull() == "ratings-v1") values.drop(1).chunked(6).map { PartDraft(it[0], it[1], it[2], it[3], it[4], it[5].toInt()) }
        else if (values.firstOrNull() == "covers-v1") values.drop(1).chunked(5).map { PartDraft(it[0], it[1], it[2], it[3], it[4]) }
        else values.chunked(4).map { PartDraft(it[0], it[1], it[2], it[3]) } })

@Composable
internal fun BookPartsEditor(vm: BookTrackerViewModel, initial: List<BookPart>, totalPages: Int, onDismiss: () -> Unit, onApply: (List<BookPart>) -> Unit) {
    var rows by rememberSaveable(stateSaver = partsDraftSaver) {
        mutableStateOf(initial.map { PartDraft(title = it.title, start = it.startPage.toString(), end = it.endPage.toString(), coverUrl = it.coverUrl, rating = it.rating) })
    }
    var error by rememberSaveable { mutableStateOf("") }
    var removeKey by rememberSaveable { mutableStateOf("") }
    var coverKey by rememberSaveable { mutableStateOf("") }
    var editingKey by rememberSaveable { mutableStateOf("") }
    var scanContents by rememberSaveable { mutableStateOf(false) }
    fun update(key: String, change: (PartDraft) -> PartDraft) { rows = rows.map { if (it.key == key) change(it) else it }; error = "" }
    BookPartsDialogFrame(onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_list_24), null, Modifier.size(24.dp), MaterialTheme.colorScheme.primary)
            Text("Assign parts", Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
        OutlinedButton(onClick = { scanContents = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
            BookButtonLabel(R.drawable.ic_scan_24, "Scan contents")
        }
        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            if (rows.isEmpty()) item { Text("Add the works in this book.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                val expanded = editingKey == row.key
                val start = row.start.toIntOrNull()
                val end = row.end.toIntOrNull()
                val length = if (start != null && end != null && start >= 1 && end >= start) end - start + 1 else 0
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        BookCoverImage(row.coverUrl, row.title.ifBlank { "Part ${index + 1}" }, Modifier.size(48.dp, 72.dp).clip(RoundedCornerShape(8.dp))
                            .semantics { contentDescription = "Change cover for part ${index + 1}" }.fittedClickable(RoundedCornerShape(8.dp)) { coverKey = row.key })
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(row.title.ifBlank { "Part ${index + 1}" }, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (length > 0) Text("$length pages · p.$start–$end", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else Text("Check pages", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(onClick = {
                            if (expanded) {
                                val valid = runCatching { validateBookParts(listOf(BookPart(row.title, start ?: 0, end ?: 0)), totalPages) }
                                valid.fold(onSuccess = { editingKey = ""; error = "" }, onFailure = { error = it.message.orEmpty() })
                            } else { editingKey = row.key; error = "" }
                        }, shape = RoundedCornerShape(8.dp)) {
                            BookButtonLabel(if (expanded) R.drawable.ic_check_24 else R.drawable.ic_edit_24, if (expanded) "Done" else "Edit")
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(expanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(row.title, { value -> update(row.key) { it.copy(title = value) } }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(row.start, { value -> update(row.key) { it.copy(start = value.filter(Char::isDigit).take(6)) } }, label = { Text("Start page") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                                OutlinedTextField(row.end, { value -> update(row.key) { it.copy(end = value.filter(Char::isDigit).take(6)) } }, label = { Text("End page") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            }
                            TextButton(onClick = { removeKey = row.key }, shape = RoundedCornerShape(8.dp)) { Text("Remove part") }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = {
                val next = PartDraft(start = ((rows.lastOrNull()?.end?.toIntOrNull() ?: 0) + 1).toString())
                rows = rows + next; editingKey = next.key; error = ""
            }, modifier = Modifier.weight(1f)) {
                BookButtonLabel(R.drawable.ic_add_24, "Add part")
            }
            Button(onClick = {
                val result = runCatching { validateBookParts(rows.map {
                    BookPart(it.title, it.start.toIntOrNull() ?: throw IllegalArgumentException("Enter a start page."), it.end.toIntOrNull() ?: throw IllegalArgumentException("Enter an end page."), it.coverUrl, it.rating)
                }, totalPages) }
                result.fold(onSuccess = onApply, onFailure = { error = it.message ?: "Check the parts." })
            }, modifier = Modifier.weight(1f)) { Text("Apply") }
        }
    }
    if (scanContents) BookContentsScannerDialog(onDismiss = { scanContents = false }, onAdd = { entries ->
        val newEntries = entries.filterNot { entry -> rows.any { it.title.equals(entry.title, ignoreCase = true) &&
            (entry.startPage == null || it.start.toIntOrNull() == entry.startPage) } }
        val ends = bookContentsEndPages(newEntries, totalPages)
        rows = rows + newEntries.mapIndexed { index, entry -> PartDraft(title = entry.title,
            start = entry.startPage?.toString().orEmpty(), end = ends[index]?.toString().orEmpty()) }
        editingKey = ""; error = ""; scanContents = false
    })
    rows.firstOrNull { it.key == coverKey }?.let { row ->
        androidx.compose.runtime.key(row.key) {
            BookPartCoverChooser(vm, row.title, row.coverUrl, onDismiss = { coverKey = "" },
                onPick = { url -> update(row.key) { it.copy(coverUrl = url) }; coverKey = "" })
        }
    }
    if (removeKey.isNotBlank()) AlertDialog(onDismissRequest = { removeKey = "" }, shape = RoundedCornerShape(8.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Remove part?") }, text = { Text(rows.firstOrNull { it.key == removeKey }?.title?.ifBlank { "This part" } ?: "This part") },
        confirmButton = { TextButton(onClick = { rows = rows.filterNot { it.key == removeKey }; removeKey = ""; error = "" }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { removeKey = "" }) { Text("Cancel") } })
}

@Composable
private fun BookPartsDialogFrame(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(8.dp)
        Column(Modifier.fillMaxWidth().padding(16.dp).imePadding().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.82f)
            .clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLow).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
