package com.roinur.booktracker

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun BookCompletionRatingDialog(book: BookRow, onDismiss: () -> Unit,
    onSave: (Int, (Boolean) -> Unit) -> Unit) {
    var rating by rememberSaveable(book.id) { mutableIntStateOf(0) }
    var busy by remember(book.id) { mutableStateOf(false) }
    var failed by remember(book.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, shape = shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        title = { Text("Rate your finished book") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium)
            if (book.authors.isNotBlank()) Text(book.authors, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("How did you like it?", style = MaterialTheme.typography.bodyMedium)
            if (!busy) BookInteractiveRating(rating, { rating = it; failed = false }, {}, {}, allowClear = false)
            else Text("Saving rating…", style = MaterialTheme.typography.bodyMedium)
            if (failed) Text("Could not save the rating. Please try again.", color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { Button(onClick = {
            busy = true
            onSave(rating) { saved -> busy = false; failed = !saved }
        }, enabled = rating in 1..5 && !busy, shape = BookSettingsButtonShape) { Text("Save rating") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy, shape = BookSettingsButtonShape) { Text("Not now") } })
}
