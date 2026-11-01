package com.roinur.booktracker

import androidx.test.platform.app.InstrumentationRegistry
import com.roinur.booktracker.data.backup.BookBackupService
import com.roinur.booktracker.data.database.BookReadingPlanRepository
import com.roinur.booktracker.data.database.BookTrackerDatabase
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BookReadingPlanPersistenceTest {
    @Test fun plansSurviveReopenAndRestoreWithRemappedBooksAndCascadingDeletion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceName = "plan-test-${UUID.randomUUID()}.db"
        val targetName = "plan-test-${UUID.randomUUID()}.db"
        var source = BookTrackerDatabase(context, sourceName)
        val target = BookTrackerDatabase(context, targetName)
        try {
            val removed = source.upsertBook(BookSeed("", "Removed", "", 100, "", "", ""))
            val a = source.upsertBook(BookSeed("", "First", "", 100, "", "", ""))
            val b = source.upsertBook(BookSeed("", "Second", "", 100, "", "", ""))
            source.deleteBook(removed)
            var repo = BookReadingPlanRepository(source)
            val plan = BookReadingPlan(name = "Romaner 2026").move(a, 1).move(b, 0)
            repo.save(plan); repo.activate(plan.id)
            repo.save(plan.copy(name = "Renamed", tiers = plan.tiers.mapIndexed { index, tier -> if (index == 0) tier.copy(name = "Next") else tier }))
            val other = BookReadingPlan(name = "Other"); repo.save(other)
            source.close(); source = BookTrackerDatabase(context, sourceName); repo = BookReadingPlanRepository(source)
            val saved = repo.list().first()
            assertTrue(saved.active); assertEquals("Next", saved.tiers.first().name); assertEquals(listOf(b, a), saved.bookIds)
            val backup = BookBackupService(source).exportBackupJson(false)
            LegacyMigration.previewTrackerBackup(backup.toString().toByteArray())
            BookBackupService(target).importBackupJson(backup)
            val restored = BookReadingPlanRepository(target).list().first()
            val books = target.listBooks("", BookSortField.ADDED, false)
            assertEquals(listOf("Second", "First"), restored.orderedBooks(books).map { it.title })
            assertNotEquals(saved.bookIds, restored.bookIds)
            assertTrue(restored.active)
            assertTrue(books.all { it.currentPage == 0 && it.readingSeconds == 0L })
            val tampered = JSONObject(backup.toString())
            tampered.getJSONArray("reading_plans").getJSONObject(0).put("name", "Tampered")
            try { LegacyMigration.previewTrackerBackup(tampered.toString().toByteArray()); fail("Checksum accepted changed plan") }
            catch (_: IllegalArgumentException) { }
            target.deleteBook(restored.bookIds.first())
            assertEquals(1, BookReadingPlanRepository(target).list().first().bookIds.size)
            BookReadingPlanRepository(target).delete(restored.id)
            assertEquals(listOf(other.id), BookReadingPlanRepository(target).list().map { it.id })
            assertEquals(1, target.listBooks("", BookSortField.ADDED, false).size)
        } finally { source.close(); target.close(); context.deleteDatabase(sourceName); context.deleteDatabase(targetName) }
    }
}
