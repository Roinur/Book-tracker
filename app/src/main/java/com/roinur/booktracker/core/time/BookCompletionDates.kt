package com.roinur.booktracker

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun legacyCompletionDate(manual: String, timestamp: Long?): String {
    val date = listOf("MMM d, uuuu", "d MMM uuuu", "uuuu-MM-dd").firstNotNullOfOrNull { pattern ->
        runCatching { LocalDate.parse(manual.trim(), DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)) }.getOrNull()
    }
    if (date != null) return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
    return timestamp?.takeIf { it > 0 }?.let { runCatching { Instant.ofEpochMilli(it).toString() }.getOrNull() }.orEmpty()
}

internal fun bookCompletionYear(value: String): Int? =
    runCatching { Instant.parse(value).atZone(ZoneId.systemDefault()).year }.getOrNull()
        ?: runCatching { LocalDate.parse(value).year }.getOrNull()

internal fun resolveCompletionDate(sessions: List<String>, notes: List<String>, imported: String, existing: String): String {
    fun latest(values: List<String>): String? = values.mapNotNull { value ->
        runCatching { value to Instant.parse(value) }.getOrNull()
    }.maxByOrNull { it.second }?.first
    return latest(sessions) ?: latest(notes) ?: imported.takeIf { bookCompletionYear(it) != null } ?: existing
}
