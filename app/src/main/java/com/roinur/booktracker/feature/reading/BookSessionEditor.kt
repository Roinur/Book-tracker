package com.roinur.booktracker

import androidx.compose.material3.Text

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun BookSessionEditDialog(
    session: BookReadingSessionRow,
    onDismiss: () -> Unit,
    onExcludedChange: (Boolean) -> Unit,
    onSave: (String, String, String, Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val excluded = session.excludeFromStatistics
    val pickerColors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var endedDraft by remember(session.id) { mutableStateOf(bookFormatEditableDate(session.activityAt)) }
    var minutesDraft by remember(session.id) { mutableStateOf((session.durationSeconds / 60L).coerceAtLeast(1L).toString()) }
    var pageReachedDraft by remember(session.id) { mutableStateOf(session.pageReached.toString()) }
    var showDeleteConfirm by rememberSaveable(session.id) { mutableStateOf(false) }
    var durationPickerOpen by remember { mutableStateOf(false) }
    fun showDateTimePicker() {
        val current = bookEditableDateToLocal(endedDraft)
        DatePickerDialog(
            bookPickerContext(context, pickerColors),
            { _, year, month, day ->
                endedDraft = LocalDateTime.of(year, month + 1, day, current.hour, current.minute)
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
        initialMinutes = minutesDraft.toLongOrNull() ?: 1L,
        onDismiss = { durationPickerOpen = false },
        onSave = { minutesDraft = it.toString(); durationPickerOpen = false }
    )
    if (showDeleteConfirm) {
        AlertDialog(
            shape = RoundedCornerShape(8.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete reading session?") },
            text = { Text("This removes the session and recalculates this book's pages and read time.") },
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
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Edit reading session", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            PickerTextField(value = endedDraft, label = "Date", onClick = ::showDateTimePicker)
            PickerTextField(value = bookFormatDurationPickerLabel(minutesDraft), label = "Time read", onClick = ::showDurationPicker)
            OutlinedTextField(
                value = pageReachedDraft,
                onValueChange = { pageReachedDraft = it.filter(Char::isDigit).take(6) },
                label = { Text("Page reached") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth().fittedClickable(RoundedCornerShape(8.dp)) { onExcludedChange(!excluded) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = excluded, onCheckedChange = null)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("Exclude from date statistics", style = MaterialTheme.typography.bodyMedium)
                    Text("Keep pages and time in totals", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onSave(endedDraft, minutesDraft, pageReachedDraft, excluded) }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
