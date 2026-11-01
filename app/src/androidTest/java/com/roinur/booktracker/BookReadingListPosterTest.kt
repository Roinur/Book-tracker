package com.roinur.booktracker

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BookReadingListPosterTest {
    @Test fun rendersPngWithLocalCoversAndSharesOnlyExportFiles() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val coverFile = File(context.cacheDir, "reading-poster-test-cover.png")
        val cover = Bitmap.createBitmap(100, 150, Bitmap.Config.ARGB_8888)
        cover.eraseColor(Color.rgb(12, 45, 120))
        coverFile.outputStream().use { cover.compress(Bitmap.CompressFormat.PNG, 100, it) }; cover.recycle()
        val books = listOf(
            BookRow(900001, "", "A finished book", "An author", 200, 180, BookStatus.FINISHED,
                0, "", Uri.fromFile(coverFile).toString(), "", "", false, "", "", "2026-01-01", "", 0),
            BookRow(900002, "", "An unread book", "Another author", 200, 0, BookStatus.WISHLIST,
                0, "", "", "", "", false, "", "", "", "", 0))
        val options = BookReadingListOptions(allBooks = true, includeAuthors = true)
        val files = exportBookReadingPoster(context, buildBookReadingList(books, emptyList(), ZoneId.of("Europe/Stockholm"), options), options)
        try {
            assertEquals(1, files.size)
            val bitmap = BitmapFactory.decodeFile(files.single().path)!!
            assertEquals(BOOK_POSTER_WIDTH, bitmap.width)
            assertTrue(bitmap.height > 1000)
            assertEquals(Color.rgb(248, 239, 220), bitmap.getPixel(0, 0))
            // The first cover's centre must contain the supplied blue cover, not a placeholder.
            assertEquals(Color.rgb(12, 45, 120), bitmap.getPixel(200, 470))
            // Status belongs to an equal-sized badge, never to the cover's border.
            assertEquals(Color.rgb(37, 111, 70), bitmap.getPixel(125, 358))
            assertEquals(Color.rgb(244, 194, 184), bitmap.getPixel(125, 920))
            assertEquals(Color.rgb(234, 222, 197), bitmap.getPixel(96, 470))
            bitmap.recycle()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.readinglistexports", files.single())
            assertEquals("image/png", context.contentResolver.getType(uri))
            context.contentResolver.openInputStream(uri)!!.use { assertEquals(137, it.read()) }
            assertThrows(IllegalArgumentException::class.java) {
                FileProvider.getUriForFile(context, "${context.packageName}.readinglistexports", coverFile)
            }
        } finally {
            files.forEach { it.delete() }; files.firstOrNull()?.parentFile?.delete(); coverFile.delete()
        }
    }

    @Test fun tallLibraryIsOnePngAndCanBePreviewedInRegions() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val books = (1..240).map { BookRow(910000 + it, "", "Book $it", "Author", 100, 100, BookStatus.FINISHED,
            0, "", "", "", "", false, "", "", "2026-01-01", "", 0) }
        val options = BookReadingListOptions(includeCompletionDates = true)
        val plan = planBookReadingPoster(buildBookReadingList(books, emptyList(), options = options), options).single()
        val files = exportBookReadingPoster(context, buildBookReadingList(books, emptyList(), options = options), options)
        try {
            assertEquals(1, files.size)
            assertTrue(plan.height > 16384)
            @Suppress("DEPRECATION")
            val decoder = BitmapRegionDecoder.newInstance(files.single().path, false)!!
            try {
                assertEquals(plan.height, decoder.height)
                val bottom = decoder.decodeRegion(Rect(0, plan.height - 100, BOOK_POSTER_WIDTH, plan.height), BitmapFactory.Options().apply { inSampleSize = 2 })!!
                assertEquals(900, bottom.width); assertEquals(50, bottom.height)
                bottom.recycle()
            } finally { decoder.recycle() }
        } finally { files.forEach { it.delete() }; files.firstOrNull()?.parentFile?.delete() }
    }
}
