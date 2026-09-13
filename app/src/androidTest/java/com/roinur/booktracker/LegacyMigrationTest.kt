package com.roinur.booktracker

import android.content.ContentValues
import android.database.DatabaseUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.TimeZone
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LegacyMigrationTest {
    @Test fun oldGoalSchemaUpgradesWithoutLosingTargets() {
        val name = "old-goals-${UUID.randomUUID()}.db"
        val original = context.openOrCreateDatabase(name, 0, null)
        original.execSQL("CREATE TABLE reading_goals(id INTEGER PRIMARY KEY, daily_minutes INTEGER NOT NULL DEFAULT 0, daily_pages INTEGER NOT NULL DEFAULT 0, yearly_books INTEGER NOT NULL DEFAULT 0, updated_at TEXT NOT NULL)")
        original.execSQL("INSERT INTO reading_goals VALUES(1, 0, 25, 12, '2026-09-01')")
        original.close()
        val upgraded = BookTrackerDatabase(context, name)
        try {
            assertEquals(25, upgraded.loadReadingGoals().dailyPages)
            assertEquals(12, upgraded.loadReadingGoals().yearlyBooks)
            assertEquals(BookGoalMetric.PAGES, upgraded.loadReadingGoals().dailyMetric)
            assertEquals(0, upgraded.loadReadingGoals().monthlyPages)
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }
    @Test fun expandedGoalsRoundTripAndManualStatusOverride() = database { db ->
        val id = seed(db)
        db.updateProgress(id, 3, BookStatus.WISHLIST)
        val row = book(db, id)!!
        assertEquals(3, row.currentPage)
        assertTrue(BookLibraryFilter.NOT_STARTED.matches(row, id))
        assertFalse(BookLibraryFilter.READING.matches(row, id))
        db.updateProgress(id, null, BookStatus.PAUSED)
        assertTrue(BookLibraryFilter.ON_HOLD.matches(book(db, id)!!, null))
        val goals = BookReadingGoals(30, 20, 12, 600, 500, BookGoalMetric.PAGES, BookGoalMetric.TIME)
        db.saveReadingGoals(goals)
        assertEquals(goals, db.loadReadingGoals())
        val backup = db.exportBackupJson()
        database { restored ->
            restored.importBackupJson(backup)
            assertEquals(goals, restored.loadReadingGoals())
            assertEquals(BookStatus.PAUSED, book(restored, id)!!.status)
            assertEquals(3, book(restored, id)!!.currentPage)
        }
    }

    @Test fun oldNotesCanBeEditedWithoutLosingText() = database { db ->
        val id = seed(db)
        val noteId = db.writableDatabase.insertOrThrow("reading_notes", null, ContentValues().apply {
            put("book_id", id); put("created_at", "2026-09-01T12:00:00Z"); put("page", 3)
            put("duration_seconds", 0); put("note", "Original")
        }).toInt()
        val text = "  Original\n" + "Added text åäö 📖\n".repeat(500) + "  "
        db.updateReadingNote(noteId, "2026-09-01T12:00:00Z", 3, text)
        assertEquals(text, db.readableDatabase.rawQuery("SELECT note FROM reading_notes WHERE id = ?", arrayOf(noteId.toString())).use { it.moveToFirst(); it.getString(0) })
        database { restored ->
            restored.importBackupJson(db.exportBackupJson())
            assertEquals(text, restored.readableDatabase.rawQuery("SELECT note FROM reading_notes WHERE id = ?", arrayOf(noteId.toString())).use { it.moveToFirst(); it.getString(0) })
        }
    }
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun <T> database(body: (BookTrackerDatabase) -> T): T {
        val name = "import-qa-${UUID.randomUUID()}.db"
        val db = BookTrackerDatabase(context, name)
        return try { body(db) } finally { db.close(); context.deleteDatabase(name) }
    }
    private fun book(db: BookTrackerDatabase, id: Int) = db.listBooks("", BookSortField.ADDED, true).firstOrNull { it.id == id }
    private fun count(db: BookTrackerDatabase, table: String) = DatabaseUtils.longForQuery(db.readableDatabase,"SELECT COUNT(*) FROM $table",null)
    private fun seed(db: BookTrackerDatabase, title: String = "Existing book must survive") = db.upsertBook(BookSeed("",title,"Original author",100,"","","Original collection"))
    private fun small(): JSONObject {
        val book = JSONObject().put("localId", "b1").put("name", "Ångström 📚").put("author", "Örn")
            .put("thoughtList", JSONArray().put(JSONObject().put("localId", "t1")))
        val thought = JSONObject().put("localId", "t1").put("bookId", JSONObject.NULL)
            .put("thought", " \nRädda hela texten — 日本語 📖\n ").put("pageNumber", "xii–xiv").put("title", "Översikt")
            .put("dateAdded", 1718078956478L).put("isDeleted", true)
        val session = JSONObject().put("localId", "s1").put("bookId", "b1")
            .put("startDate", 1686626842399L).put("endDate", JSONObject.NULL).put("readTime", 1234L).put("numberOfPages", 3)
        val future = JSONObject().put("unknown", JSONObject().put("null", JSONObject.NULL)
            .put("maxInt64", Long.MAX_VALUE).put("preciseText", "x\u0000y"))
        val tables = JSONObject().put("BookModel", JSONArray().put(book)).put("ThoughtModel", JSONArray().put(thought))
            .put("ReadingSessionModel", JSONArray().put(session)).put("FutureModel", JSONArray().put(future))
            .put("GoalModel", JSONArray().put(JSONObject().put("localId", "g1").put("numberGoal", 15)))
        return JSONObject().put("metadata", JSONObject().put("unknownFutureSetting", true)).put("tables", tables)
    }

    @Test fun futureFieldsUnknownTypesDeletedRowsAndWhitespaceArePreserved() = database { db ->
        val bytes=("\uFEFF"+small().toString(2)+"\n").toByteArray(Charsets.UTF_8)
        val plan=LegacyMigration.preview(bytes)
        db.importLegacy(plan)
        assertArrayEquals(bytes,db.legacySource(plan.hash))
        assertEquals(" \nRädda hela texten — 日本語 📖\n ",db.listAllNotes().single().note.note)
        assertEquals(1234L,db.listAllSessions().single().session.durationMilliseconds ?: -1L)
        val backup=db.exportBackupJson()
        database { target ->
            target.importBackupJson(backup)
            assertArrayEquals(bytes,target.legacySource(plan.hash))
            // Deleting a projected book cannot cascade to or erase its source archive.
            target.writableDatabase.delete("books",null,null)
            assertArrayEquals(bytes,target.legacySource(plan.hash))
            assertEquals(1,target.exportBackupJson().getJSONArray("bookly_sources").length())
        }
    }

    @Test fun failedInsertRollsBackEveryBookAndArchive() = database { db ->
        val id=seed(db)
        db.writableDatabase.execSQL("CREATE TRIGGER qa_abort BEFORE INSERT ON reading_notes BEGIN SELECT RAISE(ABORT, 'QA injected failure'); END")
        val failure=runCatching { db.importLegacy(LegacyMigration.preview(small().toString().toByteArray(Charsets.UTF_8))) }.exceptionOrNull()
        assertNotNull(failure)
        assertEquals(1L,count(db,"books"))
        assertEquals(0L,count(db,"reading_notes"))
        assertEquals(0L,count(db,"reading_sessions"))
        assertEquals(0L,count(db,"bookly_sources"))
        assertEquals(0L,count(db,"bookly_links"))
        assertEquals("Existing book must survive",book(db, id)?.title)
    }

    @Test fun malformedBackupAndBrokenReferencesDoNotEraseLibrary() = database { db ->
        val id=seed(db)
        val backup=db.exportBackupJson()
        backup.getJSONArray("reading_sessions").put(JSONObject().put("id",1).put("book_id",999999)
            .put("started_at","2023-01-01T00:00:00Z").put("ended_at","").put("duration_seconds",0))
        assertNotNull(runCatching { db.importBackupJson(backup) }.exceptionOrNull())
        assertEquals(1L,count(db,"books"))
        assertEquals("Existing book must survive",book(db, id)?.title)
        val malformed=small()
        val books=malformed.getJSONObject("tables").getJSONArray("BookModel")
        books.put(JSONObject(books.getJSONObject(0).toString()))
        assertNotNull(runCatching { LegacyMigration.preview(malformed.toString().toByteArray()) }.exceptionOrNull())
        assertEquals(1L,count(db,"books"))
    }

    @Test fun unsupportedUnlinkedContentIsArchivedInsteadOfDiscarded() = database { db ->
        val root=small()
        root.getJSONObject("tables").getJSONArray("BookModel").getJSONObject(0).put("thoughtList",JSONArray())
        val bytes=root.toString().toByteArray()
        val plan=LegacyMigration.preview(bytes)
        assertEquals(1,plan.unlinkedContent)
        db.importLegacy(plan)
        assertEquals(0L,count(db,"reading_notes"))
        assertArrayEquals(bytes,db.legacySource(plan.hash))
        assertEquals(1,JSONObject(LegacyMigration.decode(db.legacySource(plan.hash))).getJSONObject("tables").getJSONArray("ThoughtModel").length())
    }

    @Test fun checksumFailureAndUnknownBackupFieldsAreRejectedWithoutChanges() = database { db ->
        val existing=seed(db)
        val source=small().toString().toByteArray(Charsets.UTF_8)
        db.importLegacy(LegacyMigration.preview(source))
        val backup=db.exportBackupJson()
        backup.getJSONArray("bookly_sources").getJSONObject(0).put("sha256","bad")
        assertNotNull(runCatching { db.importBackupJson(backup) }.exceptionOrNull())
        val unknown=db.exportBackupJson().put("futureSection",JSONArray().put("must not discard"))
        assertNotNull(runCatching { db.importBackupJson(unknown) }.exceptionOrNull())
        assertEquals(2L,count(db,"books"))
        assertEquals("Existing book must survive",book(db, existing)?.title)
    }

    @Test fun legacyV1BackupsAppendWithoutReplacing() = database { db ->
        val first=seed(db)
        val legacy=db.exportBackupJson().put("format","BOOK_TRACKER_BACKUP_V1")
        legacy.remove("bookly_sources"); legacy.remove("bookly_links"); legacy.remove("reading_goals")
        legacy.remove("content_sha256"); legacy.remove("integrity_sha256")
        db.importBackupJson(legacy)
        assertEquals(2L,count(db,"books"))
        assertEquals("Existing book must survive",book(db, first)?.title)
    }

    @Test fun copyAllNotesUsesDatePageHeadersBlankLinesAndOldestFirst() {
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

    @Test fun libraryFilterCyclesAndSeparatesReadingFinishedAndNotStarted() {
        fun book(
            id: Int,
            status: BookStatus,
            page: Int = 0,
            seconds: Long = 0,
            startedAt: String = ""
        ) = BookRow(
            id, "", "Book $id", "", 200, page, status, 0, "", "", "", "", false,
            "2026-09-04T00:00:00Z", startedAt, "", "", seconds
        )

        val reading = book(1, BookStatus.READING, page = 12)
        val finished = book(2, BookStatus.FINISHED, page = 200)
        val notStarted = book(3, BookStatus.WISHLIST, page = 3)
        val activeNewBook = book(4, BookStatus.READING)

        assertEquals(BookLibraryFilter.FINISHED, BookLibraryFilter.READING.next())
        assertEquals(BookLibraryFilter.NOT_STARTED, BookLibraryFilter.FINISHED.next())
        assertEquals(BookLibraryFilter.ON_HOLD, BookLibraryFilter.NOT_STARTED.next())
        assertEquals(BookLibraryFilter.ALL, BookLibraryFilter.ON_HOLD.next())
        assertEquals(BookLibraryFilter.READING, BookLibraryFilter.ALL.next())
        assertTrue(BookLibraryFilter.READING.matches(reading, null))
        assertTrue(BookLibraryFilter.FINISHED.matches(finished, null))
        assertTrue(BookLibraryFilter.NOT_STARTED.matches(notStarted, null))
        assertTrue(BookLibraryFilter.READING.matches(activeNewBook, activeNewBook.id))
        assertFalse(BookLibraryFilter.NOT_STARTED.matches(activeNewBook, activeNewBook.id))
        assertTrue(listOf(reading, finished, notStarted).all { BookLibraryFilter.ALL.matches(it, null) })
    }

    @Test fun failedReadingSessionWriteKeepsBookProgressUnchanged() = database { db ->
        val bookId = seed(db)
        db.writableDatabase.execSQL(
            "CREATE TRIGGER qa_session_abort BEFORE INSERT ON reading_sessions " +
                "BEGIN SELECT RAISE(ABORT, 'QA injected session failure'); END"
        )

        assertNotNull(
            runCatching {
                db.addReadingSession(
                    bookId,
                    "2026-09-04T18:00:00Z",
                    "2026-09-04T18:30:00Z",
                    1_800,
                    pagesRead = 20,
                    pageReached = 20
                )
            }.exceptionOrNull()
        )
        assertEquals(0L, count(db, "reading_sessions"))
        assertEquals(0, book(db, bookId)?.currentPage)
        assertEquals(0L, book(db, bookId)?.readingSeconds)
    }

    @Test fun timerUsesWallClockAcrossThirtyMinutesOfScreenOffTime() {
        val started = 1_000_000L
        val returnedThirtyMinutesLater = started + 30L * 60L * 1_000L
        assertEquals(
            1_800L,
            calculateBookTimerElapsedSeconds(started, 0L, 0L, returnedThirtyMinutesLater)
        )
    }

    @Test fun finishingBookPreservesCurrentPageBeforeIndex() = database { db ->
        val bookId = seed(db, "Book with index")
        db.updateProgress(bookId, 90, BookStatus.READING)
        db.updateProgress(bookId, null, BookStatus.FINISHED)

        assertEquals(BookStatus.FINISHED, book(db, bookId)?.status)
        assertEquals(90, book(db, bookId)?.currentPage)
    }

    @Test fun statsReadTimeUsesOnlyHoursAfterFirstHour() {
        assertEquals("59m", bookFormatStatsReadTime(3_599L))
        assertEquals("1h", bookFormatStatsReadTime(3_600L))
        assertEquals("543h", bookFormatStatsReadTime(543L * 3_600L + 57L * 60L))
    }
}
