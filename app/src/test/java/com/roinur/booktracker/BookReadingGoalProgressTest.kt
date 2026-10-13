package com.roinur.booktracker

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BookReadingGoalProgressTest {
    @Test fun ringFillIsBoundedAndUnsetGoalsStayEmpty() {
        assertEquals(0f, ReadingGoalProgress("Daily", 100, 0, "pages").fraction, 0.001f)
        assertEquals(0.5f, ReadingGoalProgress("Daily", 25, 50, "pages").fraction, 0.001f)
        assertEquals(1f, ReadingGoalProgress("Daily", 100, 50, "pages").fraction, 0.001f)
    }
    private val today = LocalDate.of(2026, 10, 3)
    private fun point(day: String, value: Int) = DailyActivityPoint(LocalDate.parse(day), value, 1)
    private val stats = BookStats(finishedThisYear = 14,
        dailyActivity = listOf(point("2026-09-30", 1000), point("2026-10-01", 60), point("2026-10-03", 30), point("2025-10-03", 999)),
        dailyPageActivity = listOf(point("2026-09-30", 1000), point("2026-10-01", 40), point("2026-10-03", 20)))

    @Test fun dailyMinutesAndMonthlyPagesMatchConfiguredMetricsAndCalendarMonth() {
        val progress = readingGoalsProgress(BookReadingGoals(dailyMinutes = 45, dailyPages = 999,
            monthlyPages = 200, monthlyMinutes = 999, yearlyBooks = 20,
            dailyMetric = BookGoalMetric.TIME, monthlyMetric = BookGoalMetric.PAGES), stats, today)
        assertEquals(30, progress.daily.current)
        assertEquals(15, progress.daily.remaining)
        assertEquals("Daily: 30 / 45 minutes · 15 left", progress.daily.notificationLine)
        assertEquals(60, progress.monthly.current)
        assertEquals(140, progress.monthly.remaining)
        assertEquals(14, progress.yearly.current)
        assertEquals(6, progress.yearly.remaining)
        assertTrue(progress.hasGoals)
    }

    @Test fun switchingMetricsUsesPagesDailyAndMinutesMonthly() {
        val progress = readingGoalsProgress(BookReadingGoals(dailyPages = 50, monthlyMinutes = 120,
            dailyMetric = BookGoalMetric.PAGES, monthlyMetric = BookGoalMetric.TIME), stats, today)
        assertEquals(20, progress.daily.current)
        assertEquals("pages", progress.daily.unit)
        assertEquals(90, progress.monthly.current)
        assertEquals("minutes", progress.monthly.unit)
    }

    @Test fun nextMonthStartsFreshAndFinishedCountComesFromTheCurrentYearProjection() {
        val progress = readingGoalsProgress(BookReadingGoals(dailyMinutes = 30, monthlyPages = 200, yearlyBooks = 20),
            stats.copy(finishedThisYear = 0), LocalDate.of(2027, 1, 1))
        assertEquals(0, progress.daily.current)
        assertEquals(0, progress.monthly.current)
        assertEquals(0, progress.yearly.current)
    }

    @Test fun inactiveMetricTargetsDoNotCreateAPhantomReminder() {
        val progress = readingGoalsProgress(BookReadingGoals(dailyPages = 50, monthlyMinutes = 100,
            dailyMetric = BookGoalMetric.TIME, monthlyMetric = BookGoalMetric.PAGES), stats, today)
        assertFalse(progress.hasGoals)
        assertEquals("Daily: Not set", progress.daily.notificationLine)
    }

    @Test fun exceededTargetKeepsActualCountAndNeverShowsNegativeRemaining() {
        val progress = readingGoalsProgress(BookReadingGoals(dailyMinutes = 10, yearlyBooks = 5), stats, today)
        assertEquals(30, progress.daily.current)
        assertEquals(0, progress.daily.remaining)
        assertEquals("Daily: 30 / 10 minutes · Reached", progress.daily.notificationLine)
        assertEquals(14, progress.yearly.current)
        assertEquals(0, progress.yearly.remaining)
    }

    @Test fun onlyLocalDatedNonExcludedReadingContributesToGoals() {
        val sessions = listOf(
            BookReadingSessionRow(1, 1, "2026-10-02T23:30:00Z", "", 1800, 20, 0),
            BookReadingSessionRow(2, 1, "2026-10-02T23:30:00Z", "", 3600, 1000, 0, excludeFromStatistics = true))
        val zone = ZoneId.of("Europe/Stockholm")
        val projection = BookStats(dailyActivity = bookDailyActivity(sessions, false, zone),
            dailyPageActivity = bookDailyActivity(sessions, true, zone))
        val progress = readingGoalsProgress(BookReadingGoals(dailyMinutes = 60, monthlyPages = 100), projection, today)
        assertEquals(30, progress.daily.current)
        assertEquals(20, progress.monthly.current)
    }
}
