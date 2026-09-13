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

internal fun bookReadingStreak(points: List<DailyActivityPoint>): Int {
    val activeDates = points
        .filter { it.pagesRead > 0 }
        .map { it.date }
        .toSet()
    var cursor = LocalDate.now()
    if (cursor !in activeDates) cursor = cursor.minusDays(1)
    var streak = 0
    while (cursor in activeDates) {
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}

internal fun bookLongestReadingStreak(points: List<DailyActivityPoint>): Int {
    val sortedDates = points
        .filter { it.pagesRead > 0 }
        .map { it.date }
        .distinct()
        .sorted()
    var longest = 0
    var current = 0
    var previous: LocalDate? = null
    sortedDates.forEach { date ->
        current = if (previous?.plusDays(1) == date) current + 1 else 1
        longest = max(longest, current)
        previous = date
    }
    return longest
}

internal fun bookPointsForRange(points: List<DailyActivityPoint>, range: BookStatsRange, today: LocalDate = LocalDate.now()): List<DailyActivityPoint> {
    val start = when (range) {
        BookStatsRange.WEEK -> today.minusDays(6)
        BookStatsRange.MONTH -> today.minusDays(29)
        BookStatsRange.YEAR -> today.minusDays(364)
        BookStatsRange.ALL_TIME -> points.minByOrNull { it.date }?.date ?: today.minusDays(6)
    }
    return points.filter { !it.date.isBefore(start) && !it.date.isAfter(today) }
}

internal fun bookChartPoints(points: List<DailyActivityPoint>, range: BookStatsRange, today: LocalDate = LocalDate.now()): List<DailyActivityPoint> {
    val byDate = points.groupBy { it.date }.mapValues { (_, items) ->
        DailyActivityPoint(
            date = items.first().date,
            pagesRead = items.sumOf { it.pagesRead },
            entriesRead = items.sumOf { it.entriesRead }
        )
    }
    return when (range) {
        BookStatsRange.WEEK -> (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            byDate[date] ?: DailyActivityPoint(date, 0, 0)
        }
        BookStatsRange.MONTH -> (29 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            byDate[date] ?: DailyActivityPoint(date, 0, 0)
        }
        BookStatsRange.YEAR -> {
            val firstMonth = today.minusMonths(11).withDayOfMonth(1)
            (0..11).map { offset ->
                val month = firstMonth.plusMonths(offset.toLong())
                val monthItems = points.filter { it.date.year == month.year && it.date.month == month.month }
                DailyActivityPoint(
                    date = month,
                    pagesRead = monthItems.sumOf { it.pagesRead },
                    entriesRead = monthItems.sumOf { it.entriesRead }
                )
            }
        }
        BookStatsRange.ALL_TIME -> {
            val sorted = points.sortedBy { it.date }
            if (sorted.isEmpty()) {
                (6 downTo 0).map { offset ->
                    val date = today.minusDays(offset.toLong())
                    DailyActivityPoint(date, 0, 0)
                }
            } else {
                sorted
            }
        }
    }
}
