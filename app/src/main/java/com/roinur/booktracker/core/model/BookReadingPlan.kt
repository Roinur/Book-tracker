package com.roinur.booktracker

import java.util.UUID

internal data class BookPlanTier(val name: String, val bookIds: List<Int> = emptyList())

internal data class BookReadingPlan(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val tiers: List<BookPlanTier> = listOf("S", "A", "B", "C", "D", "E").map { BookPlanTier(it) },
    val active: Boolean = false
) {
    val bookIds: List<Int> get() = tiers.flatMap { it.bookIds }

    /** Moving also removes the old assignment, so a book can never occupy two tiers. */
    fun move(bookId: Int, tierIndex: Int?, position: Int = Int.MAX_VALUE): BookReadingPlan {
        require(tierIndex == null || tierIndex in tiers.indices)
        val cleared = tiers.map { it.copy(bookIds = it.bookIds.filterNot { id -> id == bookId }) }
        return copy(tiers = cleared.mapIndexed { index, tier ->
            if (index != tierIndex) tier else tier.copy(bookIds = tier.bookIds.toMutableList().apply {
                add(position.coerceIn(0, size), bookId)
            })
        })
    }

    fun orderedBooks(books: List<BookRow>): List<BookRow> {
        val byId = books.associateBy { it.id }
        return bookIds.distinct().mapNotNull(byId::get)
    }
}
