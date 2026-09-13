package com.roinur.booktracker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

enum class BookTrackerTab(val label: String, val symbol: String) {
    STATS("Stats", "stats"),
    LIBRARY("Library", "library")
}

internal enum class BookScreenMode {
    MAIN,
    ADD,
    DETAIL,
    READING,
    SESSION_NOTE,
    TRENDS
}

internal enum class BookCoverTarget {
    DRAFT,
    SELECTED
}

internal enum class BookStatsRange(val label: String) {
    WEEK("Week"),
    MONTH("Month"),
    YEAR("Year"),
    ALL_TIME("All time")
}

internal enum class BookGraphMetric(val label: String) {
    TIME("Time"),
    PAGES("Pages")
}

enum class BookNoteKind(val label: String, val buttonLabel: String, val prompt: String) {
    NOTE("Note", "Reading notes", "What do you want to remember?"),
    QUOTE("Quote", "Quotes", "Paste or type the quote."),
    THOUGHT("Thought", "Thoughts", "What did this make you think?"),
    WORD("Word", "Words", "Add a word, phrase, or definition."),
    COUNTDOWN("Countdown", "Countdowns", "What should this countdown remind you of?");

    companion object {
        fun fromStorage(raw: String?): BookNoteKind {
            return entries.firstOrNull { it.name == raw } ?: NOTE
        }
    }
}

enum class BookStatus(val label: String) {
    WISHLIST("Not started"),
    READING("Reading"),
    PAUSED("On hold"),
    FINISHED("Finished");

    companion object {
        fun fromStorage(raw: String?): BookStatus {
            return entries.firstOrNull { it.name == raw } ?: READING
        }
    }
}

enum class BookSortField(val label: String) {
    ADDED("Added"),
    TITLE("Title"),
    AUTHOR("Author"),
    RATING("Rating"),
    PROGRESS("Progress")
}

data class BookSeed(
    val isbn: String,
    val title: String,
    val authors: String,
    val pageCount: Int,
    val coverUrl: String,
    val sourceUrl: String,
    val collections: String
)

data class BookRow(
    val id: Int,
    val isbn: String,
    val title: String,
    val authors: String,
    val pageCount: Int,
    val currentPage: Int,
    val status: BookStatus,
    val rating: Int,
    val notes: String,
    val coverUrl: String,
    val sourceUrl: String,
    val collections: String,
    val pinned: Boolean,
    val addedAt: String,
    val startedAt: String,
    val finishedAt: String,
    val lastReadAt: String,
    val readingSeconds: Long
) {
    val progressFraction: Float
        get() = if (pageCount <= 0) 0f else (currentPage.toFloat() / pageCount.toFloat()).coerceIn(0f, 1f)
}

data class BookNote(
    val id: Int,
    val bookId: Int,
    val createdAt: String,
    val page: Int,
    val durationSeconds: Long,
    val kind: BookNoteKind,
    val note: String
)

data class BookNoteWithBook(
    val note: BookNote,
    val bookTitle: String,
    val bookAuthors: String
)

data class BookReadingSessionRow(
    val id: Int,
    val bookId: Int,
    val startedAt: String,
    val endedAt: String,
    val durationSeconds: Long,
    val pagesRead: Int,
    val pageReached: Int,
    val durationMilliseconds: Long? = null,
    val excludeFromStatistics: Boolean = false
) {
    val activityAt: String get() = endedAt.ifBlank { startedAt }
}

data class BookReadingSessionWithBook(
    val session: BookReadingSessionRow,
    val bookTitle: String
)

internal data class BookDraftSaveResult(
    val bookId: Int,
    val title: String
)

data class BookFinishedSession(
    val bookId: Int,
    val page: Int,
    val durationSeconds: Long,
    val endedAt: String
)

data class BookStats(
    val totalBooks: Int = 0,
    val wishlistBooks: Int = 0,
    val readingBooks: Int = 0,
    val finishedBooks: Int = 0,
    val finishedThisYear: Int = 0,
    val totalPages: Int = 0,
    val pagesRead: Int = 0,
    val readingSeconds: Long = 0L,
    val averageRating: Float = 0f,
    val readingSessionCount: Int = 0,
    val noteCount: Int = 0,
    val quoteCount: Int = 0,
    val thoughtCount: Int = 0,
    val wordCount: Int = 0,
    val dailyActivity: List<DailyActivityPoint> = emptyList(),
    val dailyPageActivity: List<DailyActivityPoint> = emptyList()
)

enum class BookGoalMetric(val label: String) { TIME("Minutes"), PAGES("Pages") }

data class BookReadingGoals(
    val dailyMinutes: Int = 0,
    val dailyPages: Int = 0,
    val yearlyBooks: Int = 0,
    val monthlyMinutes: Int = 0,
    val monthlyPages: Int = 0,
    val dailyMetric: BookGoalMetric = if (dailyPages > 0 && dailyMinutes == 0) BookGoalMetric.PAGES else BookGoalMetric.TIME,
    val monthlyMetric: BookGoalMetric = BookGoalMetric.PAGES
)

data class CoverImageResult(
    val title: String,
    val imageUrl: String
)

data class BookRatingHoldState(
    val bookId: Int,
    val rating: Int
)
