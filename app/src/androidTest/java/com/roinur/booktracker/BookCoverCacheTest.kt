package com.roinur.booktracker

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.roinur.booktracker.data.media.BookCoverCache
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class BookCoverCacheTest {
    @Test fun persistsAcrossInstancesAndRemovesReplacedCover() {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "cover-test-${UUID.randomUUID()}")
        val old = "https://example.test/old"
        val new = "https://example.test/new"
        var downloads = 0
        try {
            val first = BookCoverCache(directory) { downloads++; Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888) }
            first.retain(setOf(old))
            assertNotNull(first.load(old))
            assertEquals(1, downloads)
            val next = BookCoverCache(directory) { downloads++; Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888) }
            next.retain(setOf(old))
            assertNotNull(next.load(old))
            assertEquals(1, downloads)
            next.prune() // Another book can still reference the same URL.
            assertEquals(1, directory.listFiles()!!.size)
            next.retain(setOf(new))
            next.prune()
            assertEquals(0, directory.listFiles()!!.size)
            assertNotNull(next.load(new))
            assertEquals(2, downloads)
        } finally { directory.listFiles()?.forEach { it.delete() }; directory.delete() }
    }

    @Test fun completedOldDownloadCannotRecreateRemovedCover() {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "cover-test-${UUID.randomUUID()}")
        lateinit var cache: BookCoverCache
        cache = BookCoverCache(directory) {
            cache.retain(emptySet())
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        }
        cache.retain(setOf("https://example.test/old"))
        cache.load("https://example.test/old")
        assertFalse(directory.exists())
    }
}
