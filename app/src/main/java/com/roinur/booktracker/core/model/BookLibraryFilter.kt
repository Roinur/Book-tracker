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

enum class BookLibraryFilter(val label: String) {
    READING("Reading"),
    FINISHED("Finished"),
    NOT_STARTED("Not started"),
    ON_HOLD("On hold"),
    ALL("All");

    fun next(): BookLibraryFilter = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromStorage(raw: String?): BookLibraryFilter =
            entries.firstOrNull { it.name == raw } ?: READING
    }
}

fun BookLibraryFilter.matches(book: BookRow, activeBookId: Int?): Boolean = when (this) {
    BookLibraryFilter.READING -> book.status == BookStatus.READING
    BookLibraryFilter.FINISHED -> book.status == BookStatus.FINISHED
    BookLibraryFilter.NOT_STARTED -> book.status == BookStatus.WISHLIST
    BookLibraryFilter.ON_HOLD -> book.status == BookStatus.PAUSED
    BookLibraryFilter.ALL -> true
}

