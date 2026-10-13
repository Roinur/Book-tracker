package com.roinur.booktracker

import android.graphics.Bitmap
import androidx.annotation.WorkerThread
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import kotlin.math.abs

/** Both camera and gallery use the same two-pass OCR. The caller owns the source bitmap. */
@WorkerThread
internal fun bookRecognizeContents(bitmap: Bitmap, recognizer: TextRecognizer): List<BookContentsEntry> {
    val full = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
        .contentsLines()
    // Isolated page numbers are often missed among dotted leaders. Enlarge just their column,
    // retaining original coordinates so numbers are matched to rows rather than OCR block order.
    val left = (bitmap.width * 0.70f).toInt()
    val crop = Bitmap.createBitmap(bitmap, left, 0, bitmap.width - left, bitmap.height)
    val scale = minOf(2f, 4400f / bitmap.height).coerceAtLeast(1f)
    val enlarged = try {
        Bitmap.createScaledBitmap(crop, (crop.width * scale).toInt(), (crop.height * scale).toInt(), true)
    } catch (failure: Exception) { crop.recycle(); throw failure }
    val extra = try {
        // Failure of the supplementary pass must not discard titles from the full photo.
        runCatching {
            Tasks.await(recognizer.process(InputImage.fromBitmap(enlarged, 0)))
                .contentsLines(scale, left).filter { bookContentsIsPageToken(it.text) }
        }.getOrDefault(emptyList())
    } finally {
        if (enlarged !== crop) enlarged.recycle()
        crop.recycle()
    }
    val merged = full.toMutableList()
    for (number in extra) {
        val duplicate = merged.any { existing ->
            bookContentsNumberToken(existing.text).equals(bookContentsNumberToken(number.text), ignoreCase = true) &&
                abs(existing.centerY - number.centerY) < maxOf(existing.height, number.height) &&
                abs(existing.centerX - number.centerX) < maxOf(existing.right - existing.left, number.right - number.left)
        }
        if (!duplicate) merged += number
    }
    return bookParseContents(merged)
}

private fun Text.contentsLines(scale: Float = 1f, offsetX: Int = 0): List<BookContentsLine> =
    textBlocks.flatMap { block -> block.lines.mapNotNull { line ->
        line.boundingBox?.let { rect ->
            val corners = line.cornerPoints
            val slope = if (corners != null && corners.size == 4) {
                val dx = corners[1].x + corners[2].x - corners[0].x - corners[3].x
                if (dx > 0) ((corners[1].y + corners[2].y - corners[0].y - corners[3].y).toFloat() / dx).coerceIn(-0.25f, 0.25f) else 0f
            } else 0f
            BookContentsLine(line.text, (rect.left / scale).toInt() + offsetX, (rect.top / scale).toInt(),
                (rect.right / scale).toInt() + offsetX, (rect.bottom / scale).toInt(), slope)
        }
    } }
