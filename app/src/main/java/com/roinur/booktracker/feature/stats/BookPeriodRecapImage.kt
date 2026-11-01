package com.roinur.booktracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.asAndroidBitmap
import com.roinur.booktracker.data.media.BookCoverThumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

internal data class BookRecapPalette(val background: Int, val surface: Int, val ink: Int, val muted: Int,
    val accent: Int, val accentContainer: Int, val onAccentContainer: Int, val outline: Int)

/** The preview and exported PNG are the same artifact. Bounded to six cover rows at 1080px. */
internal suspend fun renderBookPeriodRecap(context: Context, recap: BookPeriodRecap, colors: BookRecapPalette): File = withContext(Dispatchers.IO) {
    val featured = recap.books.take(6)
    val headerHeight = 910
    val bookHeight = 192
    val height = headerHeight + (if (featured.isEmpty()) 108 else 76 + featured.size * bookHeight) + 138
    val bitmap = Bitmap.createBitmap(1080, height, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(bitmap)
        canvas.drawColor(colors.background)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
        fun label(value: String, x: Float, baseline: Float, size: Float, color: Int = colors.ink, bold: Boolean = false) {
            text.color = color; text.textSize = size; text.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            canvas.drawText(value, x, baseline, text)
        }
        fun wrapped(value: String, x: Float, y: Float, width: Int, size: Float, lines: Int, color: Int = colors.ink, bold: Boolean = false) {
            text.color = color; text.textSize = size; text.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            val layout = StaticLayout.Builder.obtain(value, 0, value.length, text, width).setIncludePad(false)
                .setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build()
            canvas.save(); canvas.translate(x, y); layout.draw(canvas); canvas.restore()
        }
        fun panel(rect: RectF, fill: Int = colors.surface) {
            paint.style = Paint.Style.FILL; paint.color = fill; canvas.drawRoundRect(rect, 24f, 24f, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = colors.outline
            canvas.drawRoundRect(rect, 24f, 24f, paint); paint.style = Paint.Style.FILL
        }
        fun number(value: Long) = String.format(Locale.US, "%,d", value)
        paint.color = colors.accent; canvas.drawRoundRect(RectF(56f, 54f, 122f, 62f), 4f, 4f, paint)
        label("BOOK TRACKER  /  READING RECAP", 56f, 111f, 24f, colors.muted, true)
        label(recap.heading, 56f, 190f, 60f, bold = true)
        val dates = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
        label(when {
            recap.range == BookStatsRange.ALL_TIME -> "All time · including undated reading"
            recap.range == BookStatsRange.MONTH && recap.end.dayOfMonth < recap.end.lengthOfMonth() ->
                "Month to date · through ${recap.end.format(dates)}"
            else -> "${recap.start.format(dates)} – ${recap.end.format(dates)}"
        }, 56f, 244f, 29f, colors.muted)

        panel(RectF(56f, 284f, 1024f, 486f), colors.accentContainer)
        label("PAGES READ", 88f, 333f, 23f, colors.onAccentContainer, true)
        label(number(recap.pages), 88f, 435f, 82f, colors.onAccentContainer, true)
        paint.color = colors.outline; canvas.drawRect(521f, 320f, 523f, 448f, paint)
        label("READING TIME", 558f, 333f, 23f, colors.onAccentContainer, true)
        val time = if (recap.seconds >= 3600) "${recap.seconds / 3600}h ${(recap.seconds % 3600 / 60).toString().padStart(2, '0')}m"
            else "${recap.seconds / 60}m"
        label(time, 558f, 416f, 57f, colors.onAccentContainer, true)
        label("${recap.seconds % 60}s", 558f, 454f, 23f, colors.onAccentContainer)

        val metrics = listOf(
            "Reading pace" to (recap.pagesPerHour?.let { String.format(Locale.US, "%.1f pages/h", it) } ?: "—"),
            "Longest streak" to "${recap.longestStreak} ${if (recap.longestStreak == 1) "day" else "days"}",
            "Active reading days" to number(recap.activeDays.toLong()),
            "Books finished" to number(recap.finishedBooks.toLong()),
            "Most pages in a day" to number(recap.bestPageDay.toLong()),
            "Most minutes in a day" to number(recap.bestMinuteDay.toLong())
        )
        metrics.forEachIndexed { index, (name, value) ->
            val x = 56f + (index % 2) * 496f
            val y = 514f + (index / 2) * 126f
            panel(RectF(x, y, x + 472f, y + 108f))
            label(name, x + 24f, y + 35f, 24f, colors.muted)
            label(value, x + 24f, y + 82f, 37f, colors.ink, true)
        }

        var y = headerHeight.toFloat()
        if (featured.isEmpty()) {
            label("No saved reading in this period yet.", 56f, y + 55f, 31f, colors.muted)
            y += 108f
        } else {
            label("THE BOOKS", 56f, y + 31f, 25f, colors.muted, true)
            label("${recap.books.size} read · ${recap.finishedBooks} finished", 540f, y + 31f, 25f, colors.muted)
            y += 76f
            for (entry in featured) {
                currentCoroutineContext().ensureActive()
                panel(RectF(56f, y, 1024f, y + 172f))
                val cover = entry.book.coverUrl.takeIf { it.isNotBlank() }?.let { BookCoverThumbnails.load(context, it, 420)?.asAndroidBitmap() }
                val destination = RectF(76f, y + 16f, 170f, y + 156f)
                if (cover != null) {
                    val cropWidth = minOf(cover.width.toFloat(), cover.height * destination.width() / destination.height())
                    val cropHeight = minOf(cover.height.toFloat(), cover.width * destination.height() / destination.width())
                    val source = Rect(((cover.width - cropWidth) / 2).toInt(), ((cover.height - cropHeight) / 2).toInt(),
                        ((cover.width + cropWidth) / 2).toInt(), ((cover.height + cropHeight) / 2).toInt())
                    canvas.drawBitmap(cover, source, destination, paint)
                } else {
                    paint.color = colors.accentContainer; canvas.drawRoundRect(destination, 8f, 8f, paint)
                    wrapped(entry.book.title, 85f, y + 36f, 76, 20f, 4, colors.onAccentContainer, true)
                }
                wrapped(entry.book.title, 198f, y + 20f, 785, 32f, 2, bold = true)
                wrapped(entry.book.authors, 198f, y + 98f, 785, 23f, 1, colors.muted)
                val detail = if (entry.finished) "Finished" else "${number(entry.pages)} pages read"
                label(detail, 198f, y + 149f, 24f, colors.accent, true)
                if (entry.finished && entry.book.rating > 0) label("★".repeat(entry.book.rating.coerceIn(0, 5)) + "☆".repeat(5 - entry.book.rating.coerceIn(0, 5)),
                    770f, y + 149f, 28f, colors.accent)
                y += bookHeight
            }
        }
        val footer = if (recap.books.size > featured.size) "+${recap.books.size - featured.size} more books · totals include every book" else "Saved reading · Book Tracker"
        wrapped(footer, 56f, y + 24f, 968, 25f, 2, colors.muted)
        val directory = File(context.cacheDir, "reading_list_exports").apply { check(isDirectory || mkdirs()) }
        val file = File(directory, "reading-recap-${recap.range.name.lowercase(Locale.ROOT)}-${UUID.randomUUID()}.png")
        try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }; file }
        catch (error: Exception) { file.delete(); throw error }
    } finally { bitmap.recycle() }
}
