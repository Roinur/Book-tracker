package com.roinur.booktracker

import androidx.compose.material3.Text

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookDurationPickerDialog(initialMinutes: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var hours by remember { mutableStateOf((initialMinutes / 60L).toString()) }
    var minutes by remember { mutableStateOf((initialMinutes % 60L).toString()) }
    var keyboard by remember { mutableStateOf(initialMinutes >= 1440L) }
    var clock by remember { mutableStateOf(androidx.compose.material3.TimePickerState(
        initialHour = ((initialMinutes / 60L) % 24L).toInt(), initialMinute = (initialMinutes % 60L).toInt(), is24Hour = true)) }
    val baseHours = (hours.toLongOrNull() ?: 0L) / 24L * 24L
    val numericHours = hours.toLongOrNull()
    val numericMinutes = minutes.toLongOrNull()
    val valid = !keyboard || (numericHours != null && numericMinutes != null && numericMinutes in 0L..59L && numericHours * 60L + numericMinutes > 0L)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(20.dp).imePadding().heightIn(max = 620.dp)
            .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Time read", style = MaterialTheme.typography.titleLarge)
            if (keyboard) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(hours, { hours = it.filter(Char::isDigit).take(6) }, label = { Text("Hours") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(2) }, label = { Text("Minutes") },
                        singleLine = true, isError = numericMinutes != null && numericMinutes !in 0L..59L,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
            } else {
                if (baseHours > 0L) Text("${baseHours + clock.hour}h ${clock.minute}m", color = MaterialTheme.colorScheme.primary)
                androidx.compose.material3.TimePicker(state = clock, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    if (keyboard) { clock = androidx.compose.material3.TimePickerState(((hours.toLongOrNull() ?: 0L) % 24L).toInt(), (minutes.toIntOrNull() ?: 0).coerceIn(0, 59), true) }
                    else { hours = (baseHours + clock.hour).toString(); minutes = clock.minute.toString() }
                    keyboard = !keyboard
                }) { Text(if (keyboard) "Clock" else "Keyboard") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(enabled = valid, onClick = {
                    val total = if (keyboard) numericHours!! * 60L + numericMinutes!! else (baseHours + clock.hour) * 60L + clock.minute
                    if (total > 0L) onSave(total)
                }) { Text("OK") }
            }
        }
    }
}

internal fun bookPickerContext(context: Context, colors: androidx.compose.material3.ColorScheme): Context {
    val color = colors.surface
    val dark = (color.red + color.green + color.blue) / 3f < 0.5f
    return android.view.ContextThemeWrapper(context, if (dark) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert)
}

internal fun android.app.AlertDialog.showBookStyle(colors: androidx.compose.material3.ColorScheme) {
    show()
    val density = context.resources.displayMetrics.density
    window?.setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
        setColor(colors.surface.toArgb())
        cornerRadius = 8f * density
        setStroke(density.toInt().coerceAtLeast(1), colors.primary.copy(alpha = 0.4f).toArgb())
    })
    listOf(android.content.DialogInterface.BUTTON_POSITIVE, android.content.DialogInterface.BUTTON_NEGATIVE, android.content.DialogInterface.BUTTON_NEUTRAL).forEach { which ->
        getButton(which)?.setTextColor(colors.primary.toArgb())
    }
}
