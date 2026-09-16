package com.roinur.booktracker.data.backup

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.roinur.booktracker.LegacyMigration
import com.roinur.booktracker.data.media.BookCoverCache
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream

internal object BookPortableCovers {
    fun file(context: Context, url: String) = File(context.filesDir, "portable_covers/${LegacyMigration.hash(url.toByteArray(Charsets.UTF_8))}.img")
    fun prune(context: Context, urls: Set<String>) {
        val keep = urls.map { file(context, it).name }.toSet()
        File(context.filesDir, "portable_covers").listFiles()?.filter { it.isFile && it.name !in keep }?.forEach { it.delete() }
    }
    fun export(context: Context, books: JSONArray): JSONArray {
        val urls = (0 until books.length()).map { books.getJSONObject(it).optString("cover_url") }.filter { it.isNotBlank() }.distinct()
        return JSONArray().apply { urls.forEach { url ->
            val bytes = runCatching {
                val restored = file(context, url)
                if (restored.isFile) restored.inputStream().use(::readImage)
                else if (url.startsWith("http://") || url.startsWith("https://")) {
                    val bitmap = BookCoverCache.get(context).load(url) ?: error("Cover unavailable")
                    compactBitmap(bitmap)
                } else context.contentResolver.openInputStream(Uri.parse(url))?.use(::readImage) ?: error("Cover unavailable")
            }.map { compactBytes(it) }.getOrNull()
            val asset = JSONObject().put("url", url)
            if (bytes == null || !validImage(bytes)) asset.put("missing", true)
            else asset.put("sha256", LegacyMigration.hash(bytes)).put("data_base64", Base64.encodeToString(bytes, Base64.NO_WRAP))
            put(asset)
        } }
    }
    private fun compactBytes(bytes: ByteArray): ByteArray {
        if (bytes.size <= 96 * 1024) return bytes
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1536) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Invalid cover")
        return try { compactBitmap(bitmap) } finally { bitmap.recycle() }
    }
    @Suppress("DEPRECATION")
    private fun compactBitmap(bitmap: Bitmap): ByteArray {
        val scale = minOf(1f, 768f / maxOf(bitmap.width, bitmap.height))
        val fitted = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
        return try {
            ByteArrayOutputStream().also { check(fitted.compress(Bitmap.CompressFormat.WEBP, 85, it)) }.toByteArray()
        } finally { if (fitted !== bitmap) fitted.recycle() }
    }
    private fun readImage(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= 8 * 1024 * 1024) { "Cover exceeds 8 MB" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
    private fun validImage(bytes: ByteArray): Boolean {
        if (bytes.size > 8 * 1024 * 1024) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return bounds.outWidth in 1..8192 && bounds.outHeight in 1..8192
    }
    fun validate(root: JSONObject) {
        val assets = root.optJSONArray("cover_assets") ?: return
        val urls = mutableSetOf<String>()
        val books = root.getJSONArray("books")
        val references = (0 until books.length()).map { books.getJSONObject(it).optString("cover_url") }.toSet()
        repeat(assets.length()) { i ->
            val asset = assets.getJSONObject(i)
            val url = asset.getString("url")
            require(url in references && urls.add(url)) { "Invalid cover reference" }
            require(asset.keys().asSequence().all { it in setOf("url", "missing", "sha256", "data_base64") }) { "Unknown cover fields" }
            if (!asset.optBoolean("missing")) {
                val bytes = Base64.decode(asset.getString("data_base64"), Base64.DEFAULT)
                require(LegacyMigration.hash(bytes) == asset.getString("sha256") && validImage(bytes)) { "Cover verification failed" }
            } else require(!asset.has("data_base64") && !asset.has("sha256")) { "Invalid missing cover" }
        }
    }
    fun restore(context: Context, root: JSONObject) {
        validate(root)
        val assets = root.optJSONArray("cover_assets") ?: return
        repeat(assets.length()) { i ->
            val asset = assets.getJSONObject(i)
            if (!asset.optBoolean("missing")) {
                val target = file(context, asset.getString("url"))
                check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
                val pending = File(target.parentFile, target.name + ".pending")
                val bytes = Base64.decode(asset.getString("data_base64"), Base64.DEFAULT)
                try {
                    FileOutputStream(pending).use { it.write(bytes); it.fd.sync() }
                    check(LegacyMigration.hash(pending.readBytes()) == asset.getString("sha256"))
                    check(pending.renameTo(target)) { "Could not save cover" }
                } finally { pending.delete() }
            }
        }
    }
}
