package com.roinur.booktracker

import java.time.LocalDate
import java.util.Locale

internal enum class BookLibraryIssue(val label: String) {
    TITLE("Missing title"), AUTHOR("Missing author"), PAGES("Missing total pages"),
    PAGE_RANGE("Progress exceeds total pages"), COVER("Missing cover"),
    PUBLISHER("Missing publisher collection"), TYPE("Missing type collection"), THEME("Missing theme collection"),
    UNTYPED("Some collections have no type"), STATUS("Reading at page 0, with no logged reading"),
    FINISHED_DATE("Missing completion date")
}

/** Suggestions only. Manual statuses and overlapping subjects are never inferred away. */
internal fun bookLibraryIssues(book: BookRow, types: Map<String, BookCollectionType>,
    hasReadingHistory: Boolean = false, activeBookId: Int? = null): List<BookLibraryIssue> = buildList {
    val normalized = types.mapKeys { it.key.lowercase(Locale.ROOT) }
    val names = splitBookCollections(book.collections)
    val assigned = names.mapNotNull { normalized[it.lowercase(Locale.ROOT)] }.toSet()
    if (book.title.isBlank() || book.title.equals("Untitled book", true) || book.title.startsWith("ISBN ", true)) add(BookLibraryIssue.TITLE)
    if (book.authors.isBlank() || book.authors.trim().equals("Unknown author", true) || book.authors.trim().equals("Unknown", true)) add(BookLibraryIssue.AUTHOR)
    if (book.pageCount <= 0) add(BookLibraryIssue.PAGES)
    else if (book.currentPage > book.pageCount) add(BookLibraryIssue.PAGE_RANGE)
    if (book.coverUrl.isBlank()) add(BookLibraryIssue.COVER)
    if (BookCollectionType.PUBLISHER !in assigned) add(BookLibraryIssue.PUBLISHER)
    if (BookCollectionType.TYPE !in assigned) add(BookLibraryIssue.TYPE)
    if (BookCollectionType.THEME !in assigned) add(BookLibraryIssue.THEME)
    if (names.any { it.lowercase(Locale.ROOT) !in normalized }) add(BookLibraryIssue.UNTYPED)
    if (book.id != activeBookId && book.status == BookStatus.READING && book.currentPage == 0 &&
        book.readingSeconds == 0L && !hasReadingHistory) add(BookLibraryIssue.STATUS)
    if (book.status == BookStatus.FINISHED && book.finishedAt.isBlank()) add(BookLibraryIssue.FINISHED_DATE)
}

/** Only changed columns are supplied; reading history, parts and progress remain independent. */
internal data class BookLibraryFix(
    val bookId: Int,
    val title: String? = null,
    val authors: String? = null,
    val pageCount: Int? = null,
    val isbn: String? = null,
    val coverUrl: String? = null,
    val collections: String? = null,
    val status: BookStatus? = null,
    val completionDate: LocalDate? = null,
    val collectionTypes: Map<String, BookCollectionType> = emptyMap()
) {
    val hasChanges: Boolean get() = title != null || authors != null || pageCount != null || isbn != null || coverUrl != null ||
        collections != null || status != null || completionDate != null || collectionTypes.isNotEmpty()
}
