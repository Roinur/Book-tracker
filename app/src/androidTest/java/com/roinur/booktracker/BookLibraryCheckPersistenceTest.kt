package com.roinur.booktracker

import androidx.test.platform.app.InstrumentationRegistry
import com.roinur.booktracker.data.database.BookLibraryCheckRepository
import com.roinur.booktracker.data.database.BookTrackerDatabase
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class BookLibraryCheckPersistenceTest {
    @Test fun targetedFixesPreserveActualReadingAndRollBackTogether() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "library-check-test-${UUID.randomUUID()}.db"
        val db = BookTrackerDatabase(context, name)
        try {
            val id = db.upsertBook(BookSeed("", "Original", "Author", 200, "cover", "source", "Classic", listOf(BookPart("Text", 1, 180))))
            db.writableDatabase.execSQL("UPDATE books SET current_page = 180, reading_seconds = 7200, status = 'FINISHED', finished_at = '2026-01-01', last_read_at = '2026-01-01' WHERE id = ?", arrayOf(id))
            val before = requireNotNull(db.getBook(id))
            val repo = BookLibraryCheckRepository(db)
            repo.apply(BookLibraryFix(id, title = "Corrected", collections = "Classic, Penguin", collectionTypes = mapOf("Penguin" to BookCollectionType.PUBLISHER)))
            val after = requireNotNull(db.getBook(id))
            assertEquals(before.copy(title = "Corrected", collections = "Classic, Penguin"), after)
            assertEquals(BookCollectionType.PUBLISHER, db.listCollectionTypes()["penguin"])
            try {
                repo.apply(BookLibraryFix(id, title = "Must roll back", collectionTypes = mapOf("invalid,name" to BookCollectionType.THEME)))
                fail("Invalid category accepted")
            } catch (_: IllegalArgumentException) { }
            assertEquals(after, db.getBook(id))
            try { repo.apply(BookLibraryFix(id, pageCount = 150)); fail("Current page/parts truncated") }
            catch (_: IllegalArgumentException) { }
            assertEquals(200, db.getBook(id)!!.pageCount)
            val idle = db.upsertBook(BookSeed("", "Idle", "", 100, "", "", ""))
            db.updateProgress(idle, null, BookStatus.READING)
            val idleBefore = db.getBook(idle)!!
            repo.apply(BookLibraryFix(idle, status = BookStatus.WISHLIST))
            assertEquals(idleBefore.copy(status = BookStatus.WISHLIST), db.getBook(idle))
            repo.apply(BookLibraryFix(id, completionDate = LocalDate.of(2025, 12, 31)))
            assertEquals(before.currentPage, db.getBook(id)!!.currentPage)
            db.deleteBook(idle)
            try { repo.apply(BookLibraryFix(idle, title = "Do not recreate")); fail("Missing book was recreated") }
            catch (_: IllegalArgumentException) { }
            assertEquals(1, db.listBooks("", BookSortField.ADDED, false).size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
