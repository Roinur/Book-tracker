package com.roinur.booktracker

import java.util.Locale

internal enum class BookPaceSource(val label: String) {
    BOOK("Book pace"), PUBLISHER("Publisher pace"), OVERALL("Overall pace"), UNAVAILABLE("No timed sessions")
}

internal data class BookReadingPace(val pagesPerHour: Double?, val source: BookPaceSource)

/** Weighted by reading time, not an average of session speeds. Shared publisher
 * collections form one sample; duplicate books, memberships and sessions count once. */
internal fun bookReadingPace(book: BookRow, sessions: List<BookReadingSessionRow>,
                             books: List<BookRow> = emptyList(),
                             collectionTypes: Map<String, BookCollectionType> = emptyMap()): BookReadingPace {
    val timed = sessions.distinctBy { it.id }.filter { !it.excludeFromStatistics && it.pagesRead > 0 && it.durationSeconds > 0 }
    fun result(sample: List<BookReadingSessionRow>, source: BookPaceSource): BookReadingPace? {
        if (sample.isEmpty()) return null
        return BookReadingPace(sample.sumOf { it.pagesRead.toLong() } * 3600.0 / sample.sumOf { it.durationSeconds }, source)
    }
    result(timed.filter { it.bookId == book.id }, BookPaceSource.BOOK)?.let { return it }
    val types = collectionTypes.mapKeys { it.key.lowercase(Locale.ROOT) }
    val publishers = splitBookCollections(book.collections).map { it.lowercase(Locale.ROOT) }
        .filter { types[it] == BookCollectionType.PUBLISHER }.toSet()
    if (publishers.isNotEmpty()) {
        val ids = books.filter { candidate -> splitBookCollections(candidate.collections).any { it.lowercase(Locale.ROOT) in publishers } }
            .map { it.id }.toSet()
        result(timed.filter { it.bookId in ids }, BookPaceSource.PUBLISHER)?.let { return it }
    }
    return result(timed, BookPaceSource.OVERALL) ?: BookReadingPace(null, BookPaceSource.UNAVAILABLE)
}
