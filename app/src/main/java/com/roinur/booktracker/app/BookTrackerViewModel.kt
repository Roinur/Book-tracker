package com.roinur.booktracker

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BookTrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val db = BookTrackerDatabase(application)
    suspend fun trendTargets(kind: TrendTargetKind, includeMisc: Boolean): List<TrendTarget> = withContext(Dispatchers.IO) {
        BookTrendData(db.listBooks("", BookSortField.ADDED, false), emptyList()).targets(kind)
    }
    suspend fun trendSnapshot(request: TrendRequest): TrendSnapshot = withContext(Dispatchers.IO) {
        BookTrendData(db.listBooks("", BookSortField.ADDED, false), db.listAllSessions().filterNot { it.session.excludeFromStatistics }).snapshot(request)
    }
    private val api = BookLookupClient()
    private val prefs = application.getSharedPreferences(BOOK_TRACKER_PREFS, Context.MODE_PRIVATE)

    var themeMode by mutableStateOf(loadThemeMode())
        private set
    var accentMode by mutableStateOf(loadAccentMode())
        private set
    var isbnInput by mutableStateOf("")
        private set
    var titleInput by mutableStateOf("")
        private set
    var authorsInput by mutableStateOf("")
        private set
    var pageCountInput by mutableStateOf("")
        private set
    var draftCoverUrl by mutableStateOf("")
        private set
    var collectionsInput by mutableStateOf("")
        private set
    var draftSourceUrl by mutableStateOf("")
        private set
    var editingBookId by mutableStateOf<Int?>(null)
        private set
    var searchInput by mutableStateOf("")
        private set
    var statusMessage by mutableStateOf("Ready.")
        private set
    var fetching by mutableStateOf(false)
        private set
    var statsBooks by mutableStateOf<List<BookRow>>(emptyList())
        private set

    var finishedYearBooks by mutableStateOf<List<BookRow>>(emptyList())
        private set

    var books by mutableStateOf<List<BookRow>>(emptyList())
        private set
    var collectionSuggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var coverSearchQuery by mutableStateOf("")
        private set
    var coverSearchResults by mutableStateOf<List<CoverImageResult>>(emptyList())
        private set
    var coverSearchLoading by mutableStateOf(false)
        private set
    var stats by mutableStateOf(BookStats())
        private set
    var readingGoals by mutableStateOf(BookReadingGoals())
        private set
    var selectedBookId by mutableStateOf<Int?>(null)
        private set
    var selectedBookNotes by mutableStateOf<List<BookNote>>(emptyList())
        private set
    var selectedBookSessions by mutableStateOf<List<BookReadingSessionRow>>(emptyList())
        private set
    var allBookNotes by mutableStateOf<List<BookNoteWithBook>>(emptyList())
        private set
    var allBookSessions by mutableStateOf<List<BookReadingSessionWithBook>>(emptyList())
        private set
    var lastFinishedSession by mutableStateOf<BookFinishedSession?>(runCatching {
        val json = JSONObject(prefs.getString("pending_session_note", "").orEmpty())
        BookFinishedSession(json.getInt("bookId"), json.getInt("page"), json.getLong("seconds"), json.getString("endedAt"))
    }.getOrNull())
        private set
    var activeBookId by mutableStateOf(loadActiveBookId())
        private set
    var activeStartedAtMs by mutableStateOf(loadActiveStartedAtMs())
        private set
    var activePausedAtMs by mutableStateOf(loadActivePausedAtMs())
        private set
    var activePausedTotalMs by mutableStateOf(loadActivePausedTotalMs())
        private set
    var countdownEndMs by mutableStateOf(loadCountdownEndMs())
        private set
    var countdownTotalMs by mutableStateOf(loadCountdownTotalMs())
        private set
    var galleryMode by mutableStateOf(prefs.getBoolean(KEY_BOOK_GALLERY_MODE, false))
        private set
    var galleryColumns by mutableStateOf(prefs.getInt(KEY_BOOK_GALLERY_COLUMNS, 2).coerceIn(1, 5))
        private set
    var sortField by mutableStateOf(loadSortField())
        private set
    var sortDescending by mutableStateOf(prefs.getBoolean(KEY_BOOK_SORT_DESC, true))
        private set
    var libraryFilter by mutableStateOf(BookLibraryFilter.fromStorage(prefs.getString(KEY_BOOK_LIBRARY_FILTER, null)))
        private set
    var pendingLegacyImport by mutableStateOf<LegacyImportPreview?>(null)
        private set
    var pendingTrackerImport by mutableStateOf<TrackerBackupPreview?>(null)
        private set
    var importBusy by mutableStateOf(false)
        private set
    var preservedLegacyArchives by mutableStateOf<List<LegacyArchiveInfo>>(emptyList())
        private set

    var bookAutoBackupTreeUri by mutableStateOf(prefs.getString(KEY_BOOK_AUTO_BACKUP_TREE_URI, "").orEmpty())
        private set
    var bookAutoBackupEnabled by mutableStateOf(prefs.getBoolean(KEY_BOOK_AUTO_BACKUP_ENABLED, false))
        private set

    init {
        reload()
    }

    fun reload() {
        statsBooks = db.listBooks("", BookSortField.ADDED, false)
        finishedYearBooks = statsBooks.filter { bookCompletionYear(it.finishedAt) == LocalDate.now().year }.sortedByDescending { it.finishedAt }
        preservedLegacyArchives = db.legacyArchives()
        books = db.listBooks(searchInput, sortField, sortDescending)
        stats = db.loadStats()
        readingGoals = db.loadReadingGoals()
        collectionSuggestions = db.listCollections()
        if (selectedBookId != null && statsBooks.none { it.id == selectedBookId }) {
            selectedBookId = null
        }
        selectedBookNotes = selectedBookId?.let { db.listNotes(it) }.orEmpty()
        selectedBookSessions = selectedBookId?.let { db.listSessions(it) }.orEmpty()
        allBookNotes = db.listAllNotes()
        allBookSessions = db.listAllSessions()
        if (activeBookId != null && statsBooks.none { it.id == activeBookId }) {
            clearActiveBook()
        } else if (activeBookId != null && activeStartedAtMs <= 0L) {
            activeStartedAtMs = System.currentTimeMillis()
            persistActiveBook()
            syncReadingTimerNotification()
        } else if (activeBookId != null) {
            persistActiveBook()
            syncReadingTimerNotification()
        }
    }

    fun updateIsbnInput(value: String) {
        isbnInput = value
    }

    fun updateTitleInput(value: String) {
        titleInput = value
    }

    fun updateAuthorsInput(value: String) {
        authorsInput = value
    }

    fun updatePageCountInput(value: String) {
        pageCountInput = value.filter { it.isDigit() }.take(6)
    }

    fun updateDraftCover(value: String) {
        draftCoverUrl = value
    }

    fun updateCollectionsInput(value: String) {
        collectionsInput = value
    }

    fun addCollectionToDraft(collection: String) {
        val next = normalizeBookCollections(
            (splitBookCollections(collectionsInput) + collection.trim()).joinToString(", ")
        )
        collectionsInput = next
    }

    fun removeCollectionFromDraft(collection: String) {
        collectionsInput = splitBookCollections(collectionsInput)
            .filterNot { it.equals(collection, ignoreCase = true) }
            .joinToString(", ")
    }

    fun updateCoverSearchQuery(value: String) {
        coverSearchQuery = value
    }

    fun searchAuthor(author: String) {
        libraryFilter = BookLibraryFilter.ALL
        prefs.edit().putString(KEY_BOOK_LIBRARY_FILTER, libraryFilter.name).apply()
        updateSearch(author)
    }

    fun updateSearch(value: String) {
        searchInput = value
        reload()
    }

    fun applyCollectionSuggestion(collection: String) {
        searchInput = if (searchInput.equals(collection, ignoreCase = true)) "" else collection
        reload()
    }

    fun cycleThemeMode() {
        themeMode = when (themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.SYSTEM
        }
        prefs.edit().putString(TRACKER_KEY_THEME_MODE, themeMode.name).apply()
    }

    fun chooseAccentMode(mode: AccentMode) {
        accentMode = mode
        prefs.edit().putString(TRACKER_KEY_ACCENT_MODE, mode.name).apply()
    }

    fun toggleGalleryMode() {
        galleryMode = !galleryMode
        prefs.edit().putBoolean(KEY_BOOK_GALLERY_MODE, galleryMode).apply()
    }

    fun cycleLibraryFilter() {
        libraryFilter = libraryFilter.next()
        prefs.edit().putString(KEY_BOOK_LIBRARY_FILTER, libraryFilter.name).apply()
    }

    fun updateGalleryColumns(columns: Int) {
        galleryColumns = columns.coerceIn(1, 5)
        prefs.edit().putInt(KEY_BOOK_GALLERY_COLUMNS, galleryColumns).apply()
    }

    fun toggleSort(field: BookSortField) {
        if (sortField == field) {
            sortDescending = !sortDescending
        } else {
            sortField = field
            sortDescending = field != BookSortField.TITLE && field != BookSortField.AUTHOR
        }
        prefs.edit()
            .putString(KEY_BOOK_SORT_FIELD, sortField.name)
            .putBoolean(KEY_BOOK_SORT_DESC, sortDescending)
            .apply()
        reload()
    }

    fun selectBook(bookId: Int?) {
        selectedBookId = bookId
        selectedBookNotes = bookId?.let { db.listNotes(it) }.orEmpty()
        selectedBookSessions = bookId?.let { db.listSessions(it) }.orEmpty()
    }

    fun queueIncomingText(raw: String) {
        val isbn = extractIsbn(raw)
        if (isbn == null) {
            statusMessage = "Shared text did not contain an ISBN."
            return
        }
        isbnInput = isbn
        fetchIsbn()
    }

    fun applyScannedIsbn(raw: String?) {
        val isbn = extractIsbn(raw.orEmpty())
        if (isbn == null) {
            statusMessage = "Scanner returned no ISBN."
            return
        }
        isbnInput = isbn
        fetchIsbn()
    }

    fun prepareNewBook() {
        clearDraft()
    }

    fun prepareEditBook(book: BookRow) {
        editingBookId = book.id
        isbnInput = book.isbn
        titleInput = book.title
        authorsInput = book.authors
        pageCountInput = book.pageCount.takeIf { it > 0 }?.toString().orEmpty()
        draftCoverUrl = book.coverUrl
        draftSourceUrl = book.sourceUrl
        collectionsInput = book.collections
        statusMessage = "Editing ${book.title}."
    }

    fun cancelBookDraft() {
        clearDraft()
    }

    fun fetchIsbn() {
        val isbn = normalizeIsbn(isbnInput)
        if (isbn == null) {
            statusMessage = "Enter a 10 or 13 digit ISBN first."
            return
        }
        if (fetching) return
        fetching = true
        statusMessage = "Fetching ISBN $isbn..."
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { api.lookupByIsbn(isbn) }.getOrNull()
            }
            if (result == null) {
                fetching = false
                statusMessage = "No metadata found for ISBN $isbn. Add it manually or edit the draft."
                if (titleInput.isBlank()) titleInput = "ISBN $isbn"
                return@launch
            }
            isbnInput = result.isbn
            titleInput = result.title
            authorsInput = result.authors
            pageCountInput = result.pageCount.takeIf { it > 0 }?.toString().orEmpty()
            draftCoverUrl = result.coverUrl
            draftSourceUrl = result.sourceUrl
            fetching = false
            statusMessage = "Found ${result.title.ifBlank { "ISBN $isbn" }}. Review and save."
        }
    }

    fun addManualBook(onSaved: (Int) -> Unit = {}) {
        val isbn = normalizeIsbn(isbnInput).orEmpty()
        val title = titleInput.trim().ifBlank {
            if (isbn.isNotBlank()) "ISBN $isbn" else "Untitled book"
        }
        val seed = BookSeed(
            isbn = isbn,
            title = title,
            authors = authorsInput.trim(),
            pageCount = pageCountInput.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            coverUrl = draftCoverUrl,
            sourceUrl = draftSourceUrl,
            collections = normalizeBookCollections(collectionsInput)
        )
        viewModelScope.launch {
            val editingId = editingBookId
            val id = withContext(Dispatchers.IO) { db.upsertBook(seed, editingId) }
            clearDraft()
            selectedBookId = id.takeIf { it > 0 }
            statusMessage = "Saved $title."
            reload()
            maybeAutoBackup()
            if (id > 0) onSaved(id)
        }
    }

    fun startReading(bookId: Int): Boolean {
        if (activeBookId == bookId) {
            resumeReading()
            return true
        }
        if (activeBookId != null) {
            statusMessage = "A reading session is already open. Finish it before starting another book."
            return false
        }
        activeBookId = bookId
        activeStartedAtMs = System.currentTimeMillis()
        activePausedAtMs = 0L
        activePausedTotalMs = 0L
        persistActiveBook()
        syncReadingTimerNotification()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.markStarted(bookId, Instant.now().toString()) }
            reload()
        }
        return true
    }

    fun finishReading(bookId: Int, pageText: String): Boolean {
        val page = pageText.toIntOrNull()
        if (page == null) {
            statusMessage = "Enter the page you reached."
            return false
        }
        val book = statsBooks.firstOrNull { it.id == bookId } ?: run {
            statusMessage = "Could not find the active book. Session is still running."
            return false
        }
        val safePage = page.coerceIn(0, book.pageCount.takeIf { it > 0 } ?: Int.MAX_VALUE)
        val pagesDelta = (safePage - book.currentPage).coerceAtLeast(0)
        val startedMs = activeStartedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()
        val endedMs = System.currentTimeMillis()
        val elapsed = activeReadingElapsedSeconds(endedMs).coerceAtLeast(1L)
        val started = Instant.ofEpochMilli(startedMs).toString()
        val ended = Instant.ofEpochMilli(endedMs).toString()
        val saved = runCatching {
                db.addReadingSession(bookId, started, ended, elapsed, pagesRead = pagesDelta, pageReached = safePage)
        }
        if (saved.isFailure) {
            statusMessage = "Could not save the session. It is still running: ${saved.exceptionOrNull()?.message ?: "database error"}."
            return false
        }
        clearActiveBook()
        lastFinishedSession = BookFinishedSession(bookId, safePage, elapsed, ended)
        prefs.edit().putString("pending_session_note", JSONObject().put("bookId", bookId).put("page", safePage).put("seconds", elapsed).put("endedAt", ended).toString()).commit()
        statusMessage = "Logged ${bookFormatDuration(elapsed)} to page $safePage."
        reload()
        return true
    }

    fun saveLastSessionNote(note: String): Boolean {
        val session = lastFinishedSession ?: run {
            statusMessage = "The reading session is saved, but there is no pending summary."
            return false
        }
        val trimmed = note.trim()
        val saved = runCatching {
            if (trimmed.isNotBlank()) {
                    db.saveLiveReadingNote(
                        key = "session_${session.endedAt}",
                        bookId = session.bookId,
                        createdAt = session.endedAt,
                        page = session.page,
                        durationSeconds = session.durationSeconds,
                        note = note,
                        kind = BookNoteKind.NOTE
                    )
            } else db.discardLiveReadingNote("session_${session.endedAt}")
        }
        if (saved.isFailure) {
            statusMessage = "Could not save the summary. Your text is still on screen: ${saved.exceptionOrNull()?.message ?: "database error"}."
            return false
        }
        statusMessage = if (trimmed.isBlank()) "Session saved." else "Session note saved."
        prefs.edit().remove("pending_session_note").commit()
        getApplication<Application>().getSharedPreferences("book_note_drafts", Context.MODE_PRIVATE).edit().remove("session_${session.endedAt}").commit()
        lastFinishedSession = null
        selectedBookId = session.bookId
        reload()
        maybeAutoBackup()
        return true
    }

    fun updateSessionNote(noteId: Int, dateText: String, pageText: String, text: String, onResult: (Boolean) -> Unit) {
        val original = selectedBookNotes.firstOrNull { it.id == noteId }
        val created = (if (original != null && dateText == bookFormatEditableDate(original.createdAt)) {
            runCatching { Instant.parse(original.createdAt) }.getOrNull()
        } else parseBookSessionEditDate(dateText)) ?: run {
            statusMessage = "Pick a valid note date."
            onResult(false)
            return
        }
        val page = pageText.toIntOrNull()?.coerceAtLeast(0) ?: 0
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { db.updateReadingNote(noteId, created.toString(), page, text) } }
                .onSuccess {
                    statusMessage = "Note updated."
                    reload()
                    maybeAutoBackup()
                    onResult(true)
                }.onFailure {
                    statusMessage = "Could not save note: ${it.message}."
                    onResult(false)
                }
        }
    }

    fun deleteSessionNote(note: BookNote) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.deleteReadingNote(note.id) }
            getApplication<Application>().getSharedPreferences("book_note_drafts", Context.MODE_PRIVATE).edit().remove("edit_${note.id}").commit()
            statusMessage = "${note.kind.label} deleted."
            reload()
            maybeAutoBackup()
        }
    }

    fun addManualReadingNote(
        bookId: Int,
        kind: BookNoteKind,
        dateText: String,
        pageText: String,
        minutesText: String,
        noteText: String
    ) {
        val created = parseBookSessionEditDate(dateText) ?: run {
            statusMessage = "Use date format yyyy-MM-dd HH:mm."
            return
        }
        val page = pageText.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val seconds = (minutesText.toLongOrNull()?.coerceAtLeast(0L) ?: 0L) * 60L
        val trimmed = noteText.trim()
        if (trimmed.isBlank()) {
            statusMessage = "Write something first."
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.saveLiveReadingNote(
                    key = bookLiveNoteKey(getApplication(), "new_${bookId}_${kind.name}"),
                    bookId = bookId,
                    createdAt = created.toString(),
                    page = page,
                    durationSeconds = seconds,
                    note = noteText,
                    kind = kind
                )
            }
            getApplication<Application>().getSharedPreferences("book_note_drafts", Context.MODE_PRIVATE).edit().remove("manual_$bookId").remove("quick_${bookId}_${kind.name}").commit()
            getApplication<Application>().getSharedPreferences("book_note_drafts", Context.MODE_PRIVATE).edit().remove("live_new_${bookId}_${kind.name}").commit()
            statusMessage = "${kind.label} saved."
            selectedBookId = bookId
            reload()
            maybeAutoBackup()
        }
    }

    fun addManualReadingSession(
        bookId: Int,
        endedText: String,
        minutesText: String,
        pageReachedText: String
    ) {
        val minutes = minutesText.toLongOrNull()?.coerceAtLeast(1L)
        val pageReached = pageReachedText.toIntOrNull()?.coerceAtLeast(0)
        if (minutes == null || pageReached == null) {
            statusMessage = "Enter valid session time and page reached."
            return
        }
        val ended = parseBookSessionEditDate(endedText) ?: run {
            statusMessage = "Use date format yyyy-MM-dd HH:mm."
            return
        }
        val started = ended.minusSeconds(minutes * 60L)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val pagesRead = db.inferPagesRead(
                    bookId = bookId,
                    endedIso = ended.toString(),
                    pageReached = pageReached
                )
                db.addReadingSession(
                    bookId = bookId,
                    startedIso = started.toString(),
                    endedIso = ended.toString(),
                    durationSeconds = minutes * 60L,
                    pagesRead = pagesRead,
                    pageReached = pageReached
                )
            }
            statusMessage = "Reading session added."
            selectedBookId = bookId
            reload()
            maybeAutoBackup()
        }
    }

    fun setSessionStatisticsExcluded(session: BookReadingSessionRow, excluded: Boolean) {
        try {
            db.setSessionStatisticsExcluded(session.id, excluded)
            reload()
            maybeAutoBackup()
        } catch (error: Exception) {
            statusMessage = "Could not save statistics setting. Please try again."
        }
    }

    fun updateReadingSession(
        session: BookReadingSessionRow,
        endedText: String,
        minutesText: String,
        pageReachedText: String,
        excludeFromStatistics: Boolean
    ) {
        val minutes = minutesText.toLongOrNull()?.coerceAtLeast(1L)
        val pageReached = pageReachedText.toIntOrNull()?.coerceAtLeast(0)
        if (minutes == null || pageReached == null) {
            statusMessage = "Enter valid session time and page reached."
            return
        }
        val ended = parseBookSessionEditDate(endedText)
        if (ended == null) {
            statusMessage = "Use date format yyyy-MM-dd HH:mm."
            return
        }
        val started = ended.minusSeconds(minutes * 60L)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (endedText == bookFormatEditableDate(session.activityAt) && minutesText == (session.durationSeconds / 60L).coerceAtLeast(1L).toString() && pageReached == session.pageReached) {
                    db.setSessionStatisticsExcluded(session.id, excludeFromStatistics)
                    return@withContext
                }
                val pagesRead = if (pageReached == session.pageReached) session.pagesRead else db.inferPagesRead(
                    bookId = session.bookId,
                    endedIso = ended.toString(),
                    pageReached = pageReached,
                    excludeSessionId = session.id
                )
                db.updateReadingSession(
                    sessionId = session.id,
                    bookId = session.bookId,
                    startedIso = started.toString(),
                    endedIso = ended.toString(),
                    durationSeconds = minutes * 60L,
                    pagesRead = pagesRead,
                    pageReached = pageReached,
                    excludeFromStatistics = excludeFromStatistics,
                    preserveProgress = pageReached == session.pageReached
                )
            }
            statusMessage = "Reading session updated."
            reload()
            maybeAutoBackup()
        }
    }

    fun deleteReadingSession(session: BookReadingSessionRow) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.deleteReadingSession(session.id, session.bookId) }
            statusMessage = "Reading session deleted."
            reload()
            maybeAutoBackup()
        }
    }

    fun findDraftCover() {
        val isbn = normalizeIsbn(isbnInput)
        val title = titleInput.trim()
        if (isbn == null && title.isBlank()) {
            statusMessage = "Add an ISBN or title first."
            return
        }
        if (fetching) return
        fetching = true
        statusMessage = "Looking for cover..."
        viewModelScope.launch {
            val cover = withContext(Dispatchers.IO) {
                runCatching { api.lookupCover(isbn, title, authorsInput.trim()) }.getOrNull()
            }.orEmpty()
            fetching = false
            if (cover.isBlank()) {
                statusMessage = "No cover found. Choose one from gallery."
            } else {
                draftCoverUrl = cover
                statusMessage = "Cover updated."
            }
        }
    }

    fun updateBookCover(bookId: Int, coverUrl: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.updateCover(bookId, coverUrl) }
            statusMessage = "Cover updated."
            reload()
        }
    }

    fun searchCoverImages() {
        val query = coverSearchQuery.trim().ifBlank {
            listOf(titleInput, authorsInput).filter { it.isNotBlank() }.joinToString(" ")
        }
        if (query.isBlank() || coverSearchLoading) {
            if (query.isBlank()) statusMessage = "Enter cover search terms first."
            return
        }
        coverSearchQuery = query
        coverSearchLoading = true
        statusMessage = "Searching book covers..."
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                runCatching { api.searchGoogleImages(query) }.getOrDefault(emptyList())
            }
            coverSearchResults = results
            coverSearchLoading = false
            statusMessage = if (results.isEmpty()) "No cover images found." else "Pick a cover image."
        }
    }

    fun prepareCoverSearch(defaultQuery: String) {
        val query = defaultQuery.trim()
        coverSearchQuery = query
        coverSearchResults = emptyList()
        coverSearchLoading = false
        if (query.isNotBlank()) {
            searchCoverImages()
        } else {
            statusMessage = "Enter cover search terms first."
        }
    }

    fun clearCoverSearch() {
        coverSearchQuery = ""
        coverSearchResults = emptyList()
        coverSearchLoading = false
    }

    fun findCoverForBook(book: BookRow) {
        if (fetching) return
        fetching = true
        statusMessage = "Looking for cover..."
        viewModelScope.launch {
            val cover = withContext(Dispatchers.IO) {
                runCatching { api.lookupCover(normalizeIsbn(book.isbn), book.title, book.authors) }.getOrNull()
            }.orEmpty()
            fetching = false
            if (cover.isBlank()) {
                statusMessage = "No cover found."
            } else {
                withContext(Dispatchers.IO) { db.updateCover(book.id, cover) }
                statusMessage = "Cover updated."
                reload()
            }
        }
    }

    fun toggleReadingPause() {
        if (activeBookId == null) return
        val now = System.currentTimeMillis()
        if (activePausedAtMs > 0L) {
            activePausedTotalMs += (now - activePausedAtMs).coerceAtLeast(0L)
            activePausedAtMs = 0L
            statusMessage = "Reading resumed."
        } else {
            activePausedAtMs = now
            statusMessage = "Reading paused."
        }
        persistActiveBook()
        syncReadingTimerNotification()
    }

    fun pauseReading() {
        if (activeBookId == null || activePausedAtMs > 0L) return
        activePausedAtMs = System.currentTimeMillis()
        statusMessage = "Reading paused."
        persistActiveBook()
        syncReadingTimerNotification()
    }

    fun startCountdown(minutes: Int) {
        if (activeBookId == null) {
            statusMessage = "Start reading before setting a countdown."
            return
        }
        val safeMinutes = minutes.coerceAtLeast(1)
        val totalMs = safeMinutes * 60_000L
        countdownTotalMs = totalMs
        countdownEndMs = System.currentTimeMillis() + totalMs
        prefs.edit()
            .putLong(KEY_BOOK_COUNTDOWN_TOTAL_MS, countdownTotalMs)
            .putLong(KEY_BOOK_COUNTDOWN_END_MS, countdownEndMs)
            .remove(KEY_BOOK_COUNTDOWN_DONE_MS)
            .commit()
        statusMessage = "Countdown set for ${bookFormatDurationPickerLabel(safeMinutes.toString())}."
        syncReadingTimerNotification()
    }

    fun cancelCountdown() {
        countdownEndMs = 0L
        countdownTotalMs = 0L
        prefs.edit()
            .remove(KEY_BOOK_COUNTDOWN_TOTAL_MS)
            .remove(KEY_BOOK_COUNTDOWN_END_MS)
            .remove(KEY_BOOK_COUNTDOWN_DONE_MS)
            .commit()
        statusMessage = "Countdown cancelled."
        syncReadingTimerNotification()
    }

    private fun resumeReading() {
        if (activePausedAtMs <= 0L) return
        val now = System.currentTimeMillis()
        activePausedTotalMs += (now - activePausedAtMs).coerceAtLeast(0L)
        activePausedAtMs = 0L
        statusMessage = "Reading resumed."
        persistActiveBook()
        syncReadingTimerNotification()
    }

    private fun activeReadingElapsedSeconds(nowMs: Long): Long {
        return calculateBookTimerElapsedSeconds(
            activeStartedAtMs,
            activePausedAtMs,
            activePausedTotalMs,
            nowMs
        )
    }

    fun discardReadingSession() {
        clearActiveBook()
        statusMessage = "Reading session discarded."
    }

    fun logMinutes(bookId: Int, minutes: Int) {
        val safeMinutes = minutes.coerceAtLeast(1)
        val ended = Instant.now()
        val started = ended.minusSeconds(safeMinutes * 60L)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.addReadingSession(bookId, started.toString(), ended.toString(), safeMinutes * 60L)
            }
            statusMessage = "Logged ${safeMinutes}m."
            reload()
            maybeAutoBackup()
        }
    }

    fun updateProgress(bookId: Int, pageText: String, status: BookStatus? = null) {
        val page = pageText.toIntOrNull()?.coerceAtLeast(0)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.updateProgress(bookId, page, status) }
            reload()
            maybeAutoBackup()
        }
    }

    fun changeCompletionDate(bookId: Int, date: LocalDate) {
        db.setCompletionDate(bookId, date)
        reload()
        maybeAutoBackup()
    }

    fun changeStatus(book: BookRow, status: BookStatus) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.updateProgress(book.id, null, status) }
            reload()
            maybeAutoBackup()
        }
    }

    fun updateRating(bookId: Int, rating: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.updateRating(bookId, rating.coerceIn(0, 5)) }
            reload()
            maybeAutoBackup()
        }
    }

    fun updateNotes(bookId: Int, notes: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.updateNotes(bookId, notes) }
            reload()
            maybeAutoBackup()
        }
    }

    fun toggleBookPinned(bookId: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.togglePinned(bookId) }
            reload()
            maybeAutoBackup()
        }
    }

    fun saveReadingGoals(goals: BookReadingGoals): Boolean {
        return runCatching {
            db.saveReadingGoals(goals)
            statusMessage = "Reading goals saved."
            reload()
            maybeAutoBackup()
            true
        }.getOrElse {
            statusMessage = "Could not save reading goals: ${it.message}."
            false
        }
    }

    fun saveReadingGoals(dailyMinutesText: String, dailyPagesText: String, yearlyBooksText: String): Boolean {
        fun parse(value: String, label: String): Int? {
            if (value.isBlank()) return 0
            val parsed = value.toIntOrNull()
            if (parsed == null || parsed < 0) {
                statusMessage = "$label must be a whole number or blank."
                return null
            }
            return parsed.coerceAtMost(1_000_000)
        }
        val dailyMinutes = parse(dailyMinutesText, "Daily minutes") ?: return false
        val dailyPages = parse(dailyPagesText, "Daily pages") ?: return false
        val yearlyBooks = parse(yearlyBooksText, "Yearly books") ?: return false
        runCatching { db.saveReadingGoals(BookReadingGoals(dailyMinutes, dailyPages, yearlyBooks)) }
            .onFailure {
                statusMessage = "Could not save reading goals: ${it.message ?: "database error"}."
                return false
            }
        statusMessage = "Reading goals saved."
        reload()
        maybeAutoBackup()
        return true
    }

    fun toggleBookFinished(bookId: Int) {
        val book = books.firstOrNull { it.id == bookId } ?: return
        val next = if (book.status == BookStatus.FINISHED) BookStatus.READING else BookStatus.FINISHED
        changeStatus(book, next)
    }

    fun deleteBook(bookId: Int) {
        if (activeBookId == bookId) clearActiveBook()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.deleteBook(bookId) }
            selectedBookId = selectedBookId?.takeIf { it != bookId }
            statusMessage = "Book deleted."
            reload()
            maybeAutoBackup()
        }
    }

    fun exportBackupToUri(uri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val backup = db.exportBackupJson()
                    writeAndVerifyBackup(context, uri, backup)
                    backup.getJSONArray("books").length()
                }
            }
            statusMessage = result.fold(
                onSuccess = { "Exported and verified $it ${if (it == 1) "book" else "books"}." },
                onFailure = { "Export failed: ${it.message ?: "unknown error"}" }
            )
        }
    }

    fun importBackupFromUri(uri: Uri) {
        if (importBusy || pendingLegacyImport != null || pendingTrackerImport != null) return
        importBusy = true
        val context = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use(LegacyMigration::readLimited)
                        ?: throw IOException("Could not read backup file.")
                    val root = JSONObject(LegacyMigration.decode(bytes))
                    if (root.has("tables")) {
                        Triple(LegacyMigration.preview(bytes), null, null)
                    } else {
                        Triple(null, LegacyMigration.previewTrackerBackup(bytes), null)
                    }
                }
            }
            result.onSuccess { (legacyPreview, trackerPreview, _) ->
                pendingLegacyImport = legacyPreview
                pendingTrackerImport = trackerPreview
                statusMessage = "Backup checked. Review the import preview."
            }.onFailure { statusMessage = "Import failed: ${it.message ?: "unknown error"}. Existing data kept." }
            importBusy = false
        }
    }

    fun cancelLegacyImport() { if (!importBusy) pendingLegacyImport = null }

    fun cancelTrackerImport() { if (!importBusy) pendingTrackerImport = null }

    fun confirmTrackerImport() {
        val plan = pendingTrackerImport ?: return
        if (importBusy) return
        importBusy = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val checkedAgain = LegacyMigration.previewTrackerBackup(plan.source)
                    check(checkedAgain.fileSha256 == plan.fileSha256) { "Backup changed after preview." }
                    db.importBackupJson(checkedAgain.root, checkedAgain.source)
                }
            }
            statusMessage = result.fold(
                onSuccess = { it },
                onFailure = { "Restore failed: ${it.message ?: "unknown error"}. Existing data kept." }
            )
            importBusy = false
            pendingTrackerImport = null
            reload()
            if (result.isSuccess) maybeAutoBackup()
        }
    }

    fun confirmLegacyImport() {
        val plan = pendingLegacyImport ?: return
        if (importBusy) return
        importBusy = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { db.importLegacy(plan) } }
            statusMessage = result.fold(onSuccess = { it }, onFailure = { "Import failed: ${it.message}. Existing data kept." })
            importBusy = false
            pendingLegacyImport = null
            reload()
            if (result.isSuccess) maybeAutoBackup()
        }
    }

    fun exportLegacySource(uri: Uri, hash: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = db.legacySource(hash)
                    check(LegacyMigration.hash(bytes) == hash) { "Archive checksum mismatch" }
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                        ?: throw IOException("Could not write legacy import file.")
                }
            }
            statusMessage = result.fold(onSuccess = { "Exported the original legacy import file, byte for byte." }, onFailure = { "Export failed: ${it.message}" })
        }
    }

    fun setBookAutoBackupFolder(uri: Uri) {
        bookAutoBackupTreeUri = uri.toString()
        prefs.edit().putString(KEY_BOOK_AUTO_BACKUP_TREE_URI, bookAutoBackupTreeUri).apply()
        statusMessage = "Backup folder set."
    }

    fun toggleBookAutoBackup() {
        bookAutoBackupEnabled = !bookAutoBackupEnabled
        prefs.edit().putBoolean(KEY_BOOK_AUTO_BACKUP_ENABLED, bookAutoBackupEnabled).apply()
        statusMessage = if (bookAutoBackupEnabled) "Automatic backup enabled." else "Automatic backup disabled."
        if (bookAutoBackupEnabled) backupNow()
    }

    fun backupNow() {
        val rawTreeUri = bookAutoBackupTreeUri
        if (rawTreeUri.isBlank()) {
            statusMessage = "Choose a backup folder first."
            return
        }
        val context = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val treeUri = Uri.parse(rawTreeUri)
                    val rootId = DocumentsContract.getTreeDocumentId(treeUri)
                    val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
                    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"))
                    val fileName = "book_tracker_backup_$stamp.json"
                    val pendingName = "$fileName.pending"
                    val pendingUri = DocumentsContract.createDocument(
                        context.contentResolver,
                        parent,
                        "application/json",
                        pendingName
                    ) ?: throw IOException("Could not create backup file.")
                    try {
                        writeAndVerifyBackup(context, pendingUri, db.exportBackupJson())
                        DocumentsContract.renameDocument(context.contentResolver, pendingUri, fileName)
                            ?: throw IOException("Could not finalize verified backup file.")
                    } catch (error: Throwable) {
                        runCatching { DocumentsContract.deleteDocument(context.contentResolver, pendingUri) }
                        throw error
                    }
                    fileName
                }
            }
            statusMessage = result.fold(
                onSuccess = { "Backup written: $it" },
                onFailure = { "Backup failed: ${it.message ?: "unknown error"}" }
            )
        }
    }

    fun backupFolderLabel(): String {
        return bookAutoBackupTreeUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it).lastPathSegment.orEmpty() }?.ifBlank { "Selected" } ?: "Not set"
    }

    private fun maybeAutoBackup() {
        if (bookAutoBackupEnabled && bookAutoBackupTreeUri.isNotBlank()) backupNow()
    }

    private fun writeAndVerifyBackup(context: Context, uri: Uri, backup: JSONObject) {
        val json = backup.toString(2)
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.writer(Charsets.UTF_8).use { it.write(json) }
        } ?: throw IOException("Could not write backup file.")
        val written = context.contentResolver.openInputStream(uri)?.use(LegacyMigration::readLimited)
            ?: throw IOException("Could not verify backup file.")
        val preview = LegacyMigration.previewTrackerBackup(written)
        check(preview.contentSha256 == backup.getString("content_sha256")) {
            "Backup verification checksum mismatch."
        }
    }

    fun setStatus(message: String) {
        statusMessage = message
    }

    private fun clearDraft() {
        editingBookId = null
        isbnInput = ""
        titleInput = ""
        authorsInput = ""
        pageCountInput = ""
        draftCoverUrl = ""
        draftSourceUrl = ""
        collectionsInput = ""
    }

    private fun clearActiveBook() {
        activeBookId = null
        activeStartedAtMs = 0L
        activePausedAtMs = 0L
        activePausedTotalMs = 0L
        countdownEndMs = 0L
        countdownTotalMs = 0L
        prefs.edit()
            .remove(KEY_BOOK_ACTIVE_ID)
            .remove(KEY_BOOK_ACTIVE_STARTED_MS)
            .remove(KEY_BOOK_ACTIVE_PAUSED_AT_MS)
            .remove(KEY_BOOK_ACTIVE_PAUSED_TOTAL_MS)
            .remove(KEY_BOOK_ACTIVE_TITLE)
            .remove(KEY_BOOK_ACTIVE_AUTHORS)
            .remove(KEY_BOOK_ACTIVE_PAGE)
            .remove(KEY_BOOK_ACTIVE_PAGE_COUNT)
            .remove(KEY_BOOK_COUNTDOWN_END_MS)
            .remove(KEY_BOOK_COUNTDOWN_TOTAL_MS)
            .remove(KEY_BOOK_COUNTDOWN_DONE_MS)
            .commit()
        BookReadingTimerService.stop(getApplication<Application>().applicationContext)
    }

    private fun loadThemeMode(): ThemeMode {
        return ThemeMode.entries.firstOrNull {
            it.name == prefs.getString(TRACKER_KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        } ?: ThemeMode.SYSTEM
    }

    private fun loadAccentMode(): AccentMode {
        return AccentMode.entries.firstOrNull {
            it.name == prefs.getString(TRACKER_KEY_ACCENT_MODE, AccentMode.AUTO.name)
        } ?: AccentMode.AUTO
    }

    private fun loadActiveBookId(): Int? {
        val id = prefs.getInt(KEY_BOOK_ACTIVE_ID, -1)
        return id.takeIf { it > 0 }
    }

    private fun loadActiveStartedAtMs(): Long = prefs.getLong(KEY_BOOK_ACTIVE_STARTED_MS, 0L).coerceAtLeast(0L)

    private fun loadActivePausedAtMs(): Long = prefs.getLong(KEY_BOOK_ACTIVE_PAUSED_AT_MS, 0L).coerceAtLeast(0L)

    private fun loadActivePausedTotalMs(): Long = prefs.getLong(KEY_BOOK_ACTIVE_PAUSED_TOTAL_MS, 0L).coerceAtLeast(0L)

    private fun loadCountdownEndMs(): Long = prefs.getLong(KEY_BOOK_COUNTDOWN_END_MS, 0L).coerceAtLeast(0L)

    private fun loadCountdownTotalMs(): Long = prefs.getLong(KEY_BOOK_COUNTDOWN_TOTAL_MS, 0L).coerceAtLeast(0L)

    private fun persistActiveBook() {
        val bookId = activeBookId ?: return
        val book = statsBooks.firstOrNull { it.id == bookId }
        prefs.edit()
            .putInt(KEY_BOOK_ACTIVE_ID, bookId)
            .putLong(KEY_BOOK_ACTIVE_STARTED_MS, activeStartedAtMs)
            .putLong(KEY_BOOK_ACTIVE_PAUSED_AT_MS, activePausedAtMs)
            .putLong(KEY_BOOK_ACTIVE_PAUSED_TOTAL_MS, activePausedTotalMs)
            .putString(KEY_BOOK_ACTIVE_TITLE, book?.title.orEmpty())
            .putString(KEY_BOOK_ACTIVE_AUTHORS, book?.authors.orEmpty())
            .putInt(KEY_BOOK_ACTIVE_PAGE, book?.currentPage ?: 0)
            .putInt(KEY_BOOK_ACTIVE_PAGE_COUNT, book?.pageCount ?: 0)
            .commit()
    }

    private fun syncReadingTimerNotification() {
        if (activeBookId != null) {
            BookReadingTimerService.sync(getApplication<Application>().applicationContext)
        }
    }

    private fun loadSortField(): BookSortField {
        return BookSortField.entries.firstOrNull {
            it.name == prefs.getString(KEY_BOOK_SORT_FIELD, BookSortField.ADDED.name)
        } ?: BookSortField.ADDED
    }

    override fun onCleared() {
        db.close()
        super.onCleared()
    }
}
