package com.roinur.booktracker

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

internal enum class BookGoalMode { DEADLINE, DAILY_PAGES, DAILY_MINUTES }

internal data class BookGoal(val bookId: Int, val mode: BookGoalMode, val dailyPages: Int = 0,
                             val targetDate: LocalDate? = null, val createdDate: LocalDate = LocalDate.now(),
                             val dailyMinutes: Int = 0)

internal data class BookGoalProgress(
    val remainingPages: Long, val todayPages: Long, val todayTarget: Long, val todayRemaining: Long,
    val todaySeconds: Long?, val remainingSeconds: Long?, val daysRemaining: Long,
    val expectedFinish: LocalDate, val pagesPerHour: Double?, val paceSource: BookPaceSource,
    val completed: Boolean, val overdue: Boolean,
    val timeBased: Boolean = false, val todayReadSeconds: Long = 0L,
    val hasFinishEstimate: Boolean = true
) {
    val usesOverallPace: Boolean get() = paceSource == BookPaceSource.OVERALL
    val dailySeconds: Long? get() = if (timeBased) todayTarget else if (todayTarget == 0L) 0L else
        pagesPerHour?.let { ceil(todayTarget * 3600.0 / it).toLong() }
    val todayValue: Long get() = if (timeBased) todayReadSeconds else todayPages
    val todayProgressLabel: String get() = if (timeBased) "${todayReadSeconds / 60} / ${ceil(todayTarget / 60.0).toLong()} min" else "$todayPages / $todayTarget pages"
    val todayRemainingLabel: String get() = if (timeBased) "${ceil(todayRemaining / 60.0).toLong()} min" else "$todayRemaining pages"
    val todayFraction: Float get() = if (todayTarget <= 0) 1f else
        (todayValue.toDouble() / todayTarget).toFloat().coerceIn(0f, 1f)
}

/** Deadline quota is based on the pages left at the start of today, so logging pages
 * reduces today's remainder without moving the day's target. Missed days catch up tomorrow. */
internal fun bookGoalProgress(book: BookRow, goal: BookGoal, sessions: List<BookReadingSessionRow>,
                              today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault(),
                              books: List<BookRow> = emptyList(), collectionTypes: Map<String, BookCollectionType> = emptyMap()): BookGoalProgress {
    val included = sessions.distinctBy { it.id }.filterNot { it.excludeFromStatistics }
    val todaySessions = included.filter { it.bookId == book.id && bookLocalDate(it.activityAt, zone) == today }
    val todayPages = todaySessions.sumOf { it.pagesRead.coerceAtLeast(0).toLong() }
    val todayTime = todaySessions.sumOf { it.durationSeconds.coerceAtLeast(0L) }
    val completed = book.status == BookStatus.FINISHED || (book.pageCount > 0 && book.currentPage >= book.pageCount)
    val left = if (completed) 0L else (book.pageCount.toLong() - book.currentPage).coerceAtLeast(0L)
    val deadlineDays = goal.targetDate?.let { ChronoUnit.DAYS.between(today, it) + 1L } ?: 1L
    val readingPace = bookReadingPace(book, included, books, collectionTypes)
    val pace = readingPace.pagesPerHour
    fun estimate(pages: Long): Long? = if (pages == 0L) 0L else pace?.let { ceil(pages * 3600.0 / it).toLong() }
    val remainingTime = if (book.pageCount <= 0 && !completed) null else estimate(left)
    if (goal.mode == BookGoalMode.DAILY_MINUTES) {
        val quota = goal.dailyMinutes.coerceAtLeast(1).toLong() * 60L
        val target = if (completed) todayTime else minOf(quota, remainingTime?.plus(todayTime) ?: Long.MAX_VALUE)
        val todayLeft = (target - todayTime).coerceAtLeast(0L)
        val days = if (completed) 0L else remainingTime?.let {
            1L + ceil((it - todayLeft).coerceAtLeast(0L).toDouble() / quota).toLong()
        } ?: 0L
        return BookGoalProgress(left, todayPages, target, todayLeft, todayLeft, remainingTime, days,
            today.plusDays((days - 1).coerceAtLeast(0)), pace, readingPace.source, completed, false,
            timeBased = true, todayReadSeconds = todayTime, hasFinishEstimate = completed || remainingTime != null)
    }
    val target = if (completed) todayPages else when (goal.mode) {
        BookGoalMode.DAILY_PAGES -> minOf(goal.dailyPages.coerceAtLeast(1).toLong(), left + todayPages)
        BookGoalMode.DEADLINE -> ceil((left + todayPages).toDouble() / deadlineDays.coerceAtLeast(1)).toLong()
        BookGoalMode.DAILY_MINUTES -> error("Handled above")
    }
    val todayLeft = (target - todayPages).coerceIn(0L, left)
    // Today's completed quota can move the projected finish to tomorrow, never claim a
    // future daily quota is already completed just because today's target was reached.
    val days = if (left == 0L) 0L else if (goal.mode == BookGoalMode.DEADLINE) deadlineDays.coerceAtLeast(1L)
        else if (todayLeft > 0) 1L + ceil((left - todayLeft).toDouble() / goal.dailyPages.coerceAtLeast(1)).toLong()
        else 1L + ceil(left.toDouble() / goal.dailyPages.coerceAtLeast(1)).toLong()
    return BookGoalProgress(left, todayPages, target, todayLeft, estimate(todayLeft), estimate(left), days,
        today.plusDays((days - 1).coerceAtLeast(0)), pace, readingPace.source, completed,
        !completed && goal.mode == BookGoalMode.DEADLINE && deadlineDays <= 0)
}
