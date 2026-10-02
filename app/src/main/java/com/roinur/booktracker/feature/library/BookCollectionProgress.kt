package com.roinur.booktracker

import java.util.Locale

internal data class CollectionProgress(
    val name: String,
    val type: BookCollectionType,
    val books: List<BookRow>
) {
    val counts = BookStatus.entries.associateWith { status -> books.count { it.status == status } }
    val finished: Int get() = counts.getValue(BookStatus.FINISHED)
    val knownPages = books.filter { it.pageCount > 0 }
    val readPages: Long = knownPages.sumOf { it.currentPage.coerceAtLeast(0).toLong() }
    val totalPages: Long = knownPages.sumOf { it.pageCount.toLong() }
    val unknownPageCounts: Int get() = books.size - knownPages.size
    val pageFraction: Float get() = if (totalPages == 0L) 0f else
        (readPages.toDouble() / totalPages).toFloat().coerceIn(0f, 1f)
}

internal fun collectionProgress(books: List<BookRow>, names: List<String>,
                                types: Map<String, BookCollectionType>): List<CollectionProgress> {
    val groups = linkedMapOf<String, Pair<String, MutableList<BookRow>>>()
    names.filter { it.isNotBlank() }.forEach { name ->
        groups.putIfAbsent(name.trim().lowercase(Locale.ROOT), name.trim() to mutableListOf())
    }
    books.distinctBy { it.id }.forEach { book ->
        splitBookCollections(book.collections).distinctBy { it.lowercase(Locale.ROOT) }.forEach { name ->
            groups.getOrPut(name.lowercase(Locale.ROOT)) { name to mutableListOf() }.second.add(book)
        }
    }
    return groups.map { (key, group) ->
        CollectionProgress(group.first, types[key] ?: BookCollectionType.OTHER, group.second.toList())
    }.sortedWith(compareByDescending<CollectionProgress> { it.books.size }.thenBy { it.name.lowercase(Locale.ROOT) })
}
