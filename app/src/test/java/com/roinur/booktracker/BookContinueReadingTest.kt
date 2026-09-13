package com.roinur.booktracker

import org.junit.Assert.*
import org.junit.Test

class BookContinueReadingTest {
    private fun book(id: Int, status: BookStatus = BookStatus.READING) = BookRow(id,"","Book $id","Author",200,12,status,0,"","","","",false,"","","","",0)
    private fun session(id: Int, date: String) = BookReadingSessionWithBook(BookReadingSessionRow(id,id,date,"",60,1,12),"Book $id")
    @Test fun latestSessionWinsRegardlessOfSortAndFinishedStatus() {
        val books = listOf(book(1),book(2,BookStatus.FINISHED))
        val sessions = listOf(session(1,"2026-09-12T22:00:00Z"),session(2,"2026-09-13T01:00:00Z"))
        assertEquals(2,mostRecentlyReadBook(books,sessions,null)?.id)
        assertEquals(2,mostRecentlyReadBook(books.reversed(),sessions.reversed(),null)?.id)
    }
    @Test fun activeOrPausedSessionWins() {
        assertEquals(1,mostRecentlyReadBook(listOf(book(1),book(2)),listOf(session(2,"2026-09-13T01:00:00Z")),1)?.id)
    }
    @Test fun removedBookDoesNotHideLatestExistingSession() {
        assertEquals(1,mostRecentlyReadBook(listOf(book(1)),listOf(session(1,"2026-09-12T00:00:00Z"),session(2,"2026-09-13T00:00:00Z")),null)?.id)
        assertNull(mostRecentlyReadBook(emptyList(),emptyList(),null))
    }
}
