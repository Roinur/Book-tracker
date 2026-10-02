package com.roinur.booktracker

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BookGoalTest {
    private val today = LocalDate.of(2026, 10, 2)
    private fun book(page: Int = 100, total: Int = 300, status: BookStatus = BookStatus.READING) =
        BookRow(1, "", "Book", "", total, page, status, 0, "", "", "", "", false, "", "", "", "", 0)
    private fun session(id: Int, pages: Int, day: String = today.toString(), seconds: Long = 3600, bookId: Int = 1,
                        excluded: Boolean = false) = BookReadingSessionRow(id, bookId, day, day, seconds, pages, 0, excludeFromStatistics = excluded)
    private fun daily(pages: Int = 50) = BookGoal(1, BookGoalMode.DAILY_PAGES, pages, createdDate = today)
    private fun deadline(date: LocalDate = today.plusDays(3)) = BookGoal(1, BookGoalMode.DEADLINE, targetDate = date, createdDate = today)

    @Test fun dailyGoalCountsOnlyThisBooksActualLocalTodayPages() {
        val p = bookGoalProgress(book(), daily(), listOf(session(1, 20), session(2, 90, bookId = 2),
            session(3, 1000, excluded = true), session(4, 70, day = "2026-10-01")), today)
        assertEquals(20L, p.todayPages); assertEquals(50L, p.todayTarget); assertEquals(30L, p.todayRemaining)
        assertEquals(200L, p.remainingPages); assertEquals(5L, p.daysRemaining)
        assertEquals(today.plusDays(4), p.expectedFinish)
    }

    @Test fun deadlineTargetStaysStableAsPagesAreLoggedAndCatchesUpAfterMissedDays() {
        val start = bookGoalProgress(book(), deadline(), emptyList(), today)
        val after = bookGoalProgress(book(page = 130), deadline(), listOf(session(1, 30)), today)
        assertEquals(50L, start.todayTarget); assertEquals(start.todayTarget, after.todayTarget)
        assertEquals(20L, after.todayRemaining)
        val tomorrow = bookGoalProgress(book(), deadline(), emptyList(), today.plusDays(1))
        assertEquals(67L, tomorrow.todayTarget)
    }

    @Test fun reachingTodaysQuotaDoesNotPretendTheNextDaysQuotaIsDone() {
        val p = bookGoalProgress(book(), daily(), listOf(session(1, 80)), today)
        assertEquals(0L, p.todayRemaining); assertEquals(5L, p.daysRemaining)
        assertEquals(1f, p.todayFraction, 0.0001f)
    }

    @Test fun finalDayQuotaIsCappedToActualPagesRemaining() {
        val p = bookGoalProgress(book(page = 290), daily(70), listOf(session(1, 20)), today)
        assertEquals(30L, p.todayTarget); assertEquals(10L, p.todayRemaining); assertEquals(1L, p.daysRemaining)
    }

    @Test fun finishedStatusStopsTheGoalWithoutFabricatingTailPages() {
        val p = bookGoalProgress(book(page = 180, total = 200, status = BookStatus.FINISHED), daily(), emptyList(), today)
        assertTrue(p.completed); assertEquals(0L, p.remainingPages); assertEquals(0L, p.todayRemaining)
        assertEquals(180, book(page = 180, total = 200, status = BookStatus.FINISHED).currentPage)
    }

    @Test fun weightedBookPaceExcludesImportedChunksAndOverallFallbackIsExplicit() {
        val own = bookGoalProgress(book(), daily(), listOf(session(1, 40), session(2, 20, seconds = 1800),
            session(3, 1000, seconds = 1, excluded = true)), today)
        assertEquals(40.0, own.pagesPerHour!!, 0.001); assertFalse(own.usesOverallPace)
        assertEquals(18000L, own.remainingSeconds)
        val fallback = bookGoalProgress(book(), daily(), listOf(session(4, 50, bookId = 2)), today)
        assertEquals(50.0, fallback.pagesPerHour!!, 0.001); assertTrue(fallback.usesOverallPace)
        assertEquals(3600L, fallback.todaySeconds)
        assertNull(bookGoalProgress(book(), daily(), emptyList(), today).todaySeconds)
    }

    @Test fun deadlinePassedShowsAllRemainingPagesInsteadOfNegativeDays() {
        val p = bookGoalProgress(book(), deadline(today.minusDays(1)), emptyList(), today)
        assertTrue(p.overdue); assertEquals(200L, p.todayRemaining); assertEquals(1L, p.daysRemaining)
    }

    @Test fun nearMidnightSessionsUsePhoneTimezoneAndDuplicateIdsCountOnce() {
        val s = session(1, 25, day = "2026-10-01T22:30:00Z")
        val local = bookGoalProgress(book(), daily(), listOf(s, s), today, ZoneId.of("Europe/Stockholm"))
        val utc = bookGoalProgress(book(), daily(), listOf(s), today, ZoneId.of("UTC"))
        assertEquals(25L, local.todayPages); assertEquals(0L, utc.todayPages)
    }
}
