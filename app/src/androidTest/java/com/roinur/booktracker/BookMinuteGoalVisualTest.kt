package com.roinur.booktracker

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.roinur.booktracker.data.database.BookGoalRepository
import com.roinur.booktracker.data.database.BookTrackerDatabase
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Production controls with an in-memory fixture; never changes a real book or goal. */
class BookMinuteGoalVisualTest {
    @Test fun minuteChoicePreviewSaveAndReopen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val before = BookTrackerDatabase(context).use { db ->
            db.listBooks("", BookSortField.ADDED, false) to BookGoalRepository(db).list()
        }
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val book = BookRow(-1, "", "Example book", "Author", 300, 100, BookStatus.READING, 0,
            "", "", "", "", false, "", "", "", "", 900)
        val sessions = listOf(BookReadingSessionRow(-1, -1, LocalDate.now().toString(), LocalDate.now().toString(), 900, 10, 100))
        val goal = mutableStateOf(BookGoal(-1, BookGoalMode.DAILY_PAGES, dailyPages = 50))
        val showDialog = mutableStateOf(true)
        lateinit var view: ComposeView
        try {
            instrumentation.runOnMainSync {
                view = ComposeView(activity)
                activity.addContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                view.setContent {
                    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF8BACFF), background = Color(0xFF0B0A12),
                        surface = Color(0xFF14121E), surfaceContainerLow = Color(0xFF11101A), onSurface = Color(0xFFE5E1EF))) {
                        Surface(Modifier.fillMaxSize()) {
                            Column(Modifier.safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.Center) {
                                BookGoalCard(book, goal.value, bookGoalProgress(book, goal.value, sessions),
                                    onConfigure = { showDialog.value = true }, modifier = Modifier.width(210.dp), onPartRating = { _, _ -> })
                            }
                            if (showDialog.value) BookGoalDialog(book, goal.value, sessions, listOf(book), emptyMap(),
                                onSave = { draft, done -> goal.value = draft; done(true) }, onRemove = { it(false) },
                                onDismiss = { showDialog.value = false })
                        }
                    }
                }
            }
            fun nodes(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
                node.refresh()
                return listOf(node) + (0 until node.childCount).flatMap { node.getChild(it)?.let(::nodes).orEmpty() }
            }
            fun visible(): List<AccessibilityNodeInfo> {
                repeat(30) {
                    instrumentation.uiAutomation.rootInActiveWindow?.let { return nodes(it) }
                    SystemClock.sleep(100)
                }
                error("No active app window for the visual check")
            }
            fun settle() { instrumentation.waitForIdleSync(); SystemClock.sleep(500) }
            fun click(text: String) {
                var node = visible().first { it.text?.toString() == text }
                while (!node.isClickable && node.parent != null) node = node.parent
                assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)); settle()
            }
            fun capture(name: String) {
                settle()
                val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                File(context.externalCacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            settle(); click("Minutes")
            assertTrue(visible().any { it.text?.toString() == "Minutes per day" })
            assertTrue(visible().any { it.text?.toString() == "15 / 30 min" })
            val field = visible().first { it.isEditable && it.text?.toString() == "30" }
            assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "45")
            }))
            settle()
            capture("book-minute-goal-dialog.png")
            assertTrue("Minute preview: ${visible().mapNotNull { it.text }}", visible().any { it.text?.toString() == "15 / 45 min" })
            click("Save goal")
            assertEquals(BookGoalMode.DAILY_MINUTES, goal.value.mode)
            assertEquals(45, goal.value.dailyMinutes)
            capture("book-minute-goal-card.png")
            assertTrue(visible().any { it.text?.toString() == "30 min left" })
            instrumentation.runOnMainSync { showDialog.value = true }; settle()
            assertTrue(visible().any { it.isEditable && it.text?.toString() == "45" })
            assertTrue(visible().any { it.text?.toString() == "Minutes per day" })
        } finally {
            instrumentation.runOnMainSync { view.disposeComposition(); (view.parent as? ViewGroup)?.removeView(view); activity.finish() }
        }
        val after = BookTrackerDatabase(context).use { db ->
            db.listBooks("", BookSortField.ADDED, false) to BookGoalRepository(db).list()
        }
        assertEquals(before, after)
    }
}
