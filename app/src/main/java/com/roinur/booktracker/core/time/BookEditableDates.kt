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

internal fun bookFormatEditableDate(raw: String): String {
    return runCatching {
        Instant.parse(raw)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US))
    }.getOrDefault(raw.take(16).replace('T', ' ').ifBlank { "" })
}

internal fun bookNowEditableDate(): String {
    return LocalDateTime.now()
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US))
}

internal fun parseBookSessionEditDate(raw: String): Instant? {
    return runCatching {
        LocalDateTime.parse(raw.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US))
            .atZone(ZoneId.systemDefault())
            .toInstant()
    }.getOrNull()
}

internal fun bookEditableDateToLocal(raw: String): LocalDateTime {
    return runCatching {
        LocalDateTime.parse(raw.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US))
    }.getOrDefault(LocalDateTime.now())
}

internal fun bookFormatDurationPickerLabel(minutesText: String): String {
    val minutes = minutesText.toLongOrNull()?.coerceAtLeast(1L) ?: 1L
    val hours = minutes / 60L
    val remainder = minutes % 60L
    return when {
        hours > 0L && remainder > 0L -> "${hours}h ${remainder}m"
        hours > 0L -> "${hours}h"
        else -> "${minutes}m"
    }
}

internal fun bookFormatCountdownClock(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) + 999L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

