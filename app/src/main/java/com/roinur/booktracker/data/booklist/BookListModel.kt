package com.roinur.booktracker.data.booklist

import com.roinur.booktracker.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import java.util.UUID

internal enum class BookListField(val heading: String) {
    AUTHOR("Author"), TITLE("Book List"), PAGES("Page Count"), OWNERSHIP("Ownership"),
    STATUS("Reading Status"), PRIORITY("Weight / 5"), READ_PAGES("Pages Read"), HOURS("Hours Read"),
    COLLECTIONS("Collections"), FINISHED("Finished Date"), ISBN("ISBN"), RATING("Rating / 5"), BOOKMARK("Bookmark")
}

internal data class BookListRecord(val key: String, val values: List<String>) {
    init { require(values.size == BookListField.entries.size) }
    val title get() = values[BookListField.TITLE.ordinal]
    operator fun get(field: BookListField) = values[field.ordinal]
    fun cells(): List<Any> = values.mapIndexed { index, value -> when (BookListField.entries[index]) {
        BookListField.PAGES, BookListField.READ_PAGES -> value.toInt()
        BookListField.RATING -> value.toInt().takeIf { it > 0 } ?: ""
        BookListField.OWNERSHIP -> when(value) { "Owned" -> true; "Not owned" -> false; else -> "" }
        BookListField.PRIORITY -> value.toIntOrNull()?.div(2.0) ?: ""
        BookListField.BOOKMARK -> value.toIntOrNull() ?: ""
        BookListField.HOURS -> value.toLong() / 3600.0
        else -> value
    } } + key

    companion object {
        const val KEY_HEADING = "Book Tracker ID"
        val headers = BookListField.entries.map { it.heading } + KEY_HEADING
        // Google pre-fills unused checkbox cells with FALSE. They are not books.
        fun isEmptyRow(cells: List<String>): Boolean = cells.withIndex().all { it.index == BookListField.OWNERSHIP.ordinal || it.value.isBlank() }
        fun fromBook(book: BookRow) = BookListRecord(book.listSyncKey, listOf(book.authors, book.title,
            book.pageCount.toString(), book.ownership, bookStatus(book.status),
            (if(book.status == BookStatus.FINISHED && book.rating > 0) book.rating * 2 else book.readingPriority).takeIf { it > 0 }?.toString().orEmpty(),
            book.currentPage.toString(), book.readingSeconds.toString(), normalizeBookCollections(book.collections),
            book.finishedAt.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate().toString() }.getOrDefault(it.take(10)) }.orEmpty(),
            book.isbn.replace("-", "").replace(" ", ""), book.rating.toString(), book.bookmarkPage?.toString().orEmpty()).map { it.trim() })

        fun parse(key: String, cells: List<String>): BookListRecord {
            require(runCatching { UUID.fromString(key) }.isSuccess) { "Invalid Book Tracker ID. Keep the hidden ID column unchanged." }
            val v = List(BookListField.entries.size) { cells.getOrNull(it).orEmpty().trim() }.toMutableList()
            require(v.all { it.length <= 8000 }) { "A Book List cell is too long." }
            require(v[1].isNotBlank()) { "Enter a book title." }
            fun number(index: Int, max: Long, blank: String = "0", allowEmpty: Boolean = false) {
                val raw = v[index]
                if (raw.isBlank() && allowEmpty) return
                val n = (raw.ifBlank { blank }).toBigDecimalOrNull()
                require(n != null && n.stripTrailingZeros().scale() <= 0 && n >= BigDecimal.ZERO && n <= max.toBigDecimal()) { "${BookListField.entries[index].heading}: enter a whole number from 0 to $max." }
                v[index] = n.toLong().toString()
            }
            number(2, 2_000_000); number(6, Int.MAX_VALUE.toLong()); number(11, 5); number(12, Int.MAX_VALUE.toLong(), allowEmpty = true)
            if(v[5].isNotBlank()) {
                val units=v[5].toBigDecimalOrNull()?.multiply(2.toBigDecimal())
                require(units != null && units >= BigDecimal.ZERO && units <= 10.toBigDecimal() && units.stripTrailingZeros().scale() <= 0) { "Weight / 5: enter 0 to 5 in half-star steps." }
                v[5]=units.toInt().toString()
            }
            if (v[5] == "0") v[5] = ""
            v[3] = when(v[3].lowercase()) { "", "unknown" -> "Unknown"; "owned", "true", "1", "yes" -> "Owned"; "not owned", "false", "0", "no" -> "Not owned"; else -> throw IllegalArgumentException("Ownership: choose Unknown, Owned or Not owned.") }
            v[4] = bookStatus(parseStatus(v[4]))
            val hours = v[7].ifBlank { "0" }.toBigDecimalOrNull()
            require(hours != null && hours >= BigDecimal.ZERO && hours <= 1_000_000.toBigDecimal()) { "Hours Read: enter a number from 0 to 1000000." }
            v[7] = (hours * 3600.toBigDecimal()).setScale(0, RoundingMode.HALF_UP).toLong().toString()
            v[8] = normalizeBookCollections(v[8])
            if (v[9].isNotBlank()) {
                val date = runCatching { LocalDate.parse(v[9]) }.getOrNull()
                require(date != null && !date.isAfter(LocalDate.now())) { "Finished Date: use YYYY-MM-DD, today or earlier." }
                require(v[4] == "Completed") { "Set Reading Status to Completed or clear Finished Date." }
            }
            // ISBN is an identifier. Never turn it into a number or silently drop it.
            v[10] = v[10].replace("-", "").replace(" ", "")
            require(v[10].isBlank() || v[10].matches(Regex("[0-9Xx]{10}|[0-9]{13}"))) { "ISBN: enter 10 or 13 characters, or leave it empty." }
            return BookListRecord(key, v)
        }
        fun bookStatus(status: BookStatus) = when(status) { BookStatus.WISHLIST -> "Unread"; BookStatus.READING -> "Reading"; BookStatus.PAUSED -> "On hold"; BookStatus.FINISHED -> "Completed" }
        fun parseStatus(value: String) = when(value.trim().lowercase()) { "", "unread", "not started" -> BookStatus.WISHLIST; "reading" -> BookStatus.READING; "on hold", "paused" -> BookStatus.PAUSED; "finished", "completed" -> BookStatus.FINISHED; else -> throw IllegalArgumentException("Reading Status: choose Unread, Reading, On hold or Completed.") }
    }
}

internal data class BookListConflict(val key: String, val title: String, val field: String, val appValue: String, val sheetValue: String) {
    // A choice is valid only for these exact values, never for later edits.
    val choiceKey: String get() = UUID.nameUUIDFromBytes(listOf(key, this.field, appValue, sheetValue).joinToString("\u0000").toByteArray()).toString()
}
internal data class BookListMerge(val records: Map<String, BookListRecord>, val conflicts: List<BookListConflict>)

internal object BookListMerger {
    fun merge(base: Map<String, BookListRecord>, app: Map<String, BookListRecord>, sheet: Map<String, BookListRecord>, choices: Map<String, String> = emptyMap()): BookListMerge {
        val result = linkedMapOf<String, BookListRecord>(); val conflicts = mutableListOf<BookListConflict>()
        for (key in (app.keys + sheet.keys).distinct()) {
            val a = app[key]; val s = sheet[key]; val b = base[key]
            if (a == null) {
                if (b == null) { if (s != null) result[key] = s; continue }
                if (s == null || s == b) continue // A confirmed deletion in the app.
                val c = BookListConflict(key, s.title, "Book removed in app", "Removed", "Keep book")
                when(choices[c.choiceKey]) { "sheet" -> result[key] = s; "app" -> Unit; else -> conflicts += c }
                continue
            }
            if (s == null) { result[key] = a; continue } // A missing Sheet row never deletes library data.
            val values = a.values.mapIndexed { index, av ->
                val sv = s.values[index]; val bv = b?.values?.get(index)
                when { av == sv -> av; av == bv -> sv; sv == bv -> av; else -> {
                    val c = BookListConflict(key, a.title, BookListField.entries[index].heading, av, sv)
                    when(choices[c.choiceKey]) { "app" -> av; "sheet" -> sv; else -> { conflicts += c; av } }
                } }
            }
            result[key] = BookListRecord(key, values)
        }
        if(conflicts.isEmpty()) result.replaceAll { key, record ->
            if(record[BookListField.STATUS] != "Completed") record else {
                val previous=base[key]
                val values=record.values.toMutableList()
                val weightChanged=previous == null || record[BookListField.PRIORITY] != previous[BookListField.PRIORITY]
                val ratingChanged=previous != null && record[BookListField.RATING] != previous[BookListField.RATING]
                if(weightChanged && !ratingChanged) {
                    val units=record[BookListField.PRIORITY].toIntOrNull() ?: 0
                    require(units % 2 == 0) { "${record.title}: finished books use whole-star final ratings from 0 to 5." }
                    values[BookListField.RATING.ordinal]=(units/2).toString()
                }
                val rating=values[BookListField.RATING.ordinal].toInt()
                if(rating > 0) values[BookListField.PRIORITY.ordinal]=(rating*2).toString()
                BookListRecord(key,values)
            }
        }
        return BookListMerge(result, conflicts)
    }
}
