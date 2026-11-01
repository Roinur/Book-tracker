package com.roinur.booktracker

import org.junit.Assert.*
import org.junit.Test

class BookReadingPlanTest {
    private fun book(id: Int) = BookRow(id, "", "Book $id", "", 100, 0, BookStatus.WISHLIST, 0, "", "", "", "", false, "", "", "", "", 0)

    @Test fun movesAreUniqueAndRespectTierAndExplicitPosition() {
        val plan = BookReadingPlan(name = "Novels").move(1, 1).move(2, 0).move(3, 0).move(1, 0, 1)
        assertEquals(listOf(2, 1, 3), plan.bookIds)
        assertTrue(plan.tiers[1].bookIds.isEmpty())
        assertEquals(listOf(3, 2, 1), plan.move(3, 0, 0).bookIds)
        assertEquals(listOf(2, 3), plan.move(1, null).bookIds)
    }

    @Test fun tierOrderingOverridesLibrarySortingAndDeletedBooksAreSkipped() {
        val plan = BookReadingPlan(name = "Plan").move(9, 5).move(3, 0).move(2, 0).move(8, 2)
        assertEquals(listOf(3, 2, 9), plan.orderedBooks(listOf(book(9), book(2), book(3), book(1))).map { it.id })
        assertEquals(listOf("S", "A", "B", "C", "D", "E"), plan.tiers.map { it.name })
        assertEquals(0, book(3).currentPage)
    }

    @Test fun statusCycleLeavesPlanAndNeverEntersItWithoutApplyingAPlan() {
        assertEquals(BookLibraryFilter.ALL, BookLibraryFilter.PLAN.next())
        var filter = BookLibraryFilter.ALL
        repeat(15) { filter = filter.next(); assertNotEquals(BookLibraryFilter.PLAN, filter) }
        assertTrue(BookLibraryFilter.NOT_STARTED.matches(book(1), null))
        assertFalse(BookLibraryFilter.FINISHED.matches(book(1), null))
    }
}
