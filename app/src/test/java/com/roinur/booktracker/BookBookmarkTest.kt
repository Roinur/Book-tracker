package com.roinur.booktracker

import org.junit.Assert.*
import org.junit.Test

class BookBookmarkTest {
    private fun book(read: Int = 0, bookmark: Int? = null) = BookRow(1, "", "Compendium", "Author", 300, read,
        BookStatus.READING, 0, "", "", "", "", false, "", "", "", "", 0,
        parts = listOf(BookPart("First", 1, 50), BookPart("Second", 51, 100)), bookmarkPage = bookmark)

    @Test fun finishFieldIsThePageReachedNotPagesToAdd() {
        assertEquals(BookReadingEntry(75, 144, null), bookFinishReadingEntry(book(69), "144", null))
        assertEquals(BookReadingEntry(0, 69, null), bookFinishReadingEntry(book(69), "69", null))
        assertEquals(BookReadingEntry(10, 45, 75), bookFinishReadingEntry(book(35, 65), "75", "75"))
        assertEquals(BookReadingEntry(35, 35, 65), bookFinishReadingEntry(book(), "65", "65", calculatedBookPage(0, "30", "65")))
        assertEquals(BookReadingEntry(75, 144, 144), bookFinishReadingEntry(book(69), "144", "144", 75))
        assertNull(bookFinishReadingEntry(book(69), "", null))
        assertNull(bookFinishReadingEntry(book(69), "144", null, -1))
    }

    @Test fun calculatorCounts35AndResumesAt65WithoutInflatingTotals() {
        val pages = calculatedBookPage(0, "30", "65")!!
        val entry = bookReadingEntry(book(), pages.toString(), "65")!!
        assertEquals(BookReadingEntry(35, 35, 65), entry)
        val saved = book(entry.totalPagesRead, entry.bookmarkPage)
        assertEquals(65, saved.readingPage)
        assertEquals(35f / 300, saved.progressFraction, .0001f)
        assertEquals("Second", saved.currentPart()?.title)
        assertEquals(BookReadingEntry(10, 45, 75), bookReadingEntry(saved, "10", "75"))
        assertEquals(BookReadingEntry(10, 45, null), bookReadingEntry(saved, "10", null))
    }

    @Test fun calculatorShowsCumulativeProgressAndAutomaticallyContinuesAtItsPrintedEndpoint() {
        val calculated = calculatedBookPage(0, "100", "110")!!
        val displayed = calculatedBookPage(20, "100", "110")!!.toString()
        assertEquals("30", displayed)
        val entry = bookFinishReadingEntry(book(20), displayed, "110", calculated)!!
        assertEquals(BookReadingEntry(10, 30, 110), entry)
        val saved = book(entry.totalPagesRead, entry.bookmarkPage)
        assertEquals(110, saved.readingPage)
        assertEquals(BookReadingEntry(10, 40, 120), bookFinishReadingEntry(saved, "120", null))
        assertEquals(BookReadingEntry(0, 30, 110), bookFinishReadingEntry(saved, "110", null))
        assertNull(bookFinishReadingEntry(book(20), displayed, null, calculated))
    }

    @Test fun partsUsePrintedLocationAndSplitOnlyActualPagesAndTime() {
        val session = BookReadingSessionRow(1, 1, "", "", 2100, 35, 35, bookmarkPage = 65)
        val allocated = bookPartReading(book().parts, listOf(session))
        assertEquals(listOf(BookPartReading(20, 1200), BookPartReading(15, 900)), allocated)
        assertEquals(35L, allocated.sumOf { it.pages })
        val legacy = BookReadingSessionRow(2, 1, "", "", 600, 10, 0)
        assertEquals(75, bookSessionsWithPageFallback(listOf(session, legacy))[1].pageReached)
    }

    @Test fun bookmarksDoNotAddDailyGoalProgress() {
        val saved = book(35, 65)
        val date = java.time.LocalDate.of(2026, 10, 24)
        val session = BookReadingSessionRow(1, 1, "2026-10-24T10:00:00Z", "2026-10-24T11:00:00Z", 3600, 35, 35, bookmarkPage = 65)
        val progress = bookGoalProgress(saved, BookGoal(1, BookGoalMode.DAILY_PAGES, dailyPages = 50), listOf(session), date)
        assertEquals(35L, progress.todayPages)
        assertEquals(15L, progress.todayRemaining)
    }

    @Test fun zeroAndIncorrectCatalogueLengthsAreAllowedButInvalidEntriesAreRejected() {
        assertEquals(BookReadingEntry(0, 0, 65), bookReadingEntry(book(), "0", "65"))
        assertEquals(400, bookReadingEntry(book(), "35", "400")?.bookmarkPage)
        assertNull(bookReadingEntry(book(), "-1", "65"))
        assertNull(bookReadingEntry(book(), "35", ""))
        assertNull(bookReadingEntry(book(), "35", "-1"))
        assertNull(bookReadingEntry(book(Int.MAX_VALUE), "1", null))
        assertEquals(35, book(35).readingPage)
    }
}
