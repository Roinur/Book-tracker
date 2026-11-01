package com.roinur.booktracker

import com.roinur.booktracker.data.markdown.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class BookMarkdownTest {
    private val identity = "550e8400-e29b-41d4-a716-446655440000"
    private val target = MarkdownTarget("test@example.invalid", "isolated-test-folder")
    private fun book(title: String = "Åäö – bok") = BookRow(1, "9780140449136", title, "Författare", 200, 42,
        BookStatus.READING, 4, "Första stycket\n\nAndra stycket med [[länk]].", "", "https://example.invalid/book", "Filosofi", false,
        "2026-01-01T12:00:00Z", "", "", "", 3600, listSyncKey = identity)
    private fun item(title: String = "Åäö – bok") = MarkdownItem(1, identity, 1, BookMarkdownDocument.block(book(title), listOf(
        BookNote(1,1,"2026-01-02T12:34:56Z",42,120,BookNoteKind.QUOTE,"Ett citat\n\nmed två stycken – åäö."),
        BookNote(2,1,"2026-01-03T01:02:03Z",43,60,BookNoteKind.NOTE,"Rad ett\nRad två\n\nNytt stycke."))))
    private class Bindings : MarkdownBindings {
        val files = mutableMapOf<Pair<MarkdownTarget,String>,MarkdownBinding>()
        override fun binding(target: MarkdownTarget, identity: String) = files[target to identity]
        override fun bind(target: MarkdownTarget, identity: String, binding: MarkdownBinding) { files[target to identity] = binding }
    }
    private class Remote : MarkdownTransport {
        val files = mutableMapOf<String,MarkdownRemote>()
        var creates=0; var updates=0; var ids=0; var failAfterCreate=false; var failRead=false
        var beforeUpdate: (() -> Unit)? = null
        override fun folder(id: String) = MarkdownFolder(id,"BookTracker")
        override fun find(folder: String,name: String) = files.values.filter { it.name==name }.map { it.id }
        override fun generateId() = "test-file-${++ids}"
        override fun read(folder: String,id: String): MarkdownRemote? { if(failRead) throw IOException("Offline"); return files[id] }
        override fun create(folder: String,id: String,name: String,text: String) {
            if(id !in files) { files[id]=MarkdownRemote(id,name,text,"version-1"); creates++ }
            if(failAfterCreate) { failAfterCreate=false; throw IOException("Response lost after server commit") }
        }
        override fun update(folder: String,document: MarkdownRemote,text: String) {
            beforeUpdate?.invoke(); beforeUpdate=null
            if(files[document.id]?.etag != document.etag) throw MarkdownConflict("Concurrent edit")
            files[document.id]=document.copy(text=text,etag="version-${++updates+1}")
        }
        fun edit(text: String) { val old=files.values.single(); files[old.id]=old.copy(text=text,etag=old.etag+"-obsidian") }
    }
    @Test fun utf8QuotesParagraphsDatesAndPagesAreRendered() {
        val text=BookMarkdownDocument.create(identity,item().block)
        assertTrue(text.startsWith("---\nbooktracker_id: \"$identity\"\nbooktracker_export_version: 1\n---"))
        assertTrue(text.contains("# Åäö – bok")); assertTrue(text.contains("## Citat")); assertTrue(text.contains("> Ett citat\n> \n> med två stycken – åäö."))
        assertTrue(text.contains("2026-01-02T12:34:56Z · sida 42 · lästid 120 s"))
        assertTrue(text.contains("Rad ett\nRad två\n\nNytt stycke.")); assertTrue(text.contains("## Mina Obsidian-anteckningar"))
    }
    @Test fun booksByTheSameAuthorShareAnObsidianTargetWithoutChangingFileIdentity() {
        val first=BookMarkdownDocument.block(book("Bok ett").copy(authors="Vladimir Nabokov"),emptyList())
        val second=BookMarkdownDocument.block(book("Bok två").copy(authors=" Vladimir  Nabokov "),emptyList())
        val link="[[Författare/Vladimir Nabokov|Vladimir Nabokov]]"
        assertTrue(first.contains(link)); assertTrue(second.contains(link))
        val before=BookMarkdownDocument.create(identity,item().block)+"\n[[Min egen idé]]\n"
        val after=BookMarkdownDocument.merge(before,identity,first)
        assertTrue(after.contains(link)); assertTrue(after.endsWith("[[Min egen idé]]\n"))
        assertEquals("book-$identity.md",BookMarkdownDocument.filename(identity))
    }
    @Test fun multipleAuthorsKeepSeparateSharedTargetsAndUnicodeNames() {
        assertEquals("[[Författare/Charlotte Brontë|Charlotte Brontë]], [[Författare/Emily Brontë|Emily Brontë]], [[Författare/Anne Brontë|Anne Brontë]]",
            BookMarkdownDocument.authorLinks("Charlotte Brontë, Emily Brontë; Anne Brontë\nCharlotte Brontë"))
        assertEquals("[[Författare/Antoon Geels|Antoon Geels]], [[Författare/Owe Wikström|Owe Wikström]]",
            BookMarkdownDocument.authorLinks("Antoon Geels & Owe Wikström"))
        assertEquals(BookMarkdownDocument.authorLinks("Brontë"),BookMarkdownDocument.authorLinks("Bronte\u0308"))
        assertEquals("",BookMarkdownDocument.authorLinks(" , ; \n"))
    }
    @Test fun authorNamesCannotInjectPathsLinkFragmentsOrCollidingTargets() {
        assertEquals("[[Författare/A%2FB|A/B]]",BookMarkdownDocument.authorLinks("A/B"))
        assertEquals("[[Författare/A%252FB|A%2FB]]",BookMarkdownDocument.authorLinks("A%2FB"))
        assertEquals("[[Författare/A%5DB%23C%7CD|A&#93;B#C&#124;D]]",BookMarkdownDocument.authorLinks("A]B#C|D"))
        assertEquals("[[Författare/%43ON|CON]]",BookMarkdownDocument.authorLinks("CON"))
        assertTrue(BookMarkdownDocument.authorLinks("../A\\B.").startsWith("[[Författare/..%2FA%5CB%2E|"))
        assertFalse(BookMarkdownDocument.authorLinks(BookMarkdownDocument.END).contains(BookMarkdownDocument.END))
    }
    @Test fun firstExportRetryAndTitleChangeKeepOneDriveFile() {
        val remote=Remote(); val bindings=Bindings(); val engine=BookMarkdownEngine(remote,bindings)
        engine.export(target,item()); val original=remote.files.values.single()
        engine.export(target,item()); engine.export(target,item("Ny titel"))
        assertEquals(1,remote.creates); assertEquals(1,remote.updates); assertEquals(original.id,remote.files.values.single().id)
        assertEquals("book-$identity.md",remote.files.values.single().name); assertTrue(remote.files.values.single().text.contains("# Ny titel"))
    }
    @Test fun processRestartAfterUnknownCreateResultNeverCreatesDuplicate() {
        val remote=Remote().apply { failAfterCreate=true }; val bindings=Bindings()
        assertThrows(IOException::class.java) { BookMarkdownEngine(remote,bindings).export(target,item()) }
        BookMarkdownEngine(remote,bindings).export(target,item())
        assertEquals(1,remote.creates); assertEquals(1,remote.ids); assertTrue(bindings.binding(target,identity)!!.confirmed)
    }
    @Test fun restoredInstallationDiscoversTheExistingFileByStableIdentity() {
        val remote=Remote(); BookMarkdownEngine(remote,Bindings()).export(target,item())
        BookMarkdownEngine(remote,Bindings()).export(target,item("Restored title"))
        assertEquals(1,remote.creates); assertEquals(1,remote.files.size)
    }
    @Test fun ownYamlCommentsLinksAndCrLfOutsideBlockAreByteExact() {
        val original=BookMarkdownDocument.create(identity,item().block).replace("\n", "\r\n")
            .replace("booktracker_export_version: 1", "booktracker_export_version: 1\r\ntags: [bok, idé]\r\nmy_property: 'Bevara mig'") + "<!-- egen kommentar -->\r\n[[Min idé]]\r\n"
        val updated=BookMarkdownDocument.merge(original,identity,item("Ny titel").block)
        val start=original.indexOf(BookMarkdownDocument.BEGIN)+BookMarkdownDocument.BEGIN.length
        val end=original.indexOf(BookMarkdownDocument.END)
        assertEquals(original.substring(0,start),updated.substring(0,start))
        assertEquals(original.substring(end),updated.substring(updated.indexOf(BookMarkdownDocument.END)))
        assertEquals(updated,BookMarkdownDocument.merge(updated,identity,item("Ny titel").block))
    }
    @Test fun concurrentObsidianEditIsNotOverwrittenAndRetryMergesIt() {
        val remote=Remote(); val engine=BookMarkdownEngine(remote,Bindings()); engine.export(target,item())
        remote.beforeUpdate={remote.edit(remote.files.values.single().text+"\n[[Nyligen tillagd]]\n")}
        assertThrows(MarkdownConflict::class.java) { engine.export(target,item("Changed")) }
        assertEquals(0,remote.updates); assertTrue(remote.files.values.single().text.endsWith("[[Nyligen tillagd]]\n"))
        engine.export(target,item("Changed")); assertTrue(remote.files.values.single().text.endsWith("[[Nyligen tillagd]]\n"))
        assertEquals(1,remote.files.size)
    }
    @Test fun offlineReadNeverCreatesOrOverwritesAndCanBeRetried() {
        val remote=Remote(); val engine=BookMarkdownEngine(remote,Bindings()); engine.export(target,item())
        remote.failRead=true; assertThrows(IOException::class.java) { engine.export(target,item("Offline")) }
        assertEquals(0,remote.updates); assertEquals(1,remote.creates)
        remote.failRead=false; engine.export(target,item("Online")); assertEquals(1,remote.updates)
    }
    @Test fun malformedMarkersAndWrongIdFailClosed() {
        val good=BookMarkdownDocument.create(identity,item().block)
        val malformed=listOf(good.replace(BookMarkdownDocument.BEGIN,""), good.replace(BookMarkdownDocument.END,""),
            good+BookMarkdownDocument.END+"\n", good.replace(BookMarkdownDocument.BEGIN,"SWAP").replace(BookMarkdownDocument.END,BookMarkdownDocument.BEGIN).replace("SWAP",BookMarkdownDocument.END),
            good.replace(identity,"another-book"), good.replace("booktracker_id:","not_booktracker_id:"),
            good.replace("booktracker_export_version:","booktracker_id: \"$identity\"\nbooktracker_export_version:"))
        malformed.forEach { assertThrows(MarkdownConflict::class.java) { BookMarkdownDocument.merge(it,identity,item().block) } }
    }
    @Test fun missingPreviouslyConfirmedFileIsReportedWithoutRecreatingIt() {
        val remote=Remote(); val engine=BookMarkdownEngine(remote,Bindings()); engine.export(target,item()); remote.files.clear()
        assertThrows(MarkdownConflict::class.java) { engine.export(target,item()) }
        assertEquals(1,remote.creates); assertTrue(remote.files.isEmpty())
    }
    @Test fun duplicateNamesAndRenamedFilesAreNeverOverwritten() {
        val remote=Remote(); val engine=BookMarkdownEngine(remote,Bindings()); engine.export(target,item())
        val old=remote.files.values.single(); remote.files["other"]=old.copy(id="other")
        assertThrows(MarkdownConflict::class.java) { BookMarkdownEngine(remote,Bindings()).export(target,item()) }
        remote.files.remove("other"); remote.files[old.id]=old.copy(name="manual-name.md")
        assertThrows(MarkdownConflict::class.java) { engine.export(target,item()) }; assertEquals(0,remote.updates)
    }
    @Test fun cancellationBeforeMutationDoesNotUpload() {
        val remote=Remote(); val engine=BookMarkdownEngine(remote,Bindings())
        assertThrows(IllegalStateException::class.java) { engine.export(target,item()) { error("Disabled") } }
        assertEquals(0,remote.creates)
    }
    @Test fun filenamesAreStableSafeAndCannotCollideWhenEncoded() {
        val a=BookMarkdownDocument.filename("å/b:c"); val b=BookMarkdownDocument.filename("å_b_c")
        assertNotEquals(a,b); assertTrue(a.matches(Regex("book-[A-Za-z0-9_-]+\\.md")))
        assertEquals("book-$identity.md",BookMarkdownDocument.filename(identity))
    }
    @Test fun reservedMarkerTextInNotesCannotBreakTheGeneratedContract() {
        val block=BookMarkdownDocument.block(book().copy(notes=BookMarkdownDocument.END),emptyList())
        val document=BookMarkdownDocument.create(identity,block)
        assertEquals(document,BookMarkdownDocument.merge(document,identity,block))
        assertTrue(document.contains("&lt;!-- booktracker:end --&gt;"))
    }
    @Test fun quotedYamlKeysBomAndSingleQuotedIdsAreRecognizedAndDuplicateOrMergedIdsRejected() {
        val good=BookMarkdownDocument.create(identity,item().block)
        val quoted="\uFEFF"+good.replace("booktracker_id: \"$identity\"","'booktracker_id': '$identity'")
        assertEquals(quoted,BookMarkdownDocument.merge(quoted,identity,item().block))
        val duplicate=good.replace("booktracker_export_version:","\"booktracker_id\": \"other\"\nbooktracker_export_version:")
        assertThrows(MarkdownConflict::class.java) { BookMarkdownDocument.merge(duplicate,identity,item().block) }
        val merged=good.replace("booktracker_export_version:","<<: *other\nbooktracker_export_version:")
        assertThrows(MarkdownConflict::class.java) { BookMarkdownDocument.merge(merged,identity,item().block) }
    }
}
