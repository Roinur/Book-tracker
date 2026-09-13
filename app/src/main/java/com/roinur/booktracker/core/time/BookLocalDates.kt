package com.roinur.booktracker

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal fun bookLocalDate(value: String, zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
    runCatching { Instant.parse(value).atZone(zone).toLocalDate() }.getOrNull()
        ?: runCatching { LocalDate.parse(value) }.getOrNull()

internal fun bookDailyActivity(
    sessions: List<BookReadingSessionRow>,
    pages: Boolean,
    zone: ZoneId = ZoneId.systemDefault()
): List<DailyActivityPoint> = sessions.asSequence()
    .filterNot { it.excludeFromStatistics }
    .mapNotNull { session -> bookLocalDate(session.activityAt, zone)?.let { it to session } }
    .groupBy({ it.first }, { it.second })
    .map { (date, rows) ->
        DailyActivityPoint(
            date,
            if (pages) rows.sumOf { it.pagesRead }.coerceAtLeast(0)
            else (rows.sumOf { it.durationMilliseconds ?: (it.durationSeconds * 1000L) } / 60000L).toInt().coerceAtLeast(0),
            rows.map { it.bookId }.distinct().size
        )
    }.sortedBy { it.date }
