package com.roinur.booktracker

import androidx.compose.material3.Text

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookLibraryHeader(
    vm: BookTrackerViewModel,
    visibleBookCount: Int,
    activeBook: BookRow?,
    nowMs: Long,
    onOpenBook: (Int) -> Unit,
    onContinue: (Int) -> Unit,
    onAdd: () -> Unit
) {
    val highlightedBook = mostRecentlyReadBook(vm.statsBooks, vm.allBookSessions, vm.activeBookId)
    var showLayoutDialog by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        highlightedBook?.let { book ->
            val liveSeconds = if (vm.activeBookId == book.id) {
                bookActiveElapsedSeconds(vm.activeStartedAtMs, nowMs, vm.activePausedAtMs, vm.activePausedTotalMs)
            } else {
                0L
            }
            Text(
                text = "Continue reading",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            BookPanel(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
                modifier = Modifier.fittedClickable(RoundedCornerShape(8.dp)) { onOpenBook(book.id) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    BookCover(
                        book = book,
                        modifier = Modifier
                            .width(124.dp)
                            .height(186.dp)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(186.dp),
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
                            Text(book.authors.ifBlank { "Unknown author" }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (book.pageCount > 0) "${book.currentPage.coerceAtMost(book.pageCount)} / ${book.pageCount} pages" else "${book.currentPage} pages",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                Text(
                                    bookFormatDuration(book.readingSeconds + liveSeconds),
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
                            onClick = { onContinue(book.id) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text(
                                if (vm.activeBookId == book.id) {
                                    "Resume · p.${book.currentPage} · ${bookFormatDuration(liveSeconds)}"
                                } else {
                                    "Continue"
                                }
                            )
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Your library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "$visibleBookCount ${if (visibleBookCount == 1) "book" else "books"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxHeight()
                            .fittedClickable(RoundedCornerShape(0.dp), onClick = vm::cycleLibraryFilter)
                            .padding(horizontal = 10.dp)
                    ) {
                        Text(
                            text = vm.libraryFilter.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxHeight()
                            .fittedCombinedClickable(
                                shape = RoundedCornerShape(0.dp),
                                onClick = vm::toggleGalleryMode,
                                onLongClick = { showLayoutDialog = true }
                            )
                            .padding(horizontal = 10.dp)
                    ) {
                        Icon(
                            painter = painterResource(if (vm.galleryMode) R.drawable.ic_list_24 else R.drawable.ic_grid_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (vm.galleryMode) "List" else "Gallery",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Button(
                    onClick = onAdd,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(48.dp)
                ) { Text("Add") }
            }
            OutlinedTextField(
                value = vm.searchInput,
                onValueChange = vm::updateSearch,
                label = { Text("Search library, ISBN, collection") },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_search_24),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (vm.collectionSuggestions.isNotEmpty()) {
                BookFadingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.collectionSuggestions, key = { it.lowercase(Locale.US) }) { collection ->
                        FilterChip(
                            selected = vm.searchInput.equals(collection, ignoreCase = true),
                            onClick = { vm.applyCollectionSuggestion(collection) },
                            label = { Text(collection) }
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                BookSortField.entries.forEach { field ->
                    val selected = vm.sortField == field
                    val shape = RoundedCornerShape(10.dp)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 42.dp)
                            .clip(shape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                shape = shape
                            )
                            .fittedClickable(shape) { vm.toggleSort(field) }
                            .padding(horizontal = 2.dp, vertical = 8.dp)
                    ) {
                        Text(
                            field.label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
    if (showLayoutDialog) {
        BookLibraryLayoutDialog(
            vm = vm,
            onDismiss = { showLayoutDialog = false }
        )
    }
}

@Composable
internal fun BookLibraryLayoutDialog(vm: BookTrackerViewModel, onDismiss: () -> Unit) {
    AlertDialog(
            shape = RoundedCornerShape(8.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
        onDismissRequest = onDismiss,
        title = { Text("Library layout") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Mode", style = MaterialTheme.typography.titleMedium)
                    ImmediateActionText(
                        label = if (vm.galleryMode) "Gallery" else "List",
                        onAction = vm::toggleGalleryMode,
                        textStyle = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text("Columns: ${vm.galleryColumns}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = vm.galleryColumns.toFloat(),
                    onValueChange = { vm.updateGalleryColumns(it.roundToInt()) },
                    valueRange = 1f..5f,
                    steps = 3,
                    enabled = vm.galleryMode
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
internal fun BookLibraryRow(
    book: BookRow,
    selected: Boolean,
    active: Boolean,
    activeStartedAtMs: Long,
    activePausedAtMs: Long,
    activePausedTotalMs: Long,
    nowMs: Long,
    onOpen: () -> Unit,
    onTogglePinned: (Int) -> Unit,
    onToggleFinished: (Int) -> Unit,
    onRatingPreview: (Int) -> Unit,
    onRatingCommit: (Int) -> Unit,
    onRatingCancel: () -> Unit
) {
    var widthPx by remember(book.id) { mutableStateOf(1f) }
    var dragRating by remember(book.id) { mutableStateOf(0) }
    val liveSeconds = if (active) {
        bookActiveElapsedSeconds(activeStartedAtMs, nowMs, activePausedAtMs, activePausedTotalMs)
    } else {
        0L
    }
    EntrySwipeDismissContainer(
        code = book.id,
        isPinned = book.pinned,
        isRead = book.status == BookStatus.FINISHED,
        onTogglePinned = onTogglePinned,
        onToggleRead = onToggleFinished
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { widthPx = it.size.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(book.id, widthPx) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            dragRating = mapDragPositionToRating(offset.x, widthPx)
                            onRatingPreview(dragRating)
                        },
                        onDrag = { change, _ ->
                            val next = mapDragPositionToRating(change.position.x, widthPx)
                            if (next != dragRating) {
                                dragRating = next
                                onRatingPreview(next)
                            }
                            change.consume()
                        },
                        onDragEnd = {
                            if (dragRating > 0) onRatingCommit(dragRating)
                            dragRating = 0
                            onRatingCancel()
                        },
                        onDragCancel = {
                            dragRating = 0
                            onRatingCancel()
                        }
                    )
                }
                .fittedClickable(RoundedCornerShape(8.dp), onClick = onOpen)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                PinnedCornerBleedGlow(
                    visible = book.pinned,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.matchParentSize()
                )
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BookCover(
                        book = book,
                        modifier = Modifier
                            .width(72.dp)
                            .height(102.dp)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            book.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(book.authors.ifBlank { "Unknown author" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${book.rating}/5 ★", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        LinearProgressIndicator(progress = { book.progressFraction }, modifier = Modifier.fillMaxWidth().height(3.dp))
                        Text(bookStatusLine(book, liveSeconds), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { onTogglePinned(book.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_push_pin_24),
                                contentDescription = if (book.pinned) "Unpin book" else "Pin book",
                                tint = if (book.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                    }
                }
            }
        }
    }
}

@Composable
internal fun BookGalleryTile(
    book: BookRow,
    selected: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onReadGesture: () -> Unit,
    onRatingPreview: (Int) -> Unit,
    onRatingCommit: (Int) -> Unit,
    onRatingCancel: () -> Unit
) {
    var widthPx by remember(book.id) { mutableStateOf(1f) }
    var dragRating by remember(book.id) { mutableStateOf(0) }
    EntrySwipeDismissContainer(
        code = book.id,
        isPinned = book.pinned,
        isRead = book.status == BookStatus.FINISHED,
        onTogglePinned = { onPin() },
        onToggleRead = { onReadGesture() },
        modifier = modifier
    ) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f) else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)
        ),
        modifier = Modifier
            .onGloballyPositioned { widthPx = it.size.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(book.id, widthPx) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        dragRating = mapDragPositionToRating(offset.x, widthPx)
                        onRatingPreview(dragRating)
                    },
                    onDrag = { change, _ ->
                        val next = mapDragPositionToRating(change.position.x, widthPx)
                        if (next != dragRating) {
                            dragRating = next
                            onRatingPreview(next)
                        }
                        change.consume()
                    },
                    onDragEnd = {
                        if (dragRating > 0) onRatingCommit(dragRating)
                        dragRating = 0
                        onRatingCancel()
                    },
                    onDragCancel = {
                        dragRating = 0
                        onRatingCancel()
                    }
                )
            }
            .fittedClickable(RoundedCornerShape(8.dp), onClick = onOpen)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            SelectedCardEdgeGlow(
                active = selected,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.matchParentSize()
            )
            PinnedCornerBleedGlow(
                visible = book.pinned,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.matchParentSize()
            )
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(170.dp)) {
                    BookCover(
                        book = book,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp))
                    )
                    IconButton(
                        onClick = onPin,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_push_pin_24),
                            contentDescription = if (book.pinned) "Unpin book" else "Pin book",
                            tint = if (book.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(book.authors.ifBlank { "Unknown author" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = { book.progressFraction },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(99.dp))
                    )
                    Text("${(book.progressFraction * 100f).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    }
}
