package com.roinur.booktracker.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.roinur.booktracker.data.backup.BookPortableCovers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Display-sized copies only. Original cover files remain available for detail views and backups. */
internal object BookCoverThumbnails {
    private val memory = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 16).coerceIn(12L * 1024 * 1024, 32L * 1024 * 1024).toInt()
    ) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val slots = Semaphore(4)
    private val locks = Array(32) { Mutex() }
    private fun key(url: String, size: Int) = "$size:$url"

    fun cached(url: String, size: Int): ImageBitmap? = memory.get(key(url, size))

    suspend fun load(context: Context, url: String, size: Int): ImageBitmap? {
        cached(url, size)?.let { return it }
        return withContext(Dispatchers.IO) {
            // Bound simultaneous decodes and coalesce requests for the same cover and size.
            locks[(key(url, size).hashCode() and Int.MAX_VALUE) % locks.size].withLock {
                cached(url, size)?.let { return@withLock it }
                slots.withPermit {
                    val bitmap = try {
                        val restored = BookPortableCovers.file(context, url)
                        val restoredBitmap = if (restored.isFile) {
                            runCatching { decodeCoverThumbnail(size) { restored.inputStream() } }.getOrNull()
                        } else null
                        restoredBitmap ?: when {
                            url.startsWith("content://") || url.startsWith("file://") ->
                                decodeCoverThumbnail(size) { context.contentResolver.openInputStream(Uri.parse(url)) }
                            url.startsWith("https://") || url.startsWith("http://") ->
                                BookCoverCache.get(context).loadThumbnail(url, size)
                            else -> null
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                    bitmap?.asImageBitmap()?.also { memory.put(key(url, size), it) }
                }
            }
        }
    }
}

internal fun decodeCoverThumbnail(size: Int, open: () -> InputStream?): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val stream = open() ?: return null
    stream.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val target = size.coerceIn(96, 1536)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > target * 2) sample *= 2
    val decoded = open()?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    } ?: return null
    return scaledCoverThumbnail(decoded, target)
}

internal fun scaledCoverThumbnail(bitmap: Bitmap, size: Int): Bitmap {
    val longest = max(bitmap.width, bitmap.height)
    if (longest <= size) return bitmap
    val scale = size.toFloat() / longest
    return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt().coerceAtLeast(1),
        (bitmap.height * scale).roundToInt().coerceAtLeast(1), true).also {
        if (it !== bitmap) bitmap.recycle()
    }
}
