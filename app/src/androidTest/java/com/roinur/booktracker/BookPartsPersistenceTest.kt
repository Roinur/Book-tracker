package com.roinur.booktracker

import androidx.test.platform.app.InstrumentationRegistry
import com.roinur.booktracker.data.database.BookTrackerDatabase
import com.roinur.booktracker.data.backup.BookBackupService
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BookPartsPersistenceTest {
    @Test fun oldRangesConvertToInclusiveWithoutChangingTheirLengthOrCover() {
        val old = """[{"title":"Scarlet","start_page":3,"end_page":75,"cover_url":"file:///cover.png"},{"title":"Sign","start_page":75,"end_page":145}]"""
        val parts = com.roinur.booktracker.data.database.BookPartsStorage.decode(old)
        assertEquals(listOf(BookPart("Scarlet", 3, 74, "file:///cover.png"), BookPart("Sign", 75, 144)), parts)
        assertEquals(listOf(72, 70), parts.map { it.pageCount })
        val stored = com.roinur.booktracker.data.database.BookPartsStorage.encode(parts)
        assertEquals(parts, com.roinur.booktracker.data.database.BookPartsStorage.decode(stored))
    }

    @Test fun existingDatabaseMigratesAndPartsSurviveBookEditsReopenAndVerifiedBackup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceName = "parts-${UUID.randomUUID()}.db"
        val targetName = "parts-${UUID.randomUUID()}.db"
        var source = BookTrackerDatabase(context, sourceName)
        val target = BookTrackerDatabase(context, targetName)
        try {
            // Simulate the schema before parts were introduced; onOpen must add the column.
            val oldBooksSql = source.readableDatabase.rawQuery("SELECT sql FROM sqlite_master WHERE name = 'books'", null).use {
                it.moveToFirst(); it.getString(0).replace(Regex(",\\s*parts_json TEXT NOT NULL DEFAULT '\\[\\]'"), "")
            }
            assertFalse(oldBooksSql.contains("parts_json"))
            source.close()
            context.openOrCreateDatabase(sourceName, 0, null).use { old ->
                old.execSQL("PRAGMA foreign_keys = OFF")
                old.execSQL("DROP TABLE books")
                old.execSQL(oldBooksSql)
                old.execSQL("CREATE UNIQUE INDEX index_books_isbn ON books(isbn) WHERE isbn <> ''")
                old.execSQL("CREATE INDEX index_books_pinned ON books(pinned, added_at)")
            }
            source = BookTrackerDatabase(context, sourceName)
            val parts = listOf(BookPart("Scarlet", 20, 100), BookPart("Sign", 102, 250))
            val id = source.upsertBook(BookSeed("", "Holmes", "Doyle", 400, "", "", "", parts))
            source.addReadingSession(id, "2026-10-05T10:00:00Z", "2026-10-05T11:00:00Z", 3600, 300, 300)
            source.upsertBook(BookSeed("", "Holmes edited", "Doyle", 400, "", "", ""), id)
            source.updateRating(id, 2)
            source.updatePartRating(id, parts[0], 5)
            val ratedParts = listOf(parts[0].copy(rating = 5), parts[1])
            source.close(); source = BookTrackerDatabase(context, sourceName)
            assertEquals(ratedParts, source.listBooks("", BookSortField.ADDED, false).single().parts)
            assertEquals(2, source.listBooks("", BookSortField.ADDED, false).single().rating)
            source.upsertBook(BookSeed("", "Holmes edited again", "Doyle", 400, "", "", ""), id)
            val backup = BookBackupService(source).exportBackupJson(false)
            val bytes = backup.toString().toByteArray()
            LegacyMigration.previewTrackerBackup(bytes)
            BookBackupService(target).importBackupJson(backup, bytes)
            val restored = target.listBooks("", BookSortField.ADDED, false).single()
            assertEquals(ratedParts, restored.parts); assertEquals(2, restored.rating); assertEquals(300, restored.currentPage)
            assertEquals(3600L, restored.readingSeconds)
            val tampered = JSONObject(backup.toString())
            tampered.getJSONArray("books").getJSONObject(0).put("parts_json", "[]")
            try { LegacyMigration.previewTrackerBackup(tampered.toString().toByteArray()); fail("Tampered parts accepted") }
            catch (_: IllegalArgumentException) { }
            source.upsertBook(BookSeed("", "Holmes edited", "Doyle", 400, "", "", "", emptyList()), id)
            assertTrue(source.listBooks("", BookSortField.ADDED, false).single().parts.isEmpty())
        } finally { source.close(); target.close(); context.deleteDatabase(sourceName); context.deleteDatabase(targetName) }
    }
}
