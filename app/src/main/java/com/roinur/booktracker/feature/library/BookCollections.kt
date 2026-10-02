package com.roinur.booktracker

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import java.util.Locale

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
internal fun BookLibraryCollections(vm: BookTrackerViewModel, onOpenProgress: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var missingMenu by remember { mutableStateOf(false) }
    var changing by remember { mutableStateOf<Set<String>?>(null) }
    val bounds = remember { mutableMapOf<String, Rect>() }
    val coordinates = remember { mutableMapOf<String, LayoutCoordinates>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dropTarget by remember { mutableStateOf<String?>(null) }
    val dragThreshold = with(LocalDensity.current) { 8.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(8.dp)
    val colors = MaterialTheme.colorScheme
    val selectedNames = vm.collectionSelectedNames
    val mixedNames = if (vm.collectionEditing) vm.collectionMixedNames else emptySet()
    val undoChip: @Composable () -> Unit = {
        FilterChip(selected = false, enabled = !vm.collectionBusy, onClick = vm::undoCollectionEdit,
            label = { Text("Undo", color = colors.primary) })
    }
    val collectionIcon: @Composable () -> Unit = {
        IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(40.dp)) {
            Icon(painterResource(R.drawable.ic_collections_24), "Collections menu",
                modifier = Modifier.size(24.dp), tint = colors.primary)
        }
    }
    val collectionChip: @Composable (String) -> Unit = { name ->
        val isDragging = dragging == name
        val mixed = name.lowercase(Locale.ROOT) in mixedNames
        FilterChip(selected = dropTarget == name || if (vm.collectionEditing)
            name.lowercase(Locale.ROOT) in selectedNames else vm.libraryCollection.equals(name, true),
            onClick = { vm.applyCollectionSuggestion(name) }, enabled = !vm.collectionBusy,
            colors = FilterChipDefaults.filterChipColors(
                containerColor = if (mixed) colors.primary.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent,
                labelColor = if (mixed) colors.primary else colors.onSurfaceVariant),
            label = { Text(name) }, modifier = Modifier
                .semantics { if (mixed) stateDescription = "Some selected books" }
                .zIndex(if (isDragging) 2f else 0f)
                .graphicsLayer {
                    translationX = if (isDragging) dragOffset.x else 0f
                    translationY = if (isDragging) dragOffset.y else 0f
                    scaleX = if (isDragging) 1.04f else 1f; scaleY = scaleX
                    alpha = if (isDragging) 0.85f else 1f
                }
                .onGloballyPositioned { bounds[name] = it.boundsInRoot(); coordinates[name] = it }
                .pointerInput(name, vm.collectionEditing, vm.collectionBusy, expanded) {
                    if (vm.collectionEditing && !vm.collectionBusy && expanded) awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val origin = coordinates[name]?.localToRoot(down.position) ?: down.position
                        var distance = Offset.Zero
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.any { it.id != down.id && it.pressed }) break
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val held = change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis
                                val point = coordinates[name]?.localToRoot(change.position) ?: change.position
                                distance = point - origin
                                if (!change.pressed) {
                                    val target = dropTarget
                                    if (dragging == name && target != null) {
                                        change.consume()
                                        changing = linkedSetOf(name, target)
                                    }
                                    else if (held && distance.getDistance() < dragThreshold) {
                                        change.consume()
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        changing = setOf(name)
                                    } else if (dragging == name) change.consume()
                                    break
                                }
                                if (!held && distance.getDistance() > viewConfiguration.touchSlop) break
                                if (held) {
                                    change.consume()
                                    if (dragging != name) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragging = name; dragOffset = distance
                                    dropTarget = bounds.entries.firstOrNull { it.key != name && it.value.contains(point) }?.key
                                }
                            }
                        } finally {
                            dragging = null; dropTarget = null; dragOffset = Offset.Zero
                        }
                    }
                })
    }
    Column(Modifier.animateContentSize()) {
        if (!expanded) {
            BookFadingRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item("collections_menu") { Box(Modifier.padding(top = 4.dp)) { collectionIcon() } }
                if (vm.collectionUndo != null) item("collections_undo") { undoChip() }
                items(vm.collectionSuggestions, key = { it.lowercase(Locale.ROOT) }) { name -> collectionChip(name) }
            }
        } else {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.align(Alignment.CenterVertically)) { collectionIcon() }
                Box {
                    FilterChip(selected = vm.libraryMissingType != null, enabled = !vm.collectionBusy,
                        onClick = { missingMenu = true },
                        label = { Text(vm.libraryMissingType?.let { "Without ${it.label}" } ?: "Unassigned") },
                        trailingIcon = { Icon(painterResource(R.drawable.ic_chevron_right_24), null,
                            Modifier.size(18.dp).rotate(90f)) })
                    DropdownMenu(missingMenu, { missingMenu = false },
                        modifier = Modifier.background(colors.surfaceContainerLow)) {
                        BookCollectionType.entries.forEach { type ->
                            Text("Without ${type.label}", Modifier.fillMaxWidth().fittedClickable(shape) {
                                vm.showWithoutCollectionType(type); missingMenu = false
                            }.padding(horizontal = 16.dp, vertical = 14.dp))
                        }
                        Text("All books", Modifier.fillMaxWidth().fittedClickable(shape) {
                            vm.clearCollectionFilter(); missingMenu = false
                        }.padding(horizontal = 16.dp, vertical = 14.dp))
                    }
                }
                FilterChip(selected = vm.collectionEditing, enabled = !vm.collectionBusy,
                    onClick = vm::editLibraryCollections,
                    label = { Text(if (vm.collectionBusy) "Saving…" else if (vm.collectionEditing)
                        "Done" + if (vm.collectionSelectedBooks.isEmpty()) "" else " (${vm.collectionSelectedBooks.size})" else "Edit") },
                    leadingIcon = { Icon(painterResource(if (vm.collectionEditing) R.drawable.ic_check_24 else R.drawable.ic_edit_24),
                        null, Modifier.size(18.dp)) })
                FilterChip(selected = false, onClick = onOpenProgress, enabled = !vm.collectionEditing,
                    label = { Text("Progress") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_collection_progress_24), null,
                        Modifier.size(18.dp), tint = colors.primary) })
                if (vm.collectionUndo != null) undoChip()
                vm.collectionSuggestions.forEach { name -> collectionChip(name) }
            }
        }
        if (vm.collectionError.isNotBlank()) Text(vm.collectionError, color = colors.error,
            style = MaterialTheme.typography.bodySmall)
    }
    changing?.let { names -> BookCollectionChangeDialog(vm, names) { changing = null } }
}

@Composable
private fun BookCollectionChangeDialog(vm: BookTrackerViewModel, names: Set<String>, onClose: () -> Unit) {
    val initialName = names.last()
    var target by remember(names) { mutableStateOf(initialName) }
    var type by remember(names) { mutableStateOf(vm.collectionTypes[initialName.lowercase(Locale.ROOT)] ?: BookCollectionType.OTHER) }
    var choosingType by remember { mutableStateOf(false) }
    val merging = names.size > 1 || vm.collectionSuggestions.any { it.equals(target.trim(), true) && it !in names }
    val shape = RoundedCornerShape(8.dp)
    AlertDialog(onDismissRequest = { if (!vm.collectionBusy) onClose() }, shape = shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        title = { Text(if (merging) "Merge collections" else "Edit collection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (merging) Text((names + target.trim()).filter { it.isNotBlank() }.distinct().joinToString(" + "))
                OutlinedTextField(target, { value ->
                    target = value
                    vm.collectionTypes[value.trim().lowercase(Locale.ROOT)]?.let { type = it }
                }, label = { Text("Name") },
                    shape = shape, singleLine = true, enabled = !vm.collectionBusy)
                Box {
                    OutlinedButton(onClick = { choosingType = true }, shape = shape,
                        modifier = Modifier.fillMaxWidth(), enabled = !vm.collectionBusy) {
                        Text(type.label, Modifier.weight(1f))
                        Icon(painterResource(R.drawable.ic_chevron_right_24), null, Modifier.size(20.dp).rotate(90f))
                    }
                    DropdownMenu(choosingType, { choosingType = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                        BookCollectionType.entries.forEach { option ->
                            Text(option.label, Modifier.fillMaxWidth().fittedClickable(shape) {
                                type = option; choosingType = false
                            }.padding(horizontal = 16.dp, vertical = 14.dp))
                        }
                    }
                }
                if (vm.collectionError.isNotBlank()) Text(vm.collectionError, color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = {
            TextButton(enabled = target.isNotBlank() && !vm.collectionBusy, onClick = {
                vm.changeCollections(names, target, type, onClose)
            }) { Text(if (vm.collectionBusy) "Saving…" else if (merging) "Merge" else "Save") }
        }, dismissButton = { TextButton(onClick = onClose, enabled = !vm.collectionBusy) { Text("Cancel") } })
}
