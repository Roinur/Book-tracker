package com.roinur.booktracker

import java.util.Locale
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

internal data class ReadingMapFamily(
    val name: String,
    val bookIds: Set<Int>,
    val x: Float,
    val y: Float,
    val type: BookCollectionType
)

internal data class ReadingMapBook(
    val book: BookRow,
    val families: List<String>,
    val x: Float,
    val y: Float
)

internal enum class ReadingMapFilter(val label: String) {
    ALL("All"),
    READ("Read"),
    FINISHED("Finished"),
    READING("Reading"),
    ON_HOLD("On hold"),
    NOT_STARTED("Not started");

    fun includes(book: BookRow): Boolean = when (this) {
        ALL -> true
        READ -> book.currentPage > 0 || book.readingSeconds > 0 || book.status == BookStatus.FINISHED
        FINISHED -> book.status == BookStatus.FINISHED
        READING -> book.status == BookStatus.READING
        ON_HOLD -> book.status == BookStatus.PAUSED
        NOT_STARTED -> book.status == BookStatus.WISHLIST
    }

    fun next(): ReadingMapFilter = entries[(ordinal + 1) % entries.size]
}

internal data class BookReadingMap(
    val families: List<ReadingMapFamily>,
    val books: List<ReadingMapBook>,
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float
) {
    fun filtered(filter: ReadingMapFilter, collectionType: BookCollectionType? = null): BookReadingMap {
        if (filter == ReadingMapFilter.ALL && collectionType == null) return this
        val matchingFamilies = families.filter { collectionType == null || it.type == collectionType }
        val matchingNames = matchingFamilies.mapTo(hashSetOf()) { it.name }
        val visibleBooks = books.filter { node ->
            filter.includes(node.book) && node.families.any { it in matchingNames }
        }.map { node -> node.copy(families = node.families.filter { it in matchingNames }) }
        val visibleIds = visibleBooks.mapTo(hashSetOf()) { it.book.id }
        val visibleFamilies = matchingFamilies.mapNotNull { family ->
            val ids = family.bookIds.intersect(visibleIds)
            if (ids.isEmpty()) null else family.copy(bookIds = ids)
        }
        return copy(families = visibleFamilies, books = visibleBooks)
    }

    companion object {
        fun from(books: List<BookRow>, collectionTypes: Map<String, BookCollectionType> = emptyMap()): BookReadingMap {
            val mappedBooks = books.sortedBy { it.id }
            val namesByBook = mappedBooks.associate { book ->
                book.id to splitBookCollections(book.collections)
                    .ifEmpty { listOf("Uncategorized") }
                    .distinctBy { it.lowercase(Locale.ROOT) }
            }
            val groups = linkedMapOf<String, Pair<String, MutableSet<Int>>>()
            mappedBooks.forEach { book ->
                namesByBook.getValue(book.id).forEach { name ->
                    val key = name.lowercase(Locale.ROOT)
                    groups.getOrPut(key) { name to linkedSetOf() }.second.add(book.id)
                }
            }
            val sortedGroups = groups.values.sortedWith(compareByDescending<Pair<String, MutableSet<Int>>> { it.second.size }.thenBy { it.first })
            val families = sortedGroups.mapIndexed { index, (name, ids) ->
                val ring = (index - 1).coerceAtLeast(0) / 12
                val position = (index - 1).coerceAtLeast(0) % 12
                val ringCount = minOf(12, (sortedGroups.size - 1 - ring * 12).coerceAtLeast(1))
                val angle = (-PI / 2.0 + position * 2.0 * PI / ringCount + ring * 0.35).toFloat()
                val radius = if (index == 0) 0f else 380f + ring * 300f
                ReadingMapFamily(name, ids, radius * cos(angle), radius * sin(angle),
                    collectionTypes[name.lowercase(Locale.ROOT)] ?: BookCollectionType.OTHER)
            }
            val byName = families.associateBy { it.name.lowercase(Locale.ROOT) }
            val singleFamilyIndex = mutableMapOf<String, Int>()
            val nodes = mappedBooks.map { book ->
                val names = namesByBook.getValue(book.id)
                val linked = names.mapNotNull { byName[it.lowercase(Locale.ROOT)] }
                val centerX = linked.map { it.x }.average().toFloat()
                val centerY = linked.map { it.y }.average().toFloat()
                val slot = if (linked.size == 1) {
                    val key = linked.single().name.lowercase(Locale.ROOT)
                    singleFamilyIndex[key] = (singleFamilyIndex[key] ?: 0) + 1
                    singleFamilyIndex.getValue(key)
                } else book.id
                val angle = slot * 2.3999632f
                val radius = if (linked.size == 1) 52f + 21f * sqrt(slot.toFloat()) else 21f + (book.id % 4) * 6f
                ReadingMapBook(book, linked.map { it.name }, centerX + radius * cos(angle), centerY + radius * sin(angle))
            }
            val xs = families.map { it.x } + nodes.map { it.x }
            val ys = families.map { it.y } + nodes.map { it.y }
            return BookReadingMap(families, nodes, (xs.minOrNull() ?: 0f) - 70f,
                (ys.minOrNull() ?: 0f) - 70f, (xs.maxOrNull() ?: 0f) + 70f, (ys.maxOrNull() ?: 0f) + 70f)
        }
    }
}
