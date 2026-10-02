package com.roinur.booktracker

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.roinur.booktracker.data.database.BookGoalRepository
import com.roinur.booktracker.data.database.BookTrackerDatabase
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors

internal object BookGoalNotifications {
    private const val CHANNEL = "book_goals"
    private const val TAG = "book_goal"
    private val executor = Executors.newSingleThreadExecutor()

    fun refresh(context: Context, after: (() -> Unit)? = null) {
        val app = context.applicationContext
        executor.execute {
            try {
                BookTrackerDatabase(app).use { db ->
                    sync(app, db.listBooks("", BookSortField.ADDED, false), BookGoalRepository(db).list(),
                        db.listAllSessions().map { it.session }, db.listCollectionTypes())
                }
            } catch (error: Exception) {
                android.util.Log.w("BookGoalNotifications", "Could not refresh book goals", error)
            } finally { after?.invoke() }
        }
    }

    fun update(context: Context, books: List<BookRow>, goals: Map<Int, BookGoal>, sessions: List<BookReadingSessionRow>, collectionTypes: Map<String, BookCollectionType>) {
        executor.execute { runCatching { sync(context.applicationContext, books, goals, sessions, collectionTypes) }
            .onFailure { android.util.Log.w("BookGoalNotifications", "Could not update book goals", it) } }
    }

    private fun sync(context: Context, books: List<BookRow>, goals: Map<Int, BookGoal>, sessions: List<BookReadingSessionRow>, collectionTypes: Map<String, BookCollectionType>) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Book goals", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "Daily progress towards your book goals" })
        val active = books.filter { it.id in goals && !bookGoalProgress(it, goals.getValue(it.id), sessions, books = books, collectionTypes = collectionTypes).completed }
        val prefs = context.getSharedPreferences("book_goal_notifications", Context.MODE_PRIVATE)
        val old = prefs.getStringSet("book_ids", emptySet()).orEmpty()
        val ids = active.map { it.id.toString() }.toSet()
        (old - ids).forEach { id -> manager.cancel(TAG, id.toInt()) }
        prefs.edit().putStringSet("book_ids", ids).apply()
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) active.forEach { book ->
            val progress = bookGoalProgress(book, goals.getValue(book.id), sessions, books = books, collectionTypes = collectionTypes)
            val open = PendingIntent.getActivity(context, book.id, Intent(context, MainActivity::class.java).apply {
                action = "com.roinur.booktracker.OPEN_BOOK_GOAL"
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("goal_book_id", book.id)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val time = progress.todaySeconds?.let { " · ~${bookFormatCompactHours(it)}" }.orEmpty()
            val today = if (progress.todayRemaining == 0L) "Today's goal reached" else "${progress.todayRemaining} pages left today$time"
            val total = "${progress.remainingPages} pages · ${progress.daysRemaining} days left" +
                progress.remainingSeconds?.let { " · ~${bookFormatCompactHours(it)}" }.orEmpty()
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_book_goal_24).setContentTitle(book.title).setContentText(today)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$today\n$total"))
                .setSubText("Book goal").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .setShowWhen(false).setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setProgress(progress.todayTarget.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    progress.todayPages.coerceAtMost(progress.todayTarget).toInt(), false)
                .setSilent(true).build()
            try { manager.notify(TAG, book.id, notification) } catch (_: SecurityException) { /* Permission may change while updating. */ }
        }
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(context, 0, Intent(context, BookGoalRefreshReceiver::class.java)
            .setAction("com.roinur.booktracker.REFRESH_BOOK_GOALS"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (active.isEmpty()) alarm.cancel(pending) else {
            val nextDay = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).plusMinutes(1).toInstant().toEpochMilli()
            // No exact-alarm permission or continuously running service needed for a daily reminder.
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextDay, pending)
        }
    }
}

class BookGoalRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        BookGoalNotifications.refresh(context) { pending.finish() }
    }
}
