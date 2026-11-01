package com.roinur.booktracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.asAndroidBitmap
import com.roinur.booktracker.data.media.BookCoverThumbnails
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal enum class BookReadingPosterStatus { FINISHED, IN_PROGRESS, NOT_STARTED }
internal data class BookReadingPosterTile(
    val title: String, val author: String, val cover: String, val finished: Boolean,
    val percent: Int?, val parent: String = "", val completedOn: LocalDate? = null,
    val hasReadPages: Boolean = false
) {
    val status: BookReadingPosterStatus get() = when {
        finished -> BookReadingPosterStatus.FINISHED
        percent?.let { it < 1 } ?: !hasReadPages -> BookReadingPosterStatus.NOT_STARTED
        else -> BookReadingPosterStatus.IN_PROGRESS
    }
}
internal data class BookReadingPosterSection(val heading: String, val tiles: List<BookReadingPosterTile>,
    val pagesRead: Long = 0, val readingSeconds: Long = 0)
internal data class BookReadingPosterPage(val sections: List<BookReadingPosterSection>, val height: Int, val rowHeight: Int)

internal const val BOOK_POSTER_WIDTH = 1800
internal const val BOOK_POSTER_COLUMNS = 6
private const val MARGIN = 72
private const val GAP = 24
private const val TOP = 220
private const val FOOTER = 80
private const val SECTION = 88

/** A single full-height layout. Raster memory is bounded separately by rendering one row at a time. */
internal fun planBookReadingPoster(groups: List<BookReadingListGroup>, options: BookReadingListOptions): List<BookReadingPosterPage> {
    if (groups.none { it.entries.isNotEmpty() }) return emptyList()
    val sections = groups.mapNotNull { group ->
        val tiles = group.entries.flatMap { entry ->
            listOf(BookReadingPosterTile(readingListTitle(entry.book.title),
                if (options.includeAuthors) entry.book.authors.trim().replace(Regex("\\s+"), " ") else "",
                entry.book.coverUrl, entry.finished, readingListPercent(entry.page, entry.book.pageCount),
                completedOn = if (entry.finished && options.includeCompletionDates) entry.date else null,
                hasReadPages = entry.page > 0)) +
                entry.parts.map { part -> BookReadingPosterTile(readingListTitle(part.part.title), "",
                    part.part.coverUrl.ifBlank { entry.book.coverUrl }, part.finished,
                    readingListPercent(part.part.pagesRead(part.page), part.part.pageCount), readingListTitle(entry.book.title),
                    if (part.finished && options.includeCompletionDates) part.completedOn else null,
                    part.part.pagesRead(part.page) > 0) }
        }
        if (tiles.isNotEmpty() || group.pagesRead > 0 || group.readingSeconds > 0)
            BookReadingPosterSection(group.heading, tiles, group.pagesRead, group.readingSeconds) else null
    }
    if (sections.isEmpty()) return emptyList()
    val rowHeight = if (options.includeCompletionDates) 512 else 474
    val height = TOP.toLong() + FOOTER + sections.sumOf {
        SECTION + ((it.tiles.size.toLong() + BOOK_POSTER_COLUMNS - 1) / BOOK_POSTER_COLUMNS) * rowHeight
    }
    require(height <= Int.MAX_VALUE) { "Library is too large for a PNG image" }
    return listOf(BookReadingPosterPage(sections, height.toInt(), rowHeight))
}

private val posterPaper = 0xFFF8EFDC.toInt()
private val posterInk = 0xFF28241E.toInt()
private val posterMuted = 0xFF6D614D.toInt()
private val posterGreen = 0xFF256F46.toInt()
private val posterGold = 0xFFA06C09.toInt()
private val posterRed = 0xFF9F3429.toInt()

private class BookPosterPainter(private val canvas: Canvas) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
    fun line(label: String, x: Float, y: Float, size: Float, color: Int = posterInk, bold: Boolean = false) {
        text.textSize = size; text.color = color
        text.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        canvas.drawText(label, x, y, text)
    }
    private fun wrapped(label: String, x: Float, y: Float, width: Int, size: Float, lines: Int, color: Int = posterInk, bold: Boolean = false): Float {
        text.textSize = size; text.color = color
        text.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        val layout = StaticLayout.Builder.obtain(label, 0, label.length, text, width)
            .setIncludePad(false).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build()
        canvas.save(); canvas.translate(x, y); layout.draw(canvas); canvas.restore()
        return layout.height.toFloat()
    }
    fun header(total: Int, allBooks: Boolean) {
        line(if (allBooks) "My library" else "My reading years", MARGIN.toFloat(), 104f, 62f, bold = true)
        line("$total ${if (total == 1) "book" else "books"} · Book Tracker", MARGIN.toFloat(), 156f, 27f, posterMuted)
        listOf(Triple(1000f, "Finished", posterGreen), Triple(1220f, "In progress", posterGold),
            Triple(1490f, "Not started", posterRed)).forEach { (x, label, color) ->
            paint.color = color; canvas.drawCircle(x, 144f, 9f, paint)
            line(label, x + 22, 154f, 26f, color)
        }
    }
    fun heading(section: BookReadingPosterSection) {
        line(section.heading, MARGIN.toFloat(), 42f, 43f, bold = true)
        val total = String.format(Locale.US, "%,d %s · %dh %02dm", section.pagesRead,
            if (section.pagesRead == 1L) "page" else "pages", section.readingSeconds / 3600, (section.readingSeconds % 3600) / 60)
        text.textSize = 30f; text.typeface = Typeface.DEFAULT
        line(total, BOOK_POSTER_WIDTH - MARGIN - text.measureText(total), 42f, 30f, posterMuted)
        paint.color = Color.rgb(210, 195, 166); paint.strokeWidth = 2f
        canvas.drawLine(MARGIN.toFloat(), 62f, (BOOK_POSTER_WIDTH - MARGIN).toFloat(), 62f, paint)
    }
    suspend fun tile(context: Context, tile: BookReadingPosterTile, column: Int, number: Int) {
        val cellWidth = (BOOK_POSTER_WIDTH - MARGIN * 2 - GAP * (BOOK_POSTER_COLUMNS - 1)) / BOOK_POSTER_COLUMNS
        val x = (MARGIN + column * (cellWidth + GAP)).toFloat()
        text.textSize = 23f; text.typeface = Typeface.DEFAULT
        val numberLabel = number.toString()
        line(numberLabel, x + (cellWidth - text.measureText(numberLabel)) / 2, 24f, 23f, posterMuted)
        val badge = when (tile.status) {
            BookReadingPosterStatus.FINISHED -> Triple(posterGreen, Color.WHITE, "FINISHED")
            BookReadingPosterStatus.IN_PROGRESS -> Triple(Color.rgb(246, 216, 126), posterGold,
                tile.percent?.let { "$it% READ" } ?: "IN PROGRESS")
            BookReadingPosterStatus.NOT_STARTED -> Triple(Color.rgb(244, 194, 184), posterRed, "NOT STARTED")
        }
        paint.color = badge.first
        val statusBox = RectF(x, 34f, x + cellWidth, 66f)
        canvas.drawRoundRect(statusBox, 6f, 6f, paint)
        text.textSize = 20f; text.typeface = Typeface.DEFAULT_BOLD
        line(badge.third, statusBox.centerX() - text.measureText(badge.third) / 2, 57f, 20f, badge.second, true)
        val coverBox = RectF(x + 24f, 77f, x + cellWidth - 24f, 337f)
        paint.color = Color.rgb(234, 222, 197); canvas.drawRoundRect(coverBox, 6f, 6f, paint)
        val cover = if (tile.cover.isBlank()) null else BookCoverThumbnails.load(context, tile.cover, 384)?.asAndroidBitmap()
        if (cover != null) {
            val scale = minOf(coverBox.width() / cover.width, coverBox.height() / cover.height)
            val width = cover.width * scale; val height = cover.height * scale
            val destination = RectF(coverBox.centerX() - width / 2, coverBox.centerY() - height / 2,
                coverBox.centerX() + width / 2, coverBox.centerY() + height / 2)
            canvas.drawBitmap(cover, Rect(0, 0, cover.width, cover.height), destination, paint)
        } else {
            text.textSize = 56f; text.color = posterMuted; text.typeface = Typeface.DEFAULT_BOLD
            val initial = tile.title.take(1).uppercase()
            canvas.drawText(initial, coverBox.centerX() - text.measureText(initial) / 2,
                coverBox.centerY() - (text.fontMetrics.ascent + text.fontMetrics.descent) / 2, text)
        }
        var detailsBottom = 353f + wrapped(tile.title, x, 353f, cellWidth, 26f, 2, bold = true)
        if (tile.author.isNotBlank()) detailsBottom += 6f + wrapped(tile.author, x, detailsBottom + 6f, cellWidth, 22f, 2, posterMuted)
        else if (tile.parent.isNotBlank()) detailsBottom += 6f + wrapped("Part of ${tile.parent}", x, detailsBottom + 6f, cellWidth, 20f, 2, posterMuted)
        tile.completedOn?.let { line("Finished $it", x, detailsBottom + 25f, 20f, posterMuted) }
    }
    fun footer() { line("Book Tracker", MARGIN.toFloat(), FOOTER - 28f, 23f, posterMuted) }
}

/** Stream a single PNG from small row bitmaps; memory does not grow with the full image height. */
internal suspend fun exportBookReadingPoster(
    context: Context, groups: List<BookReadingListGroup>, options: BookReadingListOptions,
    onProgress: (Int, Int) -> Unit = { _, _ -> }
): List<File> = withContext(Dispatchers.IO) {
    val poster = planBookReadingPoster(groups, options).singleOrNull()
        ?: error("No reading history for this selection")
    val directory = File(context.cacheDir, "reading_list_exports/${UUID.randomUUID()}")
    check(directory.mkdirs()) { "Could not create image folder" }
    val file = File(directory, "reading-list.png")
    val totalRows = poster.sections.sumOf { (it.tiles.size + BOOK_POSTER_COLUMNS - 1) / BOOK_POSTER_COLUMNS }
    var done = 0
    try {
        file.outputStream().buffered(65536).use { output ->
            BookPosterPngWriter(output, BOOK_POSTER_WIDTH, poster.height).use { png ->
                suspend fun strip(height: Int, draw: suspend BookPosterPainter.() -> Unit) {
                    currentCoroutineContext().ensureActive()
                    val bitmap = Bitmap.createBitmap(BOOK_POSTER_WIDTH, height, Bitmap.Config.ARGB_8888)
                    try {
                        val canvas = Canvas(bitmap); canvas.drawColor(posterPaper)
                        BookPosterPainter(canvas).draw()
                        val pixels = IntArray(BOOK_POSTER_WIDTH * height)
                        bitmap.getPixels(pixels, 0, BOOK_POSTER_WIDTH, 0, 0, BOOK_POSTER_WIDTH, height)
                        png.writePixels(pixels, height)
                    } finally { bitmap.recycle() }
                }
                strip(TOP) { header(groups.flatMap { it.entries }.map { it.book.id }.distinct().size, options.allBooks) }
                for (section in poster.sections) {
                    strip(SECTION) { heading(section) }
                    section.tiles.chunked(BOOK_POSTER_COLUMNS).forEachIndexed { row, tiles ->
                        strip(poster.rowHeight) {
                            tiles.forEachIndexed { column, item -> tile(context, item, column, row * BOOK_POSTER_COLUMNS + column + 1) }
                        }
                        done++
                        withContext(Dispatchers.Main) { onProgress(done, totalRows) }
                    }
                }
                strip(FOOTER) { footer() }
                png.finish()
            }
        }
        listOf(file)
    } catch (failure: Throwable) {
        file.delete(); directory.delete(); throw failure
    }
}
