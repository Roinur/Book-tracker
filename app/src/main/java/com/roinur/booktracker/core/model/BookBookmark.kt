package com.roinur.booktracker

internal data class BookReadingEntry(val pagesRead: Int, val totalPagesRead: Int, val bookmarkPage: Int?)

/** Ordinary input is a printed endpoint. Calculator input displays the cumulative
 * total instead, with its printed endpoint supplied separately as an automatic bookmark. */
internal fun bookFinishReadingEntry(book: BookRow, reachedText: String, bookmarkText: String?, calculatedPagesRead: Int? = null): BookReadingEntry? {
    val reached = reachedText.toIntOrNull()?.takeIf { it >= 0 } ?: return null
    if (calculatedPagesRead != null && bookmarkText == null) return null
    val pages = calculatedPagesRead ?: (reached - book.readingPage).coerceAtLeast(0)
    val bookmark = bookmarkText ?: book.bookmarkPage?.let { reachedText }
    return bookReadingEntry(book, pages.toString(), bookmark)
}

internal fun bookReadingEntry(book: BookRow, pagesText: String, bookmarkText: String?): BookReadingEntry? {
    val pages = pagesText.toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val total = book.currentPage.toLong() + pages
    if (total > Int.MAX_VALUE) return null
    val bookmark = if (bookmarkText == null) null
        else bookmarkText.toIntOrNull()?.takeIf { it >= 0 } ?: return null
    return BookReadingEntry(pages, total.toInt(), bookmark)
}
