package com.roinur.booktracker

import androidx.compose.material3.Text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max

@Composable
internal fun BookReadingGoalsDialog(
    goals: BookReadingGoals,
    todayMinutes: Int,
    todayPages: Int,
    finishedThisYear: Int,
    monthMinutes: Int,
    monthPages: Int,
    onSave: (BookReadingGoals) -> Boolean,
    onDismiss: () -> Unit
) {
    var dailyMetric by remember { mutableStateOf(goals.dailyMetric) }
    var monthlyMetric by remember { mutableStateOf(goals.monthlyMetric) }
    var dailyMinutes by remember { mutableStateOf(goals.dailyMinutes.takeIf { it > 0 }?.toString().orEmpty()) }
    var dailyPages by remember { mutableStateOf(goals.dailyPages.takeIf { it > 0 }?.toString().orEmpty()) }
    var monthlyMinutes by remember { mutableStateOf(goals.monthlyMinutes.takeIf { it > 0 }?.toString().orEmpty()) }
    var monthlyPages by remember { mutableStateOf(goals.monthlyPages.takeIf { it > 0 }?.toString().orEmpty()) }
    var books by remember { mutableStateOf(goals.yearlyBooks.takeIf { it > 0 }?.toString().orEmpty()) }
    var error by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(16.dp).imePadding().heightIn(max = 680.dp)
            .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Reading goals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                BookGoalEditor("Daily", dailyMetric, { dailyMetric = it },
                    if (dailyMetric == BookGoalMetric.TIME) dailyMinutes else dailyPages,
                    { if (dailyMetric == BookGoalMetric.TIME) dailyMinutes = it else dailyPages = it },
                    if (dailyMetric == BookGoalMetric.TIME) todayMinutes else todayPages)
                BookGoalEditor("Monthly", monthlyMetric, { monthlyMetric = it },
                    if (monthlyMetric == BookGoalMetric.TIME) monthlyMinutes else monthlyPages,
                    { if (monthlyMetric == BookGoalMetric.TIME) monthlyMinutes = it else monthlyPages = it },
                    if (monthlyMetric == BookGoalMetric.TIME) monthMinutes else monthPages)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Yearly", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(value = books, onValueChange = { books = it.filter(Char::isDigit).take(6) },
                        label = { Text("Books") }, singleLine = true, shape = RoundedCornerShape(6.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    BookGoalProgress("This year", finishedThisYear, books.toIntOrNull() ?: 0)
                }
                if (error) Text("Could not save goals. Try again.", color = MaterialTheme.colorScheme.error)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = {
                    error = !onSave(goals.copy(dailyMinutes = dailyMinutes.toIntOrNull() ?: 0, dailyPages = dailyPages.toIntOrNull() ?: 0,
                        monthlyMinutes = monthlyMinutes.toIntOrNull() ?: 0, monthlyPages = monthlyPages.toIntOrNull() ?: 0,
                        yearlyBooks = books.toIntOrNull() ?: 0, dailyMetric = dailyMetric, monthlyMetric = monthlyMetric))
                }, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) { Text("Save") }
            }
        }
    }
}

@Composable
internal fun BookGoalEditor(title: String, metric: BookGoalMetric, onMetric: (BookGoalMetric) -> Unit,
    value: String, onValue: (String) -> Unit, current: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BookGoalMetric.entries.forEach { option ->
                    FilterChip(selected = metric == option, onClick = { onMetric(option) }, label = { Text(option.label) }, shape = RoundedCornerShape(6.dp))
                }
            }
        }
        OutlinedTextField(value = value, onValueChange = { onValue(it.filter(Char::isDigit).take(6)) },
            label = { Text(metric.label) }, singleLine = true, shape = RoundedCornerShape(6.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        BookGoalProgress(if (title == "Daily") "Today" else "This month", current, value.toIntOrNull() ?: 0)
    }
}

@Composable
internal fun BookGoalProgress(label: String, current: Int, target: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(if (target > 0) "$current / $target" else "Not set", style = MaterialTheme.typography.labelLarge)
        }
        LinearProgressIndicator(
            progress = { if (target <= 0) 0f else (current.toFloat() / target.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
