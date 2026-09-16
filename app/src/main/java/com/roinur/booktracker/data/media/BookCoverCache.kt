package com.roinur.booktracker.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest
import com.roinur.booktracker.fetchThumbnailBitmapRawOnce

internal class BookCoverCache(private val directory: File, private val download: (String) -> Bitmap?) {
    private var retained = emptySet<String>()

    @Synchronized fun retain(urls: Set<String>) {
        retained = urls.filter { it.startsWith("https://") || it.startsWith("http://") }.toSet()
    }

    private fun name(url: String) = MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) } + ".png"

    @Synchronized fun prune() {
        val keep = retained.map(::name).toSet()
        directory.listFiles()?.filter { it.isFile && it.name !in keep }?.forEach { it.delete() }
    }

    fun load(url: String): Bitmap? {
        synchronized(this) {
            val file = File(directory, name(url))
            if (file.isFile) {
                BitmapFactory.decodeFile(file.path)?.let { return it }
                file.delete()
            }
        }
        val bitmap = download(url) ?: return null
        synchronized(this) {
            // A completed request must not restore an old cover after the user replaces it.
            if (url in retained) {
                runCatching {
                directory.mkdirs()
                val file = File(directory, name(url))
                val temporary = File(directory, file.name + ".tmp")
                try {
                    temporary.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    check(temporary.renameTo(file))
                } finally { temporary.delete() }
                }
            }
        }
        return bitmap
    }

    companion object {
        @Volatile private var instance: BookCoverCache? = null
        fun get(context: Context): BookCoverCache = instance ?: synchronized(this) {
            instance ?: BookCoverCache(File(context.applicationContext.filesDir, "remote_book_covers")) {
                runCatching { fetchThumbnailBitmapRawOnce(it) }.getOrNull()
            }.also { instance = it }
        }
    }
}
