package com.roinur.booktracker

import androidx.test.platform.app.InstrumentationRegistry
import com.roinur.booktracker.data.database.BookTrackerDatabase
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BookCompletionRatingPersistenceTest {
    @Test fun manualAndLastPageCompletionPromptOnceAndNeverOverwriteAnExistingRating() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="completion-rating-test-${UUID.randomUUID()}.db"
        val db=BookTrackerDatabase(context,name)
        try {
            val manual=db.upsertBook(BookSeed("","TESTDATA manual","Test author",200,"","",""))
            val before=db.listBooks("",BookSortField.ADDED,false)
            db.updateProgress(manual,null,BookStatus.FINISHED)
            val after=db.listBooks("",BookSortField.ADDED,false)
            assertEquals(listOf(manual),bookCompletionRatingQueue(before,after,emptyList()))
            assertEquals(0,db.getBook(manual)!!.currentPage)
            assertTrue(db.rateFinishedBookIfUnrated(manual,4))
            assertFalse(db.rateFinishedBookIfUnrated(manual,2))
            assertEquals(4,db.getBook(manual)!!.rating)
            val lastPage=db.upsertBook(BookSeed("","TESTDATA last page","Test author",200,"","",""))
            val beforeReading=db.listBooks("",BookSortField.ADDED,false)
            db.addReadingSession(lastPage,"2026-11-01T10:00:00Z","2026-11-01T11:00:00Z",3600,200,200)
            val afterReading=db.listBooks("",BookSortField.ADDED,false)
            assertEquals(BookStatus.FINISHED,db.getBook(lastPage)!!.status)
            assertEquals(listOf(lastPage),bookCompletionRatingQueue(beforeReading,afterReading,emptyList()))
            assertFalse(db.rateFinishedBookIfUnrated(manual,3))
            assertTrue(db.rateFinishedBookIfUnrated(lastPage,5))
            assertEquals(5,db.getBook(lastPage)!!.rating)
            assertTrue(bookCompletionRatingQueue(afterReading,db.listBooks("",BookSortField.ADDED,false),listOf(lastPage)).isEmpty())
        } finally {db.close();context.deleteDatabase(name)}
    }
}
