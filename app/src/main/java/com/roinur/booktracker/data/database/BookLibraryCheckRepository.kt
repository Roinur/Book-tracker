package com.roinur.booktracker.data.database

import android.content.ContentValues
import com.roinur.booktracker.*
import java.time.LocalDate

internal class BookLibraryCheckRepository(private val database: BookTrackerDatabase) {
    fun apply(fix: BookLibraryFix) {
        require(fix.hasChanges) { "Choose a change first." }
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val book = requireNotNull(database.getBook(fix.bookId)) { "This book was removed. Skip it and continue." }
            val values = ContentValues()
            fix.title?.let { require(it.isNotBlank()) { "Enter a title." }; values.put("title", it.trim()) }
            fix.authors?.let { values.put("authors", it.trim()) }
            fix.pageCount?.let { pages ->
                val minimum = maxOf(book.currentPage, book.parts.maxOfOrNull { it.endPage } ?: 0, 1)
                require(pages >= minimum) { "Total pages must be at least $minimum." }
                values.put("page_count", pages)
            }
            fix.isbn?.let { isbn ->
                require(isbn.isBlank() || normalizeIsbn(isbn) != null) { "Enter a valid ISBN, or leave it empty." }
                values.put("isbn", normalizeIsbn(isbn).orEmpty())
            }
            fix.coverUrl?.let { values.put("cover_url", it) }
            fix.collections?.let { values.put("collections", normalizeBookCollections(it)) }
            fix.status?.let { status ->
                values.put("status", status.name)
                if (status != BookStatus.FINISHED) {
                    values.put("finished_at", ""); values.put("finished_at_manual", 0)
                } else require(book.finishedAt.isNotBlank() || fix.completionDate != null) { "Choose the completion date." }
            }
            fix.completionDate?.let { date ->
                require((fix.status ?: book.status) == BookStatus.FINISHED) { "Only finished books have a completion date." }
                require(!date.isAfter(LocalDate.now())) { "Completion date cannot be in the future." }
            }
            if (values.size() > 0) check(db.update("books", values, "id = ?", arrayOf(book.id.toString())) == 1)
            fix.completionDate?.let { database.setCompletionDate(book.id, it) }
            fix.collectionTypes.forEach { (name, type) -> database.setCollectionType(name, type) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
