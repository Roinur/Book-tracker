package com.roinur.booktracker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext

private data class PlanDrag(val book: BookRow, val pointer: Offset, val grab: Offset)
private data class PlanCoverTarget(val book: BookRow, val visibleBounds: Rect, val origin: Offset)

@Composable
internal fun BookReadingPlansDialog(vm: BookTrackerViewModel, onClose: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf(vm.readingPlans.firstOrNull { it.active }?.id) }
    val plan = vm.readingPlans.firstOrNull { it.id == selectedId }
    var naming by remember { mutableStateOf(false) }
    var renameTier by remember { mutableStateOf<Int?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(BookLibraryFilter.ALL.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var chooseFilter by remember { mutableStateOf(false) }
    var moveBook by remember { mutableStateOf<BookRow?>(null) }
    var drag by remember { mutableStateOf<PlanDrag?>(null) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    val tierBounds = remember { mutableMapOf<Int, Rect>() }
    val coverBounds = remember { mutableMapOf<Int, Rect>() }
    val coverTargets = remember { mutableMapOf<Int, PlanCoverTarget>() }
    var poolBounds by remember { mutableStateOf(Rect.Zero) }
    var tierViewport by remember { mutableStateOf(Rect.Zero) }
    val tierHeight = 104.dp
    val coverHeight = 80.dp
    val pageScroll = rememberScrollState()
    val latestDrag by rememberUpdatedState(drag)
    val edge = with(LocalDensity.current) { 28.dp.toPx() }
    LaunchedEffect(drag?.book?.id) {
        while (currentCoroutineContext().isActive && latestDrag != null) {
            val pointer = latestDrag!!.pointer
            if (tierViewport.contains(pointer)) {
                val distance = when {
                    pointer.y < tierViewport.top + edge -> -edge / 5
                    pointer.y > tierViewport.bottom - edge -> edge / 5
                    else -> 0f
                }
                if (distance != 0f) pageScroll.scrollBy(distance)
            }
            delay(16)
        }
    }
    val haptic = LocalHapticFeedback.current
    val byId = remember(vm.statsBooks) { vm.statsBooks.associateBy { it.id } }
    val workloads = remember(vm.statsBooks, vm.allBookSessions, vm.collectionTypes) {
        val sessions = vm.allBookSessions.map { it.session }
        vm.statsBooks.associate { book ->
            book.id to bookPlanWorkload(book, bookReadingPace(book, sessions, vm.statsBooks, vm.collectionTypes).pagesPerHour)
        }
    }
    val pool = remember(vm.statsBooks, plan, filter, query) {
        val assigned = plan?.bookIds.orEmpty().toSet()
        vm.statsBooks.filter { it.id !in assigned && BookLibraryFilter.valueOf(filter).matches(it, null) &&
            (query.isBlank() || it.title.contains(query, true) || it.authors.contains(query, true)) }
            .sortedBy { it.title.lowercase() }
    }
    val finishDrag: () -> Unit = {
        val moving = drag
        if (moving != null && plan != null) {
            val tier = if (tierViewport.contains(moving.pointer)) tierBounds.entries.firstOrNull { it.value.contains(moving.pointer) }?.key else null
            if (tier != null) {
                val ids = plan.tiers[tier].bookIds.filter { it != moving.book.id }
                val position = ids.indexOfFirst { id -> coverBounds[id]?.center?.x?.let { moving.pointer.x < it } == true }
                    .let { if (it < 0) ids.size else it }
                vm.saveReadingPlan(plan.move(moving.book.id, tier, position))
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } else if (poolBounds.contains(moving.pointer)) vm.saveReadingPlan(plan.move(moving.book.id, null))
        }
        drag = null
    }
    val drop by rememberUpdatedState(finishDrag)

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)
                .onGloballyPositioned { rootOrigin = it.localToRoot(Offset.Zero) }
                .pointerInput(plan?.id) {
                    // The host owns the gesture even when scrolling moves the source cover.
                    // Once lifted, consume in Initial so neither nested scroller steals it.
                    awaitEachGesture {
                        // Start after the child's click handler has seen DOWN. Starting
                        // in Initial would mistake that consumed DOWN for a cancelled hold.
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                        val point = rootOrigin + down.position
                        val target = coverTargets.values.firstOrNull { it.visibleBounds.contains(point) }
                        if (target == null || !tierViewport.contains(point)) return@awaitEachGesture
                        val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        drag = PlanDrag(target.book, rootOrigin + held.position, point - target.origin)
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == held.id } ?: break
                                drag = drag?.copy(pointer = rootOrigin + change.position)
                                change.consume()
                                if (!change.pressed) { drop(); break }
                            }
                        } finally { drag = null }
                    }
                }) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (plan != null) IconButton(onClick = { selectedId = null }) {
                            Icon(painterResource(R.drawable.ic_collections_24), "All reading plans", tint = MaterialTheme.colorScheme.primary)
                        }
                        Text("Reading plans", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = onClose) { Text("Close") }
                    }
                    if (vm.readingPlanError.isNotBlank()) Text(vm.readingPlanError, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                    if (plan == null) {
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(vm.readingPlans, key = { it.id }) { saved ->
                                Row(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                    .fittedClickable { selectedId = saved.id }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(saved.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text("${saved.bookIds.size} ${if (saved.bookIds.size == 1) "book" else "books"}${if (saved.active && vm.libraryFilter == BookLibraryFilter.PLAN) " · Applied" else ""}",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        PlanWorkloadLine(bookPlanWorkload(saved.bookIds, workloads))
                                    }
                                    Icon(painterResource(R.drawable.ic_chevron_right_24), null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        Button(onClick = { naming = true }, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            Text("New plan")
                        }
                    } else {
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()
                            .onGloballyPositioned { tierViewport = it.boundsInRoot() }) {
                            val poolHeight = (maxHeight * 0.6f).coerceIn(220.dp, 400.dp)
                            Column(Modifier.fillMaxWidth().verticalScroll(pageScroll), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(plan.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                    IconButton(onClick = { naming = true }) { Icon(painterResource(R.drawable.ic_edit_24), "Rename plan") }
                                    TextButton(onClick = { deleting = true }, enabled = !vm.readingPlanBusy) { Text("Delete") }
                                }
                                PlanWorkloadLine(bookPlanWorkload(plan.bookIds, workloads), Modifier.fillMaxWidth().padding(bottom = 4.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    plan.tiers.forEachIndexed { tierIndex, tier -> key(tierIndex) {
                                        DisposableEffect(tierIndex) { onDispose { tierBounds.remove(tierIndex) } }
                                        val highlighted = drag?.pointer?.let { tierBounds[tierIndex]?.contains(it) } == true
                                        Row(Modifier.fillMaxWidth().height(tierHeight)
                                            .onGloballyPositioned { tierBounds[tierIndex] = it.boundsInRoot() }
                                            .border(if (highlighted) 2.dp else 1.dp,
                                                if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                            .clip(RoundedCornerShape(6.dp)), verticalAlignment = Alignment.CenterVertically) {
                                            val color = listOf(Color(0xFFEF7777), Color(0xFFECA765), Color(0xFFE3C866), Color(0xFF9DCB7B), Color(0xFF6DBCB7), Color(0xFF8D9DD8))[tierIndex]
                                            Box(Modifier.width(42.dp).height(tierHeight).background(color.copy(alpha = 0.3f))
                                                .fittedClickable(RoundedCornerShape(0.dp)) { renameTier = tierIndex }
                                                .semantics { contentDescription = "Rename tier ${tier.name}" }, contentAlignment = Alignment.Center) {
                                                Text(tier.name, Modifier.padding(4.dp), maxLines = 3, overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            }
                                            Column(Modifier.weight(1f).fillMaxHeight().padding(3.dp)) {
                                                PlanWorkloadLine(bookPlanWorkload(tier.bookIds, workloads),
                                                    Modifier.fillMaxWidth().height(18.dp).padding(horizontal = 2.dp), compact = true)
                                                Row(Modifier.fillMaxWidth().weight(1f).horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                                tier.bookIds.mapNotNull(byId::get).forEach { book -> key(book.id) {
                                                    PlanCover(book, Modifier.width(coverHeight * 0.67f).height(coverHeight).alpha(if (drag?.book?.id == book.id) 0.25f else 1f),
                                                        onClick = { moveBook = book }, onBounds = { coverBounds[book.id] = it },
                                                        onTarget = { target -> if (target == null) coverTargets.remove(book.id) else coverTargets[book.id] = target })
                                                } }
                                                if (tier.bookIds.isEmpty()) Text("", Modifier.width(40.dp))
                                                }
                                            }
                                        }
                                    } }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Books", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                    Row(Modifier.fittedClickable { chooseFilter = true }.padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(BookLibraryFilter.valueOf(filter).label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                        Icon(painterResource(R.drawable.ic_chevron_right_24), "Filter books", Modifier.size(18.dp).rotate(90f), tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { showSearch = !showSearch; if (!showSearch) query = "" }) {
                                        Icon(painterResource(R.drawable.ic_search_24), "Search books", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                if (showSearch) OutlinedTextField(query, { query = it }, label = { Text("Search books") }, singleLine = true,
                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                                Box(Modifier.height(poolHeight).fillMaxWidth().onGloballyPositioned { poolBounds = it.boundsInRoot() }
                                    .then(if (drag != null) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp)) else Modifier)) {
                                    LazyVerticalGrid(GridCells.Adaptive(56.dp), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                                        items(pool, key = { it.id }) { book ->
                                            PlanCover(book, Modifier.fillMaxWidth().aspectRatio(0.67f).alpha(if (drag?.book?.id == book.id) 0.25f else 1f),
                                                onClick = { moveBook = book }, onBounds = {},
                                                onTarget = { target -> if (target == null) coverTargets.remove(book.id) else coverTargets[book.id] = target })
                                        }
                                    }
                                    if (pool.isEmpty()) Text("No books match", Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Button(onClick = { vm.applyReadingPlan(plan.id, onClose) }, enabled = plan.bookIds.isNotEmpty() && !vm.readingPlanBusy && vm.readingPlanError.isBlank(),
                            shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(48.dp)) {
                            Text(if (vm.readingPlanBusy) "Saving…" else "Apply plan · ${plan.bookIds.size}")
                        }
                    }
                }
                drag?.let { moving ->
                    BookCoverImage(moving.book.coverUrl, moving.book.title, Modifier.zIndex(10f)
                        .offset { IntOffset((moving.pointer.x - moving.grab.x - rootOrigin.x).roundToInt(),
                            (moving.pointer.y - moving.grab.y - rootOrigin.y).roundToInt()) }
                        .width(60.dp).height(90.dp).border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp)), maxDimensionPx = 256)
                }
            }
        }
    }
    if (naming) PlanNameDialog(if (plan == null) "New plan" else "Rename plan", plan?.name.orEmpty(), onClose = { naming = false }) { name ->
        val next = plan?.copy(name = name) ?: BookReadingPlan(name = name)
        vm.saveReadingPlan(next); selectedId = next.id; naming = false
    }
    if (chooseFilter) PlanActionDialog("Show books", onClose = { chooseFilter = false }) {
        listOf(BookLibraryFilter.ALL, BookLibraryFilter.NOT_STARTED, BookLibraryFilter.READING,
            BookLibraryFilter.ON_HOLD, BookLibraryFilter.FINISHED).forEach { status ->
            Row(Modifier.fillMaxWidth().fittedClickable { filter = status.name; chooseFilter = false }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(status.label, Modifier.weight(1f), color = if (filter == status.name) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                if (filter == status.name) Icon(painterResource(R.drawable.ic_check_24), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
    renameTier?.let { tier -> plan?.let { selected ->
        PlanNameDialog("Tier name", selected.tiers[tier].name, onClose = { renameTier = null }) { name ->
            vm.saveReadingPlan(selected.copy(tiers = selected.tiers.mapIndexed { index, row -> if (index == tier) row.copy(name = name) else row }))
            renameTier = null
        }
    } }
    if (deleting && plan != null) PlanActionDialog("Delete ${plan.name}?", onClose = { deleting = false }) {
        Text("The books stay in your library.", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { vm.deleteReadingPlan(plan.id); deleting = false }) { Text("Delete plan", color = MaterialTheme.colorScheme.error) }
    }
    moveBook?.let { book -> plan?.let { selected ->
        PlanActionDialog(book.title, onClose = { moveBook = null }) {
            selected.tiers.forEachIndexed { index, tier ->
                TextButton(onClick = { vm.saveReadingPlan(selected.move(book.id, index)); moveBook = null }, modifier = Modifier.fillMaxWidth()) { Text(tier.name) }
            }
            if (book.id in selected.bookIds) TextButton(onClick = { vm.saveReadingPlan(selected.move(book.id, null)); moveBook = null }) { Text("Return to books") }
        }
    } }
}

@Composable
private fun PlanWorkloadLine(workload: BookPlanWorkload, modifier: Modifier = Modifier, compact: Boolean = false) {
    val label = remember(workload) {
        when {
            workload.bookCount == 0 -> "No books"
            workload.complete -> "Completed"
            else -> {
                val pages = if (workload.remainingPages == 0L && workload.unknownLengths > 0) "Pages unknown" else
                    "${String.format(java.util.Locale.getDefault(), "%,d", workload.remainingPages)}${if (workload.unknownLengths > 0) "+" else ""} pages left"
                val time = workload.remainingSeconds?.let { "~${bookFormatCompactHours(it)}" } ?: "Time unavailable"
                "$pages · $time"
            }
        }
    }
    Text(label, modifier, style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun PlanCover(book: BookRow, modifier: Modifier, onClick: () -> Unit, onBounds: (Rect) -> Unit,
    onTarget: (PlanCoverTarget?) -> Unit) {
    val register by rememberUpdatedState(onTarget)
    DisposableEffect(book.id) { onDispose { register(null) } }
    BookCoverImage(book.coverUrl, book.title, modifier.onGloballyPositioned {
        onBounds(it.boundsInRoot())
        // The visible rectangle is for hit testing; the unclipped origin anchors the
        // floating cover correctly even when the source is partially scrolled out.
        register(PlanCoverTarget(book, it.boundsInRoot(), it.localToRoot(Offset.Zero)))
    }.fittedClickable(RoundedCornerShape(5.dp), onClick = onClick)
        .semantics { contentDescription = book.title }, maxDimensionPx = 256)
}

@Composable
private fun PlanNameDialog(title: String, initial: String, onClose: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    PlanActionDialog(title, onClose) {
        OutlinedTextField(text, { text = it.take(80) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
        Button(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank(), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) { Text("Save") }
    }
}

@Composable
private fun PlanActionDialog(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp).imePadding(), shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = onClose) { Text("Cancel") }
                }
                content()
            }
        }
    }
}
