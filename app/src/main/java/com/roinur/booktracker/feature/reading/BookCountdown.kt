package com.roinur.booktracker

import androidx.compose.material3.Text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun BookCountdownDialog(
    book: BookRow,
    onDismiss: () -> Unit,
    onStart: (Int) -> Unit
) {
    val pickerColors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var minutesDraft by rememberSaveable { mutableStateOf("30") }
    var durationPickerOpen by remember { mutableStateOf(false) }
    fun showDurationPicker() { durationPickerOpen = true }
    if (durationPickerOpen) BookDurationPickerDialog(
        initialMinutes = minutesDraft.toLongOrNull() ?: 1L,
        onDismiss = { durationPickerOpen = false },
        onSave = { minutesDraft = it.toString(); durationPickerOpen = false }
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Reading countdown", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(book.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            PickerTextField(value = bookFormatDurationPickerLabel(minutesDraft), label = "Countdown length", onClick = ::showDurationPicker)
            Button(
                onClick = { onStart(minutesDraft.toIntOrNull()?.coerceAtLeast(1) ?: 30) },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Start countdown")
            }
        }
    }
}
