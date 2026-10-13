package com.roinur.booktracker

import java.time.LocalDate

internal data class ReadingGoalProgress(val label: String, val current: Int, val target: Int, val unit: String) {
    val enabled get() = target > 0
    val fraction get() = if (enabled) (current.toFloat() / target).coerceIn(0f, 1f) else 0f
    val remaining get() = (target - current).coerceAtLeast(0)
    val summary get() = if (enabled) "$label: $current / $target $unit" else "$label: Not set"
    val notificationLine get() = if (!enabled) summary else
        "$summary · ${if (remaining == 0) "Reached" else "$remaining left"}"
}

internal data class ReadingGoalsProgress(val daily: ReadingGoalProgress, val monthly: ReadingGoalProgress, val yearly: ReadingGoalProgress) {
    val rows get() = listOf(daily, monthly, yearly)
    val hasGoals get() = rows.any { it.enabled }
}

/** Calendar periods and logged activity match the three goal rings, regardless of graph range. */
internal fun readingGoalsProgress(goals: BookReadingGoals, stats: BookStats, today: LocalDate = LocalDate.now()): ReadingGoalsProgress {
    val dailyTime = goals.dailyMetric == BookGoalMetric.TIME
    val monthlyTime = goals.monthlyMetric == BookGoalMetric.TIME
    val daily = if (dailyTime) stats.dailyActivity else stats.dailyPageActivity
    val monthly = if (monthlyTime) stats.dailyActivity else stats.dailyPageActivity
    return ReadingGoalsProgress(
        ReadingGoalProgress("Daily", daily.firstOrNull { it.date == today }?.pagesRead ?: 0,
            if (dailyTime) goals.dailyMinutes else goals.dailyPages, if (dailyTime) "minutes" else "pages"),
        ReadingGoalProgress("Monthly", monthly.filter { it.date.year == today.year && it.date.month == today.month }.sumOf { it.pagesRead },
            if (monthlyTime) goals.monthlyMinutes else goals.monthlyPages, if (monthlyTime) "minutes" else "pages"),
        ReadingGoalProgress("Yearly", stats.finishedThisYear, goals.yearlyBooks, "books")
    )
}
