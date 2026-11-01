package com.roinur.booktracker

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BookCalendarMonthTest {
    @Test fun previousMonthIncludesItsFirstAndFinalDayInsteadOfThirtyRollingDays() {
        val month=bookStatsPeriod(BookStatsRange.MONTH,1,LocalDate.parse("2026-11-01"))
        assertEquals(LocalDate.parse("2026-10-01"),month.start)
        assertEquals(LocalDate.parse("2026-10-31"),month.end)
        val points=listOf(DailyActivityPoint(month.start.minusDays(1),500,1),DailyActivityPoint(month.start,12,1),
            DailyActivityPoint(month.end,21,1),DailyActivityPoint(month.end.plusDays(1),500,1))
        val selected=bookPointsForRange(points,BookStatsRange.MONTH,month.end)
        val chart=bookChartPoints(selected,BookStatsRange.MONTH,month.end)
        assertEquals(33,selected.sumOf {it.pagesRead});assertEquals(31,chart.size)
        assertEquals(month.start,chart.first().date);assertEquals(month.end,chart.last().date)
        assertEquals(33,chart.sumOf {it.pagesRead})
    }
    @Test fun leapAndNonLeapFebruaryHaveTheCorrectNumberOfDays() {
        for((year,days) in listOf(2024 to 29,2025 to 28)) {
            val month=bookStatsPeriod(BookStatsRange.MONTH,1,LocalDate.of(year,3,31))
            assertEquals(LocalDate.of(year,2,1),month.start);assertEquals(days,month.end.dayOfMonth)
            assertEquals(days,bookChartPoints(emptyList(),BookStatsRange.MONTH,month.end).size)
        }
    }
    @Test fun currentMonthStopsTotalsTodayButKeepsAllCalendarDaysOnTheChart() {
        val today=LocalDate.parse("2026-11-08")
        val month=bookStatsPeriod(BookStatsRange.MONTH,0,today)
        assertEquals(LocalDate.parse("2026-11-01"),month.start);assertEquals(today,month.end)
        val points=listOf(DailyActivityPoint(month.start.minusDays(1),90,1),DailyActivityPoint(month.start,7,1),
            DailyActivityPoint(today,4,1),DailyActivityPoint(today.plusDays(1),100,1))
        assertEquals(11,bookPointsForRange(points,BookStatsRange.MONTH,today).sumOf {it.pagesRead})
        val chart=bookChartPoints(points,BookStatsRange.MONTH,today)
        assertEquals(30,chart.size);assertEquals(11,chart.sumOf {it.pagesRead});assertEquals(0,chart.last().pagesRead)
    }
    @Test fun monthNavigationCrossesYearsAndMidMonthDatesDoNotShiftTheBoundaries() {
        val month=bookStatsPeriod(BookStatsRange.MONTH,1,LocalDate.parse("2027-01-15"))
        assertEquals(LocalDate.parse("2026-12-01"),month.start);assertEquals(LocalDate.parse("2026-12-31"),month.end)
        assertEquals("December 2026",BookPeriodRecap(BookStatsRange.MONTH,month.start,month.end,0,0,0,0,0,0,0,emptyList()).heading)
    }
}
