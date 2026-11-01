package com.roinur.booktracker

import androidx.test.platform.app.InstrumentationRegistry
import com.roinur.booktracker.data.database.BookTrackerDatabase
import com.roinur.booktracker.data.database.BookGoalRepository
import com.roinur.booktracker.data.backup.BookBackupService
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class BookGoalPersistenceTest {
    @Test fun goalsSurviveReopenAndBackupRemapsBooksWithoutChangingReadPages() {
        verifyRoundTrip(BookGoalMode.DAILY_MINUTES)
        verifyRoundTrip(BookGoalMode.DEADLINE)
    }

    private fun verifyRoundTrip(mode: BookGoalMode) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceName = "book-goal-test-${UUID.randomUUID()}.db"
        val targetName = "book-goal-test-${UUID.randomUUID()}.db"
        var source = BookTrackerDatabase(context, sourceName)
        val target = BookTrackerDatabase(context, targetName)
        try {
            val first = source.upsertBook(BookSeed("", "Temporary", "", 10, "", "", ""))
            val id = source.upsertBook(BookSeed("", "Goal book", "", 300, "", "", ""))
            source.deleteBook(first)
            val goal = if (mode == BookGoalMode.DAILY_MINUTES)
                BookGoal(id, mode, dailyMinutes = 45, createdDate = LocalDate.of(2026, 10, 2))
            else BookGoal(id, mode, targetDate = LocalDate.of(2026, 10, 12), createdDate = LocalDate.of(2026, 10, 2))
            BookGoalRepository(source).save(goal)
            source.close(); source = BookTrackerDatabase(context, sourceName)
            assertEquals(goal, BookGoalRepository(source).list()[id])
            val backup = BookBackupService(source).exportBackupJson(false)
            assertEquals(1, backup.getJSONArray("book_goals").length())
            if (mode == BookGoalMode.DEADLINE) {
                // Old backups had no daily_minutes column.
                backup.getJSONArray("book_goals").getJSONObject(0).remove("daily_minutes")
                LegacyMigration.sealTrackerBackup(backup)
            }
            val bytes = backup.toString().toByteArray()
            LegacyMigration.previewTrackerBackup(bytes)
            BookBackupService(target).importBackupJson(backup, bytes)
            val restored = target.listBooks("", BookSortField.ADDED, false).single()
            assertNotEquals(id, restored.id)
            assertEquals(goal.copy(bookId = restored.id), BookGoalRepository(target).list()[restored.id])
            assertEquals(0, restored.currentPage)
            val tampered = JSONObject(backup.toString())
            tampered.getJSONArray("book_goals").getJSONObject(0).put("target_date", "2026-10-30")
            try { LegacyMigration.previewTrackerBackup(tampered.toString().toByteArray()); fail("Tampered goal accepted") }
            catch (_: IllegalArgumentException) { }
            target.deleteBook(restored.id)
            assertTrue(BookGoalRepository(target).list().isEmpty())
        } finally { source.close(); target.close(); context.deleteDatabase(sourceName); context.deleteDatabase(targetName) }
    }

    @Test fun oldGoalSchemaKeepsPageGoalsAndAddsMinuteStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "book-goal-migration-${UUID.randomUUID()}.db"
        try {
            BookTrackerDatabase(context, name).use { db ->
                val id = db.upsertBook(BookSeed("", "Existing goal", "", 300, "", "", ""))
                db.writableDatabase.execSQL("DROP TABLE book_goals")
                db.writableDatabase.execSQL("CREATE TABLE book_goals (id INTEGER PRIMARY KEY AUTOINCREMENT, book_id INTEGER NOT NULL UNIQUE, mode TEXT NOT NULL, daily_pages INTEGER NOT NULL DEFAULT 0, target_date TEXT NOT NULL DEFAULT '', created_date TEXT NOT NULL)")
                db.writableDatabase.execSQL("INSERT INTO book_goals (book_id, mode, daily_pages, created_date) VALUES (?, 'DAILY_PAGES', 50, '2026-10-02')", arrayOf(id))
            }
            BookTrackerDatabase(context, name).use { db ->
                val repo = BookGoalRepository(db)
                val old = repo.list().values.single()
                assertEquals(BookGoalMode.DAILY_PAGES, old.mode); assertEquals(50, old.dailyPages)
                assertEquals(0, old.dailyMinutes)
                val minutes = old.copy(mode = BookGoalMode.DAILY_MINUTES, dailyMinutes = 30)
                repo.save(minutes)
                assertEquals(minutes, repo.list()[old.bookId])
            }
        } finally { context.deleteDatabase(name) }
    }
}
