package com.roinur.booktracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

@Composable
internal fun BookGoalDialog(book: BookRow, goal: BookGoal?, sessions: List<BookReadingSessionRow>,
                            books: List<BookRow>, collectionTypes: Map<String, BookCollectionType>,
                            onSave: (BookGoal, (Boolean) -> Unit) -> Unit, onRemove: ((Boolean) -> Unit) -> Unit, onDismiss: () -> Unit) {
    val today = LocalDate.now()
    var modeName by rememberSaveable(book.id) { mutableStateOf((goal?.mode ?: BookGoalMode.DEADLINE).name) }
    val mode = BookGoalMode.valueOf(modeName)
    var pages by rememberSaveable { mutableStateOf((goal?.dailyPages?.takeIf { it > 0 } ?: 50).toString()) }
    var minutes by rememberSaveable { mutableStateOf((goal?.dailyMinutes?.takeIf { it > 0 } ?: 30).toString()) }
    var amount by rememberSaveable { mutableStateOf((goal?.targetDate?.let { (ChronoUnit.DAYS.between(today, it) + 1).coerceAtLeast(1) } ?: 7).toString()) }
    var unit by rememberSaveable { mutableStateOf("Days") }
    var deadlineEdited by rememberSaveable { mutableStateOf(false) }
    var confirmingRemoval by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }
    val saved: (Boolean) -> Unit = { success -> saving = false; saveError = !success; if (success) onDismiss() }
    val value = when (mode) {
        BookGoalMode.DAILY_PAGES -> pages.toIntOrNull() ?: 0
        BookGoalMode.DAILY_MINUTES -> minutes.toIntOrNull() ?: 0
        BookGoalMode.DEADLINE -> amount.toIntOrNull() ?: 0
    }
    val deadline = if (mode != BookGoalMode.DEADLINE || value <= 0) null else when (unit) {
        "Weeks" -> today.plusWeeks(value.toLong()).minusDays(1)
        "Months" -> today.plusMonths(value.toLong()).minusDays(1)
        else -> today.plusDays(value.toLong() - 1)
    }
    val draft = BookGoal(book.id, mode, if (mode == BookGoalMode.DAILY_PAGES) value else 0,
        if (mode == BookGoalMode.DEADLINE) {
            if (!deadlineEdited && goal?.mode == BookGoalMode.DEADLINE) goal.targetDate else deadline
        } else null, goal?.createdDate ?: today, dailyMinutes = if (mode == BookGoalMode.DAILY_MINUTES) value else 0)
    val valid = value > 0 && (book.pageCount > 0 || mode == BookGoalMode.DAILY_MINUTES) &&
        book.status != BookStatus.FINISHED && (book.pageCount <= 0 || book.currentPage < book.pageCount)
    val progress = if (valid) bookGoalProgress(book, draft, sessions, books = books, collectionTypes = collectionTypes) else goal?.let { bookGoalProgress(book, it, sessions, books = books, collectionTypes = collectionTypes) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp).imePadding()) {
            Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).clip(shape).background(colors.surfaceContainerLow)
                .border(1.dp, colors.outlineVariant, shape).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (confirmingRemoval) "Remove book goal?" else "Book goal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(book.title, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
                    }
                    TextButton(onClick = { if (!saving) onDismiss() }) { Text("Close") }
                }
                if (saveError) Text("Could not save changes. Try again.", color = colors.error)
                if (confirmingRemoval) {
                    Text("Your reading sessions and pages will be kept.", color = colors.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { confirmingRemoval = false }, shape = shape, modifier = Modifier.weight(1f)) { Text("Keep goal") }
                        Button(onClick = { saving = true; onRemove(saved) }, enabled = !saving, shape = shape, modifier = Modifier.weight(1f)) { Text("Remove") }
                    }
                } else {
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(BookGoalMode.DEADLINE, BookGoalMode.DAILY_PAGES).forEach { option ->
                                val selected = if (option == BookGoalMode.DEADLINE) mode == option else mode != BookGoalMode.DEADLINE
                                Box(Modifier.weight(1f).height(40.dp).background(if (selected) colors.primaryContainer else colors.surface, shape)
                                    .border(1.dp, if (selected) colors.primary else colors.outline, shape)
                                    .fittedClickable(shape) { if (!selected) modeName = option.name }, contentAlignment = Alignment.Center) {
                                    Text(if (option == BookGoalMode.DEADLINE) "Finish in" else "Daily goal", style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant)
                                }
                            }
                        }
                        if (mode != BookGoalMode.DEADLINE) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(BookGoalMode.DAILY_PAGES to "Pages", BookGoalMode.DAILY_MINUTES to "Minutes").forEach { (option, label) ->
                                FilterChip(mode == option, onClick = { modeName = option.name }, label = { Text(label) }, shape = shape, modifier = Modifier.weight(1f))
                            }
                        }
                        OutlinedTextField(when (mode) { BookGoalMode.DAILY_PAGES -> pages; BookGoalMode.DAILY_MINUTES -> minutes; else -> amount },
                            onValueChange = { text -> val clean = text.filter(Char::isDigit).take(if (mode == BookGoalMode.DEADLINE) 3 else 6)
                                when (mode) { BookGoalMode.DAILY_PAGES -> pages = clean; BookGoalMode.DAILY_MINUTES -> minutes = clean
                                    else -> { amount = clean; deadlineEdited = true } } },
                            label = { Text(when (mode) { BookGoalMode.DAILY_PAGES -> "Pages per day"; BookGoalMode.DAILY_MINUTES -> "Minutes per day"; else -> unit }) },
                            shape = shape, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        if (mode == BookGoalMode.DEADLINE) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Days", "Weeks", "Months").forEach { option ->
                                FilterChip(unit == option, onClick = { unit = option; deadlineEdited = true }, label = { Text(option) }, shape = shape, modifier = Modifier.weight(1f))
                            }
                        }
                        if (book.pageCount <= 0 && mode != BookGoalMode.DAILY_MINUTES) Text("Add the book's total pages to set a goal.", color = colors.onSurfaceVariant)
                        else if (book.status == BookStatus.FINISHED || (book.pageCount > 0 && book.currentPage >= book.pageCount)) Text("Book completed", color = colors.primary)
                        if (progress != null) {
                            HorizontalDivider(color = colors.outlineVariant)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Today", color = colors.onSurfaceVariant)
                                Text(progress.todayProgressLabel, fontWeight = FontWeight.SemiBold)
                            }
                            LinearProgressIndicator(progress = { progress.todayFraction }, modifier = Modifier.fillMaxWidth().clip(shape))
                            BookGoalSummaryRow("Left today", progress.todayRemainingLabel + (if (progress.timeBased) "" else progress.todaySeconds?.let { " · ~${bookFormatCompactHours(it)}" }.orEmpty()))
                            BookGoalSummaryRow("To finish", if (progress.hasFinishEstimate) "${progress.remainingPages} pages · ${progress.daysRemaining} days" else "Not available yet")
                            BookGoalSummaryRow("Read time left", progress.remainingSeconds?.let { "~${bookFormatCompactHours(it)}" } ?: "Not available yet")
                            if (progress.hasFinishEstimate) BookGoalSummaryRow("Finish by", progress.expectedFinish.format(DateTimeFormatter.ofPattern("MMM d, yyyy")))
                            if (progress.overdue) Text("Deadline passed. Today's target covers the remaining pages.", style = MaterialTheme.typography.labelSmall, color = colors.primary)
                            if (progress.pagesPerHour != null) Text(String.format(java.util.Locale.US, "%.1f pages/h · %s", progress.pagesPerHour,
                                progress.paceSource.label), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (goal != null) OutlinedButton(onClick = { confirmingRemoval = true }, enabled = !saving, shape = shape, modifier = Modifier.weight(1f)) { Text("Remove goal") }
                        Button(onClick = { saving = true; onSave(draft, saved) }, enabled = valid && !saving, shape = shape, modifier = Modifier.weight(1f)) {
                            Text(if (saving) "Saving…" else if (goal == null) "Add goal" else "Save goal")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookGoalSummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}
