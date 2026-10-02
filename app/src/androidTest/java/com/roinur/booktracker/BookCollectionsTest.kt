package com.roinur.booktracker

import androidx.test.platform.app.InstrumentationRegistry
import com.roinur.booktracker.data.database.BookTrackerDatabase
import com.roinur.booktracker.data.backup.BookBackupService
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BookCollectionsTest {
    @Test fun renameMergeAndTypesPreserveBooksAndRoundTrip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "collections-test-${UUID.randomUUID()}.db"
        val restoredName = "collections-test-${UUID.randomUUID()}.db"
        val db = BookTrackerDatabase(context, name)
        val restored = BookTrackerDatabase(context, restoredName)
        try {
            val id = db.upsertBook(BookSeed("", "Republic", "Plato", 300, "", "", "Classic, Philosophy, Religion"))
            val other = db.upsertBook(BookSeed("", "Other", "", 100, "", "", "Classics"))
            db.changeCollections(setOf("Philosophy", "Religion"), type = BookCollectionType.THEME)
            db.changeCollections(setOf("Classic"), target = "Literature")
            db.changeCollections(setOf("Religion"), target = "Philosophy")
            val books = db.listBooks("", BookSortField.ADDED, false)
            assertEquals(2, books.size)
            assertEquals(listOf("Literature", "Philosophy"), splitBookCollections(books.first { it.id == id }.collections))
            assertEquals("Classics", books.first { it.id == other }.collections)
            assertEquals(BookCollectionType.THEME, db.listCollectionTypes()["philosophy"])
            assertFalse(db.listCollectionTypes().containsKey("religion"))
            db.addCollectionToBooks(setOf(id, other), "Novel")
            val assigned = db.listBooks("", BookSortField.ADDED, false)
            assertEquals(listOf("Literature", "Philosophy", "Novel"), splitBookCollections(assigned.first { it.id == id }.collections))
            assertEquals(listOf("Classics", "Novel"), splitBookCollections(assigned.first { it.id == other }.collections))
            assertEquals(books.map { it.copy(collections = "") }, assigned.map { it.copy(collections = "") })
            try { db.addCollectionToBooks(setOf(id, -100), "Must not be added"); fail("Invalid selection was accepted") }
            catch (_: IllegalStateException) { }
            assertEquals(assigned, db.listBooks("", BookSortField.ADDED, false))
            val change = db.updateBookCollections(setOf(id, other), setOf("History"), setOf("NOVEL"))
            val edited = db.listBooks("", BookSortField.ADDED, false)
            assertEquals(listOf("Literature", "Philosophy", "History"), splitBookCollections(edited.first { it.id == id }.collections))
            assertEquals(listOf("Classics", "History"), splitBookCollections(edited.first { it.id == other }.collections))
            assertEquals(books.map { it.copy(collections = "") }, edited.map { it.copy(collections = "") })
            db.undoBookCollections(change)
            assertEquals(assigned, db.listBooks("", BookSortField.ADDED, false))
            assertTrue(db.updateBookCollections(setOf(id, other), setOf("Novel"), emptySet()).before.isEmpty())
            val conflictChange = db.updateBookCollections(setOf(id, other), setOf("History"), setOf("NOVEL"))
            db.addCollectionToBooks(setOf(conflictChange.after.keys.last()), "Later edit")
            val withLaterEdit = db.listBooks("", BookSortField.ADDED, false)
            try { db.undoBookCollections(conflictChange); fail("Undo overwrote a later change") }
            catch (_: IllegalStateException) { }
            assertEquals(withLaterEdit, db.listBooks("", BookSortField.ADDED, false))
            BookBackupService(restored).importBackupJson(BookBackupService(db).exportBackupJson())
            assertEquals(withLaterEdit, restored.listBooks("", BookSortField.ADDED, false))
            assertEquals(db.listCollectionTypes(), restored.listCollectionTypes())
        } finally {
            db.close(); restored.close()
            context.deleteDatabase(name); context.deleteDatabase(restoredName)
        }
    }
}
