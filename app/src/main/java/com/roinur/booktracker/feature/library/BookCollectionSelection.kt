package com.roinur.booktracker

import java.util.Locale

internal data class BookCollectionSelection(
    val bookIds: Set<Int> = emptySet(),
    val changes: Map<String, Boolean> = emptyMap()
) {
    fun selectedCollections(books: List<BookRow>): Set<String> {
        val selected = books.filter { it.id in bookIds }
        val common = selected.map { book ->
            splitBookCollections(book.collections).mapTo(hashSetOf()) { it.lowercase(Locale.ROOT) }
        }.reduceOrNull { a, b -> a.intersect(b).toHashSet() }.orEmpty()
        return (common + changes.filterValues { it }.keys) - changes.filterValues { !it }.keys
    }

    fun toggleBook(id: Int) = copy(bookIds = if (id in bookIds) bookIds - id else bookIds + id)

    fun mixedCollections(books: List<BookRow>): Set<String> {
        val memberships = books.filter { it.id in bookIds }.map { book ->
            splitBookCollections(book.collections).mapTo(hashSetOf()) { it.lowercase(Locale.ROOT) }
        }
        val common = memberships.reduceOrNull { a, b -> a.intersect(b).toHashSet() }.orEmpty()
        return memberships.flatten().toSet() - common - changes.keys
    }

    fun previewCollections(book: BookRow, names: List<String>): String {
        if (book.id !in bookIds) return book.collections
        val additions = names.filterTo(linkedSetOf()) { changes[it.lowercase(Locale.ROOT)] == true }
        return editedBookCollections(book.collections, additions, changes.filterValues { !it }.keys)
    }

    fun toggleCollection(name: String, books: List<BookRow>): BookCollectionSelection {
        val key = name.lowercase(Locale.ROOT)
        return copy(changes = changes + (key to (key !in selectedCollections(books))))
    }

    fun renamed(names: Set<String>, target: String): BookCollectionSelection {
        val keys = names.mapTo(hashSetOf()) { it.lowercase(Locale.ROOT) }
        val affected = changes.filterKeys { it in keys }.values
        return if (affected.isEmpty()) this else copy(changes = changes.filterKeys { it !in keys } +
            (target.lowercase(Locale.ROOT) to affected.any { it }))
    }
}
