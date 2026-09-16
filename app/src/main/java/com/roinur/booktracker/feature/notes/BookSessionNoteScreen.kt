package com.roinur.booktracker

import com.roinur.booktracker.data.database.BookTrackerDatabase

import androidx.compose.material3.Text

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

internal fun bookLiveNoteKey(context: Context, scope: String): String {
    val prefs = context.getSharedPreferences("book_note_drafts", Context.MODE_PRIVATE)
    return prefs.getString("live_$scope", null) ?: java.util.UUID.randomUUID().toString().also {
        check(prefs.edit().putString("live_$scope", it).commit()) { "Could not save note identity." }
    }
}

@Composable
internal fun rememberAutoSavedBookNote(scope: String, bookId: Int, date: () -> String, page: () -> Int,
    kind: () -> BookNoteKind, initial: String = "", noteId: Int? = null, originalDate: String = "", durationSeconds: Long = 0L, liveKey: String? = null): androidx.compose.runtime.MutableState<String> {
    val context = LocalContext.current.applicationContext
    val prefs = remember { context.getSharedPreferences("book_note_drafts", Context.MODE_PRIVATE) }
    val db = remember(scope) { BookTrackerDatabase(context) }
    DisposableEffect(db) { onDispose { db.close() } }
    val currentDate = rememberUpdatedState(date)
    val currentPage = rememberUpdatedState(page)
    val currentKind = rememberUpdatedState(kind)
    val state = remember(scope) { mutableStateOf(prefs.getString(scope, initial).orEmpty()) }
    fun persist(value: String) {
        prefs.edit().putString(scope, value).commit()
        runCatching {
            val raw = currentDate.value()
            val iso = (if (originalDate.isNotBlank() && raw == bookFormatEditableDate(originalDate)) originalDate else null) ?: runCatching { Instant.parse(raw).toString() }.getOrNull()
                ?: parseBookSessionEditDate(raw)?.toString() ?: Instant.now().toString()
            if (noteId != null) db.updateReadingNote(noteId, iso, currentPage.value(), value)
            else if (bookId > 0) db.saveLiveReadingNote(liveKey ?: bookLiveNoteKey(context, "new_${bookId}_${currentKind.value().name}"), bookId, iso, currentPage.value(), durationSeconds, value, currentKind.value())
        }.onFailure { Toast.makeText(context, "Could not save note. Local draft retained.", Toast.LENGTH_LONG).show() }
    }
    return object : androidx.compose.runtime.MutableState<String> {
        override var value: String
            get() = state.value
            set(value) { state.value = value; persist(value) }
        override fun component1(): String = value
        override fun component2(): (String) -> Unit = { value = it }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookSessionNoteScreen(
    book: BookRow?,
    session: BookFinishedSession?,
    onSave: (String) -> Unit
) {
    val draftPrefs = LocalContext.current.getSharedPreferences("book_note_drafts", Context.MODE_PRIVATE)
    val draftKey = "session_${session?.endedAt}"
    var noteDraft by rememberAutoSavedBookNote(draftKey, session?.bookId ?: book?.id ?: 0, { session?.endedAt.orEmpty() }, { session?.page ?: 0 }, { BookNoteKind.NOTE }, durationSeconds = session?.durationSeconds ?: 0L, liveKey = draftKey)
    var noteFocused by remember { mutableStateOf(false) }
    var showSkipConfirmation by rememberSaveable(session?.endedAt) { mutableStateOf(false) }
    val noteFieldRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(noteDraft.length, noteFocused) {
        if (noteFocused) {
            delay(16L)
            noteFieldRequester.bringIntoView()
        }
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("What did you read?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            text = buildString {
                append(book?.title ?: "Session")
                session?.let { append(" - page ${it.page} - ${bookFormatDuration(it.durationSeconds)}") }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = noteDraft,
            onValueChange = { noteDraft = it; draftPrefs.edit().putString(draftKey, it).commit() },
            label = { Text("Notes for this session") },
            minLines = 7,
            maxLines = 12,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 360.dp)
                .bringIntoViewRequester(noteFieldRequester)
                .onFocusChanged { noteFocused = it.isFocused }
        )
        Button(
            onClick = { onSave(noteDraft) },
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Text("Done")
        }
        TextButton(onClick = { showSkipConfirmation = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Skip note")
        }
    }
    if (showSkipConfirmation) {
        AlertDialog(
            shape = RoundedCornerShape(8.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
            onDismissRequest = { showSkipConfirmation = false },
            title = { Text("Skip this note?") },
            text = {
                Text(
                    if (noteDraft.isBlank()) "The reading session will be saved without a note."
                    else "Your written note will be discarded. The reading session itself will stay saved."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSkipConfirmation = false
                    onSave("")
                }) { Text("Skip note") }
            },
            dismissButton = {
                TextButton(onClick = { showSkipConfirmation = false }) { Text("Keep writing") }
            }
        )
    }
}
