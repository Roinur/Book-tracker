package com.roinur.booktracker

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.roinur.booktracker.data.backup.BookBackupService
import com.roinur.booktracker.data.database.BookTrackerDatabase
import com.roinur.booktracker.data.markdown.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BookMarkdownPersistenceTest {
    private class IsolatedContext(base: Context,val tag: String) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String,mode: Int) = baseContext.getSharedPreferences("$tag-$name",mode)
    }
    private fun scenario(block: (BookTrackerDatabase,BookMarkdownOutbox,IsolatedContext) -> Unit) {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val name="markdown-test-${UUID.randomUUID()}.db"; val context=IsolatedContext(base,name)
        BookTrackerDatabase(context,name).use { db ->
            try { db.writableDatabase; block(db,BookMarkdownOutbox(db),context) }
            finally { context.getSharedPreferences("book_markdown_export",Context.MODE_PRIVATE).edit().clear().commit() }
        }
        base.deleteDatabase(name)
    }
    private fun add(db: BookTrackerDatabase) = db.upsertBook(BookSeed("","TESTDATA – Markdown","Testförfattare",200,"","",""))
    @Test fun notesAndLiveAutosavesAreQueuedTransactionallyAndSurviveReopen() = scenario { db,queue,context ->
        val id=add(db); queue.prepare(); val initial=queue.next()!!; queue.acknowledge(initial); assertEquals(0,queue.count())
        db.addReadingNote(id,"2026-10-28T10:00:00Z",12,60,"Ett stycke\n\nEtt till – åäö.")
        val changed=queue.next()!!; assertEquals(1,queue.count()); assertTrue(changed.block.contains("Ett stycke\n\nEtt till – åäö."))
        db.saveLiveReadingNote("test-live",id,"2026-10-28T11:00:00Z",14,10,"Live note")
        assertFalse(queue.current(changed)); queue.acknowledge(changed); assertEquals(1,queue.count())
        BookTrackerDatabase(context,context.tag).use { reopened ->
            val pending=BookMarkdownOutbox(reopened).next()!!
            assertTrue(pending.revision>changed.revision); assertTrue(pending.block.contains("Live note")); assertEquals(initial.identity,pending.identity)
        }
    }
    @Test fun titleChangeNoteEditAndDeleteKeepIdentityAndOnlyMarkOneBookPending() = scenario { db,queue,_ ->
        val id=add(db); val first=queue.next()!!; queue.acknowledge(first)
        db.upsertBook(BookSeed("","TESTDATA – ny titel","Författare",200,"","",""),id)
        val renamed=queue.next()!!; assertEquals(first.identity,renamed.identity); assertEquals(1,queue.count()); assertTrue(renamed.block.startsWith("# TESTDATA – ny titel"))
        db.addReadingNote(id,"2026-10-28T11:00:00Z",10,30,"Original"); val note=db.listNotes(id).single()
        queue.acknowledge(queue.next()!!); db.updateReadingNote(note.id,note.createdAt,11,"Ändrad\nFlerradig")
        assertEquals(1,queue.count()); assertTrue(queue.next()!!.block.contains("Ändrad\nFlerradig"))
        queue.acknowledge(queue.next()!!); db.deleteReadingNote(note.id)
        assertEquals(1,queue.count()); assertFalse(queue.next()!!.block.contains("Ändrad\nFlerradig"))
    }
    @Test fun deletedBookRemovesOnlyLocalQueueAndNeverHasCloudDeleteOperation() = scenario { db,queue,_ ->
        val id=add(db); db.addReadingNote(id,"2026-10-28T11:00:00Z",10,30,"Test note")
        assertEquals(1,queue.count()); db.deleteBook(id); assertEquals(0,queue.count()); assertNull(queue.next())
    }
    @Test fun nativeBackupRestorePreservesExportIdentityAndNotesWithoutExportGrants() = scenario { db,queue,context ->
        val id=add(db); db.addReadingNote(id,"2026-10-28T11:00:00Z",10,30,"Citat\n\nåäö",BookNoteKind.QUOTE)
        val original=queue.next()!!
        val state=MarkdownExportState(context); state.select(MarkdownTarget("test@example.invalid","test-folder"),"Testdata/BookTracker")
        state.enable(true); state.bind(state.target,original.identity,MarkdownBinding("test-file",true))
        val backup=BookBackupService(db).exportBackupJson()
        val backupBook=backup.getJSONArray("books").getJSONObject(0)
        assertEquals(original.identity,backupBook.getString("list_sync_key"))
        assertFalse(backup.has("markdown_outbox")); assertFalse(backup.toString().contains("test@example.invalid"))
        val restoredName="markdown-restore-test-${UUID.randomUUID()}.db"
        try {
            BookTrackerDatabase(context,restoredName).use { restored ->
                BookBackupService(restored).importBackupJson(backup)
                val item=BookMarkdownOutbox(restored).next()!!
                assertEquals(original.identity,item.identity); assertEquals(original.block,item.block)
            }
        } finally { context.deleteDatabase(restoredName) }
    }
    @Test fun disablingPreservesFilesBindingsAndPendingChangesAndNeverClaimsSuccess() = scenario { db,queue,context ->
        add(db); val item=queue.next()!!; val state=MarkdownExportState(context)
        state.select(MarkdownTarget("test@example.invalid","test-folder"),"Testdata/BookTracker")
        assertFalse(state.enabled); state.enable(true); val epoch=state.epoch
        state.bind(state.target,item.identity,MarkdownBinding("reserved-file",false))
        state.status("Offline; waiting"); assertEquals("",state.lastSuccess)
        state.enable(false); assertFalse(state.enabled); assertNotEquals(epoch,state.epoch)
        assertEquals("reserved-file",state.binding(state.target,item.identity)!!.fileId); assertEquals(1,queue.count())
        val recreated=MarkdownExportState(context); assertFalse(recreated.enabled); assertEquals(state.binding(state.target,item.identity),recreated.binding(state.target,item.identity))
    }
    @Test fun rolledBackEditsNeverReachTheDurableQueue() = scenario { db,queue,_ ->
        add(db); queue.acknowledge(queue.next()!!); assertEquals(0,queue.count())
        db.writableDatabase.beginTransaction()
        try { db.upsertBook(BookSeed("","TESTDATA – rolled back","",10,"","","")) }
        finally { db.writableDatabase.endTransaction() }
        assertEquals(0,queue.count())
    }
}
