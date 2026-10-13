package com.roinur.booktracker

import android.graphics.Bitmap
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import com.roinur.booktracker.data.backup.BookBackupService
import com.roinur.booktracker.data.backup.drive.*
import com.roinur.booktracker.data.database.BookTrackerDatabase
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.UUID

class BookDriveBackupTest {
    private fun snapshot(): JSONObject = LegacyMigration.sealTrackerBackup(JSONObject().put("format", LegacyMigration.BACKUP_FORMAT)
        .put("exported_at", "2026-10-12T10:00:00Z").apply {
            listOf("books", "reading_sessions", "reading_notes", "reading_goals", "collection_types", "book_goals", "bookly_sources", "bookly_links")
                .forEach { put(it, JSONArray()) }
        })

    @Test fun envelopeRoundTripRejectsTamperingAndOtherApps() {
        val original = snapshot()
        val text = BookCloudBackup.encode(original)
        assertEquals(original.toString(), BookCloudBackup.decode(text).snapshot.toString())
        assertThrows(IllegalArgumentException::class.java) {
            BookCloudBackup.decode(JSONObject(text).put("payload", "changed").toString())
        }
        assertThrows(IllegalArgumentException::class.java) {
            BookCloudBackup.decode(JSONObject(text).put("format", "SAUCE_TRACKER_CLOUD_BACKUP_V1").toString())
        }
        assertThrows(Exception::class.java) { BookCloudBackup.decode(text.take(text.length / 2)) }
        val invalidNative = JSONObject(original.toString()).put("books", JSONArray().put(JSONObject().put("title", "changed")))
        assertThrows(IllegalArgumentException::class.java) { BookCloudBackup.encode(invalidNative) }
    }

    @Test fun timestampAndNativeChecksumsDoNotTriggerDuplicateUploads() {
        val first = snapshot()
        val second = LegacyMigration.sealTrackerBackup(JSONObject(first.toString()).put("exported_at", "2026-10-13T10:00:00Z"))
        assertEquals(BookCloudBackup.fingerprint(first), BookCloudBackup.fingerprint(second))
        second.put("reading_goals", JSONArray().put(JSONObject().put("target", 70)))
        assertNotEquals(BookCloudBackup.fingerprint(first), BookCloudBackup.fingerprint(second))
    }

    @Test fun portableCoverBytesSurviveCloudEncodingAndAreChecked() {
        val bitmap = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888)
        val bytes = try { ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() }
            finally { bitmap.recycle() }
        val original = snapshot().apply {
            put("books", JSONArray().put(JSONObject().put("cover_url", "file:///cloud-test-cover.png")))
            put("cover_assets", JSONArray().put(JSONObject().put("url", "file:///cloud-test-cover.png")
                .put("sha256", LegacyMigration.hash(bytes)).put("data_base64", Base64.encodeToString(bytes, Base64.NO_WRAP))))
        }
        LegacyMigration.sealTrackerBackup(original)
        val restored = BookCloudBackup.decode(BookCloudBackup.encode(original)).snapshot
        assertArrayEquals(bytes, Base64.decode(restored.getJSONArray("cover_assets").getJSONObject(0).getString("data_base64"), Base64.DEFAULT))
        restored.getJSONArray("cover_assets").getJSONObject(0).put("sha256", "incorrect")
        LegacyMigration.sealTrackerBackup(restored)
        assertThrows(IllegalArgumentException::class.java) { BookCloudBackup.encode(restored) }
    }

    @Test fun databaseRoundTripKeepsPartsNotesAndSessionsAndRefusesToOverwriteAnExistingLibrary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceName = "drive-source-${UUID.randomUUID()}.db"
        val targetName = "drive-target-${UUID.randomUUID()}.db"
        val emptyName = "drive-empty-${UUID.randomUUID()}.db"
        val source = BookTrackerDatabase(context, sourceName)
        val target = BookTrackerDatabase(context, targetName)
        val empty = BookTrackerDatabase(context, emptyName)
        try {
            val parts = listOf(BookPart("First work", 1, 20, rating = 5), BookPart("Second work", 21, 40))
            val id = source.upsertBook(BookSeed("123", "Compendium", "Author", 40, "", "", "Philosophy", parts))
            source.addReadingSession(id, "2026-10-12T10:00:00Z", "2026-10-12T11:00:00Z", 3600, 25, 25)
            source.addReadingNote(id, "2026-10-12T11:00:00Z", 25, 3600, "A saved note")
            target.upsertBook(BookSeed("456", "Existing book", "Other", 100, "", "", ""))
            val decoded = BookCloudBackup.decode(BookCloudBackup.encode(BookBackupService(source).exportBackupJson(false)))
            assertThrows(IllegalArgumentException::class.java) {
                BookBackupService(target).importBackupJson(decoded.snapshot, decoded.payload.toByteArray(Charsets.UTF_8))
            }
            assertEquals("Existing book", target.listBooks("", BookSortField.ADDED, false).single().title)
            BookBackupService(empty).importBackupJson(decoded.snapshot, decoded.payload.toByteArray(Charsets.UTF_8))
            val books = empty.listBooks("", BookSortField.ADDED, false)
            assertEquals(1, books.size)
            val restored = books.single { it.title == "Compendium" }
            assertEquals(parts, restored.parts); assertEquals(25, restored.currentPage); assertEquals(3600L, restored.readingSeconds)
            assertEquals("A saved note", empty.listNotes(restored.id).single().note)
        } finally { source.close(); target.close(); empty.close(); context.deleteDatabase(sourceName); context.deleteDatabase(targetName); context.deleteDatabase(emptyName) }
    }

    private class FakeTransport : DriveBackupTransport {
        val files = mutableMapOf<String, String>()
        val verified = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        var corruptDownload = false
        var failPromotion = false
        var concurrentWriter = false
        var uploads = 0
        override fun listVerified() = verified.map { DriveBackupFile(it, it, it) }
        override fun uploadPending(text: String): DriveBackupFile {
            val id = "new-${++uploads}"
            files[id] = text
            if (concurrentWriter) verified.add(0, "another-device")
            return DriveBackupFile(id, id, id)
        }
        override fun download(id: String) = if (corruptDownload) "truncated" else files.getValue(id)
        override fun markVerified(id: String) { check(!failPromotion); verified.add(0, id) }
        override fun delete(id: String) { deleted += id; verified.remove(id); files.remove(id) }
    }

    @Test fun failedVerificationOrPromotionKeepsHealthyCopies() {
        for (promotion in listOf(false, true)) {
            val transport = FakeTransport().apply { verified += listOf("good", "older"); corruptDownload = !promotion; failPromotion = promotion }
            assertThrows(Exception::class.java) { DriveBackupRepository(transport).publish(BookCloudBackup.encode(snapshot()), "good") }
            assertEquals(listOf("good", "older"), transport.verified)
            assertEquals(listOf("new-1"), transport.deleted)
        }
    }

    @Test fun verifiedUploadRetainsFiveGenerations() {
        val transport = FakeTransport().apply { verified += (1..6).map { "old-$it" } }
        val published = DriveBackupRepository(transport).publish(BookCloudBackup.encode(snapshot()), "old-1")
        assertEquals(published.id, transport.verified.first()); assertEquals(5, transport.verified.size)
        assertEquals(listOf("old-5", "old-6"), transport.deleted)
    }

    @Test fun newInstallAndConcurrentWriterCannotSilentlyOverwriteLatest() {
        val transport = FakeTransport().apply { verified += "existing" }
        assertThrows(IllegalStateException::class.java) { DriveBackupRepository(transport).publish(BookCloudBackup.encode(snapshot()), "") }
        assertEquals(0, transport.uploads)
        transport.concurrentWriter = true
        assertThrows(IllegalStateException::class.java) { DriveBackupRepository(transport).publish(BookCloudBackup.encode(snapshot()), "existing") }
        assertEquals(listOf("another-device", "existing"), transport.verified)
        assertEquals(listOf("new-1"), transport.deleted)
    }
}
