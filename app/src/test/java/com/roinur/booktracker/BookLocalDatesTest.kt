package com.roinur.booktracker

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BookLocalDatesTest {
    private val stockholm = ZoneId.of("Europe/Stockholm")
    private fun session(id: Int, time: String, milliseconds: Long, pages: Int, excluded: Boolean = false) =
        BookReadingSessionRow(id, 1, time, "", milliseconds / 1000, pages, 0, milliseconds, excluded)

    @Test fun importedNightSessionBelongsToAugust19() {
        val sessions = listOf(session(1, "2026-08-18T23:57:16.683Z", 2582045, 27))
        assertEquals(LocalDate.of(2026, 8, 19), bookDailyActivity(sessions, true, stockholm).single().date)
        assertEquals(27, bookDailyActivity(sessions, true, stockholm).single().pagesRead)
        assertEquals(43, bookDailyActivity(sessions, false, stockholm).single().pagesRead)
    }
    @Test fun timezoneAndYearBoundaryFollowDeviceZone() {
        assertEquals(LocalDate.of(2027, 1, 1), bookLocalDate("2026-12-31T23:30:00Z", stockholm))
        assertEquals(LocalDate.of(2026, 12, 31), bookLocalDate("2027-01-01T02:30:00Z", ZoneId.of("America/New_York")))
        assertEquals(LocalDate.of(2026, 12, 31), bookLocalDate("2026-12-31", stockholm))
    }
    @Test fun daylightSavingRepeatedHourStaysOnOneDay() {
        val sessions = listOf(session(1,"2026-10-25T00:30:00Z",30000,2),session(2,"2026-10-25T01:30:00Z",30000,3))
        assertEquals(1, bookDailyActivity(sessions, false, stockholm).single().pagesRead)
        assertEquals(5, bookDailyActivity(sessions, true, stockholm).single().pagesRead)
    }
    @Test fun excludedAndUndatedSessionsDoNotEnterDailyStatistics() {
        val sessions = listOf(session(1,"2026-08-18T23:57:00Z",60000,27),session(2,"2026-08-18T23:57:00Z",60000,100,true),session(3,"",60000,100))
        assertEquals(27, bookDailyActivity(sessions,true,stockholm).single().pagesRead)
    }
}
