package com.roinur.booktracker

/** Both endpoints are printed pages included in the work: 3–74 contains 72 pages. */
data class BookPart(val title: String, val startPage: Int, val endPage: Int, val coverUrl: String = "", val rating: Int = 0) {
    val pageCount get() = endPage - startPage + 1
    fun pagesRead(page: Int) = (page.toLong() - startPage + 1).coerceIn(0L, pageCount.toLong()).toInt()
    fun completed(page: Int) = page >= endPage
}

internal fun validateBookParts(parts: List<BookPart>, totalPages: Int = 0): List<BookPart> {
    val sorted = parts.map { it.copy(title = it.title.trim()) }.sortedBy { it.startPage }
    sorted.forEachIndexed { index, part ->
        require(part.title.isNotBlank()) { "Give every part a title." }
        require(part.rating in 0..5) { "Part rating must be between 0 and 5." }
        require(part.startPage >= 1 && part.endPage >= part.startPage) { "Use page 1 or later; end page cannot be before start page." }
        require(totalPages <= 0 || part.endPage <= totalPages) { "${part.title} ends after the book's total pages." }
        require(index == 0 || sorted[index - 1].endPage < part.startPage) { "Parts cannot overlap." }
    }
    return sorted
}

internal fun BookRow.currentPart(): BookPart? = parts.firstOrNull { currentPage >= it.startPage - 1 && currentPage < it.endPage }

internal data class BookPartReading(val pages: Long = 0, val seconds: Long = 0)

internal fun bookPartRemainingSeconds(part: BookPart, currentPage: Int, timedReading: BookPartReading, fallbackPagesPerHour: Double?): Long? {
    val remaining = part.pageCount - part.pagesRead(currentPage)
    if (remaining == 0) return 0L
    val pace = if (timedReading.pages > 0 && timedReading.seconds > 0) timedReading.pages * 3600.0 / timedReading.seconds
        else fallbackPagesPerHour
    return pace?.takeIf { it.isFinite() && it > 0 }?.let { kotlin.math.ceil(remaining * 3600.0 / it).toLong() }
}

/** Infer legacy endpoints in chronological order per book, anchored by any explicitly recorded endpoint. */
internal fun bookSessionsWithPageFallback(sessions: List<BookReadingSessionRow>): List<BookReadingSessionRow> {
    val result = sessions.toMutableList()
    val cursorByBook = mutableMapOf<Int, Long>()
    fun date(session: BookReadingSessionRow): java.time.Instant =
        runCatching { java.time.Instant.parse(session.activityAt) }.getOrNull()
            ?: runCatching { java.time.LocalDate.parse(session.activityAt).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant() }.getOrNull()
            ?: java.time.Instant.MIN
    sessions.withIndex().sortedWith(compareBy<IndexedValue<BookReadingSessionRow>> { date(it.value) }
        .thenBy { it.value.id }.thenBy { it.index }).forEach { (index, session) ->
        val end = if (session.pageReached > 0) session.pageReached.toLong()
            else (cursorByBook[session.bookId] ?: 0L) + session.pagesRead.coerceAtLeast(0)
        cursorByBook[session.bookId] = end
        if (session.pageReached <= 0 && session.pagesRead > 0) result[index] = session.copy(pageReached = end.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }
    return result
}

/** Split each logged pass by its inclusive page interval, inferred from earlier passes when missing. */
internal fun bookPartReading(parts: List<BookPart>, sessions: List<BookReadingSessionRow>): List<BookPartReading> {
    val pages = LongArray(parts.size)
    val seconds = LongArray(parts.size)
    bookSessionsWithPageFallback(sessions).forEach { session ->
        val read = session.pagesRead.toLong().coerceAtLeast(0)
        if (read == 0L || session.pageReached <= 0) return@forEach
        val end = session.pageReached.toLong()
        val start = (end - read + 1).coerceAtLeast(1)
        val duration = session.durationSeconds.coerceAtLeast(0)
        var allocatedPages = 0L
        var allocatedSeconds = 0L
        parts.forEachIndexed { index, part ->
            val overlap = (minOf(end, part.endPage.toLong()) - maxOf(start, part.startPage.toLong()) + 1).coerceAtLeast(0)
                .coerceAtMost(read - allocatedPages)
            allocatedPages += overlap
            // Integer cumulative allocation keeps every second, even when a pass crosses many short parts.
            val cumulativeSeconds = (duration / read) * allocatedPages + (duration % read) * allocatedPages / read
            pages[index] += overlap
            seconds[index] += cumulativeSeconds - allocatedSeconds
            allocatedSeconds = cumulativeSeconds
        }
    }
    return parts.indices.map { BookPartReading(pages[it], seconds[it]) }
}

internal data class BookWorkStatistics(val bookId: Int, val title: String, val parentTitle: String = "", val pages: Long, val seconds: Long, val coverUrl: String = "")

/** Presentation only. Never creates books/sessions or changes goals, totals or dated activity. */
internal fun bookWorkStatistics(books: List<BookRow>, sessions: List<BookReadingSessionRow>, allTime: Boolean): List<BookWorkStatistics> {
    val byBook = sessions.groupBy { it.bookId }
    return books.flatMap { book ->
        val logged = byBook[book.id].orEmpty()
        var pages = if (allTime) book.currentPage.toLong().coerceAtLeast(0) else logged.sumOf { it.pagesRead.toLong().coerceAtLeast(0) }
        var seconds = if (allTime) book.readingSeconds.coerceAtLeast(0) else logged.sumOf { it.durationSeconds.coerceAtLeast(0) }
        val rows = mutableListOf<BookWorkStatistics>()
        val readings = bookPartReading(book.parts, logged)
        if (book.status != BookStatus.FINISHED) {
            book.parts.forEachIndexed { index, part ->
                if (!part.completed(book.currentPage)) return@forEachIndexed
                val allocatedPages = (if (allTime) part.pageCount.toLong() else readings[index].pages).coerceIn(0, pages)
                val allocatedSeconds = readings[index].seconds.coerceIn(0, seconds)
                rows += BookWorkStatistics(book.id, part.title, book.title, allocatedPages, allocatedSeconds, part.coverUrl.ifBlank { book.coverUrl })
                pages -= allocatedPages; seconds -= allocatedSeconds
            }
        }
        rows += BookWorkStatistics(book.id, book.title, pages = pages, seconds = seconds, coverUrl = book.coverUrl)
        rows
    }
}
