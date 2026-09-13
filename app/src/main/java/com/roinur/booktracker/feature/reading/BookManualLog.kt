package com.roinur.booktracker

import androidx.compose.material3.Text

import android.app.DatePickerDialog
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun BookManualLogDialog(
    book: BookRow,
    initialMode: String,
    initialKind: BookNoteKind,
    onDismiss: () -> Unit,
    onSaveNote: (BookNoteKind, String, String, String, String) -> Unit,
    onSaveSession: (String, String, String) -> Unit
) {
    val pickerColors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var mode by rememberSaveable(initialMode) { mutableStateOf(initialMode) }
    var kindName by rememberSaveable(initialKind.name) { mutableStateOf(initialKind.name) }
    var dateDraft by rememberSaveable { mutableStateOf(bookNowEditableDate()) }
    var notePageDraft by rememberSaveable(book.id) { mutableStateOf(book.currentPage.takeIf { it > 0 }?.toString().orEmpty()) }
    var noteDraft by rememberAutoSavedBookNote("manual_${book.id}", book.id, { dateDraft }, { notePageDraft.toIntOrNull() ?: book.currentPage }, { runCatching { BookNoteKind.valueOf(kindName) }.getOrDefault(initialKind) })
    var sessionMinutesDraft by rememberSaveable { mutableStateOf("10") }
    var pageReachedDraft by rememberSaveable(book.id) { mutableStateOf(book.currentPage.takeIf { it > 0 }?.toString().orEmpty()) }
    val kind = runCatching { BookNoteKind.valueOf(kindName) }.getOrDefault(BookNoteKind.NOTE)
    var durationPickerOpen by remember { mutableStateOf(false) }
    fun showDateTimePicker() {
        val current = bookEditableDateToLocal(dateDraft)
        DatePickerDialog(
            bookPickerContext(context, pickerColors),
            { _, year, month, day ->
                dateDraft = LocalDateTime.of(year, month + 1, day, current.hour, current.minute)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US))
                durationPickerOpen = true
            },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth
        ).showBookStyle(pickerColors)
    }
    fun showDurationPicker() { durationPickerOpen = true }
    if (durationPickerOpen) BookDurationPickerDialog(
        initialMinutes = sessionMinutesDraft.toLongOrNull() ?: 1L,
        onDismiss = { durationPickerOpen = false },
        onSave = { sessionMinutesDraft = it.toString(); durationPickerOpen = false }
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(14.dp)
                .animateContentSize(animationSpec = tween(220, easing = FastOutSlowInEasing)),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Manual log", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(book.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("NOTE" to "Note", "SESSION" to "Session").forEach { (value, label) ->
                    val selected = if (value == "SESSION") mode == "SESSION" else mode != "SESSION"
                    Surface(
                        onClick = { mode = value },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(vertical = 10.dp),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            PickerTextField(value = dateDraft, label = "Date", onClick = ::showDateTimePicker)
            if (mode == "SESSION") {
                PickerTextField(value = bookFormatDurationPickerLabel(sessionMinutesDraft), label = "Time read", onClick = ::showDurationPicker)
                OutlinedTextField(
                    value = pageReachedDraft,
                    onValueChange = { pageReachedDraft = it.filter(Char::isDigit).take(6) },
                    label = { Text("Page reached") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { onSaveSession(dateDraft, sessionMinutesDraft, pageReachedDraft) },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Save session")
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(BookNoteKind.entries.filter { it != BookNoteKind.COUNTDOWN }, key = { it.name }) { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kindName = option.name },
                            label = { Text(option.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = notePageDraft,
                    onValueChange = { notePageDraft = it.filter(Char::isDigit).take(6) },
                    label = { Text("Page") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    label = { Text(kind.prompt) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { onSaveNote(kind, dateDraft, notePageDraft, "0", noteDraft) },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Save ${kind.label.lowercase(Locale.US)}")
                }
            }
        }
    }
}
