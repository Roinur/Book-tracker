package com.roinur.booktracker

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.RemoteViews

internal object ReadingGoalsNotificationViews {
    fun create(context: Context, progress: ReadingGoalsProgress): Pair<RemoteViews, RemoteViews> {
        val prefs = context.getSharedPreferences(BOOK_TRACKER_PREFS, Context.MODE_PRIVATE)
        val accent = prefs.getInt(KEY_GOAL_NOTIFICATION_ACCENT, 0xFF8BC1FF.toInt())
        // The notification shade follows the phone theme, which can differ from the app's surfaces.
        val darkShade = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val track = if (darkShade) 0xFF66718A.toInt() else 0xFF9BA1AE.toInt()
        val compact = RemoteViews(context.packageName, R.layout.reading_goals_compact)
        val expanded = RemoteViews(context.packageName, R.layout.reading_goals_expanded)
        val ringIds = listOf(R.id.goal_daily_ring, R.id.goal_monthly_ring, R.id.goal_yearly_ring)
        val labelIds = listOf(R.id.goal_daily_label, R.id.goal_monthly_label, R.id.goal_yearly_label)
        val valueIds = listOf(R.id.goal_daily_value, R.id.goal_monthly_value, R.id.goal_yearly_value)
        val unitIds = listOf(R.id.goal_daily_unit, R.id.goal_monthly_unit, R.id.goal_yearly_unit)
        val remainingIds = listOf(R.id.goal_daily_remaining, R.id.goal_monthly_remaining, R.id.goal_yearly_remaining)
        progress.rows.forEachIndexed { index, row ->
            val bitmap = ring(row.fraction, accent, track, (40 * context.resources.displayMetrics.density).toInt().coerceAtLeast(40))
            for (view in listOf(compact, expanded)) {
                view.setImageViewBitmap(ringIds[index], bitmap)
                view.setContentDescription(ringIds[index], row.notificationLine)
            }
            expanded.setTextViewText(labelIds[index], row.label)
            expanded.setTextViewText(valueIds[index], if (row.enabled) "${row.current} / ${row.target}" else "Not set")
            expanded.setTextViewText(unitIds[index], if (row.enabled) row.unit else "")
            expanded.setTextViewText(remainingIds[index], if (!row.enabled) "" else if (row.remaining == 0) "Reached" else "${row.remaining} left")
        }
        return compact to expanded
    }

    internal fun ring(fraction: Float, accent: Int, track: Int, pixels: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val stroke = pixels * 0.12f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND; color = track }
        val inset = stroke / 2f + 1f
        val bounds = RectF(inset, inset, pixels - inset, pixels - inset)
        canvas.drawOval(bounds, paint)
        paint.color = accent
        if (fraction >= 1f) canvas.drawOval(bounds, paint)
        else if (fraction > 0f) canvas.drawArc(bounds, -90f, fraction * 360f, false, paint)
        return bitmap
    }
}
