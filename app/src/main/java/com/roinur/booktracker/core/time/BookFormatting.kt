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

internal fun bookFormatSessionDate(session: BookReadingSessionRow): String =
    if (session.endedAt.isBlank()) "Started ${bookFormatIsoDate(session.startedAt)} · end unknown"
    else bookFormatIsoDate(session.endedAt)

internal fun bookFormatSessionDuration(session: BookReadingSessionRow): String {
    val ms = session.durationMilliseconds ?: return bookFormatDuration(session.durationSeconds)
    return "${ms / 3600000}h ${(ms / 60000) % 60}m ${(ms / 1000) % 60}.${(ms % 1000).toString().padStart(3, '0')}s"
}

internal fun bookFormatDuration(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0L)
    val hours = seconds / 3600L
    val minutes = (seconds % 3600L) / 60L
    val secs = seconds % 60L
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${secs}s"
        else -> "${secs}s"
    }
}

internal fun bookFormatCompactHours(totalSeconds: Long): String {
    val minutes = (totalSeconds.coerceAtLeast(0L) / 60L).coerceAtLeast(0L)
    val hours = minutes / 60L
    val remainder = minutes % 60L
    return when {
        hours > 0L -> "${hours}h ${remainder}m"
        minutes > 0L -> "${minutes}m"
        else -> "0m"
    }
}

internal fun bookFormatTimer(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0L)
    val hours = seconds / 3600L
    val minutes = (seconds % 3600L) / 60L
    val secs = seconds % 60L
    return "%02dh %02dm %02ds".format(Locale.US, hours, minutes, secs)
}

internal fun bookActiveElapsedSeconds(
    activeStartedAtMs: Long,
    nowMs: Long,
    activePausedAtMs: Long,
    activePausedTotalMs: Long
): Long {
    val startedMs = activeStartedAtMs.takeIf { it > 0L } ?: nowMs
    val effectiveNowMs = activePausedAtMs.takeIf { it > 0L } ?: nowMs
    return ((effectiveNowMs - startedMs - activePausedTotalMs) / 1000L).coerceAtLeast(0L)
}

internal fun bookEstimateRemaining(book: BookRow, totalSeconds: Long): String {
    if (book.pageCount <= 0 || book.currentPage <= 0 || totalSeconds <= 0L) return "-"
    val remainingPages = (book.pageCount - book.currentPage).coerceAtLeast(0)
    if (remainingPages <= 0) return "0m"
    val secondsPerPage = totalSeconds.toDouble() / book.currentPage.toDouble().coerceAtLeast(1.0)
    return bookFormatCompactHours((remainingPages * secondsPerPage).roundToInt().toLong())
}

internal fun bookFormatIsoDate(raw: String): String {
    return runCatching {
        Instant.parse(raw)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.US))
    }.getOrDefault(raw.take(16).ifBlank { "-" })
}

internal fun bookFormatStatsReadTime(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0L)
    return if (seconds >= 3600L) "${seconds / 3600L}h" else "${seconds / 60L}m"
}

