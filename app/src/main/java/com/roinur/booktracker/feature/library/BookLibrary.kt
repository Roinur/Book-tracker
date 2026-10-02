package com.roinur.booktracker

import androidx.compose.material3.Text

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.OutlinedButton
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
    onOpenReadingMap: () -> Unit,
    onOpenCollectionProgress: () -> Unit,
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
                trailingIcon = {
                    IconButton(onClick = onOpenReadingMap) {
                        Icon(painterResource(R.drawable.ic_reading_map_24), "Open reading map",
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            BookLibraryCollections(vm, onOpenCollectionProgress)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                BookSortField.entries.filter { it != BookSortField.ADDED }.forEach { field ->
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
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    val previewBooks = vm.books.ifEmpty { vm.statsBooks }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(20.dp).widthIn(max = 400.dp).fillMaxWidth()
            .clip(shape).background(colors.surfaceContainerLow)
            .border(1.dp, colors.outlineVariant, shape).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Library layout", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = onDismiss, shape = shape) { Text("Done") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(false, true).forEach { gallery ->
                    val selected = vm.galleryMode == gallery
                    OutlinedButton(onClick = { if (!selected) vm.toggleGalleryMode() },
                        modifier = Modifier.weight(1f).height(48.dp), shape = shape,
                        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) colors.primary.copy(alpha = 0.14f) else colors.surfaceContainerLow),
                        contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Icon(painterResource(if (gallery) R.drawable.ic_grid_24 else R.drawable.ic_list_24),
                            null, Modifier.padding(end = 8.dp).size(22.dp), tint = colors.primary)
                        Text(if (gallery) "Gallery" else "List")
                    }
                }
            }
            // A fixed preview height prevents the controls moving when the mode changes.
            BoxWithConstraints(Modifier.fillMaxWidth().height(176.dp), contentAlignment = Alignment.Center) {
                if (vm.galleryMode) {
                    val count = vm.galleryColumns
                    val coverWidth = minOf((maxWidth - 8.dp * (count - 1)) / count, 96.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(count) { index ->
                            val book = previewBooks.getOrNull(index)
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                BookCoverImage(book?.coverUrl.orEmpty(), book?.title.orEmpty(),
                                    Modifier.width(coverWidth).height(coverWidth * 1.5f).clip(shape))
                                if (vm.galleryHideTitle) {
                                    Row(Modifier.width(coverWidth).height(18.dp), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        LinearProgressIndicator(progress = { book?.progressFraction ?: 0f },
                                            modifier = Modifier.weight(1f).height(3.dp))
                                        Text("${((book?.progressFraction ?: 0f) * 100).roundToInt()}%",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp))
                                    }
                                } else Text(book?.title.orEmpty(), style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        previewBooks.take(2).forEach { book ->
                            Row(Modifier.fillMaxWidth().background(colors.surfaceContainer, shape).padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                BookCoverImage(book.coverUrl, book.title, Modifier.size(32.dp, 48.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(book.title, style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(book.authors, style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Columns", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { count ->
                        val selected = vm.galleryColumns == count && vm.galleryMode
                        OutlinedButton(onClick = { vm.updateGalleryColumns(count) }, enabled = vm.galleryMode,
                            modifier = Modifier.weight(1f).height(44.dp), shape = shape,
                            border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) colors.primary.copy(alpha = 0.14f) else colors.surfaceContainerLow),
                            contentPadding = PaddingValues(0.dp)) { Text(count.toString()) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Disable book title", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    color = if (vm.galleryMode) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Switch(checked = vm.galleryHideTitle, onCheckedChange = vm::updateGalleryHideTitle,
                    enabled = vm.galleryMode)
            }

        }
    }
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
    onRatingCancel: () -> Unit,
    collectionsPreview: String? = null
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
        onToggleRead = onToggleFinished,
        enabled = collectionsPreview == null
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
                .pointerInput(book.id, widthPx, collectionsPreview) {
                    if (collectionsPreview != null) return@pointerInput
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
                        BookCollectionPreview(collectionsPreview) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(book.authors.ifBlank { "Unknown author" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${book.rating}/5 ★", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        LinearProgressIndicator(progress = { book.progressFraction }, modifier = Modifier.fillMaxWidth().height(3.dp))
                        Text(bookStatusLine(book, liveSeconds), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { onTogglePinned(book.id) }, enabled = collectionsPreview == null, modifier = Modifier.size(32.dp)) {
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
    onRatingCancel: () -> Unit,
    collectionsPreview: String? = null,
    hideTitle: Boolean = false
) {
    var widthPx by remember(book.id) { mutableStateOf(1f) }
    var dragRating by remember(book.id) { mutableStateOf(0) }
    EntrySwipeDismissContainer(
        code = book.id,
        isPinned = book.pinned,
        isRead = book.status == BookStatus.FINISHED,
        onTogglePinned = { onPin() },
        onToggleRead = { onReadGesture() },
        modifier = modifier,
        enabled = collectionsPreview == null
    ) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val compact = maxWidth < 150.dp
    val density = LocalDensity.current
    var frostHeight by remember(book.id) { mutableStateOf(0.dp) }
    val dense = maxWidth < 100.dp
    val coverDimensionPx = with(density) { (maxWidth / 0.8f).toPx() }
    val coverSize = when {
        coverDimensionPx <= 384f -> 384
        coverDimensionPx <= 640f -> 640
        else -> 1536
    }
    val spacing = if (dense) 2.dp else if (compact) 3.dp else 7.dp
    val titleStyle = if (dense) MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 14.sp) else MaterialTheme.typography.titleSmall
    val metadataStyle = if (dense) MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp) else MaterialTheme.typography.bodySmall
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected && collectionsPreview == null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f) else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)
        ),
        modifier = Modifier.fillMaxWidth()
            .height(maxWidth / 0.8f)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(book.id, widthPx, collectionsPreview) {
                    if (collectionsPreview != null) return@pointerInput
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
        Box(Modifier.fillMaxSize()) {
            BookCoverImage(book.coverUrl, book.title, Modifier.matchParentSize(),
                contentScale = ContentScale.Crop, frostedBottomHeight = frostHeight,
                maxDimensionPx = coverSize)
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onSizeChanged { frostHeight = with(density) { it.height.toDp() } }
                .background(Brush.verticalGradient(listOf(
                    MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f),
                    MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.9f))))
                .padding(if (dense) 4.dp else if (compact) 6.dp else 10.dp)) {
                val showText = !hideTitle && collectionsPreview == null
                // Hidden-title mode reserves only the author and progress footprint, including during collection editing.
                Column(if (showText) Modifier.fillMaxWidth() else Modifier.fillMaxWidth()
                    .alpha(0f).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(spacing)) {
                    if (!hideTitle) Text(book.title, style = titleStyle, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface, maxLines = if (compact) 1 else 2,
                        overflow = TextOverflow.Ellipsis)
                    Text(book.authors.ifBlank { "Unknown author" }, style = metadataStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BookGalleryProgress(book, compact, dense, metadataStyle)
                }
                when {
                    collectionsPreview != null -> Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterStart) {
                        Text(collectionsPreview.ifBlank { "No collections" }, style = metadataStyle,
                            color = MaterialTheme.colorScheme.onSurface, maxLines = if (compact) 3 else 4,
                            overflow = TextOverflow.Ellipsis)
                    }
                    hideTitle -> Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                        BookGalleryProgress(book, compact, dense, metadataStyle)
                    }
                }
            }
            PinnedCornerBleedGlow(book.pinned, MaterialTheme.colorScheme.primary, modifier = Modifier.matchParentSize())
            SelectedCardEdgeGlow(selected, MaterialTheme.colorScheme.primary, Modifier.matchParentSize())
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(44.dp)
                .background(Brush.verticalGradient(listOf(
                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                    androidx.compose.ui.graphics.Color.Transparent))))
            IconButton(onClick = onPin, enabled = collectionsPreview == null,
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(32.dp)) {
                Icon(painterResource(R.drawable.ic_push_pin_24),
                    if (book.pinned) "Unpin book" else "Pin book",
                    tint = if (book.pinned) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(if (dense) 16.dp else 20.dp))
            }
        }
    }
    }
    }
}

@Composable
private fun BookGalleryProgress(book: BookRow, compact: Boolean, dense: Boolean,
                                metadataStyle: androidx.compose.ui.text.TextStyle) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
        LinearProgressIndicator(progress = { book.progressFraction },
            modifier = Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(99.dp)),
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
        Text("${(book.progressFraction * 100f).roundToInt()}%",
            style = if (dense) metadataStyle else MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun BookCollectionPreview(collections: String?, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        // Keep the normal metadata measurement so editing never changes card geometry.
        Box(if (collections == null) Modifier else Modifier.alpha(0f).clearAndSetSemantics {}) { content() }
        if (collections != null) Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterStart) {
            Text(collections.ifBlank { "No collections" }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
