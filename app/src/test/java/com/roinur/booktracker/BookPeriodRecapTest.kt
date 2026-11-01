package com.roinur.booktracker

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BookPeriodRecapTest {
    private val zone = ZoneId.of("Europe/Stockholm")
    private val start = LocalDate.parse("2026-10-01")
    private val end = LocalDate.parse("2026-10-30")
    private fun book(id: Int = 1, finished: String = "") = BookRow(id,"","TESTDATA – Åäö","Testförfattare",200,50,
        if(finished.isBlank()) BookStatus.READING else BookStatus.FINISHED,4,"","","","",false,"","",finished,"",3600)
    private fun session(id: Int, date: String, pages: Int = 10, seconds: Long = 600, bookId: Int = 1,
        excluded: Boolean = false, ms: Long? = null) = BookReadingSessionRow(id,bookId,date,date,seconds,pages,0,ms,excluded)
    private fun stats(rows: List<BookReadingSessionRow>) = BookStats(pagesRead=900,readingSeconds=7200,finishedBooks=4,
        dailyPageActivity=bookDailyActivity(rows,true,zone), dailyActivity=bookDailyActivity(rows,false,zone))
    private fun recap(rows: List<BookReadingSessionRow>, books: List<BookRow> = listOf(book()), range: BookStatsRange = BookStatsRange.MONTH) =
        buildBookPeriodRecap(books,rows,stats(rows),range,start,end,zone)

    @Test fun historicalBoundsAreInclusiveAndNeverUseTheCurrentMonthOrLifetimeTotals() {
        val rows=listOf(session(1,"2026-09-30",100),session(2,"2026-10-01",11),session(3,"2026-10-30",23),session(4,"2026-10-31",200))
        val result=recap(rows)
        assertEquals(34L,result.pages); assertEquals(1200L,result.seconds)
        assertEquals(start,result.start); assertEquals(end,result.end); assertEquals(23,result.bestPageDay)
        assertEquals(10,result.bestMinuteDay); assertEquals(2,result.activeDays); assertEquals(1,result.longestStreak)
        assertEquals(102.0,result.pagesPerHour!!,0.001)
    }
    @Test fun excludedAndUndatedImportsNeverBecomeDatedReadingOrCoverRows() {
        val rows=listOf(session(1,"2026-10-04",12),session(2,"",500,9000,2,true),session(3,"2026-10-05",100,900,2,true),session(4,"bad-date",80,900,2))
        val result=recap(rows,listOf(book(),book(2)))
        assertEquals(12L,result.pages); assertEquals(600L,result.seconds); assertEquals(listOf(1),result.books.map {it.book.id})
        assertEquals(1,result.activeDays); assertEquals(1,result.longestStreak)
    }
    @Test fun allTimeRetainsAuthoritativeUndatedTotalsWithoutAssigningThemToToday() {
        val rows=listOf(session(1,"2026-09-10",12),session(2,"",500,7000,2,true))
        val result=recap(rows,listOf(book(),book(2,"unknown")),BookStatsRange.ALL_TIME)
        assertEquals(900L,result.pages); assertEquals(7200L,result.seconds); assertEquals(4,result.finishedBooks)
        assertEquals(1,result.activeDays); assertEquals(LocalDate.parse("2026-09-10"),result.start)
    }
    @Test fun finishesMustFallInTheSelectedPeriodAndPartsNeverDoubleCountBooks() {
        val result=recap(emptyList(),listOf(book(1,"2026-10-04"),book(2,"2026-09-30"),book(3,"2026-10-31"),book(4,"unknown")))
        assertEquals(1,result.finishedBooks); assertEquals(listOf(1),result.books.map {it.book.id})
        assertTrue(result.books.single().finished)
    }
    @Test fun timezoneAndMillisecondsAreHonoredAndTimeOnlyReadingCountsAsActivity() {
        val rows=listOf(session(1,"2026-09-30T22:30:00Z",0,0,ms=30_500),session(2,"2026-10-01T08:00:00Z",0,0,ms=30_500))
        val result=recap(rows)
        assertEquals(61L,result.seconds); assertEquals(0L,result.pages); assertEquals(1,result.activeDays)
        assertEquals(1,result.bestMinuteDay); assertEquals(1,result.longestStreak); assertEquals(1,result.books.size)
    }
    @Test fun streakIsClippedToTheSelectedIntervalAndDuplicateSessionsDoNotAddDays() {
        val rows=listOf(session(1,"2026-09-30"),session(2,"2026-10-01"),session(3,"2026-10-01"),session(4,"2026-10-02"),session(5,"2026-10-04"))
        val result=recap(rows)
        assertEquals(3,result.activeDays); assertEquals(2,result.longestStreak)
    }
    @Test fun zeroReadingHasNoInvalidPaceOrInventedCompletion() {
        val result=recap(emptyList())
        assertEquals(0L,result.pages); assertEquals(0L,result.seconds); assertNull(result.pagesPerHour)
        assertEquals(0,result.activeDays); assertEquals(0,result.finishedBooks); assertTrue(result.books.isEmpty())
    }
    @Test fun allTimeWithOnlyFutureDatedActivityDoesNotCreateAnInvalidDateRange() {
        val future=end.plusDays(5)
        val result=buildBookPeriodRecap(emptyList(),emptyList(),BookStats(dailyActivity=listOf(DailyActivityPoint(future,1,1))),
            BookStatsRange.ALL_TIME,future,end,zone)
        assertEquals(end,result.start);assertEquals(0,result.activeDays)
    }
    @Test fun summariesKeepAllBooksEvenWhenTheImageOnlyFeaturesSixCovers() {
        val books=(1..12).map {book(it,"2026-10-08").copy(title="TESTDATA – bok $it")}
        val result=recap(emptyList(),books)
        assertEquals(12,result.books.size); assertEquals(12,result.finishedBooks)
    }
}
