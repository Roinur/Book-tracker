package com.roinur.booktracker

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class BookCompletionDatesTest {
    @Test fun manualDateWinsOverAutomaticTimestamp() {
        val result = legacyCompletionDate("Jul 20, 2024", Instant.parse("2026-03-01T12:00:00Z").toEpochMilli())
        assertEquals(2024, bookCompletionYear(result))
    }
    @Test fun missingManualDateUsesTimestampAndMissingBothStaysUnknown() {
        assertEquals(2026, bookCompletionYear(legacyCompletionDate("", Instant.parse("2026-03-01T12:00:00Z").toEpochMilli())))
        assertEquals("", legacyCompletionDate("", null))
    }
    @Test fun januaryFirstCountsInLocalYear() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Stockholm"))
            assertEquals(2026, bookCompletionYear(legacyCompletionDate("Jan 1, 2026", null)))
        } finally { TimeZone.setDefault(previous) }
    }
    @Test fun readingEvidenceOverridesExistingDate() {
        assertEquals("2026-05-01T12:00:00Z", resolveCompletionDate(
            listOf("invalid", "2025-01-01T12:00:00Z", "2026-05-01T12:00:00Z"),
            listOf("2026-06-01T12:00:00Z"), "2024-01-01T12:00:00Z", "2026-09-01T12:00:00Z"))
    }
    @Test fun missingSessionsUseNotesThenImportThenExisting() {
        val date = "2026-05-01T12:00:00Z"
        assertEquals(date, resolveCompletionDate(listOf("invalid"), listOf(date), "", "old"))
        assertEquals(date, resolveCompletionDate(emptyList(), emptyList(), date, "old"))
        assertEquals(date, resolveCompletionDate(emptyList(), emptyList(), "", date))
    }
}
