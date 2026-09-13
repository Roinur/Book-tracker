package com.roinur.booktracker

import androidx.compose.material3.Text

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

@Composable
internal fun BookNotesDialog(
    notes: List<BookNote>,
    filterKind: BookNoteKind?,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onOpenNote: (BookNote) -> Unit
) {
    val context = LocalContext.current
    val visibleNotes = remember(notes, filterKind) {
        filterKind?.let { kind -> notes.filter { it.kind == kind } } ?: notes
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.78f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(14.dp)
                .animateContentSize(animationSpec = tween(220, easing = FastOutSlowInEasing)),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(filterKind?.buttonLabel ?: "Reading notes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        enabled = notes.isNotEmpty(),
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("Reading notes", formatBookNotesForClipboard(notes))
                            )
                            Toast.makeText(context, "Copied ${notes.size} reading notes", Toast.LENGTH_SHORT).show()
                        }
                    ) { Text("Copy") }
                    TextButton(onClick = onAdd) { Text("Add") }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (visibleNotes.isEmpty()) {
                    item { Text("No session notes yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(visibleNotes, key = { it.id }) { note ->
                        val rowShape = RoundedCornerShape(8.dp)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(rowShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .fittedClickable(rowShape) { onOpenNote(note) }
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("${note.kind.label} - ${bookFormatIsoDate(note.createdAt)} - page ${note.page}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(note.note, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BookSessionsDialog(
    sessions: List<BookReadingSessionRow>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (BookReadingSessionRow) -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.78f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(14.dp)
                .animateContentSize(animationSpec = tween(220, easing = FastOutSlowInEasing)),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Reading sessions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onAdd) { Text("Add") }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (sessions.isEmpty()) {
                    item { Text("No reading sessions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(sessions, key = { it.id }) { session ->
                        val rowShape = RoundedCornerShape(8.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(rowShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .fittedClickable(rowShape) { onEdit(session) }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(bookFormatSessionDate(session), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                if (session.excludeFromStatistics) Text("Excluded from date statistics", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text("${bookFormatSessionDuration(session)} - ${session.pagesRead} pages - reached ${session.pageReached}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("Edit", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BookNoteEditDialog(
    note: BookNote,
    onDismiss: () -> Unit,
    onSave: (String, String, String, (Boolean) -> Unit) -> Unit,
    onDelete: () -> Unit
) {
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }
    val pickerColors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var dateDraft by remember(note.id) { mutableStateOf(bookFormatEditableDate(note.createdAt)) }
    var pageDraft by remember(note.id) { mutableStateOf(note.page.toString()) }
    val draftPrefs = context.getSharedPreferences("book_note_drafts", Context.MODE_PRIVATE)
    val draftKey = "edit_${note.id}"
    var noteDraft by rememberAutoSavedBookNote(draftKey, note.bookId, { dateDraft }, { pageDraft.toIntOrNull() ?: note.page }, { note.kind }, initial = note.note, noteId = note.id, originalDate = note.createdAt)
    var showDeleteConfirm by rememberSaveable(note.id) { mutableStateOf(false) }
    fun showDateTimePicker() {
        val current = bookEditableDateToLocal(dateDraft)
        DatePickerDialog(
            bookPickerContext(context, pickerColors),
            { _, year, month, day ->
                TimePickerDialog(
                    bookPickerContext(context, pickerColors),
                    { _, hour, minute ->
                        dateDraft = LocalDateTime.of(year, month + 1, day, hour, minute)
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US))
                    },
                    current.hour,
                    current.minute,
                    true
                ).showBookStyle(pickerColors)
            },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth
        ).showBookStyle(pickerColors)
    }
    if (showDeleteConfirm) {
        AlertDialog(
            shape = RoundedCornerShape(8.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${note.kind.label.lowercase(Locale.US)}?") },
            text = { Text("This removes it permanently from this book.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .imePadding()
                .heightIn(max = 720.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Reading note", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            PickerTextField(value = dateDraft, label = "Date", onClick = ::showDateTimePicker)
            OutlinedTextField(
                value = pageDraft,
                onValueChange = { pageDraft = it.filter(Char::isDigit).take(6) },
                label = { Text("Page") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = noteDraft,
                onValueChange = { noteDraft = it; draftPrefs.edit().putString(draftKey, it).commit() },
                label = { Text("Note") },
                minLines = 1,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
            )
            if (saveError) Text("Could not save. Check the date and try again.", color = MaterialTheme.colorScheme.error)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Reading note", noteDraft))
                        }
                    ) {
                        Text("Copy")
                    }
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(enabled = !saving, onClick = { saving = true; onSave(dateDraft, pageDraft, noteDraft) { ok -> saving = false; saveError = !ok; if (ok) draftPrefs.edit().remove(draftKey).commit() } }) {
                        Text(if (saving) "Saving" else "Save")
                    }
                }
            }
        }
    }
}

@Composable
internal fun BookGlobalNotesDialog(
    notes: List<BookNoteWithBook>,
    filterKind: BookNoteKind?,
    onDismiss: () -> Unit,
    onOpenNote: (BookNote) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visibleNotes = remember(notes, filterKind, query) {
        if (query.isBlank()) filterKind?.let { kind -> notes.filter { it.note.kind == kind } } ?: notes
        else notes.filter { it.note.note.contains(query.trim(), ignoreCase = true) || it.bookTitle.contains(query.trim(), ignoreCase = true) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.78f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Global ${filterKind?.buttonLabel ?: "Reading notes"}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("Search all notes") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (visibleNotes.isEmpty()) {
                    item { Text("No global notes yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(visibleNotes, key = { it.note.id }) { row ->
                        val note = row.note
                        val rowShape = RoundedCornerShape(8.dp)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(rowShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .fittedClickable(rowShape) { onOpenNote(note) }
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(row.bookTitle.ifBlank { "Unknown book" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${note.kind.label} - ${bookFormatIsoDate(note.createdAt)} - page ${note.page}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(note.note, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BookGlobalSessionsDialog(
    title: String = "Reading sessions",
    sessions: List<BookReadingSessionWithBook>,
    onDismiss: () -> Unit,
    onEdit: (BookReadingSessionRow) -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.78f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (sessions.isEmpty()) {
                    item { Text("No reading sessions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(sessions, key = { it.session.id }) { row ->
                        val session = row.session
                        val rowShape = RoundedCornerShape(8.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(rowShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .fittedClickable(rowShape) { onEdit(session) }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(row.bookTitle.ifBlank { "Unknown book" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (session.excludeFromStatistics) Text("Excluded from date statistics", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text("${bookFormatSessionDate(session)} - ${bookFormatSessionDuration(session)} - ${session.pagesRead} pages - reached ${session.pageReached}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("Edit", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
