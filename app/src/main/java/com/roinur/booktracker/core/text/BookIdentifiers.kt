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

internal fun normalizeIsbn(raw: String): String? {
    val compact = raw.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase(Locale.US)
    return compact.takeIf { it.length == 10 || it.length == 13 }
}

internal fun extractIsbn(raw: String): String? {
    ISBN_CANDIDATE_PATTERN.findAll(raw).forEach { match ->
        normalizeIsbn(match.value)?.let { return it }
    }
    return null
}

internal fun normalizeBookCollections(raw: String): String {
    return splitBookCollections(raw)
        .distinctBy { it.lowercase(Locale.US) }
        .joinToString(", ")
}

internal fun splitBookCollections(raw: String): List<String> {
    return raw.split(',', ';', '\n')
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

