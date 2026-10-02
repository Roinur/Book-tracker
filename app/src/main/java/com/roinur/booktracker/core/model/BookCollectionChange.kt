package com.roinur.booktracker

import java.util.Locale

internal data class BookCollectionChange(val before: Map<Int, String>, val after: Map<Int, String>)

internal fun editedBookCollections(current: String, additions: Set<String>, removals: Set<String>): String {
    val removedKeys = removals.mapTo(hashSetOf()) { it.lowercase(Locale.ROOT) }
    return normalizeBookCollections((splitBookCollections(current)
        .filterNot { it.lowercase(Locale.ROOT) in removedKeys } + additions).joinToString(", "))
}
