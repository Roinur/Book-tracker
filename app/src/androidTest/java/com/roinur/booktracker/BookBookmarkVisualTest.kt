package com.roinur.booktracker

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.os.Bundle
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.roinur.booktracker.data.database.BookTrackerDatabase
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Uses production UI with an in-memory fixture; never inserts demonstration books or sessions. */
class BookBookmarkVisualTest {
    @Test fun renderBookmarkAndFinishDialogWithoutChangingLibrary() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val before = BookTrackerDatabase(context).use { it.listBooks("", BookSortField.ADDED, false) }
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val book = BookRow(-1, "", "Compendium", "Example author", 200, 35, BookStatus.READING, 0,
            "", "", "", "", false, "", "", "", "", 3600,
            parts = listOf(BookPart("First work", 1, 100), BookPart("Second work", 101, 200)), bookmarkPage = 65)
        val finish = mutableStateOf(false)
        val finishPage = mutableStateOf("20")
        val finishBook = book.copy(currentPage = 20, bookmarkPage = null)
        val savedEntries = mutableListOf<BookReadingEntry?>()
        lateinit var view: ComposeView
        try {
            instrumentation.runOnMainSync {
                view = ComposeView(activity)
                activity.addContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                view.setContent {
                    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF8BACFF), background = Color(0xFF0B0A12),
                        surface = Color(0xFF14121E), surfaceContainerLow = Color(0xFF11101A), surfaceContainerHigh = Color(0xFF211E2B),
                        onSurface = Color(0xFFE5E1EF), onSurfaceVariant = Color(0xFFB9B3CC), outlineVariant = Color(0xFF302B40))) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface) {
                        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BookAdaptiveViewport {
                                Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text("Book details", Modifier.fillMaxWidth().padding(vertical = 18.dp), style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    BookDetailCard(onCompletionDate = {}, modifier = Modifier.weight(1f), book = book,
                                        bookGoal = null, goalOpenRequest = 0, allGoalSessions = emptyList(), allPaceBooks = listOf(book), collectionTypes = emptyMap(),
                                        onSaveGoal = { _, result -> result(false) }, onRemoveGoal = { it(false) }, active = false,
                                        activeStartedAtMs = 0, activePausedAtMs = 0, activePausedTotalMs = 0, nowMs = System.currentTimeMillis(),
                                        notes = emptyList(), sessions = emptyList(), onStart = {}, onStatusChange = {}, onRating = {},
                                        onPartRating = { _, _ -> }, onRatingPreview = {}, onRatingCancel = {}, onPickCover = {}, onAuthorSelected = {},
                                        onEdit = {}, onOpenNotes = {}, onOpenSessions = {}, onAddManual = {}, onOpenGraph = {})
                                }
                            }
                            if (finish.value) FinishReadingDialog(finishBook, finishPage.value, { finishPage.value = it }, {}, {}, { bookmark, calculated ->
                                savedEntries += bookFinishReadingEntry(finishBook, finishPage.value, bookmark, calculated)
                            })
                        }
                        }
                    }
                }
            }
            fun capture(name: String) {
                instrumentation.waitForIdleSync()
                SystemClock.sleep(750)
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                requireNotNull(bitmap)
                File(context.externalCacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            capture("bookmark-details-qa.png")
            instrumentation.runOnMainSync { finish.value = true }
            fun nodes(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = listOf(node) +
                (0 until node.childCount).flatMap { node.getChild(it)?.let(::nodes).orEmpty() }
            instrumentation.waitForIdleSync()
            var visible = nodes(instrumentation.uiAutomation.rootInActiveWindow)
            repeat(10) {
                if (visible.none { it.text?.toString() == "Page reached" }) {
                    SystemClock.sleep(150)
                    visible = nodes(instrumentation.uiAutomation.rootInActiveWindow)
                }
            }
            assertTrue(visible.any { it.text?.toString() == "Page reached" })
            assertFalse(visible.any { it.isCheckable })
            fun currentNodes() = nodes(instrumentation.uiAutomation.rootInActiveWindow)
            fun setField(old: String, value: String, lastMatch: Boolean = false) {
                var candidate: AccessibilityNodeInfo? = null
                repeat(20) {
                    if (candidate == null) {
                        val matches = currentNodes().filter { it.isEditable && it.text?.toString().orEmpty() == old }
                        candidate = if (lastMatch) matches.lastOrNull() else matches.firstOrNull()
                        if (candidate == null) SystemClock.sleep(100)
                    }
                }
                val field = requireNotNull(candidate) { "Missing input $old; editable fields: ${currentNodes().filter { it.isEditable }.map { it.text }}" }
                assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
                }))
                instrumentation.waitForIdleSync()
                SystemClock.sleep(400)
            }
            fun click(text: String) {
                var node = currentNodes().first { it.text?.toString() == text }
                while (!node.isClickable && node.parent != null) node = node.parent
                assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                instrumentation.waitForIdleSync()
                SystemClock.sleep(400)
            }
            fun buttonBounds(text: String): Rect {
                var candidate: AccessibilityNodeInfo? = null
                repeat(20) {
                    if (candidate == null) {
                        candidate = currentNodes().firstOrNull { it.text?.toString() == text }
                        if (candidate == null) SystemClock.sleep(100)
                    }
                }
                var node = requireNotNull(candidate) { "Missing button $text: ${currentNodes().mapNotNull { it.text }}" }
                while (!node.isClickable && node.parent != null) node = node.parent
                return Rect().also { node.getBoundsInScreen(it) }
            }
            assertTrue(currentNodes().any { it.text?.toString() == "Page reached" })
            setField("20", "144")
            assertEquals("144", finishPage.value)
            capture("bookmark-finish-qa.png")
            click("Save session")
            assertEquals(BookReadingEntry(124, 144, null), savedEntries.single())
            click("Page calculator")
            setField("20", "100")
            assertEquals("144", finishPage.value)
            setField("", "110")
            capture("bookmark-calculator-qa.png")
            assertEquals("30", finishPage.value)
            val automation = instrumentation.uiAutomation
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(300)
            val saveBeforeKeyboard = buttonBounds("Save session")
            val endField = currentNodes().first { it.isEditable && it.text?.toString() == "110" }
            val fieldBounds = Rect().also { endField.getBoundsInScreen(it) }
            val endBeforeKeyboard = Rect(fieldBounds)
            val eventTime = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(eventTime, SystemClock.uptimeMillis(), action,
                    fieldBounds.exactCenterX(), fieldBounds.exactCenterY(), 0)
                automation.injectInputEvent(event, true)
                event.recycle()
            }
            var ime = automation.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            repeat(20) {
                if (ime == null) {
                    SystemClock.sleep(150)
                    ime = automation.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                }
            }
            assertNotNull("Keyboard must be visible for the layout check", ime)
            capture("finish-reading-keyboard-qa.png")
            val summaryBounds = Rect().also { bounds ->
                currentNodes().first { it.text?.toString() == "Continue from page 110" }.getBoundsInScreen(bounds)
            }
            val saveBounds = buttonBounds("Save session")
            val endAfterKeyboard = Rect().also { bounds ->
                currentNodes().first { it.isEditable && it.text?.toString() == "110" }.getBoundsInScreen(bounds)
            }
            val imeBounds = Rect().also { requireNotNull(ime).getBoundsInScreen(it) }
            assertTrue("Bookmark must be above the actions", summaryBounds.bottom <= saveBounds.top)
            assertTrue("Save must be above the keyboard", saveBounds.bottom <= imeBounds.top)
            assertEquals("Keyboard must not change button horizontal bounds",
                listOf(saveBeforeKeyboard.left, saveBeforeKeyboard.right), listOf(saveBounds.left, saveBounds.right))
            assertEquals("Keyboard must not change field horizontal bounds",
                listOf(endBeforeKeyboard.left, endBeforeKeyboard.right), listOf(endAfterKeyboard.left, endAfterKeyboard.right))
            click("Save session")
            assertEquals(BookReadingEntry(10, 30, 110), savedEntries.last())
            click("Page calculator")
            assertFalse(currentNodes().any { it.isCheckable })
            capture("bookmark-calculator-summary-qa.png")
            click("Save session")
            instrumentation.waitForIdleSync()
            assertEquals(BookReadingEntry(10, 30, 110), savedEntries.last())
            click("Reset")
            assertEquals("20", finishPage.value)
        } finally {
            instrumentation.runOnMainSync { view.disposeComposition(); (view.parent as? ViewGroup)?.removeView(view); activity.finish() }
        }
        val after = BookTrackerDatabase(context).use { it.listBooks("", BookSortField.ADDED, false) }
        assertEquals(before, after)
    }
}
