package com.roinur.booktracker

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BookReadingPaceTest {
    private fun book(id: Int, collections: String) = BookRow(id, "", "Book $id", "", 200, 0,
        BookStatus.WISHLIST, 0, "", "", "", collections, false, "", "", "", "", 0)
    private fun session(id: Int, bookId: Int, pages: Int, seconds: Long = 3600, excluded: Boolean = false) =
        BookReadingSessionRow(id, bookId, "2026-10-01", "2026-10-01", seconds, pages, pages, excludeFromStatistics = excluded)
    private val books = listOf(book(1, "Classics, Penguin"), book(2, "penguin, Classics"), book(3, "Oxford, Classics"))
    private val types = mapOf("Penguin" to BookCollectionType.PUBLISHER, "Classics" to BookCollectionType.TYPE,
        "Oxford" to BookCollectionType.PUBLISHER)

    @Test fun bookSessionsTakePriorityOverPublisherAndOverall() {
        val pace = bookReadingPace(books[0], listOf(session(1, 1, 10), session(2, 2, 40), session(3, 3, 100)), books, types)
        assertEquals(BookPaceSource.BOOK, pace.source); assertEquals(10.0, pace.pagesPerHour!!, 0.001)
    }

    @Test fun unstartedBookUsesWeightedPublisherSampleNotSharedTypeOrOverall() {
        val sessions = listOf(session(1, 2, 40), session(2, 2, 20, 1800), session(3, 3, 1000), session(4, 2, 9000, 1, true))
        val pace = bookReadingPace(books[0], sessions, books, types)
        assertEquals(BookPaceSource.PUBLISHER, pace.source); assertEquals(40.0, pace.pagesPerHour!!, 0.001)
        val goal = BookGoal(1, BookGoalMode.DAILY_PAGES, 50)
        val progress = bookGoalProgress(books[0], goal, sessions, LocalDate.of(2026, 10, 2), books = books, collectionTypes = types)
        assertEquals(BookPaceSource.PUBLISHER, progress.paceSource)
        assertEquals(4500L, progress.todaySeconds); assertEquals(18000L, progress.remainingSeconds)
    }

    @Test fun absentOrUntimedPublisherFallsBackToOverall() {
        val sessions = listOf(session(1, 2, 30, 0), session(2, 2, 90, excluded = true), session(3, 3, 60))
        val emptyPublisher = bookReadingPace(books[0], sessions, books, types)
        assertEquals(BookPaceSource.OVERALL, emptyPublisher.source); assertEquals(60.0, emptyPublisher.pagesPerHour!!, 0.001)
        val noPublisher = bookReadingPace(books[0].copy(collections = "Classics"), sessions, books, types)
        assertEquals(BookPaceSource.OVERALL, noPublisher.source)
    }

    @Test fun multiplePublishersAndRepeatedMembershipsDoNotDoubleCountSamples() {
        val overlap = books[0].copy(collections = "Penguin, Oxford, penguin")
        val shared = books[1].copy(collections = "Penguin, Oxford")
        val s = session(1, 2, 20)
        val pace = bookReadingPace(overlap, listOf(s, s, session(2, 3, 60)), listOf(overlap, shared, shared, books[2]), types)
        assertEquals(BookPaceSource.PUBLISHER, pace.source); assertEquals(40.0, pace.pagesPerHour!!, 0.001)
    }

    @Test fun withoutTimedDataNoSpeedIsInvented() {
        val pace = bookReadingPace(books[0], listOf(session(1, 1, 0)), books, types)
        assertEquals(BookPaceSource.UNAVAILABLE, pace.source); assertNull(pace.pagesPerHour)
    }
}
