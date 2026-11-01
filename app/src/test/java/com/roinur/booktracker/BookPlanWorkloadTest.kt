package com.roinur.booktracker

import org.junit.Assert.*
import org.junit.Test

class BookPlanWorkloadTest {
    private fun book(id: Int, pages: Int = 300, read: Int = 100, status: BookStatus = BookStatus.READING) =
        BookRow(id, "", "Book $id", "", pages, read, status, 0, "", "", "", "", false, "", "", "", "", 0)

    @Test fun totalsSumEachBooksOwnRemainingTimeRatherThanAveragingSpeeds() {
        val books = mapOf(1 to bookPlanWorkload(book(1), 50.0), 2 to bookPlanWorkload(book(2), 100.0))
        val total = bookPlanWorkload(listOf(1, 2), books)
        assertEquals(400L, total.remainingPages)
        assertEquals(21600L, total.remainingSeconds)
        assertEquals(2, total.bookCount)
    }

    @Test fun completedBooksDoNotAddSkippedTailPagesToRemainingWork() {
        val finished = bookPlanWorkload(book(1, pages = 200, read = 180, status = BookStatus.FINISHED), null)
        assertTrue(finished.complete); assertEquals(0L, finished.remainingPages); assertEquals(0L, finished.remainingSeconds)
        assertTrue(bookPlanWorkload(book(2, read = 350), 40.0).complete)
    }

    @Test fun missingLengthsAndPaceNeverLookLikeZeroReadingTime() {
        val unknown = bookPlanWorkload(book(1, pages = 0), 50.0)
        val noPace = bookPlanWorkload(book(2), null)
        val total = bookPlanWorkload(listOf(1, 2), mapOf(1 to unknown, 2 to noPace))
        assertEquals(200L, total.remainingPages); assertEquals(1, total.unknownLengths)
        assertNull(total.remainingSeconds); assertFalse(total.complete)
        assertNull(bookPlanWorkload(book(3), Double.NaN).remainingSeconds)
    }

    @Test fun duplicateAssignmentsAndDeletedBooksAreNotCountedTwice() {
        val workloads = mapOf(1 to bookPlanWorkload(book(1), 50.0))
        val total = bookPlanWorkload(listOf(1, 1, 999), workloads)
        assertEquals(1, total.bookCount); assertEquals(200L, total.remainingPages); assertEquals(14400L, total.remainingSeconds)
        assertEquals(BookPlanWorkload(), bookPlanWorkload(emptyList(), workloads))
    }

    @Test fun movingBooksReassignsTierWorkWithoutChangingPlanTotal() {
        val workloads = mapOf(1 to bookPlanWorkload(book(1), 50.0), 2 to bookPlanWorkload(book(2), 100.0))
        val plan = BookReadingPlan(name = "Plan").move(1, 0).move(2, 1)
        val moved = plan.move(2, 0)
        assertEquals(bookPlanWorkload(plan.bookIds, workloads), bookPlanWorkload(moved.bookIds, workloads))
        assertEquals(400L, bookPlanWorkload(moved.tiers[0].bookIds, workloads).remainingPages)
        assertEquals(0L, bookPlanWorkload(moved.tiers[1].bookIds, workloads).remainingPages)
    }
}
