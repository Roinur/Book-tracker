package com.roinur.booktracker

import android.graphics.Paint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.sqrt

private fun mapPoint(graph: BookReadingMap, viewport: IntSize, zoom: Float, pan: Offset, x: Float, y: Float): Offset {
    val fit = min(viewport.width / (graph.maxX - graph.minX), viewport.height / (graph.maxY - graph.minY)) * 0.92f
    return Offset(
        viewport.width / 2f + ((x - (graph.minX + graph.maxX) / 2f) * fit * zoom) + pan.x,
        viewport.height / 2f + ((y - (graph.minY + graph.maxY) / 2f) * fit * zoom) + pan.y
    )
}

internal fun mapPanAfterZoom(pan: Offset, centroid: Offset, viewport: IntSize, zoomFactor: Float, drag: Offset): Offset {
    val center = Offset(viewport.width / 2f, viewport.height / 2f)
    val anchor = centroid - center
    return pan * zoomFactor + anchor * (1f - zoomFactor) + drag
}

private data class MapFamilyLabel(val name: String, val text: String, val bounds: Rect)

@Composable
internal fun BookReadingMapScreen(books: List<BookRow>, collectionTypes: Map<String, BookCollectionType>,
                                  onOpenBook: (Int) -> Unit) {
    val allBooksGraph = remember(books, collectionTypes) { BookReadingMap.from(books, collectionTypes) }
    var filterName by rememberSaveable { mutableStateOf(ReadingMapFilter.ALL.name) }
    val filter = ReadingMapFilter.entries.firstOrNull { it.name == filterName } ?: ReadingMapFilter.ALL
    var typeFilterName by rememberSaveable { mutableStateOf("") }
    val typeFilter = BookCollectionType.entries.firstOrNull { it.name == typeFilterName }
    val typeChoices = listOf(null, BookCollectionType.THEME, BookCollectionType.TYPE,
        BookCollectionType.PUBLISHER, BookCollectionType.OTHER)
    val graph = remember(allBooksGraph, filter, typeFilter) { allBooksGraph.filtered(filter, typeFilter) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var selectedBookId by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedFamilyName by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    val primary = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val border = MaterialTheme.colorScheme.outlineVariant
    val darkSurface = surface.luminance() < 0.5f
    fun nodeColor(status: BookStatus): Color = when (status) {
        BookStatus.FINISHED -> if (darkSurface) Color(0xFF78D6A3) else Color(0xFF24764B)
        BookStatus.READING -> primary
        BookStatus.PAUSED -> if (darkSurface) Color(0xFFFFC36D) else Color(0xFF9B5D07)
        BookStatus.WISHLIST -> muted
    }
    val density = LocalDensity.current.density
    val fontScale = LocalDensity.current.fontScale
    fun familyRadius(count: Int): Float = ((10f + sqrt(count.toFloat()) * 0.75f) * density * sqrt(zoom)).coerceAtMost(28f * density)
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var cameraJob by remember { mutableStateOf<Job?>(null) }
    val labels = remember { ArrayList<MapFamilyLabel>() }
    fun moveCamera(target: ReadingMapCamera) {
        cameraJob?.cancel()
        val startZoom = zoom
        val startPan = pan
        cameraJob = scope.launch {
            animate(0f, 1f, animationSpec = tween(360, easing = FastOutSlowInEasing)) { fraction, _ ->
                zoom = startZoom + (target.zoom - startZoom) * fraction
                pan = startPan + (target.pan - startPan) * fraction
            }
            zoom = target.zoom
            pan = target.pan
        }
    }
    fun selectFamily(family: ReadingMapFamily) {
        focusManager.clearFocus()
        selectedFamilyName = family.name
        selectedBookId = null
        search = ""
        val points = graph.books.filter { it.book.id in family.bookIds }.map { Offset(it.x, it.y) } + Offset(family.x, family.y)
        moveCamera(readingMapFocusCamera(graph, viewport, points, density))
    }
    fun selectBook(node: ReadingMapBook, focus: Boolean = false) {
        cameraJob?.cancel()
        focusManager.clearFocus()
        selectedBookId = node.book.id
        selectedFamilyName = null
        search = ""
        if (focus) moveCamera(readingMapFocusCamera(graph, viewport, listOf(Offset(node.x, node.y)), density))
    }
    DisposableEffect(graph) {
        cameraJob?.cancel()
        onDispose { cameraJob?.cancel() }
    }
    val labelPaint = remember { android.text.TextPaint(Paint.ANTI_ALIAS_FLAG) }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${graph.books.size} ${if (graph.books.size == 1) "book" else "books"} · ${graph.families.size} ${if (graph.families.size == 1) "family" else "families"}",
                style = MaterialTheme.typography.titleMedium)
            OutlinedButton(shape = RoundedCornerShape(8.dp), onClick = {
                filterName = filter.next().name
                selectedBookId = null
                selectedFamilyName = null
            }) { Text(filter.label) }
        }
        OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, label = { Text("Find book or family") })
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp))
                .background(surface, RoundedCornerShape(8.dp))
                .border(1.dp, border, RoundedCornerShape(8.dp))) {
                Canvas(Modifier.fillMaxSize().onSizeChanged { viewport = it }
                    .pointerInput(graph) {
                        detectTransformGestures { centroid, delta, change, _ ->
                            cameraJob?.cancel()
                            val nextZoom = (zoom * change).coerceIn(0.65f, 7f)
                            val actual = nextZoom / zoom
                            pan = mapPanAfterZoom(pan, centroid, viewport, actual, delta)
                            zoom = nextZoom
                        }
                    }
                    .pointerInput(graph) {
                        detectTapGestures(onTap = { point ->
                            val label = labels.firstOrNull { it.bounds.contains(point) }
                            val nearestFamily = graph.families.minByOrNull {
                                (mapPoint(graph, viewport, zoom, pan, it.x, it.y) - point).getDistance()
                            }
                            val nearestBook = graph.books.minByOrNull {
                                (mapPoint(graph, viewport, zoom, pan, it.x, it.y) - point).getDistance()
                            }
                            when {
                                label != null -> graph.families.firstOrNull { it.name == label.name }?.let { selectFamily(it) }
                                nearestBook != null && (mapPoint(graph, viewport, zoom, pan, nearestBook.x, nearestBook.y) - point).getDistance() < 10f * density -> selectBook(nearestBook)
                                nearestFamily != null &&
                                    (mapPoint(graph, viewport, zoom, pan, nearestFamily.x, nearestFamily.y) - point).getDistance() <
                                    maxOf(20f * density, familyRadius(nearestFamily.bookIds.size)) -> selectFamily(nearestFamily)
                                nearestBook != null && (mapPoint(graph, viewport, zoom, pan, nearestBook.x, nearestBook.y) - point).getDistance() < 18f * density -> selectBook(nearestBook)
                                else -> { cameraJob?.cancel(); focusManager.clearFocus(); selectedBookId = null; selectedFamilyName = null }
                            }
                        })
                    }
                ) {
                    val view = IntSize(size.width.toInt(), size.height.toInt())
                    val hasSelection = selectedBookId != null || selectedFamilyName != null
                    val selectedMemberships = graph.books.firstOrNull { it.book.id == selectedBookId }?.families.orEmpty()
                    val centers = graph.families.associate { it.name to mapPoint(graph, view, zoom, pan, it.x, it.y) }
                    graph.books.forEach { node ->
                        val point = mapPoint(graph, view, zoom, pan, node.x, node.y)
                        val highlighted = selectedBookId == node.book.id || selectedFamilyName in node.families
                        node.families.forEach familyLink@ { family ->
                            val center = centers[family] ?: return@familyLink
                            drawLine(primary.copy(alpha = if (highlighted) 0.65f else if (hasSelection) 0.05f else 0.14f), center, point,
                                strokeWidth = if (highlighted) 2f else 1f)
                        }
                    }
                    graph.families.forEach { family ->
                        val point = centers.getValue(family.name)
                        val radius = familyRadius(family.bookIds.size)
                        val highlighted = selectedFamilyName == family.name || family.name in selectedMemberships
                        drawCircle(primary.copy(alpha = if (highlighted) 0.25f else if (hasSelection) 0.05f else 0.12f), radius * 1.45f, point)
                        drawCircle(if (highlighted) primary else surface, radius, point)
                        drawCircle(primary.copy(alpha = if (highlighted) 1f else if (hasSelection) 0.32f else 0.78f), radius, point,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (highlighted) 3f else 2f))
                    }
                    graph.books.forEach { node ->
                        val point = mapPoint(graph, view, zoom, pan, node.x, node.y)
                        val highlighted = selectedBookId == node.book.id || selectedFamilyName in node.families
                        val radius = (3.4f * density * sqrt(zoom)).coerceIn(2.5f * density, 8.5f * density)
                        if (highlighted) drawCircle(primary.copy(alpha = 0.22f), radius + 4f * density, point)
                        drawCircle(nodeColor(node.book.status).copy(alpha = if (!hasSelection || highlighted) 1f else 0.25f), radius, point)
                    }
                    val paint = labelPaint.apply {
                        color = textColor.toArgb()
                        textSize = 11f * density * fontScale.coerceAtMost(1.4f)
                        textAlign = Paint.Align.CENTER
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    }
                    val labelArea = Rect(8f * density, 100f * density, size.width - 8f * density,
                        size.height - if (hasSelection) 180f * density else 8f * density)
                    val occupied = graph.families.map { family ->
                        val center = centers.getValue(family.name)
                        val radius = familyRadius(family.bookIds.size) + 3f * density
                        Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
                    }.toMutableList()
                    labels.clear()
                    val priority = graph.families.sortedWith(compareByDescending<ReadingMapFamily> {
                        it.name == selectedFamilyName || it.name in selectedMemberships
                    }.thenByDescending { it.bookIds.size })
                    priority.forEach { family ->
                        val point = centers.getValue(family.name)
                        if (!labelArea.contains(point)) return@forEach
                        val maxWidth = min(200f * density, size.width * 0.55f)
                        val label = android.text.TextUtils.ellipsize(family.name,
                            paint, maxWidth - 12f * density,
                            android.text.TextUtils.TruncateAt.END).toString()
                        val width = paint.measureText(label) + 12f * density
                        val bounds = readingMapLabelBounds(point, familyRadius(family.bookIds.size),
                            width, paint.textSize + 10f * density, 4f * density, labelArea, occupied)
                        if (bounds != null) {
                            labels.add(MapFamilyLabel(family.name, label, bounds))
                            occupied.add(Rect(bounds.left - 3f * density, bounds.top - 3f * density,
                                bounds.right + 3f * density, bounds.bottom + 3f * density))
                        }
                    }
                    drawIntoCanvas { canvas ->
                        graph.families.forEach { family ->
                            val point = centers.getValue(family.name)
                            paint.color = (if (family.name == selectedFamilyName || family.name in selectedMemberships) onPrimary
                                else textColor.copy(alpha = if (hasSelection) 0.5f else 1f)).toArgb()
                            canvas.nativeCanvas.drawText(family.bookIds.size.toString(), point.x,
                                point.y - (paint.ascent() + paint.descent()) / 2f, paint)
                        }
                    }
                    labels.forEach { label ->
                        val selected = label.name == selectedFamilyName || label.name in selectedMemberships
                        drawRoundRect(surface.copy(alpha = 0.96f), label.bounds.topLeft, label.bounds.size,
                            CornerRadius(5f * density))
                        paint.color = (if (selected) primary else textColor).copy(alpha = if (!hasSelection || selected) 1f else 0.6f).toArgb()
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(label.text, label.bounds.center.x,
                                label.bounds.center.y - (paint.ascent() + paint.descent()) / 2f, paint)
                        }
                    }
                }
                Column(Modifier.align(Alignment.TopStart).padding(8.dp)
                    .background(surface.copy(alpha = 0.93f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        MapStatusLegendItem("Finished", nodeColor(BookStatus.FINISHED))
                        MapStatusLegendItem("Reading", nodeColor(BookStatus.READING))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        MapStatusLegendItem("On hold", nodeColor(BookStatus.PAUSED))
                        MapStatusLegendItem("Not started", nodeColor(BookStatus.WISHLIST))
                    }
                }
                Column(Modifier.align(Alignment.TopEnd).padding(8.dp),
                    horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(shape = RoundedCornerShape(8.dp), onClick = {
                        val next = typeChoices[(typeChoices.indexOf(typeFilter) + 1) % typeChoices.size]
                        typeFilterName = next?.name.orEmpty()
                        selectedBookId = null
                        selectedFamilyName = null
                    }) { Text(typeFilter?.label ?: "All types") }
                    if (zoom != 1f || pan != Offset.Zero) {
                        OutlinedButton(shape = RoundedCornerShape(8.dp), onClick = { selectedBookId = null; selectedFamilyName = null; moveCamera(ReadingMapCamera(1f, Offset.Zero)) }) { Text("Reset view") }
                    }
                }
                if (graph.books.isEmpty()) {
                    Box(Modifier.align(Alignment.Center)) { BookEmptyStateText("No books in this view.") }
                }
                if (search.isNotBlank()) {
                    val matchingFamilies = graph.families.filter { it.name.contains(search, ignoreCase = true) }.take(5)
                    val matchingBooks = graph.books.filter {
                        it.book.title.contains(search, ignoreCase = true) || it.book.authors.contains(search, ignoreCase = true)
                    }.take(8)
                    if (matchingFamilies.isNotEmpty() || matchingBooks.isNotEmpty()) {
                        BookPanel(modifier = Modifier.align(Alignment.TopCenter)
                            .padding(start = 8.dp, end = 8.dp, top = 64.dp).heightIn(max = 145.dp)) {
                            LazyColumn(Modifier.heightIn(max = 125.dp)) {
                                items(matchingFamilies) { family ->
                                    Text("${family.name} · ${family.type.label} · ${family.bookIds.size}", modifier = Modifier.fillMaxWidth()
                                        .fittedClickable(RoundedCornerShape(8.dp), onClick = {
                                            selectFamily(family)
                                        }).padding(6.dp))
                                }
                                items(matchingBooks) { node ->
                                    Text(node.book.title, modifier = Modifier.fillMaxWidth()
                                        .fittedClickable(RoundedCornerShape(8.dp), onClick = {
                                            selectBook(node, focus = true)
                                        }).padding(6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                AnimatedContent(
                    targetState = selectedBookId to selectedFamilyName,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                    transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(90)) },
                    label = "Reading map selection"
                ) { selection ->
                    val node = graph.books.firstOrNull { it.book.id == selection.first }
                    val family = graph.families.firstOrNull { it.name == selection.second }
                    when {
                        node != null -> BookPanel(containerColor = surface) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                BookCoverImage(node.book.coverUrl, node.book.title, Modifier.size(44.dp, 62.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(node.book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(node.families.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                                        color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(node.book.status.label, style = MaterialTheme.typography.bodySmall,
                                        color = nodeColor(node.book.status))
                                }
                                Button(shape = RoundedCornerShape(8.dp), onClick = { onOpenBook(node.book.id) }) { Text("Open") }
                            }
                        }
                        family != null -> {
                            val familyBooks = graph.books.filter { it.book.id in family.bookIds }
                            BookPanel(containerColor = surface) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    familyBooks.firstOrNull()?.let { representative ->
                                        BookCoverImage(representative.book.coverUrl, representative.book.title,
                                            Modifier.size(44.dp, 62.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(family.name, style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text("${family.type.label} · ${family.bookIds.size} ${if (family.bookIds.size == 1) "book" else "books"}",
                                            style = MaterialTheme.typography.bodySmall, color = muted)
                                    }
                                }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(familyBooks, key = { it.book.id }) { familyBook ->
                                        Row(Modifier.width(156.dp).fittedClickable(RoundedCornerShape(8.dp),
                                            onClick = { onOpenBook(familyBook.book.id) }).padding(4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            BookCoverImage(familyBook.book.coverUrl, familyBook.book.title,
                                                Modifier.size(30.dp, 44.dp))
                                            Text(familyBook.book.title, style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun MapStatusLegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
