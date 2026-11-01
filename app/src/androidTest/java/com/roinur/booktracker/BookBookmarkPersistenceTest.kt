package com.roinur.booktracker

import androidx.test.platform.app.InstrumentationRegistry
import com.roinur.booktracker.data.database.BookTrackerDatabase
import com.roinur.booktracker.data.backup.BookBackupService
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BookBookmarkPersistenceTest {
    @Test fun bookmarkAndActualPagesSurviveEditsReopenVerifiedBackupAndDeletion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "bookmark-${UUID.randomUUID()}.db"
        val restoredName = "bookmark-${UUID.randomUUID()}.db"
        var db = BookTrackerDatabase(context, name)
        val restoredDb = BookTrackerDatabase(context, restoredName)
        try {
            val id = db.upsertBook(BookSeed("", "Compendium", "Author", 300, "", "", "", listOf(BookPart("One", 1, 50), BookPart("Two", 51, 100))))
            db.addReadingSession(id, "2026-10-01T10:00:00Z", "2026-10-01T11:00:00Z", 3600, 35, 35, bookmarkPage = 65, updateBookmark = true)
            assertEquals(35, db.getBook(id)!!.currentPage)
            assertEquals(65, db.getBook(id)!!.readingPage)
            val first = db.listSessions(id).single()
            assertEquals(65, first.bookmarkPage)
            db.updateReadingSession(first.id, id, first.startedAt, first.endedAt, 3500, 35, 35, true, true, 66, true)
            assertEquals(35, db.getBook(id)!!.currentPage)
            assertEquals(66, db.getBook(id)!!.bookmarkPage)
            db.setSessionStatisticsExcluded(first.id, false)
            db.addReadingSession(id, "2026-10-02T10:00:00Z", "2026-10-02T11:00:00Z", 600, 10, 45, bookmarkPage = 76, updateBookmark = true)
            assertEquals(45, db.getBook(id)!!.currentPage)
            val last = db.listSessions(id).maxBy { it.id }
            db.close(); db = BookTrackerDatabase(context, name)
            assertEquals(76, db.getBook(id)!!.readingPage)
            val backup = BookBackupService(db).exportBackupJson(false)
            val bytes = backup.toString().toByteArray()
            LegacyMigration.previewTrackerBackup(bytes)
            BookBackupService(restoredDb).importBackupJson(backup, bytes)
            val restored = restoredDb.listBooks("", BookSortField.ADDED, false).single()
            assertEquals(45, restored.currentPage); assertEquals(76, restored.bookmarkPage)
            assertEquals(4100L, restored.readingSeconds)
            assertEquals(listOf(66, 76), restoredDb.listSessions(restored.id).sortedBy { it.id }.map { it.bookmarkPage })
            val tampered = JSONObject(backup.toString())
            tampered.getJSONArray("books").getJSONObject(0).put("bookmark_page", 200)
            try { LegacyMigration.previewTrackerBackup(tampered.toString().toByteArray()); fail("Tampered bookmark accepted") }
            catch (_: IllegalArgumentException) { }
            db.deleteReadingSession(last.id, id)
            assertEquals(35, db.getBook(id)!!.currentPage); assertEquals(66, db.getBook(id)!!.bookmarkPage)
            db.deleteReadingSession(first.id, id)
            assertEquals(0, db.getBook(id)!!.currentPage); assertNull(db.getBook(id)!!.bookmarkPage)
        } finally { db.close(); restoredDb.close(); context.deleteDatabase(name); context.deleteDatabase(restoredName) }
    }

    @Test fun preBookmarkDatabaseKeepsItsProgressWhenMigrated() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "bookmark-migration-${UUID.randomUUID()}.db"
        var db = BookTrackerDatabase(context, name)
        try {
            val id = db.upsertBook(BookSeed("", "Existing book", "Author", 200, "", "", ""))
            db.addReadingSession(id, "2026-01-01T10:00:00Z", "2026-01-01T11:00:00Z", 3600, 80, 80)
            db.writableDatabase.execSQL("ALTER TABLE books DROP COLUMN bookmark_page")
            db.writableDatabase.execSQL("ALTER TABLE reading_sessions DROP COLUMN bookmark_page")
            db.close(); db = BookTrackerDatabase(context, name)
            assertEquals(80, db.getBook(id)!!.currentPage)
            assertEquals(80, db.getBook(id)!!.readingPage)
            assertNull(db.getBook(id)!!.bookmarkPage)
            assertNull(db.listSessions(id).single().bookmarkPage)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
