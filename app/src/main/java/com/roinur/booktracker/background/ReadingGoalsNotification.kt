package com.roinur.booktracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

internal object ReadingGoalsNotification {
    const val OPEN_ACTION = "com.roinur.booktracker.OPEN_READING_GOALS"
    private const val CHANNEL = "reading_goals"
    private const val TAG = "reading_goals"
    private const val ID = 1

    /** Returns whether a configured reminder needs the next calendar-day refresh. */
    fun sync(context: Context, goals: BookReadingGoals, stats: BookStats): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        val enabled = context.getSharedPreferences(BOOK_TRACKER_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_READING_GOAL_NOTIFICATIONS_ENABLED, true)
        val progress = readingGoalsProgress(goals, stats)
        if (!enabled || !progress.hasGoals) {
            manager.cancel(TAG, ID)
            return false
        }
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Reading goals", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "Daily, monthly and yearly reading targets" })
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return true
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).apply {
            action = OPEN_ACTION
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val style = NotificationCompat.InboxStyle().setBigContentTitle("Reading goals")
        progress.rows.forEach { style.addLine(it.notificationLine) }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_book_goal_24)
            .setContentTitle("Reading goals")
            .setContentText(progress.rows.filter { it.enabled }.joinToString(" · ") {
                "${it.label}: ${it.current}/${it.target} ${it.unit}"
            })
            .setStyle(style).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setShowWhen(false).setCategory(NotificationCompat.CATEGORY_PROGRESS).setSilent(true).build()
        try { manager.notify(TAG, ID, notification) } catch (_: SecurityException) { /* Permission changed during refresh. */ }
        return true
    }
}
