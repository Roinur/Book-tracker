package com.roinur.booktracker

import com.roinur.booktracker.BookFitText as Text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription

@Composable
internal fun BookAddScreen(
    vm: BookTrackerViewModel,
    editing: Boolean,
    onScan: () -> Unit,
    onPickCover: () -> Unit,
    onFindCover: () -> Unit,
    onSaved: (Int) -> Unit
) {
    var showNewCollectionDialog by rememberSaveable { mutableStateOf(false) }
    var newCollectionDraft by rememberSaveable { mutableStateOf("") }
    var showRemoveConfirm by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BookPanel(contentPadding = PaddingValues(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier.width(92.dp).height(136.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .fittedClickable(RoundedCornerShape(12.dp), onClick = onPickCover)
                ) {
                    BookCoverImage(vm.draftCoverUrl, vm.titleInput.ifBlank { "BK" }, Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        BookActionTile(R.drawable.ic_link_24, if (vm.fetching) "Fetching…" else "Fetch ISBN", true, !vm.fetching, vm::fetchIsbn, Modifier.weight(1f))
                        BookActionTile(R.drawable.ic_scan_24, "Scan", false, true, onScan, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        BookActionTile(R.drawable.ic_search_24, "Find cover", false, !vm.fetching, onFindCover, Modifier.weight(1f))
                        BookActionTile(R.drawable.ic_image_24, "Gallery", false, true, onPickCover, Modifier.weight(1f))
                    }
                }
            }
        }

        BookFormTextField(
            value = vm.isbnInput,
            onValueChange = vm::updateIsbnInput,
            label = "ISBN",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.fetchIsbn() })
        )
        BookFormTextField(
            value = vm.titleInput,
            onValueChange = vm::updateTitleInput,
            label = "Title"
        )
        BookFormTextField(
            value = vm.authorsInput,
            onValueChange = vm::updateAuthorsInput,
            label = "Author"
        )
        BookFormTextField(
            value = vm.pageCountInput,
            onValueChange = vm::updatePageCountInput,
            label = "Total pages",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        BookCollectionPicker(
            selected = splitBookCollections(vm.collectionsInput),
            suggestions = vm.collectionSuggestions,
            onAddExisting = vm::addCollectionToDraft,
            onRemove = vm::removeCollectionFromDraft,
            onCreate = {
                newCollectionDraft = ""
                showNewCollectionDialog = true
            }
        )
        Button(
            onClick = { vm.addManualBook(onSaved) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(if (editing) "Save changes" else "Save book")
        }
        if (!vm.statusMessage.startsWith("Editing ")) {
            Text(
                text = vm.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
        if (editing) {
            OutlinedButton(
                onClick = { showRemoveConfirm = true },
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text("Remove book")
            }
        }
    }
    if (showNewCollectionDialog) {
        AlertDialog(
            shape = RoundedCornerShape(8.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
            onDismissRequest = { showNewCollectionDialog = false },
            title = { Text("New collection") },
            text = {
                OutlinedTextField(
                    value = newCollectionDraft,
                    onValueChange = { newCollectionDraft = it },
                    label = { Text("Collection name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addCollectionToDraft(newCollectionDraft)
                    showNewCollectionDialog = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showNewCollectionDialog = false }) { Text("Cancel") }
            }
        )
    }
    if (showRemoveConfirm) {
        AlertDialog(
            shape = RoundedCornerShape(8.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove book?") },
            text = { Text("This removes the book, its sessions, and its reading notes.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.editingBookId?.let { vm.deleteBook(it) }
                    showRemoveConfirm = false
                    onSaved(0)
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
internal fun BookActionTile(
    iconRes: Int,
    label: String,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    val container = if (filled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (filled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .height(64.dp)
            .clip(shape)
            .background(container.copy(alpha = if (enabled) 1f else 0.48f))
            .border(1.dp, if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .fittedClickable(shape, enabled = enabled, onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(21.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
    }
}

