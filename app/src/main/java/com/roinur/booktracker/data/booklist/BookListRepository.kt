package com.roinur.booktracker.data.booklist

import android.content.ContentValues
import com.roinur.booktracker.*
import com.roinur.booktracker.data.database.BookTrackerDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Applies a reviewed three-way merge in one transaction. Never creates dated sessions. */
internal class BookListRepository(private val database: BookTrackerDatabase) {
    fun books(): List<BookRow> = database.listBooks("", BookSortField.ADDED, false)
    fun ensureKeys(): List<BookRow> {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            books().filter { it.listSyncKey.isBlank() }.forEach { book ->
                check(db.update("books", ContentValues().apply { put("list_sync_key", UUID.randomUUID().toString()) },
                    "id = ? AND list_sync_key = ''", arrayOf(book.id.toString())) == 1)
            }
            val result = books()
            require(result.map { it.listSyncKey }.distinct().size == result.size) { "Duplicate book identities. Sync cancelled." }
            db.setTransactionSuccessful()
            return result
        } finally { db.endTransaction() }
    }

    fun apply(expected: List<BookRow>, records: Collection<BookListRecord>) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            check(books() == expected) { "Library changed during sync. Try syncing again." }
            val existing = expected.associateBy { it.listSyncKey }
            val isbns = records.map { it[BookListField.ISBN] }.filter { it.isNotBlank() }
            require(isbns.distinct().size == isbns.size) { "Duplicate ISBN in Book List. No library changes saved." }
            for (record in records) {
                val old = existing[record.key]
                if (old != null && record == BookListRecord.fromBook(old)) continue
                val pages = record[BookListField.PAGES].toInt()
                require(pages == 0 || pages >= (old?.parts?.maxOfOrNull { it.endPage } ?: 0)) { "${record.title}: total pages cannot cut off an assigned part." }
                val id = old?.id ?: database.upsertBook(BookSeed(record[BookListField.ISBN], record.title,
                    record[BookListField.AUTHOR], pages, "", "", record[BookListField.COLLECTIONS]))
                require(old != null || id !in expected.map { it.id }) { "${record.title}: ISBN already belongs to another book." }
                val status = BookListRecord.parseStatus(record[BookListField.STATUS])
                val values = ContentValues().apply {
                    put("list_sync_key", record.key); put("title", record.title); put("authors", record[BookListField.AUTHOR])
                    put("page_count", pages); put("ownership", record[BookListField.OWNERSHIP])
                    put("reading_priority", if(status == BookStatus.FINISHED && old != null) old.readingPriority else record[BookListField.PRIORITY].toIntOrNull() ?: 0)
                    put("status", status.name); put("collections", record[BookListField.COLLECTIONS]); put("isbn", record[BookListField.ISBN])
                    put("rating", record[BookListField.RATING].toInt())
                    val readPages = record[BookListField.READ_PAGES].toInt()
                    val seconds = record[BookListField.HOURS].toLong()
                    db.rawQuery("SELECT COALESCE(SUM(pages_read), 0), COALESCE(SUM(COALESCE(duration_milliseconds, duration_seconds * 1000)), 0) / 1000 FROM reading_sessions WHERE book_id = ?", arrayOf(id.toString())).use {
                        it.moveToFirst()
                        if (old == null || readPages != old.currentPage) { put("current_page", readPages); put("unlogged_pages", readPages.toLong() - it.getLong(0)) }
                        if (old == null || seconds != old.readingSeconds) { put("reading_seconds", seconds); put("unlogged_seconds", seconds - it.getLong(1)) }
                    }
                    val bookmark = record[BookListField.BOOKMARK].toIntOrNull()
                    if (bookmark == null) putNull("bookmark_page") else put("bookmark_page", bookmark)
                    if (status == BookStatus.FINISHED && (record[BookListField.FINISHED].isNotBlank() || old == null || old.status != BookStatus.FINISHED)) {
                        val date = record[BookListField.FINISHED].ifBlank { LocalDate.now().toString() }
                        if (date != old?.let { BookListRecord.fromBook(it)[BookListField.FINISHED] }) {
                            put("finished_at", LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toString())
                            put("finished_at_manual", 1)
                        }
                    } else if (old == null || status != old.status) { put("finished_at", ""); put("finished_at_manual", 0) }
                    if (status == BookStatus.READING && old?.startedAt.isNullOrBlank()) put("started_at", Instant.now().toString())
                }
                check(db.update("books", values, "id = ?", arrayOf(id.toString())) == 1)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
