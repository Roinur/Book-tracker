package com.roinur.booktracker

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.roinur.booktracker.data.backup.BookBackupStatus
import com.roinur.booktracker.data.backup.BookBackupStatusStore
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BookReadingGoalsNotificationTest {
    @Test fun collapsedLayoutHasOnlyThreeHorizontalRingsAndExpandedHasAllGoalDetails() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val progress = ReadingGoalsProgress(ReadingGoalProgress("Daily", 25, 50, "pages"),
            ReadingGoalProgress("Monthly", 60, 200, "minutes"), ReadingGoalProgress("Yearly", 14, 20, "books"))
        instrumentation.runOnMainSync {
            val (compact, expanded) = ReadingGoalsNotificationViews.create(context, progress)
            val collapsed = compact.apply(context, FrameLayout(context))
            val opened = expanded.apply(context, FrameLayout(context))
            val density = context.resources.displayMetrics.density
            fun measure(view: View) {
                view.measure(View.MeasureSpec.makeMeasureSpec((320 * density).toInt(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            }
            measure(collapsed); measure(opened)
            fun textCount(view: View): Int = when (view) {
                is TextView -> 1
                is ViewGroup -> (0 until view.childCount).sumOf { textCount(view.getChildAt(it)) }
                else -> 0
            }
            assertEquals(0, textCount(collapsed))
            assertTrue(collapsed.measuredHeight <= 48 * density + 1)
            val ringIds = listOf(R.id.goal_daily_ring, R.id.goal_monthly_ring, R.id.goal_yearly_ring)
            val positions = ringIds.map { id ->
                val image = collapsed.findViewById<ImageView>(id)
                val parent = image.parent as View
                intArrayOf(parent.left + image.left, parent.top + image.top)
            }
            assertTrue(positions[0][0] < positions[1][0] && positions[1][0] < positions[2][0])
            assertEquals(positions[0][1], positions[1][1]); assertEquals(positions[1][1], positions[2][1])
            assertTrue(opened.measuredHeight <= 252 * density)
            assertEquals("Daily", opened.findViewById<TextView>(R.id.goal_daily_label).text.toString())
            assertEquals("25 / 50", opened.findViewById<TextView>(R.id.goal_daily_value).text.toString())
            assertEquals("minutes", opened.findViewById<TextView>(R.id.goal_monthly_unit).text.toString())
            assertEquals("6 left", opened.findViewById<TextView>(R.id.goal_yearly_remaining).text.toString())
            assertTrue(collapsed.findViewById<ImageView>(R.id.goal_daily_ring).contentDescription.contains("25 left"))
            val ring = ReadingGoalsNotificationViews.ring(0.5f, Color.BLUE, Color.GRAY, 80)
            assertEquals(Color.TRANSPARENT, ring.getPixel(40, 40))
        }
    }

    @Test fun verifiedBackupStatusSurvivesRestartAndDoesNotMislabelADifferentFolder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "backup-status-test-${UUID.randomUUID()}"
        val isolated = object : ContextWrapper(context) {
            override fun getSharedPreferences(ignored: String, mode: Int): SharedPreferences = context.getSharedPreferences(name, mode)
        }
        try {
            BookBackupStatusStore(isolated).record("folder-a", BookBackupStatus().succeeded(1000).failed(2000, "Disk full"))
            val reopened = BookBackupStatusStore(isolated)
            assertEquals(1000L, reopened.load("folder-a").lastSuccessAtMs)
            assertEquals("Disk full", reopened.load("folder-a").error)
            assertEquals(0L, reopened.load("folder-b").lastSuccessAtMs)
        } finally { context.deleteSharedPreferences(name) }
    }
}
