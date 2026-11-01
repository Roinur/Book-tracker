package com.roinur.booktracker

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.roinur.booktracker.data.booklist.*
import com.roinur.booktracker.data.backup.BookBackupService
import com.roinur.booktracker.data.database.BookTrackerDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import java.io.File
import java.util.UUID

class BookListSyncTest {
    @Test fun sheetLayoutUsesTheReferencePaletteCheckboxesAndWholeRecordSorting() {
        val requests=BookListSheetLayout.requests(42,0,10001,28,"sv_SE")
        val items=(0 until requests.length()).map(requests::getJSONObject)
        assertFalse(items.first().getJSONObject("updateSheetProperties").getJSONObject("properties").getJSONObject("gridProperties").getBoolean("hideGridlines"))
        val entry=BookListSheetLayout.entryRequests(42,146,61)
        val validation=(0 until entry.length()).map(entry::getJSONObject).mapNotNull { it.optJSONObject("setDataValidation") }
            .single { it.optJSONObject("rule")?.optJSONObject("condition")?.optString("type")=="ONE_OF_LIST" }
        assertEquals(147,validation.getJSONObject("range").getInt("endRowIndex"))
        assertFalse((0 until entry.length()).any { entry.getJSONObject(it).has("updateBorders") })
        assertTrue(items.mapNotNull { it.optJSONObject("updateDimensionProperties") }.any {
            it.getJSONObject("range").getInt("startIndex")==3 && it.getJSONObject("properties").optBoolean("hiddenByUser")
        })
        val rules=items.mapNotNull { it.optJSONObject("addConditionalFormatRule")?.getJSONObject("rule") }
        val author=rules.single { r ->
            r.getJSONArray("ranges").getJSONObject(0).getInt("startColumnIndex")==0
        }.getJSONObject("booleanRule").getJSONObject("format").getJSONObject("backgroundColor")
        assertEquals(162/255.0,author.getDouble("red"),0.0001)
        assertEquals(196/255.0,author.getDouble("green"),0.0001)
        assertTrue(rules.any { it.optJSONObject("booleanRule")?.optJSONObject("format")?.optJSONObject("textFormat")?.optBoolean("bold")==true })
        val finishedAuthor=rules.mapNotNull { it.optJSONObject("booleanRule") }.single {
            it.getJSONObject("condition").getJSONArray("values").getJSONObject(0).getString("userEnteredValue")=="=${'$'}U2=\"Completed\""
        }.getJSONObject("format")
        assertTrue(finishedAuthor.getJSONObject("textFormat").getBoolean("bold"))
        assertEquals(70/255.0,finishedAuthor.getJSONObject("backgroundColor").getDouble("red"),0.0001)
        val completed=rules.mapNotNull { it.optJSONObject("booleanRule") }.single {
            it.getJSONObject("condition").getJSONArray("values").getJSONObject(0).getString("userEnteredValue")=="=${'$'}E2=\"Completed\""
        }.getJSONObject("format")
        assertEquals(17/255.0,completed.getJSONObject("backgroundColor").getDouble("red"),0.0001)
        assertEquals(1.0,completed.getJSONObject("textFormat").getJSONObject("foregroundColor").getDouble("red"),0.0001)
        val sort=BookListSheetLayout.sortRequest(42).getJSONObject("sortRange")
        assertEquals(14,sort.getJSONObject("range").getInt("endColumnIndex"))
        assertEquals(0,sort.getJSONArray("sortSpecs").getJSONObject(0).getInt("dimensionIndex"))
        assertEquals(1,sort.getJSONArray("sortSpecs").getJSONObject(1).getInt("dimensionIndex"))
    }
    @Before fun removeOnlyAnInterruptedFakeTestConnection() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val p=context.getSharedPreferences("book_list_sync",Context.MODE_PRIVATE)
        if(p.getString("file_id","")=="new-test-sheet" && p.getString("account","")=="test@example.invalid") p.edit().clear().commit()
    }
    private class IsolatedContext(base: Context, val tag: String = "book-list-test-${UUID.randomUUID()}") : ContextWrapper(base) {
        override fun getFilesDir(): File = File(baseContext.cacheDir, tag).apply { mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int) = baseContext.getSharedPreferences("$tag-$name", mode)
    }
    private class FakeTransport : BookListTransport {
        var rows = mutableListOf<MutableList<String>>()
        var created = 0; var writes = 0; var wrongFile = false; var failWrite = false
        override fun create(instance: String): String { created++; return "new-test-sheet" }
        override fun verify(id: String, instance: String) { require(!wrongFile) { "Wrong file" } }
        override fun initialize(id: String, records: Collection<BookListRecord>) {
            rows = (listOf(BookListRecord.headers) + records.map { it.cells().map(Any::toString) }).map { it.toMutableList() }.toMutableList()
        }
        override fun read(id: String): List<List<String>> = rows.map { row -> List(14) { row.getOrElse(it) { "" } } }.dropLastWhile(BookListRecord::isEmptyRow)
        override fun write(id: String, changes: List<BookListCellWrite>) {
            check(!failWrite) { "Interrupted upload" }; writes++
            for (change in changes) {
                val match = Regex("!([A-N])(\\d+)").find(change.range)!!
                val column = match.groupValues[1].single() - 'A'; val start = match.groupValues[2].toInt()-1
                change.rows.forEachIndexed { offset, values ->
                    while(rows.size <= start+offset) rows.add(MutableList(14) { "" })
                    values.forEachIndexed { col, value -> rows[start+offset][column+col] = value.toString() }
                }
            }
        }
    }
    private fun scenario(block: (BookTrackerDatabase, BookListStore, FakeTransport, BookListSyncEngine, Int) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val isolated = IsolatedContext(context)
        val name = "${isolated.tag}.db"
        val db = BookTrackerDatabase(context, name)
        try {
            val id = db.upsertBook(BookSeed("9780140449136", "Compendium", "Author", 200, "", "", "Philosophy", listOf(BookPart("Part one",1,100))))
            db.addReadingSession(id, "2024-02-01T10:00:00Z", "2024-02-01T11:00:00Z", 3600, 50, 50)
            db.addReadingNote(id, "2024-02-01T11:00:00Z", 50, 3600, "Keep this note")
            val store = BookListStore(isolated); val transport = FakeTransport()
            val engine = BookListSyncEngine(BookListRepository(db), store, transport, checkpoint = {})
            engine.create("test@example.invalid")
            assertEquals(1, transport.created); assertTrue(store.ready)
            block(db, store, transport, engine, id)
        } finally {
            db.close(); context.deleteDatabase(name)
            isolated.getSharedPreferences("book_list_sync", Context.MODE_PRIVATE).edit().clear().commit()
            isolated.filesDir.deleteRecursively()
        }
    }
    @Test fun twoWaySyncKeepsHistoryAndSeparatesUndatedTotalsFromDailyActivity() = scenario { db, _, remote, engine, id ->
        val sessions = db.listSessions(id); val notes = db.listNotes(id)
        remote.rows[1][4] = "Completed"; remote.rows[1][6] = "180"; remote.rows[1][7] = "2.5"; remote.rows[1][9] = "2024-07-30"
        remote.rows[1][3] = "Owned"; remote.rows[1][5] = "4"
        assertTrue(engine.sync())
        val book = db.getBook(id)!!
        assertEquals(180, book.currentPage); assertEquals(9000L, book.readingSeconds); assertEquals(BookStatus.FINISHED, book.status)
        assertEquals("Owned",book.ownership); assertEquals(0,book.readingPriority); assertEquals(4,book.rating)
        assertEquals(sessions, db.listSessions(id)); assertEquals(notes,db.listNotes(id))
        assertEquals(50, bookDailyActivity(db.listAllSessions().map { it.session }, true).sumOf { it.pagesRead })
        db.upsertBook(BookSeed(book.isbn,"Changed in app",book.authors,book.pageCount,book.coverUrl,book.sourceUrl,book.collections),id)
        assertTrue(engine.sync()); assertEquals("Changed in app",remote.rows[1][1])
        db.addReadingSession(id,"2026-10-25T10:00:00Z","2026-10-25T11:00:00Z",3600,10,190)
        assertEquals(190,db.getBook(id)!!.currentPage); assertEquals(12600L,db.getBook(id)!!.readingSeconds)
        db.deleteReadingSession(sessions.single().id,id)
        assertEquals(140,db.getBook(id)!!.currentPage); assertEquals(9000L,db.getBook(id)!!.readingSeconds)
    }
    @Test fun expectedWeightBecomesFinalRatingAfterCompletionAndEditsSyncBothWays() = scenario { db, _, remote, engine, id ->
        remote.rows[1][5]="4.5"; assertTrue(engine.sync()); assertEquals(9,db.getBook(id)!!.readingPriority)
        db.updateProgress(id,null,BookStatus.FINISHED); db.updateRating(id,3)
        assertTrue(engine.sync()); assertEquals("3.0",remote.rows[1][5]); assertEquals("3",remote.rows[1][11])
        assertEquals(9,db.getBook(id)!!.readingPriority)
        remote.rows[1][5]="4"; assertTrue(engine.sync()); assertEquals(4,db.getBook(id)!!.rating)
        val before=db.getBook(id)
        remote.rows[1][5]="3.5"
        assertThrows(IllegalArgumentException::class.java) { engine.sync() }
        assertEquals(before,db.getBook(id))
    }
    @Test fun conflictsRequireAChoiceAndNoPartialLocalMutation() = scenario { db, store, remote, engine, id ->
        val book = db.getBook(id)!!
        db.upsertBook(BookSeed(book.isbn,"App title",book.authors,200,"","","Philosophy"),id)
        remote.rows[1][1] = "Sheet title"; remote.rows[1][3] = "Owned"
        assertFalse(engine.sync()); assertEquals("App title",db.getBook(id)!!.title); assertEquals("Unknown",db.getBook(id)!!.ownership)
        store.choose(store.conflicts().single(),true)
        assertTrue(engine.sync()); assertEquals("Sheet title",db.getBook(id)!!.title); assertEquals("Owned",db.getBook(id)!!.ownership)
    }
    @Test fun newRowsGetAnIdentityAndRetryDoesNotDuplicateBooks() = scenario { db, _, remote, engine, _ ->
        remote.rows.add(MutableList(14) { "" }.also { it[0]="Other author"; it[1]="New book"; it[2]="100" })
        assertTrue(engine.sync()); assertEquals(2,db.listBooks("",BookSortField.ADDED,false).size)
        assertTrue(remote.rows[2][13].isNotBlank()); assertTrue(engine.sync())
        assertEquals(2,db.listBooks("",BookSortField.ADDED,false).size)
    }
    @Test fun removedSheetRowIsRestoredButConfirmedAppDeletionRemovesItsRow() = scenario { db, _, remote, engine, id ->
        remote.rows.removeAt(1); assertTrue(engine.sync()); assertNotNull(db.getBook(id)); assertEquals(2,remote.read("x").size)
        db.deleteBook(id); assertTrue(engine.sync()); assertEquals(1,remote.read("x").size)
    }
    @Test fun unusedCheckboxRowsDoNotCreateBooksOrPreventAppendingNewBooks() = scenario { db, _, remote, engine, _ ->
        repeat(30) { remote.rows.add(MutableList(14) { "" }.also { it[3]="false" }) }
        assertTrue(engine.sync());assertEquals(1,db.listBooks("",BookSortField.ADDED,false).size)
        db.upsertBook(BookSeed("","Appended book","Author",100,"","",""))
        assertTrue(engine.sync());assertEquals(2,remote.read("x").drop(1).count { !BookListRecord.isEmptyRow(it) })
    }
    @Test fun duplicateKeysAndUnmarkedFilesCannotChangeTheLibrary() = scenario { db, _, remote, engine, id ->
        val before = db.getBook(id)
        remote.rows.add(remote.rows[1].toMutableList())
        assertThrows(IllegalArgumentException::class.java) { engine.sync() }; assertEquals(before,db.getBook(id))
        remote.rows.removeAt(2); remote.wrongFile=true
        assertThrows(IllegalArgumentException::class.java) { engine.sync() }; assertEquals(before,db.getBook(id))
    }
    @Test fun syncFieldsAndUndatedOffsetsSurviveNativeBackup() = scenario { db, _, remote, engine, id ->
        remote.rows[1][3]="Owned";remote.rows[1][5]="4";remote.rows[1][6]="80";remote.rows[1][7]="2"
        assertTrue(engine.sync())
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="book-list-restore-${UUID.randomUUID()}.db"
        BookTrackerDatabase(context,name).use { target ->
            try {
                BookBackupService(target).importBackupJson(BookBackupService(db).exportBackupJson(false))
                val restored=target.listBooks("",BookSortField.ADDED,false).single()
                assertEquals(db.getBook(id)!!.listSyncKey,restored.listSyncKey);assertEquals("Owned",restored.ownership);assertEquals(8,restored.readingPriority)
                val session=target.listSessions(restored.id).single();target.deleteReadingSession(session.id,restored.id)
                assertEquals(30,target.getBook(restored.id)!!.currentPage);assertEquals(3600L,target.getBook(restored.id)!!.readingSeconds)
            } finally { target.close();context.deleteDatabase(name) }
        }
    }
}
