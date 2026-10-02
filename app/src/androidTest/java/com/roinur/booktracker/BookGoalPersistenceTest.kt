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
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceName = "book-goal-test-${UUID.randomUUID()}.db"
        val targetName = "book-goal-test-${UUID.randomUUID()}.db"
        var source = BookTrackerDatabase(context, sourceName)
        val target = BookTrackerDatabase(context, targetName)
        try {
            val first = source.upsertBook(BookSeed("", "Temporary", "", 10, "", "", ""))
            val id = source.upsertBook(BookSeed("", "Goal book", "", 300, "", "", ""))
            source.deleteBook(first)
            val goal = BookGoal(id, BookGoalMode.DEADLINE, targetDate = LocalDate.of(2026, 10, 12), createdDate = LocalDate.of(2026, 10, 2))
            BookGoalRepository(source).save(goal)
            source.close(); source = BookTrackerDatabase(context, sourceName)
            assertEquals(goal, BookGoalRepository(source).list()[id])
            val backup = BookBackupService(source).exportBackupJson(false)
            assertEquals(1, backup.getJSONArray("book_goals").length())
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
}
