package com.roinur.booktracker

import org.junit.Assert.assertEquals
import org.junit.Test

class BookCollectionProgressTest {
    private fun book(id: Int, collections: String, status: BookStatus, read: Int, pages: Int = 200) =
        BookRow(id, "", "Book $id", "", pages, read, status, 0, "", "", "", collections,
            false, "", "", "", "", 0)

    @Test fun finishedDoesNotInventReadPagesAndManualStatusWins() {
        val rows = collectionProgress(listOf(
            book(1, "Philosophy", BookStatus.FINISHED, 180),
            book(2, "Philosophy", BookStatus.WISHLIST, 3)
        ), emptyList(), emptyMap())
        val group = rows.single()
        assertEquals(1, group.finished)
        assertEquals(1, group.counts[BookStatus.WISHLIST])
        assertEquals(183L, group.readPages)
        assertEquals(400L, group.totalPages)
    }

    @Test fun overlappingAndRepeatedCollectionsDoNotDuplicateBooksWithinACollection() {
        val shared = book(1, "Classics, Philosophy, philosophy", BookStatus.READING, 40)
        val groups = collectionProgress(listOf(shared, shared), listOf("Classics", "Philosophy", "Empty"),
            mapOf("philosophy" to BookCollectionType.THEME))
        assertEquals(3, groups.size)
        assertEquals(1, groups.single { it.name == "Philosophy" }.books.size)
        assertEquals(BookCollectionType.THEME, groups.single { it.name == "Philosophy" }.type)
        assertEquals(1, groups.flatMap { it.books }.distinctBy { it.id }.size)
        assertEquals(0, groups.single { it.name == "Empty" }.books.size)
    }

    @Test fun unknownPageCountsDoNotDistortPageProgress() {
        val group = collectionProgress(listOf(
            book(1, "Theme", BookStatus.READING, 100),
            book(2, "Theme", BookStatus.FINISHED, 500, 0)
        ), emptyList(), emptyMap()).single()
        assertEquals(1, group.unknownPageCounts)
        assertEquals(100L, group.readPages)
        assertEquals(200L, group.totalPages)
        assertEquals(0.5f, group.pageFraction, 0.0001f)
    }

    @Test fun insightsUseWeightedTimedPaceAndRespectExcludedSessions() {
        val group = collectionProgress(listOf(book(1, "Theme", BookStatus.READING, 60)), emptyList(), emptyMap()).single()
        val sessions = listOf(
            BookReadingSessionRow(1, 1, "2026-10-01", "2026-10-01", 3600, 40, 40),
            BookReadingSessionRow(2, 1, "2026-10-02", "2026-10-02", 1800, 20, 60),
            BookReadingSessionRow(3, 1, "2020-01-01", "2020-01-01", 10, 1000, 60, excludeFromStatistics = true),
            BookReadingSessionRow(4, 2, "", "", 100, 100, 100)
        )
        val insight = CollectionInsights(group, sessions)
        assertEquals(40.0, insight.pagesPerHour!!, 0.001)
        assertEquals(140L, insight.remainingPages)
        assertEquals(12600L, insight.remainingSeconds)
        assertEquals(5L, insight.estimatedSessions)
        assertEquals(3, insight.loggedSessionCount)
        assertEquals(2, insight.daysRead)
    }

    @Test fun estimatesCanUseOverallPaceButDoNotClaimItAsCollectionPace() {
        val group = collectionProgress(listOf(book(1, "New", BookStatus.WISHLIST, 0)), emptyList(), emptyMap()).single()
        val insight = CollectionInsights(group, listOf(BookReadingSessionRow(1, 2, "", "", 3600, 50, 50)))
        assertEquals(null, insight.pagesPerHour)
        assertEquals(true, insight.usesOverallPace)
        assertEquals(14400L, insight.remainingSeconds)
    }

    @Test fun finishedNotesAndUnknownBookLengthsDoNotInflateTimeLeft() {
        val group = collectionProgress(listOf(
            book(1, "Theme", BookStatus.FINISHED, 180),
            book(2, "Theme", BookStatus.READING, 100, 0),
            book(3, "Theme", BookStatus.PAUSED, 20)
        ), emptyList(), emptyMap()).single()
        val insight = CollectionInsights(group, listOf(BookReadingSessionRow(1, 1, "", "", 3600, 60, 180)))
        assertEquals(300L, insight.totalReadPages)
        assertEquals(180L, insight.remainingPages)
        assertEquals(1, insight.missingRemainingCounts)
        assertEquals(listOf(3), insight.nextToFinish.map { it.id })
    }
}
