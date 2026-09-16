package com.roinur.booktracker.data.stats

import com.roinur.booktracker.data.database.BookTrackerDatabase

import com.roinur.booktracker.*
import java.time.LocalDate

/** Read-only statistics projection; never changes book or session records. */
internal class BookStatsRepository(private val database: BookTrackerDatabase) {
    fun loadStats(): BookStats {
        val allBooks = database.listBooks("", BookSortField.ADDED, true)
        val sessions = database.listAllSessions().map { it.session }
        val points = bookDailyActivity(sessions, pages = false)
        val pagePoints = bookDailyActivity(sessions, pages = true)
        val sessionCount = database.readableDatabase.rawQuery(
            "SELECT COUNT(*) AS count FROM reading_sessions",
            emptyArray()
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(cursor.getColumnIndexOrThrow("count")).coerceAtLeast(0) else 0
        }
        val noteCounts = mutableMapOf<BookNoteKind, Int>()
        database.readableDatabase.rawQuery(
            """
            SELECT COALESCE(kind, ?) AS kind, COUNT(*) AS count
            FROM reading_notes
            GROUP BY kind
            """.trimIndent(),
            arrayOf(BookNoteKind.NOTE.name)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val kind = BookNoteKind.fromStorage(cursor.getString(cursor.getColumnIndexOrThrow("kind")))
                noteCounts[kind] = cursor.getInt(cursor.getColumnIndexOrThrow("count")).coerceAtLeast(0)
            }
        }
        val rated = allBooks.filter { it.rating > 0 }
        return BookStats(
            totalBooks = allBooks.size,
            wishlistBooks = allBooks.count { it.status == BookStatus.WISHLIST },
            readingBooks = allBooks.count { it.status == BookStatus.READING || it.status == BookStatus.PAUSED },
            finishedBooks = allBooks.count { it.status == BookStatus.FINISHED },
            finishedThisYear = allBooks.count {
                bookCompletionYear(it.finishedAt) == LocalDate.now().year
            },
            totalPages = allBooks.sumOf { it.pageCount.coerceAtLeast(0) },
            pagesRead = allBooks.sumOf { book ->
                book.currentPage.coerceAtLeast(0)
            },
            readingSeconds = LegacyMigration.totalReadingSeconds(database.readableDatabase),
            averageRating = if (rated.isEmpty()) 0f else rated.sumOf { it.rating }.toFloat() / rated.size.toFloat(),
            readingSessionCount = sessionCount,
            noteCount = noteCounts[BookNoteKind.NOTE] ?: 0,
            quoteCount = noteCounts[BookNoteKind.QUOTE] ?: 0,
            thoughtCount = noteCounts[BookNoteKind.THOUGHT] ?: 0,
            wordCount = noteCounts[BookNoteKind.WORD] ?: 0,
            dailyActivity = points,
            dailyPageActivity = pagePoints
        )
    }

}
