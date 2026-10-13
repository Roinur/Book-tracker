package com.roinur.booktracker

import org.junit.Assert.*
import org.junit.Test

class BookPartsTest {
    private val scarlet = BookPart("A Study in Scarlet", 20, 99)
    private val sign = BookPart("The Sign of Four", 102, 249)
    private fun book(page: Int = 300, status: BookStatus = BookStatus.READING) = BookRow(
        id = 1, isbn = "", title = "Sherlock Holmes", authors = "Arthur Conan Doyle", pageCount = 400,
        currentPage = page, status = status, rating = 0, notes = "", coverUrl = "", sourceUrl = "", collections = "",
        pinned = false, addedAt = "", startedAt = "", finishedAt = "", lastReadAt = "", readingSeconds = 3000,
        parts = listOf(scarlet, sign))
    private fun session(pages: Int = 300, end: Int = 300, seconds: Long = 3000) = BookReadingSessionRow(1, 1, "", "", seconds, pages, end)

    @Test fun pagePositionsIncludeBothEndpointsAndAllowUnassignedGaps() {
        assertEquals(80, scarlet.pageCount)
        assertEquals(0, scarlet.pagesRead(10))
        assertEquals(41, scarlet.pagesRead(60))
        assertEquals(80, scarlet.pagesRead(300))
        assertFalse(scarlet.completed(98)); assertTrue(scarlet.completed(99))
        assertEquals(sign, book(140).currentPart())
        assertNull(book(100).currentPart()); assertNull(book(249).currentPart())
        assertEquals(sign, book(101).currentPart())
        assertEquals(sign, book(102).currentPart())
        assertEquals(listOf(scarlet, sign), validateBookParts(listOf(sign, scarlet), 400))
        val first = BookPart("Scarlet", 3, 74)
        assertEquals(72, first.pageCount)
        assertEquals(0, first.pagesRead(2)); assertEquals(1, first.pagesRead(3))
        assertEquals(72, first.pagesRead(74)); assertTrue(first.completed(74))
        assertEquals(1, validateBookParts(listOf(BookPart("One page", 75, 75)), 400).single().pageCount)
    }

    @Test fun invalidRangesDoNotGetSilentlyChangedOrDiscarded() {
        for (parts in listOf(listOf(BookPart("", 20, 100)), listOf(BookPart("Bad", -1, 80)),
            listOf(BookPart("Bad", 100, 99)), listOf(scarlet, BookPart("Overlap", 99, 150)), listOf(BookPart("Too long", 1, 401)), listOf(BookPart("Zero", 0, 10)))) {
            try { validateBookParts(parts, 400); fail("Invalid range accepted: $parts") } catch (_: IllegalArgumentException) { }
        }
        assertEquals(listOf(scarlet, sign), validateBookParts(listOf(scarlet, sign), 0))
    }

    @Test fun unfinishedCompendiumSplitsWorksWithoutChangingPagesOrTime() {
        val rows = bookWorkStatistics(listOf(book()), listOf(session()), true)
        assertEquals(80L, rows.first { it.title == scarlet.title }.pages)
        assertEquals(800L, rows.first { it.title == scarlet.title }.seconds)
        assertEquals(148L, rows.first { it.title == sign.title }.pages)
        assertEquals(72L, rows.first { it.parentTitle.isBlank() }.pages)
        assertEquals(300L, rows.sumOf { it.pages }); assertEquals(3000L, rows.sumOf { it.seconds })
        assertEquals("Sherlock Holmes", rows.first().parentTitle)
    }

    @Test fun periodRowsOnlyAllocateReadingActuallyLoggedInThatPeriod() {
        val rows = bookWorkStatistics(listOf(book()), listOf(session(40, 60, 400)), false)
        assertEquals(40L, rows.first { it.title == scarlet.title }.pages)
        assertEquals(400L, rows.first { it.title == scarlet.title }.seconds)
        assertEquals(40L, rows.sumOf { it.pages }); assertEquals(400L, rows.sumOf { it.seconds })
        val otherPeriod = bookWorkStatistics(listOf(book()), listOf(session(50, 300, 500)), false)
        assertEquals(0L, otherPeriod.filter { it.parentTitle.isNotBlank() }.sumOf { it.pages })
        assertEquals(50L, otherPeriod.last().pages)
    }

    @Test fun finishedParentReplacesPartsAndDoesNotAssumeAllPagesWereRead() {
        val rows = bookWorkStatistics(listOf(book(status = BookStatus.FINISHED)), listOf(session()), true)
        assertEquals(1, rows.size); assertEquals("Sherlock Holmes", rows.single().title)
        assertEquals(300L, rows.single().pages); assertEquals(3000L, rows.single().seconds)
        assertEquals(2, book(status = BookStatus.FINISHED).parts.size)
    }

    @Test fun activeOrUnreadPartsDoNotBecomeCompletedWorksAndLegacyPagesGetPositions() {
        val rows = bookWorkStatistics(listOf(book(60)), emptyList(), true)
        assertEquals(1, rows.size); assertEquals(60L, rows.single().pages); assertEquals(3000L, rows.single().seconds)
        val noLocations = bookWorkStatistics(listOf(book()), listOf(session(300, 0)), false)
        assertEquals(80L, noLocations.first().pages); assertEquals(800L, noLocations.first().seconds)
        assertEquals(300L, noLocations.sumOf { it.pages }); assertEquals(3000L, noLocations.sumOf { it.seconds })
    }

    @Test fun anHourAcrossFortyAndTwentyPagesSplitsIntoFortyAndTwentyMinutes() {
        val parts = listOf(BookPart("First", 1, 60), BookPart("Second", 61, 100))
        val pass = session(60, 80, 3600)
        assertEquals(listOf(BookPartReading(40, 2400), BookPartReading(20, 1200)), bookPartReading(parts, listOf(pass)))
        val rows = bookWorkStatistics(listOf(book(100).copy(pageCount = 100, readingSeconds = 3600, parts = parts)), listOf(pass), false)
        assertEquals(2400L, rows.first { it.title == "First" }.seconds)
        assertEquals(1200L, rows.first { it.title == "Second" }.seconds)
        assertEquals(3600L, rows.sumOf { it.seconds }); assertEquals(60L, rows.sumOf { it.pages })
    }

    @Test fun roundingAndUnassignedPagesDoNotLoseOrDuplicateSeconds() {
        val parts = (1..3).map { BookPart("Part $it", it, it) }
        val result = bookPartReading(parts, listOf(session(3, 3, 5)))
        assertEquals(listOf(1L, 2L, 2L), result.map { it.seconds })
        assertEquals(5L, result.sumOf { it.seconds })
        val gaps = listOf(BookPart("First", 1, 10), BookPart("Second", 21, 30))
        val rows = bookWorkStatistics(listOf(book(30).copy(parts = gaps, readingSeconds = 60)), listOf(session(30, 30, 60)), false)
        assertEquals(20L, rows.last().seconds); assertEquals(10L, rows.last().pages)
        assertEquals(60L, rows.sumOf { it.seconds })
    }

    @Test fun legacySixSeventeenAndTwentyPagesReachFortyThreeAndAllocateTheirTime() {
        val passes = listOf(
            session(20, 0, 1200).copy(id = 3, endedAt = "2026-02-03T12:00:00Z"),
            session(6, 0, 360).copy(id = 1, endedAt = "2026-02-01T12:00:00Z"),
            session(17, 0, 1020).copy(id = 2, endedAt = "2026-02-02T12:00:00Z"))
        val resolved = bookSessionsWithPageFallback(passes)
        assertEquals(listOf(43, 6, 23), resolved.map { it.pageReached })
        assertEquals(listOf(0, 0, 0), passes.map { it.pageReached })
        val parts = listOf(BookPart("First", 1, 23), BookPart("Second", 24, 43))
        assertEquals(listOf(BookPartReading(23, 1380), BookPartReading(20, 1200)), bookPartReading(parts, passes))
        // A period selection keeps all earlier reading as positional context.
        val febThirdOnly = resolved.filter { it.id == 3 }
        val rows = bookWorkStatistics(listOf(book(43).copy(parts = parts, readingSeconds = 2580)), febThirdOnly, false)
        assertEquals(0L, rows.first().seconds); assertEquals(1200L, rows[1].seconds)
        assertEquals(20L, rows.sumOf { it.pages }); assertEquals(1200L, rows.sumOf { it.seconds })
    }

    @Test fun explicitPositionsAnchorFallbackAndBooksKeepSeparateCursors() {
        val passes = listOf(session(6, 0).copy(id = 1), session(10, 100).copy(id = 2),
            session(5, 0).copy(id = 3), session(7, 0).copy(id = 4, bookId = 2),
            session(4, 20).copy(id = 5), session(3, 0).copy(id = 6), session(0, 0).copy(id = 7))
        assertEquals(listOf(6, 100, 105, 7, 20, 23, 0), bookSessionsWithPageFallback(passes).map { it.pageReached })
    }

    @Test fun remainingTimeUsesThePartsOwnPaceThenFallsBackAndStopsAtCompletion() {
        val part = BookPart("First", 1, 100)
        assertEquals(3600L, bookPartRemainingSeconds(part, 40, BookPartReading(40, 2400), 20.0))
        assertEquals(7200L, bookPartRemainingSeconds(part, 0, BookPartReading(), 50.0))
        assertNull(bookPartRemainingSeconds(part, 0, BookPartReading(), null))
        assertNull(bookPartRemainingSeconds(part, 0, BookPartReading(), 0.0))
        assertEquals(0L, bookPartRemainingSeconds(part, 100, BookPartReading(), null))
    }
}
