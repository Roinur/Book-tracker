package com.roinur.booktracker

import androidx.compose.material3.Text

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Composable
internal fun BookQuickNoteDialog(
    book: BookRow,
    kind: BookNoteKind,
    elapsedSeconds: Long,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    val pickerColors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var dateDraft by rememberSaveable { mutableStateOf(bookNowEditableDate()) }
    var pageDraft by rememberSaveable(book.id) { mutableStateOf(book.currentPage.takeIf { it > 0 }?.toString().orEmpty()) }
    var textDraft by rememberAutoSavedBookNote("quick_${book.id}_${kind.name}", book.id, { dateDraft }, { pageDraft.toIntOrNull() ?: book.currentPage }, { kind })
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
                Text("Add ${kind.label.lowercase(Locale.US)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            Text(book.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            PickerTextField(value = dateDraft, label = "Date", onClick = ::showDateTimePicker)
            OutlinedTextField(value = pageDraft, onValueChange = { pageDraft = it.filter(Char::isDigit).take(6) }, label = { Text("Page") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = textDraft, onValueChange = { textDraft = it }, label = { Text(kind.prompt) }, minLines = 5, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onSave(dateDraft, pageDraft, "0", textDraft) }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text("Save")
            }
        }
    }
}
