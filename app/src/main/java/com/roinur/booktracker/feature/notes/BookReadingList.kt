package com.roinur.booktracker

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

internal data class BookReadingListOptions(
    val excludedCollections: Set<String> = emptySet(),
    val finishedOnly: Boolean = false,
    val includeParts: Boolean = true,
    val includeAuthors: Boolean = false,
    val allBooks: Boolean = false,
    val includeCompletionDates: Boolean = false
)

internal data class BookReadingListPart(val part: BookPart, val index: Int, val page: Int, val completedOn: LocalDate? = null) {
    val finished: Boolean get() = part.completed(page)
}

internal data class BookReadingListEntry(
    val book: BookRow, val date: LocalDate?, val finished: Boolean, val page: Int,
    val parts: List<BookReadingListPart>, val unread: Boolean = false
)

internal data class BookReadingListGroup(
    val heading: String, val entries: List<BookReadingListEntry>, val pagesRead: Long = 0, val readingSeconds: Long = 0
)

/** One source of truth for text and cover-grid exports. Never changes library data. */
internal fun buildBookReadingList(
    books: List<BookRow>,
    sessions: List<BookReadingSessionRow>,
    zone: ZoneId = ZoneId.systemDefault(),
    options: BookReadingListOptions = BookReadingListOptions()
): List<BookReadingListGroup> {
    val entries = mutableListOf<BookReadingListEntry>()
    // Calendar totals belong to the selected books, not to the placement of their covers.
    val totals = mutableMapOf<Pair<Int?, Boolean>, Pair<Long, Long>>()
    fun addTotals(year: Int?, pages: Long, seconds: Long) {
        if (pages == 0L && seconds == 0L) return
        val key = year to false
        val previous = totals[key] ?: (0L to 0L)
        totals[key] = previous.first + pages to previous.second + seconds
    }
    val sessionsByBook = bookSessionsWithPageFallback(sessions).groupBy { it.bookId }
    val excludedNames = options.excludedCollections.mapTo(mutableSetOf()) { it.trim().lowercase(Locale.ROOT) }
    fun date(value: String) = bookLocalDate(value, zone)
    for (book in books) {
        if (options.finishedOnly && book.status != BookStatus.FINISHED) continue
        if (splitBookCollections(book.collections).any { it.lowercase(Locale.ROOT) in excludedNames }) continue
        val history = sessionsByBook[book.id].orEmpty().mapNotNull { session ->
            (date(session.activityAt) ?: date(session.startedAt))?.let { it to session }
        }.sortedWith(compareBy({ it.first }, { it.second.id }))
        val latestReadingDate = listOfNotNull(date(book.lastReadAt), history.lastOrNull()?.first).maxOrNull()
        val lastActivity = latestReadingDate ?: date(book.startedAt)
        val finishDate = if (book.status == BookStatus.FINISHED) date(book.finishedAt) ?: latestReadingDate else null
        val positionYear = if (book.status == BookStatus.FINISHED) finishDate?.year else lastActivity?.year
        val bookSessions = sessionsByBook[book.id].orEmpty()
        val unplacedPages = maxOf(book.currentPage.toLong().coerceAtLeast(0), bookSessions.sumOf { it.pagesRead.toLong().coerceAtLeast(0) }) -
            history.sumOf { it.second.pagesRead.toLong().coerceAtLeast(0) }
        val unplacedSeconds = maxOf(book.readingSeconds.coerceAtLeast(0), bookSessions.sumOf { it.durationSeconds.coerceAtLeast(0) }) -
            history.sumOf { it.second.durationSeconds.coerceAtLeast(0) }
        history.forEach { (day, session) ->
            addTotals(day.year, session.pagesRead.toLong().coerceAtLeast(0), session.durationSeconds.coerceAtLeast(0))
        }
        addTotals(positionYear, unplacedPages, unplacedSeconds)
        // Never use the added/import date as a substitute for a reading date.
        val years = history.filter { finishDate == null || it.first <= finishDate }.map { it.first.year }.toMutableSet()
        if (book.status == BookStatus.FINISHED) finishDate?.year?.let(years::add)
        else if (book.currentPage > 0 || book.readingSeconds > 0) lastActivity?.year?.let(years::add)
        val knownDates = years.isNotEmpty()
        if (!options.allBooks && !knownDates && book.status != BookStatus.FINISHED && book.currentPage <= 0 && book.readingSeconds <= 0) continue
        // All-books mode lists each library book once, keeping unread books out of invented years.
        val exportYears: List<Int?> = if (options.allBooks) listOf(positionYear)
            else if (knownDates) years.sorted() else listOf(null)
        for (year in exportYears) {
            val finished = book.status == BookStatus.FINISHED && year == finishDate?.year
            if (options.finishedOnly && !finished) continue
            val inYear = history.filter { it.first.year == year && (finishDate == null || it.first <= finishDate) }
            val beforeEnd = history.filter { year != null && it.first.year <= year && (finishDate == null || it.first <= finishDate) }
            val beforeStart = history.filter { year != null && it.first.year < year }
            val recordedPage = beforeEnd.maxOfOrNull { it.second.pageReached } ?: 0
            // The current library position is authoritative only for its latest known reading year.
            val page = if (options.allBooks || finished || year == positionYear || year == null) book.currentPage.coerceAtLeast(0) else recordedPage
            val previousPage = beforeStart.maxOfOrNull { it.second.pageReached } ?: 0
            // Partial entries need ten percentage points of actual progress in this year.
            // Compare with all earlier history, even when that history was too small to list.
            if (!finished && !options.allBooks) {
                if (book.pageCount <= 0) continue
                val gainedPages = page.coerceIn(0, book.pageCount) - previousPage.coerceIn(0, book.pageCount)
                if (gainedPages.toLong() * 10 < book.pageCount) continue
            }
            val parts = if (options.includeParts) book.parts.mapIndexedNotNull { index, part ->
                val read = part.pagesRead(page)
                if (read <= 0 && !options.allBooks) null
                // Past-year parts are already listed in their own year, unless only a library date is available.
                else if (!options.allBooks && year != null && inYear.isNotEmpty() && previousPage >= part.endPage) null
                else BookReadingListPart(part, index + 1, page,
                    beforeEnd.firstOrNull { it.second.pageReached >= part.endPage }?.first)
            } else emptyList()
            val entryDate = if (finished) finishDate else inYear.lastOrNull()?.first ?: lastActivity
            val unread = year == null && !finished && page == 0 && book.readingSeconds == 0L && history.isEmpty()
            entries += BookReadingListEntry(book, entryDate, finished, page, parts, unread)
        }
    }
    val coversByYear = entries.groupBy { it.date?.year to it.unread }
    return (coversByYear.keys + totals.keys)
        .sortedWith(compareBy({ it.first ?: Int.MAX_VALUE }, { it.second }))
        .map { key ->
            val rows = coversByYear[key].orEmpty()
            val reading = totals[key] ?: (0L to 0L)
            BookReadingListGroup(key.first?.toString() ?: if (key.second) "Not started" else "Unknown year",
                rows.sortedWith(compareBy({ it.date ?: LocalDate.MAX }, { it.book.id })), reading.first, reading.second)
        }
}

internal fun readingListTitle(value: String) = value.trim().replace(Regex("\\s+"), " ").ifBlank { "Untitled book" }
internal fun readingListPercent(page: Int, total: Int): Int? =
    if (total > 0) (page.toLong() * 100 / total).coerceIn(0, 100).toInt() else null

internal fun formatBookReadingList(
    books: List<BookRow>, sessions: List<BookReadingSessionRow>, zone: ZoneId = ZoneId.systemDefault(),
    options: BookReadingListOptions = BookReadingListOptions()
): String {
    fun percent(page: Int, total: Int) = readingListPercent(page, total)?.let { " $it%" }.orEmpty()
    return buildBookReadingList(books, sessions, zone, options).filter { it.entries.isNotEmpty() }.joinToString("\n\n") { group ->
            var number = 0
            buildList {
                add("${group.heading}:")
                group.entries.forEach { entry ->
                    val author = if (options.includeAuthors) entry.book.authors.trim().replace(Regex("\\s+"), " ") else ""
                    val label = readingListTitle(entry.book.title) + if (author.isNotBlank()) " - $author" else ""
                    val completed = if (options.includeCompletionDates && entry.finished && entry.date != null) " · ${entry.date}" else ""
                    add(if (entry.finished) "${++number}:$label$completed" else label + percent(entry.page, entry.book.pageCount))
                    entry.parts.forEach { part ->
                        val partDate = if (options.includeCompletionDates && part.completedOn != null) " · ${part.completedOn}" else ""
                        add(if (part.finished) "    (${part.index}):${readingListTitle(part.part.title)}$partDate"
                            else "    ${readingListTitle(part.part.title)}${percent(part.part.pagesRead(part.page), part.part.pageCount)}")
                    }
                }
            }.joinToString("\n")
        }
}
