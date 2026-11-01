package com.roinur.booktracker.data.markdown

import android.database.sqlite.SQLiteDatabase
import com.roinur.booktracker.data.database.BookTrackerDatabase
import com.roinur.booktracker.data.booklist.BookListRepository

/** Triggered in the SAME SQLite transaction as local edits, including live autosaves and imports. */
internal class BookMarkdownOutbox(private val database: BookTrackerDatabase) {
    companion object {
        fun ensureSchema(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS markdown_outbox (
                book_id INTEGER PRIMARY KEY REFERENCES books(id) ON DELETE CASCADE,
                revision INTEGER NOT NULL DEFAULT 1, exported_revision INTEGER NOT NULL DEFAULT 0)""")
            fun dirty(book: String) = "INSERT OR IGNORE INTO markdown_outbox(book_id,revision) VALUES ($book,0); UPDATE markdown_outbox SET revision=revision+1 WHERE book_id=$book;"
            db.execSQL("CREATE TRIGGER IF NOT EXISTS markdown_book_insert AFTER INSERT ON books BEGIN ${dirty("NEW.id")} END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS markdown_book_update AFTER UPDATE ON books BEGIN ${dirty("NEW.id")} END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS markdown_note_insert AFTER INSERT ON reading_notes BEGIN ${dirty("NEW.book_id")} END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS markdown_note_update AFTER UPDATE ON reading_notes BEGIN ${dirty("OLD.book_id")} ${dirty("NEW.book_id")} END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS markdown_note_delete AFTER DELETE ON reading_notes WHEN EXISTS(SELECT 1 FROM books WHERE id=OLD.book_id) BEGIN ${dirty("OLD.book_id")} END")
            db.execSQL("INSERT OR IGNORE INTO markdown_outbox(book_id) SELECT id FROM books")
        }
    }
    fun count(): Int = database.readableDatabase.rawQuery("SELECT count(*) FROM markdown_outbox WHERE revision>exported_revision", emptyArray()).use { it.moveToFirst(); it.getInt(0) }
    fun allPending() { database.writableDatabase.execSQL("UPDATE markdown_outbox SET revision=revision+1") }
    fun current(item: MarkdownItem): Boolean = database.readableDatabase.rawQuery(
        "SELECT 1 FROM markdown_outbox q JOIN books b ON b.id=q.book_id WHERE q.book_id=? AND q.revision=? AND b.list_sync_key=?",
        arrayOf(item.bookId.toString(), item.revision.toString(), item.identity)).use { it.moveToFirst() }
    fun acknowledge(item: MarkdownItem) { database.writableDatabase.execSQL(
        "UPDATE markdown_outbox SET exported_revision=? WHERE book_id=? AND revision=?", arrayOf(item.revision, item.bookId, item.revision)) }
    fun prepare() { BookListRepository(database).ensureKeys() }
    fun next(afterBookId: Int = 0): MarkdownItem? {
        // Reuse the UUID already included in native backups and Book List; never the reusable SQLite row number.
        val missingKeys = database.readableDatabase.rawQuery("SELECT 1 FROM books WHERE list_sync_key='' LIMIT 1", emptyArray()).use { it.moveToFirst() }
        if (missingKeys) prepare()
        val db = database.readableDatabase
        db.beginTransactionNonExclusive()
        try {
            val item = db.rawQuery("SELECT book_id,revision FROM markdown_outbox WHERE revision>exported_revision AND book_id>? ORDER BY book_id LIMIT 1", arrayOf(afterBookId.toString())).use {
                if (it.moveToFirst()) it.getInt(0) to it.getLong(1) else null
            } ?: return null
            val book = database.getBook(item.first) ?: return null
            return MarkdownItem(book.id, book.listSyncKey, item.second, BookMarkdownDocument.block(book, database.listNotes(book.id)))
        } finally { db.endTransaction() }
    }
}
