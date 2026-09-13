package com.roinur.booktracker

import java.time.Instant

internal fun mostRecentlyReadBook(books: List<BookRow>, sessions: List<BookReadingSessionWithBook>, activeBookId: Int?): BookRow? {
    val byId = books.associateBy { it.id }
    byId[activeBookId]?.let { return it }
    val latest = sessions.mapNotNull { row ->
        val book = byId[row.session.bookId] ?: return@mapNotNull null
        runCatching { Instant.parse(row.session.activityAt) }.getOrNull()?.let { it to book }
    }.maxByOrNull { it.first }
    if (latest != null) return latest.second
    return books.mapNotNull { book ->
        runCatching { Instant.parse(book.lastReadAt) }.getOrNull()?.let { it to book }
    }.maxByOrNull { it.first }?.second
}
