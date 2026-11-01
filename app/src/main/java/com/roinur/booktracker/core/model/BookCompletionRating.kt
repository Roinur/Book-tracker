package com.roinur.booktracker

/** Only a real transition of an existing book creates a prompt; opening/importing a library does not. */
internal fun bookCompletionRatingQueue(before: List<BookRow>, after: List<BookRow>, pending: List<Int>): List<Int> {
    val previous = before.associateBy { it.id }
    val eligible = after.filter { it.status == BookStatus.FINISHED && it.rating <= 0 }.associateBy { it.id }
    val added = eligible.values.filter { book ->
        previous[book.id]?.let { it.status != BookStatus.FINISHED } == true
    }.map { it.id }
    return (pending + added).distinct().filter { it in eligible }
}
