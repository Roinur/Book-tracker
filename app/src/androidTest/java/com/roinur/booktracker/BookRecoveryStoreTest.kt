package com.roinur.booktracker

import androidx.test.platform.app.InstrumentationRegistry
import com.roinur.booktracker.data.backup.BookBackupService
import com.roinur.booktracker.data.backup.BookRecoveryStore
import com.roinur.booktracker.data.database.BookTrackerDatabase
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class BookRecoveryStoreTest {
    @Test fun keepsFiveVerifiedCopiesAndRejectsCorruptionWithoutRotating() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "recovery-test-${UUID.randomUUID()}.db"
        val directory = File(context.cacheDir, "recovery-test-${UUID.randomUUID()}")
        val db = BookTrackerDatabase(context, name)
        try {
            val store = BookRecoveryStore(directory)
            val bookId = db.upsertBook(BookSeed("", "Before edit", "Author", 100, "", "", ""))
            val bytes = BookBackupService(db).exportBackupJson().toString().toByteArray(Charsets.UTF_8)
            repeat(7) { store.save(bytes, "before-import") }
            db.upsertBook(BookSeed("", "After edit", "Author", 100, "", "", ""), bookId)
            assertEquals(5, store.list().size)
            val names = store.list().map { it.name }
            assertArrayEquals(bytes, store.read(names.first()))
            val saved = LegacyMigration.previewTrackerBackup(store.read(names.first()))
            assertEquals("Before edit", saved.root.getJSONArray("books").getJSONObject(0).getString("title"))
            try { store.save("broken".toByteArray(), "invalid"); fail("Invalid backup accepted") } catch (_: Exception) {}
            assertEquals(names, store.list().map { it.name })
            try { store.read("../outside.json"); fail("Path traversal accepted") } catch (_: IllegalArgumentException) {}
            assertTrue(directory.listFiles()!!.none { it.name.endsWith(".pending") })
        } finally {
            db.close(); context.deleteDatabase(name)
            directory.listFiles()?.forEach { it.delete() }; directory.delete()
        }
    }
}
