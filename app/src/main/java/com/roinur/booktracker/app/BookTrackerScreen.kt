package com.roinur.booktracker

import androidx.compose.material3.Text

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookTrackerScreen(vm: BookTrackerViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val libraryListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    var selectedTabName by rememberSaveable { mutableStateOf(BookTrackerTab.STATS.name) }
    // Return to a running session after recreation, but keep an explicitly
    // minimized/paused session available from the main screens.
    var screenModeName by rememberSaveable {
        mutableStateOf(
            if (vm.lastFinishedSession != null) BookScreenMode.SESSION_NOTE.name
            else if (vm.activeBookId != null && vm.activePausedAtMs <= 0L) BookScreenMode.READING.name
            else BookScreenMode.MAIN.name
        )
    }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var scannerOpen by rememberSaveable { mutableStateOf(false) }
    var showFinishDialog by rememberSaveable { mutableStateOf(false) }
    var finishPageDraft by rememberSaveable { mutableStateOf("") }
    var editingNoteId by rememberSaveable { mutableStateOf<Int?>(null) }
    var coverTargetName by rememberSaveable { mutableStateOf<String?>(null) }
    var coverSearchTargetName by rememberSaveable { mutableStateOf<String?>(null) }
    var showCoverSearch by rememberSaveable { mutableStateOf(false) }
    var showNotesDialog by rememberSaveable { mutableStateOf(false) }
    var showSessionsDialog by rememberSaveable { mutableStateOf(false) }
    var showGlobalSessionsDialog by rememberSaveable { mutableStateOf(false) }
    var sessionsDay by remember { mutableStateOf<LocalDate?>(null) }
    var globalNotesFilterKindName by rememberSaveable { mutableStateOf<String?>(null) }
    var editingSessionId by rememberSaveable { mutableStateOf<Int?>(null) }
    var notesFilterKindName by rememberSaveable { mutableStateOf<String?>(null) }
    var manualLogInitialMode by rememberSaveable { mutableStateOf<String?>(null) }
    var actionNoteKindName by rememberSaveable { mutableStateOf<String?>(null) }
    var showCountdownDialog by rememberSaveable { mutableStateOf(false) }
    var showBookGraphDialog by rememberSaveable { mutableStateOf(false) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var holdPopup by remember { mutableStateOf<BookRatingHoldState?>(null) }
    val selectedTab = runCatching { BookTrackerTab.valueOf(selectedTabName) }.getOrDefault(BookTrackerTab.STATS)
    val screenMode = runCatching { BookScreenMode.valueOf(screenModeName) }.getOrDefault(BookScreenMode.MAIN)
    val coverTarget = coverTargetName?.let { runCatching { BookCoverTarget.valueOf(it) }.getOrNull() }
    val coverSearchTarget = coverSearchTargetName?.let { runCatching { BookCoverTarget.valueOf(it) }.getOrNull() }
    val activeBook = vm.statsBooks.firstOrNull { it.id == vm.activeBookId }
    val visibleLibraryBooks = vm.books.filter { vm.libraryFilter.matches(it, vm.activeBookId) }
    val fallbackBook = mostRecentlyReadBook(vm.statsBooks, vm.allBookSessions, vm.activeBookId)
    val selectedBook = vm.statsBooks.firstOrNull { it.id == vm.selectedBookId } ?: fallbackBook
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scannerOpen = true
        } else {
            vm.setStatus("Camera permission is needed for ISBN scanning.")
        }
    }
    val readingNotificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            vm.setStatus("Notification permission denied. The timer still runs, but Android may hide it from the shade.")
        }
    }
    val draftCoverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.updateDraftCover(it.toString())
        }
    }
    val bookCoverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val book = vm.books.firstOrNull { it.id == vm.selectedBookId }
        if (uri != null && book != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.updateBookCover(book.id, uri.toString())
        }
    }
    val coverCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap ?: return@rememberLauncherForActivityResult
        val uriString = runCatching { saveCoverBitmapToInternalStorage(context, bitmap) }.getOrNull()
            ?: return@rememberLauncherForActivityResult
        when (coverTarget) {
            BookCoverTarget.DRAFT -> vm.updateDraftCover(uriString)
            BookCoverTarget.SELECTED -> vm.selectedBookId?.let { vm.updateBookCover(it, uriString) }
            null -> Unit
        }
        coverTargetName = null
    }
    var legacyExportHash by rememberSaveable { mutableStateOf<String?>(null) }
    val legacySourceExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val hash = legacyExportHash
        if (uri != null && hash != null) vm.exportLegacySource(uri, hash)
        legacyExportHash = null
    }
    LegacyImportDialog(vm.pendingLegacyImport, vm.importBusy, vm::confirmLegacyImport, vm::cancelLegacyImport)
    TrackerBackupImportDialog(vm.pendingTrackerImport, vm.importBusy, vm::confirmTrackerImport, vm::cancelTrackerImport)
    val bookImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importBackupFromUri) ?: vm.setStatus("Import cancelled.")
    }
    val bookExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportBackupToUri) ?: vm.setStatus("Export cancelled.")
    }
    val bookBackupFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            vm.setBookAutoBackupFolder(uri)
        } else {
            vm.setStatus("Backup folder selection cancelled.")
        }
    }

    fun openScanner() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            scannerOpen = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun requestReadingNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            readingNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun closeToMain() {
        showFinishDialog = false
        screenModeName = BookScreenMode.MAIN.name
        scope.launch { listState.scrollToItem(0) }
    }

    fun openReading(bookId: Int) {
        requestReadingNotificationPermissionIfNeeded()
        if (!vm.startReading(bookId)) return
        vm.selectBook(bookId)
        showSettings = false
        screenModeName = BookScreenMode.READING.name
        scope.launch { listState.scrollToItem(0) }
    }

    fun minimizeReading() {
        vm.pauseReading()
        showFinishDialog = false
        selectedTabName = BookTrackerTab.LIBRARY.name
        screenModeName = BookScreenMode.MAIN.name
        scope.launch { listState.scrollToItem(0) }
    }

    fun leaveAddScreen() {
        val returnToDetails = vm.editingBookId != null && selectedBook != null
        vm.cancelBookDraft()
        screenModeName = if (returnToDetails) BookScreenMode.DETAIL.name else BookScreenMode.MAIN.name
        scope.launch { listState.scrollToItem(0) }
    }

    BackHandler(enabled = showSettings || screenMode != BookScreenMode.MAIN || showFinishDialog || manualLogInitialMode != null || actionNoteKindName != null || showCountdownDialog || showBookGraphDialog || showGlobalSessionsDialog || globalNotesFilterKindName != null) {
        when {
            showFinishDialog -> showFinishDialog = false
            actionNoteKindName != null -> actionNoteKindName = null
            showCountdownDialog -> showCountdownDialog = false
            globalNotesFilterKindName != null -> globalNotesFilterKindName = null
            showGlobalSessionsDialog -> showGlobalSessionsDialog = false
            manualLogInitialMode != null -> manualLogInitialMode = null
            showBookGraphDialog -> showBookGraphDialog = false
            showSettings -> showSettings = false
            showCoverSearch -> showCoverSearch = false
            coverTargetName != null -> coverTargetName = null
            editingNoteId != null -> editingNoteId = null
            editingSessionId != null -> editingSessionId = null
            showNotesDialog -> showNotesDialog = false
            showSessionsDialog -> showSessionsDialog = false
            screenMode == BookScreenMode.SESSION_NOTE -> screenModeName = BookScreenMode.DETAIL.name
            screenMode == BookScreenMode.READING -> minimizeReading()
            screenMode == BookScreenMode.ADD -> leaveAddScreen()
            else -> closeToMain()
        }
    }

    LaunchedEffect(vm.activeBookId, vm.countdownEndMs) {
        while (vm.activeBookId != null || vm.countdownEndMs > System.currentTimeMillis()) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val pageAnimationKey = "${showSettings}_${screenMode.name}_${selectedTab.name}_${vm.selectedBookId ?: 0}"
    var pageVisible by remember(pageAnimationKey) { mutableStateOf(false) }
    LaunchedEffect(pageAnimationKey) { pageVisible = true }
    val pageAlpha by animateFloatAsState(
        targetValue = if (pageVisible) 1f else 0f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "bookPageAlpha"
    )
    val pageOffset by animateFloatAsState(
        targetValue = if (pageVisible) 0f else 24f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "bookPageOffset"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        key(vm.themeMode, vm.accentMode, MaterialTheme.colorScheme.background) {
            BookTopBar(
                title = when {
                    showSettings -> "Settings"
                    screenMode == BookScreenMode.ADD -> if (vm.editingBookId != null) "Edit book" else "Add book"
                    screenMode == BookScreenMode.DETAIL -> "Book details"
                    screenMode == BookScreenMode.READING -> "Reading"
                    screenMode == BookScreenMode.SESSION_NOTE -> "Session notes"
                    screenMode == BookScreenMode.TRENDS -> "Reading Trends"
                    else -> APP_TITLE
                },
                themeMode = vm.themeMode,
                accentMode = vm.accentMode,
                settingsActive = showSettings,
                onBack = if (!showSettings && screenMode != BookScreenMode.MAIN) {
                    {
                        when (screenMode) {
                            BookScreenMode.READING -> minimizeReading()
                            BookScreenMode.ADD -> leaveAddScreen()
                            BookScreenMode.SESSION_NOTE -> screenModeName = BookScreenMode.DETAIL.name
                            else -> closeToMain()
                        }
                    }
                } else {
                    null
                },
                onCycleThemeMode = vm::cycleThemeMode,
                onAccentModeSelected = vm::chooseAccentMode,
                onSettings = {
                    showSettings = !showSettings
                    if (showSettings) screenModeName = BookScreenMode.MAIN.name
                    scope.launch { listState.scrollToItem(0) }
                }
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer(alpha = pageAlpha, translationY = pageOffset)
        ) {
            if (!showSettings && screenMode == BookScreenMode.TRENDS) {
                TrendOverTimePanel(vm::trendTargets, vm::trendSnapshot, Modifier.fillMaxSize().padding(12.dp))
            } else LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                state = if (!showSettings && screenMode == BookScreenMode.MAIN && selectedTabName == BookTrackerTab.LIBRARY.name) libraryListState else listState,
                userScrollEnabled = screenMode != BookScreenMode.DETAIL && screenMode != BookScreenMode.ADD,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (showSettings) {
                    item(contentType = "book_settings") {
                        BookSettingsContent(
                            vm = vm,
                            onImport = { bookImportLauncher.launch(arrayOf("application/json", "text/plain", "text/*")) },
                            onExport = { bookExportLauncher.launch("book_tracker_backup_${LocalDate.now(ZoneId.systemDefault())}.json") },
                            onPickBackupFolder = { bookBackupFolderLauncher.launch(null) },
                            onExportLegacy = { hash ->
                                legacyExportHash = hash
                                legacySourceExportLauncher.launch("original_import_${hash.take(12)}.json")
                            }
                        )
                    }
                } else if (screenMode == BookScreenMode.ADD) {
                    item(contentType = "book_add_screen") {
                            BookAddScreen(
                                vm = vm,
                                editing = vm.editingBookId != null,
                                onScan = ::openScanner,
                                onPickCover = { coverTargetName = BookCoverTarget.DRAFT.name },
                                onFindCover = vm::findDraftCover,
                                onSaved = {
                                    vm.selectBook(it.takeIf { id -> id > 0 })
                                    screenModeName = BookScreenMode.DETAIL.name
                                    selectedTabName = BookTrackerTab.LIBRARY.name
                                    scope.launch { listState.scrollToItem(0) }
                                }
                            )
                        }
                } else if (screenMode == BookScreenMode.DETAIL) {
                    item(contentType = "book_detail_screen") {
                        if (selectedBook == null) {
                            BookEmptyStateText("Select a book from Library first.")
                        } else {
                            BookDetailCard(
                                modifier = Modifier.fillParentMaxHeight(),
                                book = selectedBook,
                                active = selectedBook.id == vm.activeBookId,
                                activeStartedAtMs = vm.activeStartedAtMs,
                                activePausedAtMs = vm.activePausedAtMs,
                                activePausedTotalMs = vm.activePausedTotalMs,
                                nowMs = nowMs,
                                notes = vm.selectedBookNotes,
                                sessions = vm.selectedBookSessions,
                                onStart = { openReading(selectedBook.id) },
                                onStatusChange = { status -> vm.changeStatus(selectedBook, status) },
                                onCompletionDate = { date -> vm.changeCompletionDate(selectedBook.id, date) },
                                onRating = { rating -> vm.updateRating(selectedBook.id, rating) },
                                onRatingPreview = { rating ->
                                    holdPopup = BookRatingHoldState(selectedBook.id, rating)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                onRatingCancel = { holdPopup = null },
                                onPickCover = { coverTargetName = BookCoverTarget.SELECTED.name },
                                onAuthorSelected = { author ->
                                    vm.searchAuthor(author)
                                    selectedTabName = BookTrackerTab.LIBRARY.name
                                    screenModeName = BookScreenMode.MAIN.name
                                    scope.launch { libraryListState.scrollToItem(0) }
                                },
                                onEdit = {
                                    vm.prepareEditBook(selectedBook)
                                    screenModeName = BookScreenMode.ADD.name
                                    scope.launch { listState.scrollToItem(0) }
                                },
                                onOpenNotes = {
                                    notesFilterKindName = null
                                    showNotesDialog = true
                                },
                                onOpenSessions = { showSessionsDialog = true },
                                onAddManual = { manualLogInitialMode = "NOTE" },
                                onOpenGraph = { showBookGraphDialog = true }
                            )
                        }
                    }
                } else if (screenMode == BookScreenMode.READING) {
                    item(contentType = "book_reading_screen") {
                        if (selectedBook == null) {
                            BookEmptyStateText("Select a book from Library first.")
                        } else {
                            BookReadingSessionScreen(
                                book = selectedBook,
                                activeStartedAtMs = vm.activeStartedAtMs,
                                activePausedAtMs = vm.activePausedAtMs,
                                activePausedTotalMs = vm.activePausedTotalMs,
                                countdownEndMs = vm.countdownEndMs,
                                countdownTotalMs = vm.countdownTotalMs,
                                nowMs = nowMs,
                                modifier = Modifier.fillMaxWidth(),
                                onStop = {
                                    finishPageDraft = selectedBook.currentPage.takeIf { it > 0 }?.toString().orEmpty()
                                    showFinishDialog = true
                                },
                                onPause = {
                                    vm.toggleReadingPause()
                                },
                                onCountdown = {
                                    if (vm.countdownEndMs > System.currentTimeMillis()) {
                                        vm.cancelCountdown()
                                    } else {
                                        showCountdownDialog = true
                                    }
                                },
                                onAction = { kind ->
                                    actionNoteKindName = kind.name
                                }
                            )
                        }
                    }
                } else if (screenMode == BookScreenMode.SESSION_NOTE) {
                    item(contentType = "book_session_note_screen") {
                        BookSessionNoteScreen(
                            book = selectedBook ?: vm.statsBooks.firstOrNull { it.id == vm.lastFinishedSession?.bookId },
                            session = vm.lastFinishedSession,
                            onSave = {
                                if (vm.saveLastSessionNote(it)) {
                                    screenModeName = BookScreenMode.DETAIL.name
                                }
                            }
                        )
                    }
                } else {
                    when (selectedTab) {
                        BookTrackerTab.STATS -> {
                            item(contentType = "book_stats") {
                                BookStatsContent(
                                    stats = vm.stats,
                                    finishedYearBooks = vm.finishedYearBooks,
                                    statsBooks = vm.statsBooks,
                                    sessions = vm.allBookSessions,
                                    onOpenDaySessions = { day -> sessionsDay = day; showGlobalSessionsDialog = true },
                                    goals = vm.readingGoals,
                                    activeBook = activeBook,
                                    activeStartedAtMs = vm.activeStartedAtMs,
                                    activePausedAtMs = vm.activePausedAtMs,
                                    activePausedTotalMs = vm.activePausedTotalMs,
                                    nowMs = nowMs,
                                    modifier = Modifier.fillParentMaxHeight(),
                                    onContinue = { book -> openReading(book.id) },
                                    onOpenGlobalNotes = { kind ->
                                        globalNotesFilterKindName = kind.name
                                    },
                                    onOpenGlobalSessions = {
                                        sessionsDay = null
                                        showGlobalSessionsDialog = true
                                    },
                                    onDaySelected = { point ->
                                        vm.setStatus("${point.date}: ${point.pagesRead} on the selected graph across ${point.entriesRead} ${if (point.entriesRead == 1) "book" else "books"}.")
                                    },
                                    onOpenTrends = { screenModeName = BookScreenMode.TRENDS.name },
                                    onSaveGoals = vm::saveReadingGoals
                                )
                            }
                        }

                        BookTrackerTab.LIBRARY -> {
                            item(contentType = "book_library_header") {
                                BookLibraryHeader(
                                    vm = vm,
                                    visibleBookCount = visibleLibraryBooks.size,
                                    activeBook = activeBook,
                                    nowMs = nowMs,
                                    onOpenBook = { bookId ->
                                        vm.selectBook(bookId)
                                        screenModeName = BookScreenMode.DETAIL.name
                                    },
                                    onContinue = { bookId ->
                                        openReading(bookId)
                                    },
                                    onAdd = {
                                        vm.prepareNewBook()
                                        screenModeName = BookScreenMode.ADD.name
                                        scope.launch { listState.scrollToItem(0) }
                                    }
                                )
                            }
                            if (visibleLibraryBooks.isEmpty()) {
                                item(contentType = "book_empty") {
                                    BookEmptyStateText("No books match the current library view.")
                                }
                            } else if (vm.galleryMode) {
                                val rows = visibleLibraryBooks.chunked(vm.galleryColumns.coerceIn(1, 5))
                                items(rows.size, key = { index -> "gallery_$index" }, contentType = { "book_gallery_row" }) { rowIndex ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rows[rowIndex].forEach { book ->
                                            BookGalleryTile(
                                                book = book,
                                                selected = book.id == vm.selectedBookId,
                                                modifier = Modifier.weight(1f),
                                                onOpen = {
                                                    vm.selectBook(book.id)
                                                    screenModeName = BookScreenMode.DETAIL.name
                                                },
                                                onPin = { vm.toggleBookPinned(book.id) },
                                                onReadGesture = { openReading(book.id) },
                                                onRatingPreview = { rating ->
                                                    holdPopup = BookRatingHoldState(book.id, rating)
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                },
                                                onRatingCommit = { rating -> vm.updateRating(book.id, rating) },
                                                onRatingCancel = { holdPopup = null }
                                            )
                                        }
                                        repeat(vm.galleryColumns.coerceIn(1, 5) - rows[rowIndex].size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            } else {
                                items(visibleLibraryBooks, key = { it.id }, contentType = { "book_row" }) { book ->
                                    BookLibraryRow(
                                        book = book,
                                        selected = book.id == vm.selectedBookId,
                                        active = book.id == vm.activeBookId,
                                        activeStartedAtMs = vm.activeStartedAtMs,
                                        activePausedAtMs = vm.activePausedAtMs,
                                        activePausedTotalMs = vm.activePausedTotalMs,
                                        nowMs = nowMs,
                                        onOpen = {
                                            vm.selectBook(book.id)
                                            screenModeName = BookScreenMode.DETAIL.name
                                        },
                                        onTogglePinned = vm::toggleBookPinned,
                                        onToggleFinished = { openReading(it) },
                                        onRatingPreview = { rating ->
                                            holdPopup = BookRatingHoldState(book.id, rating)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        },
                                        onRatingCommit = { rating -> vm.updateRating(book.id, rating) },
                                        onRatingCancel = { holdPopup = null }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!showSettings && screenMode == BookScreenMode.MAIN) {
            BookBottomBar(
                selected = selectedTab,
                onSelected = {
                    selectedTabName = it.name
                    scope.launch { listState.scrollToItem(0) }
                }
            )
        }
    }

    holdPopup?.let { popup ->
        Dialog(
            onDismissRequest = { holdPopup = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                LocalEntryHoldPopup(
                    code = popup.bookId,
                    rating = popup.rating,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (scannerOpen) {
        BuiltInBookScannerDialog(
            onDismiss = { scannerOpen = false },
            onIsbn = { isbn ->
                scannerOpen = false
                vm.applyScannedIsbn(isbn)
                screenModeName = BookScreenMode.ADD.name
                showSettings = false
            }
        )
    }

    coverTarget?.let { target ->
        CoverSourceDialog(
            onDismiss = { coverTargetName = null },
            onGallery = {
                if (target == BookCoverTarget.DRAFT) {
                    draftCoverPicker.launch(arrayOf("image/*"))
                } else {
                    bookCoverPicker.launch(arrayOf("image/*"))
                }
                coverTargetName = null
            },
            onCamera = { coverCameraLauncher.launch(null) },
            onSearch = {
                coverSearchTargetName = target.name
                coverTargetName = null
                val defaultQuery = when (target) {
                    BookCoverTarget.DRAFT -> listOf(vm.titleInput, vm.authorsInput).filter { it.isNotBlank() }.joinToString(" ")
                    BookCoverTarget.SELECTED -> selectedBook?.let { listOf(it.title, it.authors).filter { value -> value.isNotBlank() }.joinToString(" ") }.orEmpty()
                }
                vm.prepareCoverSearch(defaultQuery)
                showCoverSearch = true
            }
        )
    }

    if (showCoverSearch) {
        CoverSearchDialog(
            vm = vm,
            onDismiss = {
                showCoverSearch = false
                coverTargetName = null
                coverSearchTargetName = null
            },
            onPick = { url ->
                when (coverSearchTarget ?: coverTarget) {
                    BookCoverTarget.DRAFT -> vm.updateDraftCover(url)
                    BookCoverTarget.SELECTED -> vm.selectedBookId?.let { vm.updateBookCover(it, url) }
                    null -> Unit
                }
                showCoverSearch = false
                coverTargetName = null
                coverSearchTargetName = null
            }
        )
    }

    editingNoteId?.let { noteId ->
        vm.selectedBookNotes.firstOrNull { it.id == noteId }?.let { note ->
            BookNoteEditDialog(
                note = note,
                onDismiss = { editingNoteId = null },
                onSave = { dateText, pageText, text, result ->
                    vm.updateSessionNote(note.id, dateText, pageText, text) { saved ->
                        result(saved)
                        if (saved) editingNoteId = null
                    }
                },
                onDelete = {
                    vm.deleteSessionNote(note)
                    editingNoteId = null

                }
            )
        } ?: run { editingNoteId = null }
    }

    if (showNotesDialog && editingNoteId == null && manualLogInitialMode == null) {
        val filterKind = notesFilterKindName?.let { runCatching { BookNoteKind.valueOf(it) }.getOrNull() }
        BookNotesDialog(
            notes = vm.selectedBookNotes,
            filterKind = filterKind,
            onDismiss = {
                showNotesDialog = false
                notesFilterKindName = null
            },
            onAdd = {
                manualLogInitialMode = "NOTE"
            },
            onOpenNote = { note ->
                editingNoteId = note.id
            }
        )
    }

    if (showSessionsDialog && editingSessionId == null && manualLogInitialMode == null) {
        BookSessionsDialog(
            sessions = vm.selectedBookSessions,
            onDismiss = { showSessionsDialog = false },
            onAdd = {
                manualLogInitialMode = "SESSION"
            },
            onEdit = { session -> editingSessionId = session.id }
        )
    }

    globalNotesFilterKindName?.takeIf { editingNoteId == null }?.let { rawKind ->
        val filterKind = runCatching { BookNoteKind.valueOf(rawKind) }.getOrNull()
        BookGlobalNotesDialog(
            notes = vm.allBookNotes,
            filterKind = filterKind,
            onDismiss = { globalNotesFilterKindName = null },
            onOpenNote = { note ->
                vm.selectBook(note.bookId)
                editingNoteId = note.id
            }
        )
    }

    if (showGlobalSessionsDialog && editingSessionId == null) {
        BookGlobalSessionsDialog(
            sessions = vm.allBookSessions.filter { row -> sessionsDay == null || (!row.session.excludeFromStatistics && bookLocalDate(row.session.activityAt) == sessionsDay) },
            title = sessionsDay?.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)) ?: "Reading sessions",
            onDismiss = { showGlobalSessionsDialog = false },
            onEdit = { session ->
                vm.selectBook(session.bookId)
                editingSessionId = session.id
            }
        )
    }

    selectedBook?.let { book ->
        manualLogInitialMode?.let { mode ->
            BookManualLogDialog(
                book = book,
                initialMode = mode,
                initialKind = notesFilterKindName?.let { runCatching { BookNoteKind.valueOf(it) }.getOrNull() } ?: BookNoteKind.NOTE,
                onDismiss = { manualLogInitialMode = null },
                onSaveNote = { kind, dateText, pageText, minutesText, noteText ->
                    vm.addManualReadingNote(book.id, kind, dateText, pageText, minutesText, noteText)
                    manualLogInitialMode = null
                },
                onSaveSession = { endedText, minutesText, pageReachedText ->
                    vm.addManualReadingSession(book.id, endedText, minutesText, pageReachedText)
                    manualLogInitialMode = null
                }
            )
        }
        actionNoteKindName?.let { rawKind ->
            val kind = runCatching { BookNoteKind.valueOf(rawKind) }.getOrDefault(BookNoteKind.THOUGHT)
            BookQuickNoteDialog(
                book = book,
                kind = kind,
                elapsedSeconds = bookActiveElapsedSeconds(vm.activeStartedAtMs, nowMs, vm.activePausedAtMs, vm.activePausedTotalMs),
                onDismiss = { actionNoteKindName = null },
                onSave = { dateText, pageText, minutesText, noteText ->
                    vm.addManualReadingNote(book.id, kind, dateText, pageText, minutesText, noteText)
                    actionNoteKindName = null
                }
            )
        }
        if (showCountdownDialog) {
            BookCountdownDialog(
                book = book,
                onDismiss = { showCountdownDialog = false },
                onStart = { minutes ->
                    vm.startCountdown(minutes)
                    showCountdownDialog = false
                }
            )
        }
        if (showBookGraphDialog) {
            BookGraphDialog(
                book = book,
                sessions = vm.selectedBookSessions,
                onDismiss = { showBookGraphDialog = false },
                onPointSelected = { point ->
                    vm.setStatus("${point.date}: ${point.pagesRead} on this book graph.")
                }
            )
        }
    }

    editingSessionId?.let { sessionId ->
        vm.selectedBookSessions.firstOrNull { it.id == sessionId }?.let { session ->
            BookSessionEditDialog(
                session = session,
                onExcludedChange = { vm.setSessionStatisticsExcluded(session, it) },
                onDismiss = { editingSessionId = null },
                onSave = { endedText, minutesText, pageReachedText, excluded ->
                    vm.updateReadingSession(session, endedText, minutesText, pageReachedText, excluded)
                    editingSessionId = null

                },
                onDelete = {
                    vm.deleteReadingSession(session)
                    editingSessionId = null

                }
            )
        } ?: run { editingSessionId = null }
    }

    if (showFinishDialog && selectedBook != null) {
        FinishReadingDialog(
            book = selectedBook,
            pageDraft = finishPageDraft,
            onPageChange = { finishPageDraft = it.filter { char -> char.isDigit() }.take(6) },
            onDismiss = { showFinishDialog = false },
            onDiscard = {
                vm.discardReadingSession()
                showFinishDialog = false
                screenModeName = BookScreenMode.DETAIL.name
            },
            onSave = {
                if (vm.finishReading(selectedBook.id, finishPageDraft)) {
                    showFinishDialog = false
                    screenModeName = BookScreenMode.SESSION_NOTE.name
                }
            }
        )
    }
}
