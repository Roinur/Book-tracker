package com.roinur.booktracker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class BookNotesCopyFormatTest {
    @Test
    fun dateAndPageSitAboveEachNoteWithBlankLinesAndOldestFirst() {
        val originalTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Stockholm"))
            val notes = listOf(
                BookNote(1, 7, "2026-08-27T21:28:00Z", 170, 0, BookNoteKind.NOTE, "First line\nSecond line"),
                BookNote(2, 7, "2026-08-28T08:05:00Z", 175, 0, BookNoteKind.THOUGHT, "Later note")
            )

            assertEquals(
                "Aug 27, 2026 23:28 - page 170\nFirst line\nSecond line\n\n" +
                    "Aug 28, 2026 10:05 - page 175\nLater note",
                formatBookNotesForClipboard(notes)
            )
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }
}
