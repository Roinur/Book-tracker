package com.roinur.booktracker

import kotlin.math.abs

/** OCR geometry matters: many contents pages put all page numbers in a separate text block. */
internal data class BookContentsLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int, val slope: Float = 0f) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

internal data class BookContentsEntry(val title: String, val startPage: Int? = null)

internal fun bookContentsNumberToken(text: String): String = text.trim().trim('.', '·', '…', '_', '–', '—').trim()

internal fun bookContentsIsPageToken(text: String): Boolean = bookContentsNumberToken(text)
    .matches(Regex("(\\d{1,6}|[ivxlcdm]+)", RegexOption.IGNORE_CASE))

internal fun bookParseContents(lines: List<BookContentsLine>): List<BookContentsEntry> {
    val ordered = lines.filter { it.text.isNotBlank() }.sortedWith(compareBy({ it.top }, { it.left }))
    val numbers = ordered.filter { bookContentsNumberToken(it.text).matches(Regex("\\d{1,6}")) }
    val usedNumbers = mutableSetOf<BookContentsLine>()
    val entries = mutableListOf<BookContentsEntry>()
    val trailingPage = Regex("^(.*?)[\\s.·…_—–-]+(\\d{1,6})\\s*$")
    for (line in ordered) {
        val raw = line.text.trim().replace(Regex("\\s+"), " ")
        if (line in numbers || bookContentsIsPageToken(raw) || raw.matches(Regex("(contents|table of contents|innehåll|innehållsförteckning)", RegexOption.IGNORE_CASE))) continue
        fun numberDistance(number: BookContentsLine): Float = abs(number.centerY - (line.centerY + line.slope * (number.centerX - line.centerX)))
        val match = trailingPage.matchEntire(raw)
        val separateNumber = if (match == null) numbers.filter { number ->
            number !in usedNumbers && number.left >= line.right - line.height &&
                numberDistance(number) <= maxOf(number.height, line.height) * 0.75
        }.minByOrNull { numberDistance(it) } else null
        separateNumber?.let { usedNumbers += it }
        val title = (match?.groupValues?.get(1) ?: raw)
            .replace(Regex("^[0-9]{1,3}[.)]?\\s+"), "")
            .replace(Regex("[.·…_]{2,}\\s*[ivxlcdm]+$", RegexOption.IGNORE_CASE), "")
            .trim().trimEnd('.', '·', '…', '_', '–', '—').trim()
        if (title.none(Char::isLetter)) continue
        val page = (match?.groupValues?.get(2) ?: separateNumber?.text?.let(::bookContentsNumberToken))?.toIntOrNull()?.takeIf { it > 0 }
        entries += BookContentsEntry(title, page)
    }
    return entries
}

/** Keep scan order and avoid importing the same row twice when consecutive photos overlap. */
internal fun bookMergeContents(existing: List<BookContentsEntry>, scanned: List<BookContentsEntry>): List<BookContentsEntry> {
    val result = existing.toMutableList()
    for (entry in scanned) {
        val index = result.indexOfFirst { it.title.equals(entry.title, ignoreCase = true) &&
            (it.startPage == entry.startPage || it.startPage == null || entry.startPage == null) }
        if (index < 0) result += entry else if (result[index].startPage == null && entry.startPage != null) result[index] = entry
    }
    return result
}

/** Only infer an end from an actual following start; missing pages stay editable, never invented. */
internal fun bookContentsEndPages(entries: List<BookContentsEntry>, totalPages: Int): List<Int?> = entries.mapIndexed { index, entry ->
    val next = entries.getOrNull(index + 1)?.startPage
    when {
        entry.startPage == null -> null
        next != null && next > entry.startPage -> next - 1
        index == entries.lastIndex && totalPages >= entry.startPage -> totalPages
        else -> null
    }
}
