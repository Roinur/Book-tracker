package com.roinur.booktracker

import java.time.LocalDate
import kotlin.math.ceil

internal class CollectionInsights(group: CollectionProgress, allSessions: List<BookReadingSessionRow>) {
    private val ids = group.books.mapTo(hashSetOf()) { it.id }
    private val sessions = allSessions.distinctBy { it.id }.filter { it.bookId in ids }
    private val included = sessions.filterNot { it.excludeFromStatistics }
    private val timed = included.filter { it.pagesRead > 0 && it.durationSeconds > 0 }
    private val globalTimed = allSessions.distinctBy { it.id }
        .filter { !it.excludeFromStatistics && it.pagesRead > 0 && it.durationSeconds > 0 }
    private val estimateSample = timed.ifEmpty { globalTimed }
    private fun pace(rows: List<BookReadingSessionRow>): Double? = if (rows.isEmpty()) null else
        rows.sumOf { it.pagesRead.toLong() } * 3600.0 / rows.sumOf { it.durationSeconds }
    val pagesPerHour = pace(timed)
    val minutesPerPage = pagesPerHour?.let { 60.0 / it }
    val usesOverallPace = timed.isEmpty() && globalTimed.isNotEmpty()
    val estimatePagesPerHour = pace(estimateSample)
    val timedSessionCount = timed.size
    val estimateSessionCount = estimateSample.size
    val loggedSessionCount = sessions.size
    val totalReadingSeconds = group.books.sumOf { it.readingSeconds.coerceAtLeast(0) }
    val totalReadPages = group.books.sumOf { it.currentPage.coerceAtLeast(0).toLong() }
    val unfinishedBooks = group.books.filter { it.status != BookStatus.FINISHED }
    val missingRemainingCounts = unfinishedBooks.count { it.pageCount <= 0 }
    val remainingPages = unfinishedBooks.filter { it.pageCount > 0 }
        .sumOf { (it.pageCount.toLong() - it.currentPage.coerceAtLeast(0)).coerceAtLeast(0) }
    val remainingSeconds = estimatePagesPerHour?.let { ceil(remainingPages * 3600.0 / it).toLong() }
    val estimatedSessions = estimateSample.takeIf { it.isNotEmpty() }?.let { rows ->
        ceil(remainingPages / rows.map { it.pagesRead.toDouble() }.average()).toLong()
    }
    val averageSessionSeconds = timed.takeIf { it.isNotEmpty() }?.let { rows ->
        rows.sumOf { it.durationSeconds } / rows.size
    }
    val averageSessionPages = timed.takeIf { it.isNotEmpty() }?.map { it.pagesRead }?.average()
    val averageBookPages = group.knownPages.takeIf { it.isNotEmpty() }?.map { it.pageCount }?.average()
    val ratedBooks = group.books.filter { it.rating in 1..5 }
    val averageRating = ratedBooks.takeIf { it.isNotEmpty() }?.map { it.rating }?.average()
    val largestBook = group.knownPages.maxByOrNull { it.pageCount }
    val nextToFinish = unfinishedBooks.filter { it.pageCount > 0 && it.currentPage < it.pageCount }
        .sortedWith(compareBy<BookRow> { it.pageCount - it.currentPage.coerceAtLeast(0) }
            .thenBy { it.title }).take(3)
    private val activeDates = included.filter { it.pagesRead > 0 }.mapNotNull { bookLocalDate(it.activityAt) }.distinct()
    val daysRead = activeDates.size
    val firstRead: LocalDate? = activeDates.minOrNull()
    val lastRead: LocalDate? = activeDates.maxOrNull()
}
