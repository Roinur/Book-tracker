package com.roinur.booktracker

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.roinur.booktracker.data.backup.BookBackupService
import com.roinur.booktracker.data.backup.BookPortableCovers
import com.roinur.booktracker.data.database.BookTrackerDatabase
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class BookPortableCoversTest {
    @Test fun coverSurvivesLossOfOriginalFileAndBackupRoundTrip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val original = File(context.cacheDir, "$id.png")
        val first = BookTrackerDatabase(context, "$id-source.db")
        val second = BookTrackerDatabase(context, "$id-target.db")
        val url = Uri.fromFile(original).toString()
        val restored = BookPortableCovers.file(context, url)
        try {
            val bitmap = Bitmap.createBitmap(3, 4, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.RED)
            original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val originalBytes = original.readBytes()
            first.upsertBook(BookSeed("123", "Portable cover", "Author", 100, url, "", ""))
            val backup = BookBackupService(first, context).exportBackupJson()
            assertEquals(1, backup.getJSONArray("cover_assets").length())
            assertTrue(original.delete())
            BookBackupService(second, context).importBackupJson(backup)
            assertArrayEquals(originalBytes, restored.readBytes())
            assertEquals(3, BitmapFactory.decodeFile(restored.path).width)
            second.readableDatabase.rawQuery("SELECT cover_url FROM books", null).use {
                assertTrue(it.moveToFirst()); assertEquals(url, it.getString(0))
            }
            val again = BookBackupService(second, context).exportBackupJson()
            assertEquals(backup.getString("content_sha256"), again.getString("content_sha256"))
            val bad = org.json.JSONObject(backup.toString())
            bad.getJSONArray("cover_assets").getJSONObject(0).put("sha256", "wrong")
            LegacyMigration.sealTrackerBackup(bad)
            try { LegacyMigration.previewTrackerBackup(bad.toString().toByteArray()); fail("Invalid asset accepted") }
            catch (_: IllegalArgumentException) { }
        } finally {
            first.close(); second.close()
            context.deleteDatabase("$id-source.db"); context.deleteDatabase("$id-target.db")
            original.delete(); restored.delete()
        }
    }
}
