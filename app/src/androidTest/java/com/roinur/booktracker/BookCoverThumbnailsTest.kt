package com.roinur.booktracker

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.roinur.booktracker.data.media.BookCoverThumbnails
import com.roinur.booktracker.data.media.decodeCoverThumbnail
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class BookCoverThumbnailsTest {
    @Test fun sizesCopiesReusesConcurrentLoadsAndPreservesOriginal() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "thumbnail-test-${UUID.randomUUID()}.png")
        val original = Bitmap.createBitmap(1200, 1800, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { original.compress(Bitmap.CompressFormat.PNG, 100, it) }
            original.recycle()
            val url = Uri.fromFile(file).toString()
            val copies = coroutineScope { (1..5).map { async { BookCoverThumbnails.load(context, url, 384) } }.awaitAll() }
            assertNotNull(copies.first())
            copies.forEach { assertSame(copies.first(), it) }
            assertEquals(384, copies.first()!!.height)
            assertEquals(256, copies.first()!!.width)
            val detail = BookCoverThumbnails.load(context, url, 1024)!!
            assertEquals(1024, detail.height)
            assertNotSame(copies.first(), detail)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            assertEquals(1200, bounds.outWidth)
            assertEquals(1800, bounds.outHeight)
        } finally { file.delete(); if (!original.isRecycled) original.recycle() }
    }

    @Test fun smallImagesKeepTheirSizeAndInvalidImagesAreRejected() {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "thumbnail-test-${UUID.randomUUID()}.png")
        val small = Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val decoded = decodeCoverThumbnail(384) { file.inputStream() }!!
            assertEquals(80, decoded.width)
            assertEquals(120, decoded.height)
            decoded.recycle()
            file.writeText("invalid image")
            assertNull(decodeCoverThumbnail(384) { file.inputStream() })
        } finally { file.delete(); small.recycle() }
    }
}
