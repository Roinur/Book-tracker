package com.roinur.booktracker

import org.junit.Assert.*
import org.junit.Test

class BookLibraryCheckTest {
    private val types = mapOf("penguin" to BookCollectionType.PUBLISHER, "classic" to BookCollectionType.TYPE,
        "philosophy" to BookCollectionType.THEME, "religion" to BookCollectionType.THEME, "school" to BookCollectionType.OTHER)
    private fun book(status: BookStatus = BookStatus.WISHLIST, page: Int = 0, collections: String = "Penguin, Classic, Philosophy, Religion, School") =
        BookRow(1, "", "Candide", "Voltaire", 200, page, status, 0, "", "cover", "", collections, false, "", "", "", "", 0)

    @Test fun incompleteSetupAndUnusedReadingStatusAreSuggestions() {
        val issues = bookLibraryIssues(book(BookStatus.READING, collections = "Classic, Philosophy"), types)
        assertEquals(listOf(BookLibraryIssue.PUBLISHER, BookLibraryIssue.STATUS), issues)
        assertEquals(BookStatus.READING, book(BookStatus.READING).status)
    }

    @Test fun explicitStatusesAndOverlappingSubjectsAreRespected() {
        assertTrue(bookLibraryIssues(book(page = 60), types).isEmpty())
        assertTrue(bookLibraryIssues(book(BookStatus.PAUSED, 60), types).isEmpty())
        assertTrue(bookLibraryIssues(book(BookStatus.FINISHED, 180).copy(finishedAt = "2026-01-01"), types).isEmpty())
    }

    @Test fun activeSessionsAndRealHistoryDoNotSuggestResettingReading() {
        assertFalse(BookLibraryIssue.STATUS in bookLibraryIssues(book(BookStatus.READING), types, activeBookId = 1))
        assertFalse(BookLibraryIssue.STATUS in bookLibraryIssues(book(BookStatus.READING), types, hasReadingHistory = true))
        assertFalse(BookLibraryIssue.STATUS in bookLibraryIssues(book(BookStatus.READING).copy(readingSeconds = 60), types))
    }

    @Test fun otherIsAnExplicitTypeButUnknownCollectionsNeedClassification() {
        assertFalse(BookLibraryIssue.UNTYPED in bookLibraryIssues(book(), types))
        assertTrue(BookLibraryIssue.UNTYPED in bookLibraryIssues(book(collections = "Penguin, Classic, Philosophy, New collection"), types))
        assertTrue(bookLibraryIssues(book(collections = "PENGUIN, classic, PHILOSOPHY"), types).isEmpty())
    }

    @Test fun missingDataAndImpossiblePageRangeAreReportedButIsbnIsOptional() {
        val missing = book().copy(title = "Untitled book", authors = "", pageCount = 0, coverUrl = "", status = BookStatus.FINISHED)
        assertTrue(bookLibraryIssues(missing, types).containsAll(listOf(BookLibraryIssue.TITLE, BookLibraryIssue.AUTHOR,
            BookLibraryIssue.PAGES, BookLibraryIssue.COVER, BookLibraryIssue.FINISHED_DATE)))
        assertTrue(BookLibraryIssue.PAGE_RANGE in bookLibraryIssues(book(page = 250), types))
        assertTrue(bookLibraryIssues(book(), types).isEmpty())
    }
}
