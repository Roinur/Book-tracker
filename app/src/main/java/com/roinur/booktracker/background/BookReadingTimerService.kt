package com.roinur.booktracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlin.math.max

private const val BOOK_TIMER_PREFS = "book_tracker_prefs"
private const val KEY_BOOK_ACTIVE_ID_SERVICE = "book_active_id"
private const val KEY_BOOK_ACTIVE_STARTED_MS_SERVICE = "book_active_started_ms"
private const val KEY_BOOK_ACTIVE_PAUSED_AT_MS_SERVICE = "book_active_paused_at_ms"
private const val KEY_BOOK_ACTIVE_PAUSED_TOTAL_MS_SERVICE = "book_active_paused_total_ms"
private const val KEY_BOOK_ACTIVE_TITLE_SERVICE = "book_active_title"
private const val KEY_BOOK_ACTIVE_AUTHORS_SERVICE = "book_active_authors"
private const val KEY_BOOK_ACTIVE_PAGE_SERVICE = "book_active_page"
private const val KEY_BOOK_ACTIVE_PAGE_COUNT_SERVICE = "book_active_page_count"
private const val KEY_BOOK_COUNTDOWN_END_MS_SERVICE = "book_countdown_end_ms"
private const val KEY_BOOK_COUNTDOWN_TOTAL_MS_SERVICE = "book_countdown_total_ms"
private const val KEY_BOOK_COUNTDOWN_DONE_MS_SERVICE = "book_countdown_done_ms"
private const val BOOK_TIMER_CHANNEL_ID = "book_reading_timer_v2"
private const val BOOK_TIMER_NOTIFICATION_ID = 42081
private const val BOOK_COUNTDOWN_DONE_NOTIFICATION_ID = 42082
private const val ACTION_SYNC = "com.roinur.booktracker.booktimer.SYNC"
private const val ACTION_STOP = "com.roinur.booktracker.booktimer.STOP"

internal fun calculateBookTimerElapsedSeconds(
    startedMs: Long,
    pausedAtMs: Long,
    pausedTotalMs: Long,
    nowMs: Long
): Long {
    val started = startedMs.takeIf { it > 0L } ?: nowMs
    val effectiveNow = pausedAtMs.takeIf { it > 0L } ?: nowMs
    return max(0L, (effectiveNow - started - pausedTotalMs.coerceAtLeast(0L)) / 1000L)
}

class BookReadingTimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val prefs = getSharedPreferences(BOOK_TIMER_PREFS, Context.MODE_PRIVATE)
            if (prefs.getInt(KEY_BOOK_ACTIVE_ID_SERVICE, -1) <= 0) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            maybeNotifyCountdownComplete(prefs)
            val notification = buildNotification()
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            runCatching { manager?.notify(BOOK_TIMER_NOTIFICATION_ID, notification) }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            handler.removeCallbacks(tick)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val prefs = getSharedPreferences(BOOK_TIMER_PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_BOOK_ACTIVE_ID_SERVICE, -1) <= 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                BOOK_TIMER_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(BOOK_TIMER_NOTIFICATION_ID, notification)
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val prefs = getSharedPreferences(BOOK_TIMER_PREFS, Context.MODE_PRIVATE)
        val title = prefs.getString(KEY_BOOK_ACTIVE_TITLE_SERVICE, "").orEmpty().ifBlank { "Reading session" }
        val authors = prefs.getString(KEY_BOOK_ACTIVE_AUTHORS_SERVICE, "").orEmpty()
        val page = prefs.getInt(KEY_BOOK_ACTIVE_PAGE_SERVICE, 0).coerceAtLeast(0)
        val pageCount = prefs.getInt(KEY_BOOK_ACTIVE_PAGE_COUNT_SERVICE, 0).coerceAtLeast(0)
        val paused = prefs.getLong(KEY_BOOK_ACTIVE_PAUSED_AT_MS_SERVICE, 0L) > 0L
        val elapsed = serviceElapsedSeconds(prefs, System.currentTimeMillis())
        val countdownEndMs = prefs.getLong(KEY_BOOK_COUNTDOWN_END_MS_SERVICE, 0L).coerceAtLeast(0L)
        val countdownRemainingMs = (countdownEndMs - System.currentTimeMillis()).coerceAtLeast(0L)
        val countdownActive = countdownRemainingMs > 0L
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pageText = if (pageCount > 0) "$page/$pageCount" else "$page pages"
        val builder = NotificationCompat.Builder(this, BOOK_TIMER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(if (paused) "Reading paused" else "Reading now")
            .setContentText(
                if (countdownActive) {
                    "$title - ${formatCountdown(countdownRemainingMs)} left - $pageText"
                } else {
                    "$title - ${formatTimer(elapsed)} - $pageText"
                }
            )
            .setSubText(authors.ifBlank { "Book Tracker" })
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    buildString {
                        append(title)
                        if (authors.isNotBlank()) append("\n").append(authors)
                        append("\n").append(if (paused) "Paused at " else "Elapsed ")
                        append(formatTimer(elapsed))
                        append(" - page ").append(pageText)
                        if (countdownActive) {
                            append("\nCountdown ")
                            append(formatCountdown(countdownRemainingMs))
                        }
                    }
                )
            )
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (countdownActive) {
            builder
                .setWhen(countdownEndMs)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
        } else {
            builder.setShowWhen(false)
        }
        return builder.build()
    }

    private fun maybeNotifyCountdownComplete(prefs: android.content.SharedPreferences) {
        val endMs = prefs.getLong(KEY_BOOK_COUNTDOWN_END_MS_SERVICE, 0L)
        if (endMs <= 0L || endMs > System.currentTimeMillis()) return
        if (prefs.getLong(KEY_BOOK_COUNTDOWN_DONE_MS_SERVICE, 0L) == endMs) return
        prefs.edit()
            .putLong(KEY_BOOK_COUNTDOWN_DONE_MS_SERVICE, endMs)
            .remove(KEY_BOOK_COUNTDOWN_END_MS_SERVICE)
            .remove(KEY_BOOK_COUNTDOWN_TOTAL_MS_SERVICE)
            .apply()
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, BOOK_TIMER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Countdown finished")
            .setContentText("Your reading countdown is done.")
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        runCatching { manager?.notify(BOOK_COUNTDOWN_DONE_NOTIFICATION_ID, notification) }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(BOOK_TIMER_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            BOOK_TIMER_CHANNEL_ID,
            "Reading timer",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Shows the active book reading timer on the lockscreen and notification shade."
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun serviceElapsedSeconds(prefs: android.content.SharedPreferences, nowMs: Long): Long {
        val started = prefs.getLong(KEY_BOOK_ACTIVE_STARTED_MS_SERVICE, 0L).takeIf { it > 0L } ?: nowMs
        val pausedAt = prefs.getLong(KEY_BOOK_ACTIVE_PAUSED_AT_MS_SERVICE, 0L)
        val pausedTotal = prefs.getLong(KEY_BOOK_ACTIVE_PAUSED_TOTAL_MS_SERVICE, 0L).coerceAtLeast(0L)
        return calculateBookTimerElapsedSeconds(started, pausedAt, pausedTotal, nowMs)
    }

    private fun formatTimer(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        val hours = safe / 3600L
        val minutes = (safe % 3600L) / 60L
        val secs = safe % 60L
        return "%02dh %02dm %02ds".format(hours, minutes, secs)
    }

    private fun formatCountdown(milliseconds: Long): String {
        val totalSeconds = (milliseconds.coerceAtLeast(0L) + 999L) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val secs = totalSeconds % 60L
        return if (hours > 0L) {
            "%02d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%02d:%02d".format(minutes, secs)
        }
    }

    companion object {
        fun sync(context: Context) {
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, BookReadingTimerService::class.java).setAction(ACTION_SYNC)
            )
        }

        fun stop(context: Context) {
            context.applicationContext.startService(
                Intent(context.applicationContext, BookReadingTimerService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
