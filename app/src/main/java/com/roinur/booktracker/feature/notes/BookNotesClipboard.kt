package com.roinur.booktracker

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

internal fun formatBookNotesForClipboard(notes: List<BookNote>): String {
    return notes
        .sortedWith(
            compareBy<BookNote> {
                runCatching { Instant.parse(it.createdAt).toEpochMilli() }.getOrDefault(Long.MAX_VALUE)
            }.thenBy { it.id }
        )
        .joinToString("\n\n") { note ->
            "${bookFormatIsoDate(note.createdAt)} - page ${note.page}\n${note.note}"
        }
}

