package com.roinur.booktracker

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BookTrendDataTest {
    @Test fun metricUsesPagesAndFractionalMinutesAndShareUsesSameUnit() {
        val today = LocalDate.now()
        val first = session(1, 1, today).let { it.copy(session = it.session.copy(pagesRead = 30, durationSeconds = 30)) }
        val second = session(2, 2, today).let { it.copy(session = it.session.copy(pagesRead = 10, durationSeconds = 90)) }
        val data = BookTrendData(listOf(book(1, "Classic", 5), book(2, "Other", 2)), listOf(first, second))
        val target = data.targets(TrendTargetKind.TAGS).first { it.name == "Classic" }
        fun line(metric: TrendMetric) = data.snapshot(TrendRequest(TrendTargetKind.TAGS, listOf(target.id), StatsRange.WEEK, metric = metric)).series.single()
        assertEquals(30f, trendValues(line(TrendMetric.PAGES), TrendScale.READS, TrendSignal.ALL).last(), 0.001f)
        assertEquals(75f, trendValues(line(TrendMetric.PAGES), TrendScale.SHARE, TrendSignal.ALL).last(), 0.001f)
        assertEquals(0.5f, trendValues(line(TrendMetric.TIME), TrendScale.READS, TrendSignal.ALL).last(), 0.001f)
        assertEquals(25f, trendValues(line(TrendMetric.TIME), TrendScale.SHARE, TrendSignal.ALL).last(), 0.001f)
        assertEquals(50f, trendValues(line(TrendMetric.SESSIONS), TrendScale.SHARE, TrendSignal.ALL).last(), 0.001f)
    }
    private fun book(id: Int, collections: String, rating: Int) = BookRow(
        id, "", "Book $id", "Author $id", 300, 20, BookStatus.READING, rating,
        "", "", "", collections, false, "", "", "", "", 0L
    )
    private fun session(id: Int, book: Int, day: LocalDate) = BookReadingSessionWithBook(
        BookReadingSessionRow(id, book, day.toString(), day.toString(), 60L, 1, 20), "Book $book"
    )
    @Test fun sharedCollectionsCountEachSessionOnceAndKeepRatingDistribution() {
        val today = LocalDate.now()
        val data = BookTrendData(listOf(book(1, "Classic, classic", 5), book(2, "Other", 2)),
            listOf(session(1, 1, today), session(2, 1, today), session(3, 2, today)))
        val target = data.targets(TrendTargetKind.TAGS).first { it.name == "Classic" }
        assertEquals(1, target.entryCount)
        val snapshot = data.snapshot(TrendRequest(TrendTargetKind.TAGS, listOf(target.id), StatsRange.WEEK))
        assertEquals(7, snapshot.buckets.size)
        val point = snapshot.series.single().points.last()
        assertEquals(2, point.matchingReads)
        assertEquals(3, point.totalReads)
        assertEquals(2, point.rating5Count)
        assertEquals(66.6667f, trendValues(snapshot.series.single(), TrendScale.SHARE, TrendSignal.ALL).last(), 0.001f)
    }
    @Test fun emptyDaysCarryShareAndIdsSurviveLibraryReordering() {
        val books = listOf(book(1, "Classic", 0), book(2, "Other", 0))
        val data = BookTrendData(books, listOf(session(1, 1, LocalDate.now().minusDays(2))))
        assertEquals(data.targets(TrendTargetKind.TAGS).map { it.id }.toSet(), BookTrendData(books.reversed(), emptyList()).targets(TrendTargetKind.TAGS).map { it.id }.toSet())
        val result = data.snapshot(TrendRequest(TrendTargetKind.TAGS, emptyList(), StatsRange.WEEK, viewAll = true))
        val line = result.series.first { it.target.name == "Classic" }
        assertEquals(listOf(100f, 100f, 100f), trendValues(line, TrendScale.SHARE, TrendSignal.ALL).takeLast(3))
        assertEquals(0, line.points.last().matchingReads)
    }
}
