package com.roinur.booktracker

import kotlin.math.ceil

/** Remaining work, not lifetime reading totals. Finished books need no more reading,
 * even when their final pages contain notes or other material the reader skipped. */
internal data class BookPlanWorkload(
    val bookCount: Int = 0,
    val remainingPages: Long = 0,
    val remainingSeconds: Long? = 0,
    val unknownLengths: Int = 0
) {
    val complete: Boolean get() = bookCount > 0 && remainingPages == 0L && unknownLengths == 0
}

internal fun bookPlanWorkload(book: BookRow, pagesPerHour: Double?): BookPlanWorkload {
    if (book.status == BookStatus.FINISHED || (book.pageCount > 0 && book.currentPage >= book.pageCount))
        return BookPlanWorkload(bookCount = 1)
    if (book.pageCount <= 0) return BookPlanWorkload(bookCount = 1, remainingSeconds = null, unknownLengths = 1)
    val remaining = (book.pageCount.toLong() - book.currentPage.coerceAtLeast(0)).coerceAtLeast(0)
    val seconds = pagesPerHour?.takeIf { it.isFinite() && it > 0 }
        ?.let { ceil(remaining * 3600.0 / it).toLong() }
    return BookPlanWorkload(bookCount = 1, remainingPages = remaining, remainingSeconds = seconds)
}

internal fun bookPlanWorkload(ids: List<Int>, books: Map<Int, BookPlanWorkload>): BookPlanWorkload =
    ids.distinct().mapNotNull(books::get).fold(BookPlanWorkload()) { total, book ->
        BookPlanWorkload(total.bookCount + book.bookCount, total.remainingPages + book.remainingPages,
            total.remainingSeconds?.let { seconds -> book.remainingSeconds?.let { seconds + it } },
            total.unknownLengths + book.unknownLengths)
    }
