package com.roinur.booktracker

import org.junit.Assert.*
import org.junit.Test

class BookCompletionRatingTest {
    private val book = BookRow(1,"","Test book","Test author",200,100,BookStatus.READING,0,
        "","","","",false,"","","","",0)

    @Test fun newlyFinishedUnratedBookRequestsRating() {
        assertEquals(listOf(1),bookCompletionRatingQueue(listOf(book),listOf(book.copy(status=BookStatus.FINISHED)),emptyList()))
    }
    @Test fun alreadyRatedBookDoesNotRequestRating() {
        assertTrue(bookCompletionRatingQueue(listOf(book),listOf(book.copy(status=BookStatus.FINISHED,rating=4)),emptyList()).isEmpty())
    }
    @Test fun openingExistingFinishedBooksDoesNotPrompt() {
        val finished=book.copy(status=BookStatus.FINISHED)
        assertTrue(bookCompletionRatingQueue(emptyList(),listOf(finished),emptyList()).isEmpty())
        assertTrue(bookCompletionRatingQueue(listOf(finished),listOf(finished),emptyList()).isEmpty())
    }
    @Test fun dismissedPromptDoesNotReappearOnReload() {
        val finished=book.copy(status=BookStatus.FINISHED)
        assertTrue(bookCompletionRatingQueue(listOf(finished),listOf(finished),emptyList()).isEmpty())
        assertEquals(listOf(1),bookCompletionRatingQueue(listOf(book),listOf(finished),emptyList()))
    }
    @Test fun queuedPromptsAreDeduplicatedAndRemovedWhenNoLongerEligible() {
        val finished=book.copy(status=BookStatus.FINISHED)
        assertEquals(listOf(1),bookCompletionRatingQueue(listOf(book),listOf(finished),listOf(1,1)))
        assertTrue(bookCompletionRatingQueue(listOf(finished),listOf(finished.copy(rating=5)),listOf(1)).isEmpty())
        assertTrue(bookCompletionRatingQueue(listOf(finished),listOf(book),listOf(1)).isEmpty())
        assertTrue(bookCompletionRatingQueue(listOf(finished),emptyList(),listOf(1)).isEmpty())
    }
    @Test fun expectedBookListWeightDoesNotCountAsAFinalRating() {
        val expected=book.copy(readingPriority=5)
        assertEquals(listOf(1),bookCompletionRatingQueue(listOf(expected),listOf(expected.copy(status=BookStatus.FINISHED)),emptyList()))
    }
}
