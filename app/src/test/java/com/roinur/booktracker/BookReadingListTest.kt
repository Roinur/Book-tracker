package com.roinur.booktracker

import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class BookReadingListTest {
    private val zone = ZoneId.of("Europe/Stockholm")
    private fun book(id: Int, title: String, page: Int = 0, total: Int = 100, status: BookStatus = BookStatus.READING,
        finished: String = "", last: String = "", parts: List<BookPart> = emptyList()) =
        BookRow(id, "", title, "Author", total, page, status, 0, "", "", "", "", false,
            "2026-10-16T10:00:00Z", "", finished, last, 0, parts)
    private fun session(id: Int, book: Int, date: String, pages: Int, reached: Int = 0, excluded: Boolean = false) =
        BookReadingSessionRow(id, book, date, date, 60, pages, reached, excludeFromStatistics = excluded)

    @Test fun yearsAndFinishedNumbersFollowCompletionDatesNotLibraryOrderOrDateAdded() {
        val books = listOf(book(1, "Later", 80, status = BookStatus.FINISHED, finished = "2025-12-01"),
            book(2, "Earlier", 90, status = BookStatus.FINISHED, finished = "2025-01-01"),
            book(3, "Bible", 100, status = BookStatus.FINISHED, finished = "2023-12-01"), book(4, "Unread"))
        assertEquals("2023:\n1:Bible\n\n2025:\n1:Earlier\n2:Later", formatBookReadingList(books, emptyList(), zone))
    }

    @Test fun partialHistoryUsesYearEndPositionAndFinishingLaterDoesNotRewriteIt() {
        val books = listOf(book(1, "The Count of Monte Cristo", 200, 200, BookStatus.FINISHED, "2026-02-01"),
            book(2, "Ongoing", 30, 100, last = "2026-01-01"))
        val sessions = listOf(session(1, 1, "2024-03-01", 96, 96), session(2, 1, "2026-02-01", 104, 200),
            session(3, 2, "2024-04-01", 4, 4), session(4, 2, "2026-01-01", 26, 30))
        assertEquals("2024:\nThe Count of Monte Cristo 48%\n\n2026:\nOngoing 30%\n1:The Count of Monte Cristo",
            formatBookReadingList(books, sessions.reversed(), zone))
    }

    @Test fun legacyAndExcludedSessionsStillSupplyHistoricalPagePositions() {
        val sessions = listOf(session(1, 1, "2024-01-01", 6, excluded = true), session(2, 1, "2024-02-01", 17, excluded = true),
            session(3, 1, "2025-01-01", 20, excluded = true))
        assertEquals("2024:\nBook 23%\n\n2025:\nBook 43%", formatBookReadingList(listOf(book(1, "Book", 43, last = "2024-01-01")), sessions, zone))
    }

    @Test fun completedCompendiumPartsAreIndentedAndUnreadPartsAreNotInvented() {
        val parts = listOf(BookPart("First", 1, 20), BookPart("Second", 21, 40), BookPart("Third", 41, 60))
        assertEquals("2026:\nCompendium 50%\n    (1):First\n    Second 50%", formatBookReadingList(
            listOf(book(1, "Compendium", 30, 60, last = "2026-03-01", parts = parts)), emptyList(), zone))
        // Finished is explicit; it does not pretend that skipped pages or parts were read.
        assertEquals("2026:\n1:Compendium\n    (1):First\n    Second 50%", formatBookReadingList(
            listOf(book(1, "Compendium", 30, 60, BookStatus.FINISHED, "2026-03-01", parts = parts)), emptyList(), zone))
    }

    @Test fun partsAlreadyReadInAnEarlierYearDoNotAppearAsNewReadingAgain() {
        val parts = listOf(BookPart("First", 1, 20), BookPart("Second", 21, 40))
        val sessions = listOf(session(1, 1, "2025-01-01", 20, 20), session(2, 1, "2026-01-01", 20, 40))
        assertEquals("2025:\nCompendium 50%\n    (1):First\n\n2026:\n1:Compendium\n    (2):Second",
            formatBookReadingList(listOf(book(1, "Compendium", 40, 40, BookStatus.FINISHED, "2026-01-01", parts = parts)), sessions, zone))
    }

    @Test fun datesUseLocalYearAndMissingDatesAreExplicitInsteadOfUsingImportDate() {
        assertEquals("2026:\n1:New Year\n\nUnknown year:\n1:Undated", formatBookReadingList(listOf(
            book(1, "New Year", 80, status = BookStatus.FINISHED, finished = "2025-12-31T23:30:00Z"),
            book(2, "Undated", 30, status = BookStatus.FINISHED), book(3, "Unknown\n length", 20, total = 0)), emptyList(), zone))
    }

    @Test fun emptyLibraryAndUnstartedBooksHaveNoReadingList() {
        assertEquals("", formatBookReadingList(emptyList(), emptyList(), zone))
        assertEquals("", formatBookReadingList(listOf(book(1, "Unstarted", status = BookStatus.WISHLIST)), emptyList(), zone))
    }

    @Test fun aStartDateAloneCannotInventAFinishedYear() {
        val undated = book(1, "Finished without an end date", 60, status = BookStatus.FINISHED).copy(startedAt = "2023-01-01")
        assertEquals("Unknown year:\n1:Finished without an end date", formatBookReadingList(listOf(undated), emptyList(), zone))
    }

    @Test fun partialBooksNeedAtLeastTenPercentAndTimeAloneDoesNotQualify() {
        val books = listOf(book(1, "Time only", 0, last = "2024-01-01"),
            book(2, "Below threshold", 99, 1000, last = "2024-01-02"),
            book(3, "Exactly ten percent", 100, 1000, last = "2024-01-03"),
            book(4, "Finished below threshold", 1, 1000, BookStatus.FINISHED, "2024-01-04"))
        assertEquals("2024:\nExactly ten percent 10%\n1:Finished below threshold", formatBookReadingList(
            books, listOf(session(1, 1, "2024-01-01", 0)), zone))
    }

    @Test fun eachYearNeedsTenPercentagePointsEvenAfterYearsThatWereNotListed() {
        val sessions = listOf(session(1, 1, "2023-01-01", 90, 90),
            session(2, 1, "2024-01-01", 90, 180),
            session(3, 1, "2025-01-01", 100, 280),
            session(4, 1, "2026-01-01", 99, 379),
            session(5, 1, "2027-01-01", 100, 479),
            session(6, 1, "2028-01-01", 1, 480))
        assertEquals("2025:\nBook 28%\n\n2027:\nBook 47%\n\n2028:\n1:Book", formatBookReadingList(
            listOf(book(1, "Book", 480, 1000, BookStatus.FINISHED, "2028-01-01")), sessions, zone))
    }

    @Test fun excludedCollectionsOmitWholeBooksWithExactCaseInsensitiveMembership() {
        val books = listOf(
            book(1, "School book", 100, status = BookStatus.FINISHED, finished = "2025-01-01").copy(collections = "Philosophy, Skola"),
            book(2, "Another school book", 100, status = BookStatus.FINISHED, finished = "2025-01-02").copy(collections = " SKOLA ; Classics"),
            book(3, "Schoolwork is a different collection", 100, status = BookStatus.FINISHED, finished = "2025-01-03").copy(collections = "Skolarbete"),
            book(4, "Unassigned", 100, status = BookStatus.FINISHED, finished = "2025-01-04"))
        assertEquals("2025:\n1:Schoolwork is a different collection\n2:Unassigned",
            formatBookReadingList(books, emptyList(), zone, BookReadingListOptions(excludedCollections = setOf(" skola "))))
        assertEquals(4, formatBookReadingList(books, emptyList(), zone).lineSequence().count { it.matches(Regex("\\d:.*")) })
        assertEquals("", formatBookReadingList(books.take(2), emptyList(), zone, BookReadingListOptions(excludedCollections = setOf("skola"))))
    }

    @Test fun finishedOnlyIncludesOnlyCompletionYearAndRespectsManualFinishedStatus() {
        val books = listOf(book(1, "Finished", 80, status = BookStatus.FINISHED, finished = "2026-01-01"),
            book(2, "Still reading", 100, last = "2025-01-01"),
            book(3, "Undated finished", 20, status = BookStatus.FINISHED))
        val sessions = listOf(session(1, 1, "2025-01-01", 40, 40), session(2, 1, "2026-01-01", 40, 80))
        assertEquals("2026:\n1:Finished\n\nUnknown year:\n1:Undated finished", formatBookReadingList(
            books, sessions, zone, BookReadingListOptions(finishedOnly = true)))
    }

    @Test fun disablingPartsLeavesCompendiumEntriesAndTheirProgressIntact() {
        val book = book(1, "Compendium", 30, 60, last = "2026-03-01",
            parts = listOf(BookPart("First", 1, 20), BookPart("Second", 21, 40)))
        assertEquals("2026:\nCompendium 50%", formatBookReadingList(
            listOf(book), emptyList(), zone, BookReadingListOptions(includeParts = false)))
    }

    @Test fun authorsAreOptionalNormalizedAndOmittedWhenMissing() {
        val books = listOf(
            book(1, "Finished", 80, status = BookStatus.FINISHED, finished = "2026-01-01").copy(authors = "  First\n Last  "),
            book(2, "Ongoing", 30, last = "2026-01-02").copy(authors = "Writer"),
            book(3, "No author", 20, last = "2026-01-03").copy(authors = "  "))
        assertEquals("2026:\n1:Finished - First Last\nOngoing - Writer 30%\nNo author 20%", formatBookReadingList(
            books, emptyList(), zone, BookReadingListOptions(includeAuthors = true)))
        assertEquals("2026:\n1:Finished\nOngoing 30%\nNo author 20%", formatBookReadingList(books, emptyList(), zone))
    }

    @Test fun exportOptionsCombineWithCollectionExclusions() {
        val books = listOf(
            book(1, "Excluded", 100, status = BookStatus.FINISHED, finished = "2025-01-01").copy(collections = "School"),
            book(2, "Reading", 50, last = "2025-01-02"),
            book(3, "Compendium", 80, status = BookStatus.FINISHED, finished = "2025-01-03",
                parts = listOf(BookPart("Part", 1, 20))))
        assertEquals("2025:\n1:Compendium - Author", formatBookReadingList(books, emptyList(), zone,
            BookReadingListOptions(setOf("School"), finishedOnly = true, includeParts = false, includeAuthors = true)))
    }

    @Test fun allBooksIncludesUnreadAndSmallProgressOnceAndStillExcludesCollections() {
        val books = listOf(
            book(1, "Finished", 80, status = BookStatus.FINISHED, finished = "2026-01-01"),
            book(2, "Small progress", 3, last = "2025-01-01"),
            book(3, "Unread", status = BookStatus.WISHLIST),
            book(4, "School", status = BookStatus.WISHLIST).copy(collections = "School"))
        val sessions = listOf(session(1, 1, "2024-01-01", 40, 40), session(2, 1, "2026-01-01", 40, 80))
        val groups = buildBookReadingList(books, sessions, zone,
            BookReadingListOptions(excludedCollections = setOf("school"), allBooks = true))
        assertEquals(listOf("2024", "2025", "2026", "Not started"), groups.map { it.heading })
        assertEquals(listOf(2, 1, 3), groups.flatMap { it.entries }.map { it.book.id })
        assertEquals(listOf(false, true, false), groups.flatMap { it.entries }.map { it.finished })
        assertEquals("2025:\nSmall progress 3%\n\n2026:\n1:Finished\n\nNot started:\nUnread 0%",
            formatBookReadingList(books, sessions, zone, BookReadingListOptions(setOf("school"), allBooks = true)))
    }

    @Test fun allBooksStatusColorFollowsFinishedStatusRatherThanPageCount() {
        val groups = buildBookReadingList(listOf(
            book(1, "Marked finished", 80, status = BookStatus.FINISHED, finished = "2026-01-01"),
            book(2, "Still reading", 100, last = "2026-01-02")), emptyList(), zone, BookReadingListOptions(allBooks = true))
        assertEquals(listOf(true, false), planBookReadingPoster(groups, BookReadingListOptions()).flatMap { it.sections }.flatMap { it.tiles }.map { it.finished })
    }

    @Test fun posterKeepsEveryBookAndPartInOneLongImage() {
        val books = (1..250).map { book(it, "Book $it", 80, status = BookStatus.FINISHED, finished = "2026-01-01",
            parts = listOf(BookPart("Part $it", 1, 20))) }
        val pages = planBookReadingPoster(buildBookReadingList(books, emptyList(), zone), BookReadingListOptions(includeAuthors = true))
        assertEquals(1, pages.size)
        assertTrue(pages.single().height > 30000)
        val tiles = pages.flatMap { it.sections }.flatMap { it.tiles }
        assertEquals(500, tiles.size)
        assertEquals(500, tiles.map { it.title }.distinct().size)
        assertTrue(tiles.filter { it.parent.isBlank() }.all { it.author == "Author" })
        assertEquals(listOf("2026"), pages.single().sections.map { it.heading })
    }

    @Test fun completionDatesUseActualLocalFinishDateAndStayOffByDefault() {
        val books = listOf(book(1, "Finished", 80, status = BookStatus.FINISHED, finished = "2025-12-31T23:30:00Z"),
            book(2, "Ongoing", 30, last = "2026-01-02"), book(3, "Unknown", 80, status = BookStatus.FINISHED))
        val options = BookReadingListOptions(includeCompletionDates = true)
        assertEquals("2026:\n1:Finished · 2026-01-01\nOngoing 30%\n\nUnknown year:\n1:Unknown",
            formatBookReadingList(books, emptyList(), zone, options))
        val groups = buildBookReadingList(books, emptyList(), zone, options)
        val tiles = planBookReadingPoster(groups, options).single().sections.flatMap { it.tiles }
        assertEquals("2026-01-01", tiles.first().completedOn.toString())
        assertNull(tiles[1].completedOn)
        assertNull(tiles[2].completedOn)
        assertTrue(planBookReadingPoster(groups, BookReadingListOptions()).single().sections.flatMap { it.tiles }.all { it.completedOn == null })
    }

    @Test fun statusIsRedBelowOnePercentYellowFromOnePercentAndGreenWhenFinished() {
        fun tile(percent: Int?, read: Boolean = false, finished: Boolean = false) =
            BookReadingPosterTile("Book", "", "", finished, percent, hasReadPages = read).status
        assertEquals(BookReadingPosterStatus.NOT_STARTED, tile(readingListPercent(9, 1000), true))
        assertEquals(BookReadingPosterStatus.NOT_STARTED, tile(0))
        assertEquals(BookReadingPosterStatus.NOT_STARTED, tile(null))
        assertEquals(BookReadingPosterStatus.IN_PROGRESS, tile(readingListPercent(10, 1000), true))
        assertEquals(BookReadingPosterStatus.IN_PROGRESS, tile(100, true))
        assertEquals(BookReadingPosterStatus.IN_PROGRESS, tile(null, true))
        assertEquals(BookReadingPosterStatus.FINISHED, tile(0, finished = true))
    }

    @Test fun yearTotalsUseActualReadingInEachYearAndRespectExportCollectionsWithoutDoublingParts() {
        val books = listOf(book(1, "Compendium", 180, 200, BookStatus.FINISHED, "2026-02-01",
            parts = listOf(BookPart("Part", 1, 20))),
            book(2, "School", 100, status = BookStatus.FINISHED, finished = "2026-02-01").copy(collections = "School"))
        val sessions = listOf(session(1, 1, "2024-01-01", 96, 96, excluded = true).copy(durationSeconds = 3600),
            session(2, 1, "2026-02-01", 84, 180).copy(durationSeconds = 7200),
            session(3, 2, "2026-02-01", 100, 100).copy(durationSeconds = 36000))
        val options = BookReadingListOptions(excludedCollections = setOf("school"))
        val groups = buildBookReadingList(books, sessions, zone, options)
        assertEquals(listOf(96L, 84L), groups.map { it.pagesRead })
        assertEquals(listOf(3600L, 7200L), groups.map { it.readingSeconds })
        val sections = planBookReadingPoster(groups, options).single().sections
        assertEquals(listOf(96L, 84L), sections.map { it.pagesRead })
        assertEquals(listOf(3600L, 7200L), sections.map { it.readingSeconds })
    }

    @Test fun yearTotalsKeepLibraryOnlyReadingWithoutInventingUnreadPages() {
        val books = listOf(book(1, "Historical", 100, last = "2025-01-01").copy(readingSeconds = 3600),
            book(2, "Finished with notes skipped", 180, 200, BookStatus.FINISHED, "2026-01-01").copy(readingSeconds = 7200))
        val sessions = listOf(session(1, 1, "2024-01-01", 40, 40).copy(durationSeconds = 1200))
        val groups = buildBookReadingList(books, sessions, zone)
        assertEquals(listOf("2024", "2025", "2026"), groups.map { it.heading })
        assertEquals(listOf(40L, 60L, 180L), groups.map { it.pagesRead })
        assertEquals(listOf(1200L, 2400L, 7200L), groups.map { it.readingSeconds })
    }

    @Test fun allLibraryKeepsEveryYearTotalEvenWhenTheBookCoverAppearsOnlyAtCompletion() {
        val books = listOf(book(1, "Spanning years", 180, 200, BookStatus.FINISHED, "2026-02-01",
            parts = listOf(BookPart("Part", 1, 20))), book(2, "Unread", status = BookStatus.WISHLIST))
        val sessions = listOf(session(1, 1, "2024-03-01", 96, 96, excluded = true).copy(durationSeconds = 3600),
            session(2, 1, "2026-02-01", 84, 180).copy(durationSeconds = 7200))
        val normal = buildBookReadingList(books, sessions, zone)
        val options = BookReadingListOptions(allBooks = true)
        val all = buildBookReadingList(books, sessions, zone, options)
        normal.forEach { expected ->
            val actual = all.single { it.heading == expected.heading }
            assertEquals(expected.pagesRead, actual.pagesRead)
            assertEquals(expected.readingSeconds, actual.readingSeconds)
        }
        assertEquals(180L, all.sumOf { it.pagesRead })
        assertEquals(10800L, all.sumOf { it.readingSeconds })
        assertTrue(all.single { it.heading == "2024" }.entries.isEmpty())
        assertEquals(listOf(1, 2), all.flatMap { it.entries }.map { it.book.id })
        val poster = planBookReadingPoster(all, options).single()
        assertEquals(listOf("2024", "2026", "Not started"), poster.sections.map { it.heading })
        assertEquals(96L, poster.sections.first().pagesRead)
        assertTrue(poster.sections.first().tiles.isEmpty())
    }

    @Test fun coverThresholdsDoNotChangeTotalsAndCollectionExclusionsStillApply() {
        val books = listOf(book(1, "Small progress", 10, 100, last = "2026-01-01"),
            book(2, "School", 100, status = BookStatus.FINISHED, finished = "2026-01-01").copy(collections = "School"))
        val sessions = listOf(session(1, 1, "2025-01-01", 4, 4), session(2, 1, "2026-01-01", 6, 10),
            session(3, 2, "2026-01-01", 100, 100).copy(durationSeconds = 3600))
        val options = BookReadingListOptions(excludedCollections = setOf("School"))
        val normal = buildBookReadingList(books, sessions, zone, options)
        val all = buildBookReadingList(books, sessions, zone, options.copy(allBooks = true))
        assertEquals(normal.map { it.heading to (it.pagesRead to it.readingSeconds) }, all.map { it.heading to (it.pagesRead to it.readingSeconds) })
        assertEquals(10L, all.sumOf { it.pagesRead })
        assertEquals(120L, all.sumOf { it.readingSeconds })
        assertTrue(normal.flatMap { it.entries }.isEmpty())
        assertEquals(listOf(1), all.flatMap { it.entries }.map { it.book.id })
    }

    @Test fun allLibraryTotalsRespectLocalNewYearAndKeepUndatedReadingOnce() {
        val books = listOf(book(1, "Local new year", 80, status = BookStatus.FINISHED, finished = "2026-02-01"),
            book(2, "Unknown date", 30, status = BookStatus.FINISHED).copy(readingSeconds = 3600))
        val sessions = listOf(session(1, 1, "2025-12-31T23:30:00Z", 40, 40), session(2, 1, "2026-02-01", 40, 80))
        val groups = buildBookReadingList(books, sessions, zone, BookReadingListOptions(allBooks = true))
        assertEquals(listOf("2026", "Unknown year"), groups.map { it.heading })
        assertEquals(listOf(80L, 30L), groups.map { it.pagesRead })
        assertEquals(listOf(120L, 3600L), groups.map { it.readingSeconds })
    }
}
