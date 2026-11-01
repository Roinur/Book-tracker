package com.roinur.booktracker

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class BookRecapEntry(val book: BookRow, val pages: Long, val finished: Boolean)
internal data class BookPeriodRecap(
    val range: BookStatsRange,
    val start: LocalDate,
    val end: LocalDate,
    val pages: Long,
    val seconds: Long,
    val finishedBooks: Int,
    val activeDays: Int,
    val longestStreak: Int,
    val bestPageDay: Int,
    val bestMinuteDay: Int,
    val books: List<BookRecapEntry>
) {
    val pagesPerHour: Double? get() = if (seconds > 0) pages * 3600.0 / seconds else null
    val heading: String get() = when (range) {
        BookStatsRange.WEEK -> "A week in books"
        BookStatsRange.MONTH -> end.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))
        BookStatsRange.YEAR -> "A year in books"
        BookStatsRange.ALL_TIME -> "My reading story"
    }
}

/** Read-only snapshot. No dates are invented for imported lifetime totals or undated completions. */
internal fun buildBookPeriodRecap(
    books: List<BookRow>, sessions: List<BookReadingSessionRow>, stats: BookStats,
    range: BookStatsRange, start: LocalDate, end: LocalDate, zone: ZoneId = ZoneId.systemDefault()
): BookPeriodRecap {
    val allTime = range == BookStatsRange.ALL_TIME
    require(allTime || start <= end)
    fun within(date: LocalDate) = date >= start && date <= end
    val dated = sessions.filterNot { it.excludeFromStatistics }.mapNotNull { session ->
        bookLocalDate(session.activityAt, zone)?.takeIf { it <= end && (allTime || within(it)) }?.let { it to session }
    }
    val selectedSessions = dated.map { it.second }
    val pagePoints = stats.dailyPageActivity.filter { it.date <= end && (allTime || within(it.date)) }
    val minutePoints = stats.dailyActivity.filter { it.date <= end && (allTime || within(it.date)) }
    fun milliseconds(session: BookReadingSessionRow) =
        (session.durationMilliseconds ?: (session.durationSeconds.coerceAtLeast(0) * 1000L)).coerceAtLeast(0)
    val totalsByBook = selectedSessions.groupBy { it.bookId }
    fun completed(book: BookRow) = book.status == BookStatus.FINISHED &&
        (allTime || bookLocalDate(book.finishedAt, zone)?.let(::within) == true)
    val entries = books.mapNotNull { book ->
        val history = totalsByBook[book.id].orEmpty()
        val pages = if (allTime) book.currentPage.toLong().coerceAtLeast(0) else history.sumOf { it.pagesRead.toLong().coerceAtLeast(0) }
        val finished = completed(book)
        val read = if (allTime) pages > 0 || book.readingSeconds > 0 else history.any { it.pagesRead > 0 || milliseconds(it) > 0 }
        if (finished || read) BookRecapEntry(book, pages, finished) else null
    }.sortedWith(compareByDescending<BookRecapEntry> { it.finished }.thenByDescending { it.pages }.thenBy { it.book.title })
    val activityDates = (pagePoints.filter { it.pagesRead > 0 }.map { it.date } +
        minutePoints.filter { it.pagesRead > 0 }.map { it.date } +
        dated.filter { it.second.pagesRead > 0 || milliseconds(it.second) > 0 }.map { it.first }).toSet()
    val allKnownDates = activityDates + books.mapNotNull { bookLocalDate(it.finishedAt, zone)?.takeIf { date -> date <= end } }
    return BookPeriodRecap(
        range, if (allTime) allKnownDates.minOrNull() ?: minOf(start, end) else start, end,
        if (allTime) stats.pagesRead.toLong().coerceAtLeast(0) else pagePoints.sumOf { it.pagesRead.toLong().coerceAtLeast(0) },
        if (allTime) stats.readingSeconds.coerceAtLeast(0) else selectedSessions.sumOf(::milliseconds) / 1000L,
        if (allTime) stats.finishedBooks else books.count(::completed),
        activityDates.size, bookLongestReadingStreak(activityDates.map { DailyActivityPoint(it, 1, 1) }),
        pagePoints.maxOfOrNull { it.pagesRead }?.coerceAtLeast(0) ?: 0,
        minutePoints.maxOfOrNull { it.pagesRead }?.coerceAtLeast(0) ?: 0, entries
    )
}
