package com.roinur.booktracker.data.markdown

internal data class MarkdownTarget(val account: String, val folder: String)
internal data class MarkdownItem(val bookId: Int, val identity: String, val revision: Long, val block: String)
internal data class MarkdownBinding(val fileId: String, val confirmed: Boolean)
internal data class MarkdownRemote(val id: String, val name: String, val text: String, val etag: String)
internal data class MarkdownFolder(val id: String, val name: String)

internal interface MarkdownBindings {
    fun binding(target: MarkdownTarget, identity: String): MarkdownBinding?
    /** Must be durably committed before attempting creation. */
    fun bind(target: MarkdownTarget, identity: String, binding: MarkdownBinding)
}
internal interface MarkdownTransport {
    fun folder(id: String): MarkdownFolder
    fun find(folder: String, name: String): List<String>
    fun generateId(): String
    fun read(folder: String, id: String): MarkdownRemote?
    fun create(folder: String, id: String, name: String, text: String)
    fun update(folder: String, document: MarkdownRemote, text: String)
}

/** No deletion or import operations exist on this interface. A failed read never becomes an empty file. */
internal class BookMarkdownEngine(private val remote: MarkdownTransport, private val bindings: MarkdownBindings) {
    fun export(target: MarkdownTarget, item: MarkdownItem, checkCurrent: () -> Unit = {}) {
        checkCurrent()
        remote.folder(target.folder)
        val name = BookMarkdownDocument.filename(item.identity)
        var binding = bindings.binding(target, item.identity)
        if (binding == null) {
            val found = remote.find(target.folder, name)
            if (found.size > 1) throw MarkdownConflict("More than one $name exists in the selected folder. Resolve the duplicate files in Drive and retry.")
            binding = if (found.isEmpty()) MarkdownBinding(remote.generateId(), false) else MarkdownBinding(found.single(), true)
            bindings.bind(target, item.identity, binding)
        }
        var document = remote.read(target.folder, binding.fileId)
        if (document == null) {
            if (binding.confirmed) throw MarkdownConflict("$name is missing, trashed, moved or no longer accessible. Restore the file or its permission in Drive, then retry. No replacement was created.")
            checkCurrent()
            remote.create(target.folder, binding.fileId, name, BookMarkdownDocument.create(item.identity, item.block))
            document = remote.read(target.folder, binding.fileId)
                ?: throw MarkdownConflict("Drive did not confirm the new Markdown file. Retry to verify the same file ID.")
        }
        if (document.name != name) throw MarkdownConflict("The exported file was renamed in Drive. Restore its stable filename $name and retry.")
        val merged = BookMarkdownDocument.merge(document.text, item.identity, item.block)
        if (merged != document.text) {
            checkCurrent()
            remote.update(target.folder, document, merged)
            document = remote.read(target.folder, binding.fileId)
                ?: throw MarkdownConflict("The uploaded file could not be verified. Retry; no success has been recorded.")
        }
        if (BookMarkdownDocument.merge(document.text, item.identity, item.block) != document.text)
            throw MarkdownConflict("The file changed during verification. Retry to read and merge the latest version.")
        bindings.bind(target, item.identity, MarkdownBinding(binding.fileId, true))
    }
}
