package com.roinur.booktracker.data.database

import com.roinur.booktracker.*

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

internal class BookTrackerDatabase(context: Context, databaseName: String = BOOK_TRACKER_DB) : SQLiteOpenHelper(context, databaseName, null, 1) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS books (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                isbn TEXT NOT NULL DEFAULT '',
                title TEXT NOT NULL DEFAULT '',
                authors TEXT NOT NULL DEFAULT '',
                page_count INTEGER NOT NULL DEFAULT 0,
                current_page INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'READING',
                rating INTEGER NOT NULL DEFAULT 0,
                notes TEXT NOT NULL DEFAULT '',
                cover_url TEXT NOT NULL DEFAULT '',
                source_url TEXT NOT NULL DEFAULT '',
                collections TEXT NOT NULL DEFAULT '',
                pinned INTEGER NOT NULL DEFAULT 0,
                added_at TEXT NOT NULL,
                started_at TEXT NOT NULL DEFAULT '',
                finished_at TEXT NOT NULL DEFAULT '',
                last_read_at TEXT NOT NULL DEFAULT '',
                reading_seconds INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reading_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                book_id INTEGER NOT NULL,
                started_at TEXT NOT NULL,
                ended_at TEXT NOT NULL,
                duration_seconds INTEGER NOT NULL DEFAULT 0,
                pages_read INTEGER NOT NULL DEFAULT 0,
                page_reached INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(book_id) REFERENCES books(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_books_isbn ON books(isbn) WHERE isbn <> ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_books_pinned ON books(pinned, added_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reading_sessions_book ON reading_sessions(book_id)")
        ensureBookColumns(db)
        ensureReadingSessionColumns(db)
        ensureSessionNoteSchema(db)
        ensureReadingGoalsSchema(db)
        LegacyMigration.ensureSchema(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        ensureBookColumns(db)
        ensureReadingSessionColumns(db)
        ensureSessionNoteSchema(db)
        ensureReadingGoalsSchema(db)
        LegacyMigration.ensureSchema(db)
        LegacyMigration.restoreMissingCompletionDates(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun ensureSessionNoteSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reading_notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                book_id INTEGER NOT NULL,
                created_at TEXT NOT NULL,
                page INTEGER NOT NULL DEFAULT 0,
                duration_seconds INTEGER NOT NULL DEFAULT 0,
                kind TEXT NOT NULL DEFAULT 'NOTE',
                note TEXT NOT NULL DEFAULT '',
                FOREIGN KEY(book_id) REFERENCES books(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        val columns = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info(reading_notes)", emptyArray()).use { cursor ->
            while (cursor.moveToNext()) {
                columns += cursor.getString(cursor.getColumnIndexOrThrow("name")).orEmpty()
            }
        }
        if ("autosave_key" !in columns) db.execSQL("ALTER TABLE reading_notes ADD COLUMN autosave_key TEXT NOT NULL DEFAULT ''")
        if ("kind" !in columns) {
            db.execSQL("ALTER TABLE reading_notes ADD COLUMN kind TEXT NOT NULL DEFAULT 'NOTE'")
        }
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reading_notes_book ON reading_notes(book_id, created_at)")
    }

    private fun ensureReadingGoalsSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reading_goals (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                daily_minutes INTEGER NOT NULL DEFAULT 0,
                daily_pages INTEGER NOT NULL DEFAULT 0,
                yearly_books INTEGER NOT NULL DEFAULT 0,
                updated_at TEXT NOT NULL
            )
            """.trimIndent()
        )
        val columns = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info(reading_goals)", null).use { cursor ->
            while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
        }
        for (column in listOf("monthly_minutes", "monthly_pages")) {
            if (column !in columns) db.execSQL("ALTER TABLE reading_goals ADD COLUMN $column INTEGER NOT NULL DEFAULT 0")
        }
        if ("daily_metric" !in columns) {
            db.execSQL("ALTER TABLE reading_goals ADD COLUMN daily_metric TEXT NOT NULL DEFAULT 'TIME'")
            db.execSQL("UPDATE reading_goals SET daily_metric = 'PAGES' WHERE daily_pages > 0 AND daily_minutes = 0")
        }
        if ("monthly_metric" !in columns) db.execSQL("ALTER TABLE reading_goals ADD COLUMN monthly_metric TEXT NOT NULL DEFAULT 'PAGES'")
    }

    private fun ensureBookColumns(db: SQLiteDatabase) {
        val columns = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info(books)", emptyArray()).use { cursor ->
            while (cursor.moveToNext()) {
                columns += cursor.getString(cursor.getColumnIndexOrThrow("name")).orEmpty()
            }
        }
        if ("finished_at_manual" !in columns) db.execSQL("ALTER TABLE books ADD COLUMN finished_at_manual INTEGER NOT NULL DEFAULT 0")
        if ("collections" !in columns) {
            db.execSQL("ALTER TABLE books ADD COLUMN collections TEXT NOT NULL DEFAULT ''")
        }
    }

    private fun ensureReadingSessionColumns(db: SQLiteDatabase) {
        val columns = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info(reading_sessions)", emptyArray()).use { cursor ->
            while (cursor.moveToNext()) {
                columns += cursor.getString(cursor.getColumnIndexOrThrow("name")).orEmpty()
            }
        }
        if ("exclude_from_statistics" !in columns) db.execSQL("ALTER TABLE reading_sessions ADD COLUMN exclude_from_statistics INTEGER NOT NULL DEFAULT 0")
        if ("duration_milliseconds" !in columns) {
            db.execSQL("ALTER TABLE reading_sessions ADD COLUMN duration_milliseconds INTEGER")
        }
        if ("pages_read" !in columns) {
            db.execSQL("ALTER TABLE reading_sessions ADD COLUMN pages_read INTEGER NOT NULL DEFAULT 0")
        }
        if ("page_reached" !in columns) {
            db.execSQL("ALTER TABLE reading_sessions ADD COLUMN page_reached INTEGER NOT NULL DEFAULT 0")
        }
    }

    fun upsertBook(seed: BookSeed, preferredId: Int? = null): Int {
        val existing = preferredId?.let { getBook(it) } ?: seed.isbn.takeIf { it.isNotBlank() }?.let { findBookByIsbn(it) }
        val now = Instant.now().toString()
        val values = ContentValues().apply {
            put("isbn", seed.isbn)
            put("title", seed.title.ifBlank { "Untitled book" })
            put("authors", seed.authors)
            put("page_count", seed.pageCount.coerceAtLeast(0))
            put("cover_url", seed.coverUrl)
            put("source_url", seed.sourceUrl)
            put("collections", normalizeBookCollections(seed.collections))
        }
        return if (existing != null) {
            writableDatabase.update("books", values, "id = ?", arrayOf(existing.id.toString()))
            existing.id
        } else {
            values.put("added_at", now)
            values.put("status", BookStatus.READING.name)
            writableDatabase.insert("books", null, values).toInt()
        }
    }

    fun listBooks(query: String, sortField: BookSortField, sortDescending: Boolean): List<BookRow> {
        val args = mutableListOf<String>()
        val where = if (query.isBlank()) {
            ""
        } else {
            val needle = "%${query.trim().lowercase(Locale.US)}%"
            args += needle
            args += needle
            args += needle
            args += needle
            args += needle
            "WHERE lower(title) LIKE ? OR lower(authors) LIKE ? OR lower(isbn) LIKE ? OR lower(notes) LIKE ? OR lower(collections) LIKE ?"
        }
        val direction = if (sortDescending) "DESC" else "ASC"
        val sortExpr = when (sortField) {
            BookSortField.ADDED -> "added_at $direction, id $direction"
            BookSortField.TITLE -> "lower(title) $direction, id DESC"
            BookSortField.AUTHOR -> "lower(authors) $direction, lower(title) ASC"
            BookSortField.RATING -> "rating $direction, added_at DESC"
            BookSortField.PROGRESS -> "CASE WHEN page_count > 0 THEN CAST(current_page AS REAL) / page_count ELSE 0 END $direction, added_at DESC"
        }
        val sql = "SELECT * FROM books $where ORDER BY pinned DESC, $sortExpr"
        readableDatabase.rawQuery(sql, args.toTypedArray()).use { cursor ->
            val rows = mutableListOf<BookRow>()
            while (cursor.moveToNext()) rows += cursor.toBookRow()
            return rows
        }
    }

    fun listCollections(): List<String> {
        val values = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT collections FROM books WHERE collections <> ''", emptyArray()).use { cursor ->
            while (cursor.moveToNext()) {
                values += cursor.getString(0).orEmpty()
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }
        }
        return values
            .distinctBy { it.lowercase(Locale.US) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
            .take(24)
    }

    fun loadReadingGoals(): BookReadingGoals = readableDatabase.rawQuery(
        "SELECT daily_minutes, daily_pages, yearly_books, monthly_minutes, monthly_pages, daily_metric, monthly_metric FROM reading_goals WHERE id = 1",
        emptyArray()
    ).use { cursor ->
        if (!cursor.moveToFirst()) BookReadingGoals() else BookReadingGoals(
            dailyMinutes = cursor.getInt(0).coerceAtLeast(0),
            dailyPages = cursor.getInt(1).coerceAtLeast(0),
            yearlyBooks = cursor.getInt(2).coerceAtLeast(0),
            monthlyMinutes = cursor.getInt(3).coerceAtLeast(0),
            monthlyPages = cursor.getInt(4).coerceAtLeast(0),
            dailyMetric = runCatching { BookGoalMetric.valueOf(cursor.getString(5)) }.getOrDefault(BookGoalMetric.TIME),
            monthlyMetric = runCatching { BookGoalMetric.valueOf(cursor.getString(6)) }.getOrDefault(BookGoalMetric.PAGES)
        )
    }

    fun saveReadingGoals(goals: BookReadingGoals) {
        val values = ContentValues().apply {
            put("id", 1)
            put("daily_minutes", goals.dailyMinutes.coerceAtLeast(0))
            put("daily_pages", goals.dailyPages.coerceAtLeast(0))
            put("yearly_books", goals.yearlyBooks.coerceAtLeast(0))
            put("monthly_minutes", goals.monthlyMinutes.coerceAtLeast(0))
            put("monthly_pages", goals.monthlyPages.coerceAtLeast(0))
            put("daily_metric", goals.dailyMetric.name)
            put("monthly_metric", goals.monthlyMetric.name)
            put("updated_at", Instant.now().toString())
        }
        check(writableDatabase.insertWithOnConflict("reading_goals", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) {
            "Could not save reading goals."
        }
    }

    fun markStarted(bookId: Int, startedIso: String) {
        val existing = getBook(bookId) ?: return
        val values = ContentValues().apply {
            put("started_at", existing.startedAt.ifBlank { startedIso })
            put("last_read_at", startedIso)
            if (existing.status == BookStatus.WISHLIST || existing.status == BookStatus.PAUSED) {
                put("status", BookStatus.READING.name)
            }
        }
        writableDatabase.update("books", values, "id = ?", arrayOf(bookId.toString()))
    }

    fun addReadingSession(
        bookId: Int,
        startedIso: String,
        endedIso: String,
        durationSeconds: Long,
        pagesRead: Int = 0,
        pageReached: Int = 0
    ) {
        val safeDuration = durationSeconds.coerceAtLeast(1L)
        writableDatabase.beginTransaction()
        try {
            writableDatabase.insertOrThrow(
                "reading_sessions",
                null,
                ContentValues().apply {
                    put("book_id", bookId)
                    put("started_at", startedIso)
                    put("ended_at", endedIso)
                    put("duration_seconds", safeDuration)
                    put("duration_milliseconds", safeDuration * 1000)
                    put("pages_read", pagesRead.coerceAtLeast(0))
                    put("page_reached", pageReached.coerceAtLeast(0))
                }
            )
            val existing = getBook(bookId)
            val values = ContentValues().apply {
                put("reading_seconds", (existing?.readingSeconds ?: 0L) + safeDuration)
                if (pageReached > 0) put("current_page", pageReached.coerceAtMost(existing?.pageCount?.takeIf { it > 0 } ?: Int.MAX_VALUE))
                put("last_read_at", endedIso)
                if (existing != null && existing.pageCount > 0 && pageReached >= existing.pageCount) {
                    put("status", BookStatus.FINISHED.name)
                    put("finished_at", endedIso)
                } else if (existing?.status != BookStatus.FINISHED) {
                    put("status", BookStatus.READING.name)
                }
            }
            check(writableDatabase.update("books", values, "id = ?", arrayOf(bookId.toString())) == 1) {
                "The active book no longer exists."
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun addReadingNote(
        bookId: Int,
        createdAt: String,
        page: Int,
        durationSeconds: Long,
        note: String,
        kind: BookNoteKind = BookNoteKind.NOTE
    ) {
        writableDatabase.insertOrThrow(
            "reading_notes",
            null,
            ContentValues().apply {
                put("book_id", bookId)
                put("created_at", createdAt)
                put("page", page.coerceAtLeast(0))
                put("duration_seconds", durationSeconds.coerceAtLeast(0L))
                put("kind", kind.name)
                put("note", note)
            }
        )
    }

    fun saveLiveReadingNote(key: String, bookId: Int, createdAt: String, page: Int, durationSeconds: Long, note: String, kind: BookNoteKind = BookNoteKind.NOTE) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id = db.rawQuery("SELECT id FROM reading_notes WHERE autosave_key = ? AND book_id = ?", arrayOf(key, bookId.toString())).use { if (it.moveToFirst()) it.getInt(0) else null }
            val values = ContentValues().apply {
                put("book_id", bookId); put("created_at", createdAt); put("page", page.coerceAtLeast(0))
                put("duration_seconds", durationSeconds); put("kind", kind.name); put("note", note); put("autosave_key", key)
            }
            if (id != null) db.update("reading_notes", values, "id = ?", arrayOf(id.toString()))
            else if (note.isNotBlank()) db.insertOrThrow("reading_notes", null, values)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun discardLiveReadingNote(key: String) {
        writableDatabase.delete("reading_notes", "autosave_key = ?", arrayOf(key))
    }

    fun updateReadingNote(noteId: Int, createdAt: String, page: Int, note: String) {
        val updated = writableDatabase.update(
            "reading_notes",
            ContentValues().apply {
                put("created_at", createdAt)
                put("page", page.coerceAtLeast(0))
                put("note", note)
            },
            "id = ?",
            arrayOf(noteId.toString())
        )
        check(updated == 1) { "Note no longer exists." }
    }

    fun deleteReadingNote(noteId: Int) {
        writableDatabase.delete(
            "reading_notes",
            "id = ?",
            arrayOf(noteId.toString())
        )
    }

    fun listNotes(bookId: Int): List<BookNote> {
        readableDatabase.rawQuery(
            """
            SELECT id, book_id, created_at, page, duration_seconds, kind, note
            FROM reading_notes
            WHERE book_id = ?
            ORDER BY created_at DESC, id DESC
            """.trimIndent(),
            arrayOf(bookId.toString())
        ).use { cursor ->
            val rows = mutableListOf<BookNote>()
            while (cursor.moveToNext()) {
                rows += BookNote(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                    bookId = cursor.getInt(cursor.getColumnIndexOrThrow("book_id")),
                    createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at")).orEmpty(),
                    page = cursor.getInt(cursor.getColumnIndexOrThrow("page")).coerceAtLeast(0),
                    durationSeconds = cursor.getLong(cursor.getColumnIndexOrThrow("duration_seconds")).coerceAtLeast(0L),
                    kind = BookNoteKind.fromStorage(cursor.getString(cursor.getColumnIndexOrThrow("kind"))),
                    note = cursor.getString(cursor.getColumnIndexOrThrow("note")).orEmpty()
                )
            }
            return rows
        }
    }

    fun listAllNotes(): List<BookNoteWithBook> {
        readableDatabase.rawQuery(
            """
            SELECT rn.id, rn.book_id, rn.created_at, rn.page, rn.duration_seconds, rn.kind, rn.note,
                   b.title AS book_title, b.authors AS book_authors
            FROM reading_notes rn
            LEFT JOIN books b ON b.id = rn.book_id
            ORDER BY rn.created_at DESC, rn.id DESC
            """.trimIndent(),
            emptyArray()
        ).use { cursor ->
            val rows = mutableListOf<BookNoteWithBook>()
            while (cursor.moveToNext()) {
                val note = BookNote(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                    bookId = cursor.getInt(cursor.getColumnIndexOrThrow("book_id")),
                    createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at")).orEmpty(),
                    page = cursor.getInt(cursor.getColumnIndexOrThrow("page")).coerceAtLeast(0),
                    durationSeconds = cursor.getLong(cursor.getColumnIndexOrThrow("duration_seconds")).coerceAtLeast(0L),
                    kind = BookNoteKind.fromStorage(cursor.getString(cursor.getColumnIndexOrThrow("kind"))),
                    note = cursor.getString(cursor.getColumnIndexOrThrow("note")).orEmpty()
                )
                rows += BookNoteWithBook(
                    note = note,
                    bookTitle = cursor.getString(cursor.getColumnIndexOrThrow("book_title")).orEmpty(),
                    bookAuthors = cursor.getString(cursor.getColumnIndexOrThrow("book_authors")).orEmpty()
                )
            }
            return rows
        }
    }

    fun listSessions(bookId: Int): List<BookReadingSessionRow> {
        readableDatabase.rawQuery(
            """
            SELECT id, book_id, started_at, ended_at, duration_seconds, duration_milliseconds, pages_read, page_reached, exclude_from_statistics
            FROM reading_sessions
            WHERE book_id = ?
            ORDER BY COALESCE(NULLIF(ended_at, ''), started_at) DESC, id DESC
            """.trimIndent(),
            arrayOf(bookId.toString())
        ).use { cursor ->
            val rows = mutableListOf<BookReadingSessionRow>()
            while (cursor.moveToNext()) {
                rows += BookReadingSessionRow(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                    bookId = cursor.getInt(cursor.getColumnIndexOrThrow("book_id")),
                    startedAt = cursor.getString(cursor.getColumnIndexOrThrow("started_at")).orEmpty(),
                    endedAt = cursor.getString(cursor.getColumnIndexOrThrow("ended_at")).orEmpty(),
                    durationSeconds = cursor.getLong(cursor.getColumnIndexOrThrow("duration_seconds")).coerceAtLeast(0L),
                    pagesRead = cursor.getInt(cursor.getColumnIndexOrThrow("pages_read")).coerceAtLeast(0),
                    pageReached = cursor.getInt(cursor.getColumnIndexOrThrow("page_reached")).coerceAtLeast(0),
                    durationMilliseconds = cursor.getColumnIndexOrThrow("duration_milliseconds").let { if (cursor.isNull(it)) null else cursor.getLong(it) },
                    excludeFromStatistics = cursor.getInt(cursor.getColumnIndexOrThrow("exclude_from_statistics")) != 0
                )
            }
            return rows
        }
    }

    fun listAllSessions(): List<BookReadingSessionWithBook> {
        readableDatabase.rawQuery(
            """
            SELECT rs.id, rs.book_id, rs.started_at, rs.ended_at, rs.duration_seconds, rs.duration_milliseconds, rs.pages_read, rs.page_reached, rs.exclude_from_statistics,
                   b.title AS book_title
            FROM reading_sessions rs
            LEFT JOIN books b ON b.id = rs.book_id
            ORDER BY COALESCE(NULLIF(rs.ended_at, ''), rs.started_at) DESC, rs.id DESC
            """.trimIndent(),
            emptyArray()
        ).use { cursor ->
            val rows = mutableListOf<BookReadingSessionWithBook>()
            while (cursor.moveToNext()) {
                rows += BookReadingSessionWithBook(
                    session = BookReadingSessionRow(
                        id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                        bookId = cursor.getInt(cursor.getColumnIndexOrThrow("book_id")),
                        startedAt = cursor.getString(cursor.getColumnIndexOrThrow("started_at")).orEmpty(),
                        endedAt = cursor.getString(cursor.getColumnIndexOrThrow("ended_at")).orEmpty(),
                        durationSeconds = cursor.getLong(cursor.getColumnIndexOrThrow("duration_seconds")).coerceAtLeast(0L),
                        pagesRead = cursor.getInt(cursor.getColumnIndexOrThrow("pages_read")).coerceAtLeast(0),
                        pageReached = cursor.getInt(cursor.getColumnIndexOrThrow("page_reached")).coerceAtLeast(0),
                    durationMilliseconds = cursor.getColumnIndexOrThrow("duration_milliseconds").let { if (cursor.isNull(it)) null else cursor.getLong(it) },
                    excludeFromStatistics = cursor.getInt(cursor.getColumnIndexOrThrow("exclude_from_statistics")) != 0
                    ),
                    bookTitle = cursor.getString(cursor.getColumnIndexOrThrow("book_title")).orEmpty()
                )
            }
            return rows
        }
    }

    fun setSessionStatisticsExcluded(sessionId: Int, excluded: Boolean) {
        writableDatabase.update("reading_sessions", ContentValues().apply { put("exclude_from_statistics", if (excluded) 1 else 0) }, "id = ?", arrayOf(sessionId.toString()))
    }

    fun updateReadingSession(
        sessionId: Int,
        bookId: Int,
        startedIso: String,
        endedIso: String,
        durationSeconds: Long,
        pagesRead: Int,
        pageReached: Int,
        excludeFromStatistics: Boolean,
        preserveProgress: Boolean = false
    ) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.update(
                "reading_sessions",
                ContentValues().apply {
                    put("exclude_from_statistics", if (excludeFromStatistics) 1 else 0)
                    put("started_at", startedIso)
                    put("ended_at", endedIso)
                    put("duration_seconds", durationSeconds.coerceAtLeast(1L))
                    put("duration_milliseconds", durationSeconds.coerceAtLeast(1L) * 1000)
                    put("pages_read", pagesRead.coerceAtLeast(0))
                    put("page_reached", pageReached.coerceAtLeast(0))
                },
                "id = ?",
                arrayOf(sessionId.toString())
            )
            recalculateBookReadingProgress(bookId, preserveProgress)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun inferPagesRead(
        bookId: Int,
        endedIso: String,
        pageReached: Int,
        excludeSessionId: Int? = null
    ): Int {
        val safeReached = pageReached.coerceAtLeast(0)
        val previousPage = readableDatabase.rawQuery(
            buildString {
                append(
                    """
                    SELECT MAX(page_reached) AS previous_page
                    FROM reading_sessions
                    WHERE book_id = ? AND ended_at < ?
                    """.trimIndent()
                )
                if (excludeSessionId != null) append(" AND id != ?")
            },
            if (excludeSessionId != null) {
                arrayOf(bookId.toString(), endedIso, excludeSessionId.toString())
            } else {
                arrayOf(bookId.toString(), endedIso)
            }
        ).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(cursor.getColumnIndexOrThrow("previous_page"))) {
                cursor.getInt(cursor.getColumnIndexOrThrow("previous_page")).coerceAtLeast(0)
            } else {
                0
            }
        }
        return (safeReached - previousPage).coerceAtLeast(0)
    }

    fun deleteReadingSession(sessionId: Int, bookId: Int) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete(
                "reading_sessions",
                "id = ?",
                arrayOf(sessionId.toString())
            )
            recalculateBookReadingProgress(bookId)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    private fun recalculateBookReadingProgress(bookId: Int, preserveProgress: Boolean = false) {
        val book = getBook(bookId) ?: return
        var totalSeconds = 0L
        var maxPageReached = book.currentPage
        var lastReadAt = book.lastReadAt
        readableDatabase.rawQuery(
            """
            SELECT SUM(COALESCE(duration_milliseconds, duration_seconds * 1000)) / 1000 AS seconds,
                   MAX(page_reached) AS max_page,
                   MAX(COALESCE(NULLIF(ended_at, ''), started_at)) AS last_read
            FROM reading_sessions
            WHERE book_id = ?
            """.trimIndent(),
            arrayOf(bookId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                totalSeconds = cursor.getLong(cursor.getColumnIndexOrThrow("seconds")).coerceAtLeast(0L)
                maxPageReached = cursor.getInt(cursor.getColumnIndexOrThrow("max_page")).coerceAtLeast(0)
                lastReadAt = cursor.getString(cursor.getColumnIndexOrThrow("last_read")).orEmpty()
            }
        }
        val safePage = (if (preserveProgress) book.currentPage else maxPageReached).coerceIn(0, book.pageCount.takeIf { it > 0 } ?: Int.MAX_VALUE)
        writableDatabase.update(
            "books",
            ContentValues().apply {
                put("reading_seconds", totalSeconds)
                put("current_page", safePage)
                put("last_read_at", lastReadAt)
                if (book.pageCount > 0 && safePage >= book.pageCount) {
                    put("status", BookStatus.FINISHED.name)
                    put("finished_at", book.finishedAt.ifBlank { lastReadAt.ifBlank { Instant.now().toString() } })
                } else if (book.status == BookStatus.FINISHED) {
                    put("status", BookStatus.READING.name)
                    put("finished_at", "")
                }
            },
            "id = ?",
            arrayOf(bookId.toString())
        )
    }

    fun updateProgress(bookId: Int, currentPage: Int?, status: BookStatus?) {
        val existing = getBook(bookId) ?: return
        val nextStatus = status ?: existing.status
        val page = currentPage
            ?.coerceIn(0, existing.pageCount.takeIf { it > 0 } ?: Int.MAX_VALUE)
            ?: existing.currentPage
        val now = Instant.now().toString()
        val values = ContentValues().apply {
            put("current_page", page)
            put("status", nextStatus.name)
            put("last_read_at", now)
            if (nextStatus == BookStatus.FINISHED) {
                put("finished_at", existing.finishedAt.ifBlank { now })
            } else {
                put("finished_at", "")
            }
            if (nextStatus == BookStatus.READING && existing.startedAt.isBlank()) {
                put("started_at", now)
            }
        }
        writableDatabase.update("books", values, "id = ?", arrayOf(bookId.toString()))
    }

    fun setCompletionDate(bookId: Int, date: LocalDate) {
        writableDatabase.update("books", ContentValues().apply {
            put("finished_at", date.atStartOfDay(ZoneId.systemDefault()).toInstant().toString())
            put("finished_at_manual", 1)
        }, "id = ?", arrayOf(bookId.toString()))
    }

    fun updateRating(bookId: Int, rating: Int) {
        writableDatabase.update(
            "books",
            ContentValues().apply { put("rating", rating.coerceIn(0, 5)) },
            "id = ?",
            arrayOf(bookId.toString())
        )
    }

    fun updateCover(bookId: Int, coverUrl: String) {
        writableDatabase.update(
            "books",
            ContentValues().apply { put("cover_url", coverUrl) },
            "id = ?",
            arrayOf(bookId.toString())
        )
    }

    fun updateNotes(bookId: Int, notes: String) {
        writableDatabase.update(
            "books",
            ContentValues().apply { put("notes", notes) },
            "id = ?",
            arrayOf(bookId.toString())
        )
    }

    fun togglePinned(bookId: Int) {
        val book = getBook(bookId) ?: return
        writableDatabase.update(
            "books",
            ContentValues().apply { put("pinned", if (book.pinned) 0 else 1) },
            "id = ?",
            arrayOf(bookId.toString())
        )
    }

    fun deleteBook(bookId: Int) {
        writableDatabase.delete("reading_notes", "book_id = ?", arrayOf(bookId.toString()))
        writableDatabase.delete("reading_sessions", "book_id = ?", arrayOf(bookId.toString()))
        writableDatabase.delete("books", "id = ?", arrayOf(bookId.toString()))
    }

    private fun getBook(bookId: Int): BookRow? {
        readableDatabase.rawQuery("SELECT * FROM books WHERE id = ?", arrayOf(bookId.toString())).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toBookRow() else null
        }
    }

    private fun findBookByIsbn(isbn: String): BookRow? {
        readableDatabase.rawQuery("SELECT * FROM books WHERE isbn = ? LIMIT 1", arrayOf(isbn)).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toBookRow() else null
        }
    }

    private fun android.database.Cursor.toBookRow(): BookRow {
        return BookRow(
            id = getInt(getColumnIndexOrThrow("id")),
            isbn = getString(getColumnIndexOrThrow("isbn")).orEmpty(),
            title = getString(getColumnIndexOrThrow("title")).orEmpty().ifBlank { "Untitled book" },
            authors = getString(getColumnIndexOrThrow("authors")).orEmpty(),
            pageCount = getInt(getColumnIndexOrThrow("page_count")).coerceAtLeast(0),
            currentPage = getInt(getColumnIndexOrThrow("current_page")).coerceAtLeast(0),
            status = BookStatus.fromStorage(getString(getColumnIndexOrThrow("status"))),
            rating = getInt(getColumnIndexOrThrow("rating")).coerceIn(0, 5),
            notes = getString(getColumnIndexOrThrow("notes")).orEmpty(),
            coverUrl = getString(getColumnIndexOrThrow("cover_url")).orEmpty(),
            sourceUrl = getString(getColumnIndexOrThrow("source_url")).orEmpty(),
            collections = getString(getColumnIndexOrThrow("collections")).orEmpty(),
            pinned = getInt(getColumnIndexOrThrow("pinned")) != 0,
            addedAt = getString(getColumnIndexOrThrow("added_at")).orEmpty(),
            startedAt = getString(getColumnIndexOrThrow("started_at")).orEmpty(),
            finishedAt = getString(getColumnIndexOrThrow("finished_at")).orEmpty(),
            lastReadAt = getString(getColumnIndexOrThrow("last_read_at")).orEmpty(),
            readingSeconds = getLong(getColumnIndexOrThrow("reading_seconds")).coerceAtLeast(0L)
        )
    }
}
