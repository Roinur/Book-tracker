package com.roinur.booktracker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal const val BOOK_TRACKER_DB = "book_tracker.db"
internal const val BOOK_TRACKER_PREFS = "book_tracker_prefs"
internal const val KEY_BOOK_ACTIVE_ID = "book_active_id"
internal const val KEY_BOOK_ACTIVE_STARTED_MS = "book_active_started_ms"
internal const val KEY_BOOK_ACTIVE_PAUSED_AT_MS = "book_active_paused_at_ms"
internal const val KEY_BOOK_ACTIVE_PAUSED_TOTAL_MS = "book_active_paused_total_ms"
internal const val KEY_BOOK_ACTIVE_TITLE = "book_active_title"
internal const val KEY_BOOK_ACTIVE_AUTHORS = "book_active_authors"
internal const val KEY_BOOK_ACTIVE_PAGE = "book_active_page"
internal const val KEY_BOOK_ACTIVE_PAGE_COUNT = "book_active_page_count"
internal const val KEY_BOOK_COUNTDOWN_END_MS = "book_countdown_end_ms"
internal const val KEY_BOOK_COUNTDOWN_TOTAL_MS = "book_countdown_total_ms"
internal const val KEY_BOOK_COUNTDOWN_DONE_MS = "book_countdown_done_ms"
internal const val KEY_BOOK_GALLERY_MODE = "book_gallery_mode"
internal const val KEY_BOOK_GALLERY_COLUMNS = "book_gallery_columns"
internal const val KEY_BOOK_SORT_FIELD = "book_sort_field"
internal const val KEY_BOOK_SORT_DESC = "book_sort_desc"
internal const val KEY_BOOK_LIBRARY_FILTER = "book_library_filter"
internal const val KEY_BOOK_AUTO_BACKUP_TREE_URI = "book_auto_backup_tree_uri"
internal const val KEY_BOOK_AUTO_BACKUP_ENABLED = "book_auto_backup_enabled"
internal val ISBN_CANDIDATE_PATTERN = Regex("(?i)(?:97[89][\\-\\s]?)?[0-9][0-9xX\\-\\s]{8,20}")

