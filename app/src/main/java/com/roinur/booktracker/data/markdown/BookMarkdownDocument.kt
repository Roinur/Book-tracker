package com.roinur.booktracker.data.markdown

import com.roinur.booktracker.*
import java.util.Base64
import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

internal class MarkdownConflict(message: String) : IllegalStateException(message)

/** The only part of an existing document we may replace is the managed block's interior. */
internal object BookMarkdownDocument {
    const val BEGIN = "<!-- booktracker:begin -->"
    const val END = "<!-- booktracker:end -->"
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    fun filename(id: String): String {
        require(id.isNotBlank()) { "The book has no persistent identity." }
        val safe = if (uuid.matches(id)) id else "id-" + Base64.getUrlEncoder().withoutPadding().encodeToString(id.toByteArray(Charsets.UTF_8))
        return "book-$safe.md"
    }
    private fun literal(text: String) = text.replace(BEGIN, "&lt;!-- booktracker:begin --&gt;")
        .replace(END, "&lt;!-- booktracker:end --&gt;")
    private fun heading(text: String) = literal(text).replace('\r', ' ').replace('\n', ' ')
        .replace("[", "\\[").replace("]", "\\]").replace("#", "\\#")

    /** App metadata stores multiple authors comma-separated; explicit semicolons/newlines/& also separate names.
     * A shared vault-relative target connects books without extra Drive files or permissions.
     * Encode path/link delimiters rather than replacing them, so different names cannot collapse to one target.
     */
    internal fun authorLinks(authors: String): String = authors
        .split(Regex("[,;\\r\\n]|\\s+&\\s+"))
        .map { Normalizer.normalize(it.trim().replace(Regex("\\s+"), " "), Normalizer.Form.NFC) }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(", ") { name ->
            val reserved = Regex("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?$").matches(name)
            val safe = buildString {
                name.forEachIndexed { index, char ->
                    if (char.code < 32 || char in "%/\\[]#|^:*?\"<>" ||
                        (char == '.' && index == name.lastIndex) || (reserved && index == 0))
                        append("%" + char.code.toString(16).uppercase(Locale.ROOT).padStart(2, '0'))
                    else append(char)
                }
            }
            val label = literal(name).replace("[", "&#91;").replace("]", "&#93;").replace("|", "&#124;")
            "[[Författare/$safe|$label]]"
        }

    fun block(book: BookRow, notes: List<BookNote>): String = buildString {
        appendLine("# ${heading(book.title)}")
        appendLine()
        appendLine("## Bokinformation")
        fun field(label: String, value: String) { if (value.isNotBlank()) appendLine("- **$label:** ${literal(value).replace("\n", "  \n  ")}") }
        if (book.authors.isNotBlank()) appendLine("- **Författare:** ${authorLinks(book.authors)}")
        field("ISBN", book.isbn)
        field("Status", book.status.label); field("Sidor", book.pageCount.toString())
        field("Lästa sidor", book.currentPage.toString()); book.bookmarkPage?.let { field("Bokmärke", it.toString()) }
        field("Lästid (sekunder)", book.readingSeconds.toString())
        if (book.rating > 0) field("Betyg", book.rating.toString())
        field("Samlingar", book.collections); field("Tillagd", book.addedAt)
        field("Påbörjad", book.startedAt); field("Avslutad", book.finishedAt); field("Senast läst", book.lastReadAt)
        field("Källa", book.sourceUrl)
        if (book.parts.isNotEmpty()) {
            appendLine(); appendLine("## Delverk")
            book.parts.forEach { appendLine("- ${heading(it.title)} · sidor ${it.startPage}–${it.endPage}" + if (it.rating > 0) " · betyg ${it.rating}" else "") }
        }
        if (book.notes.isNotBlank()) { appendLine(); appendLine("## Bokanteckningar"); appendLine(); appendLine(literal(book.notes)) }
        val sections = linkedMapOf(BookNoteKind.NOTE to "Läsanteckningar", BookNoteKind.QUOTE to "Citat",
            BookNoteKind.THOUGHT to "Tankar", BookNoteKind.WORD to "Ord", BookNoteKind.COUNTDOWN to "Nedräkningar")
        sections.forEach { (kind, title) ->
            val rows = notes.filter { it.kind == kind }.sortedWith(compareBy<BookNote> { it.createdAt }.thenBy { it.id })
            if (rows.isNotEmpty()) { appendLine(); appendLine("## $title") }
            rows.forEach { note ->
                appendLine(); appendLine("### ${heading(note.createdAt)} · sida ${note.page} · lästid ${note.durationSeconds} s"); appendLine()
                val text = literal(note.note)
                appendLine(if (kind == BookNoteKind.QUOTE) text.split('\n').joinToString("\n") { "> $it" } else text)
            }
        }
    }.trimEnd() + "\n"

    fun create(id: String, block: String): String = "---\nbooktracker_id: ${JsonPrimitive(id)}\nbooktracker_export_version: 1\n---\n" +
        "$BEGIN\n$block$END\n\n## Mina Obsidian-anteckningar\n\n"

    private data class Region(val start: Int, val end: Int, val newline: String)
    private fun region(text: String, id: String): Region {
        val lines = Regex("(?m)^[^\\n]*(?:\\n|$)").findAll(text).filter { it.value.isNotEmpty() }.toList()
        fun line(match: MatchResult) = match.value.removeSuffix("\n").removeSuffix("\r").removePrefix("\uFEFF")
        if (lines.firstOrNull()?.let(::line) != "---") throw MarkdownConflict("Missing YAML properties; the file was left unchanged.")
        val yamlEnd = lines.drop(1).indexOfFirst { line(it) == "---" }.let { if (it < 0) -1 else it + 1 }
        if (yamlEnd < 0) throw MarkdownConflict("Invalid YAML properties; the file was left unchanged.")
        val yaml = lines.subList(1, yamlEnd).map { line(it) }
        // Fail closed for complex/merged identity declarations, rather than ignoring a second effective ID.
        if (yaml.any { it.startsWith("<<:") || it.startsWith("? ") || it.startsWith(": ") })
            throw MarkdownConflict("Complex or merged YAML identity properties cannot be validated safely. Use one explicit booktracker_id; the file was left unchanged.")
        val keyPattern = Regex("^(\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^']|'')*'|[^\\s:#][^:]*?)\\s*:")
        fun key(raw: String): String = when {
            raw.startsWith('"') -> runCatching { Json.parseToJsonElement(raw).jsonPrimitive.content }.getOrDefault("")
            raw.startsWith('\'') -> raw.removeSurrounding("'").replace("''", "'")
            else -> raw.trim()
        }
        val ids = yaml.mapNotNull { text -> keyPattern.find(text)?.let { match ->
            if (key(match.groupValues[1]) == "booktracker_id") text.substring(match.range.last + 1).trim() else null
        } }
        if (ids.size != 1) throw MarkdownConflict("Missing or duplicate booktracker_id; the file was left unchanged.")
        val raw = ids.single()
        val parsed = runCatching {
            when {
                raw.startsWith('"') -> Json.parseToJsonElement(raw).jsonPrimitive.content
                raw.startsWith('\'') && raw.endsWith('\'') -> raw.substring(1, raw.length - 1).replace("''", "'")
                else -> raw
            }
        }.getOrNull()
        if (parsed != id) throw MarkdownConflict("The file belongs to another book (booktracker_id differs); nothing was overwritten.")
        val begins = lines.filter { line(it) == BEGIN }; val ends = lines.filter { line(it) == END }
        if (begins.size != 1 || ends.size != 1 || begins.single().range.first <= lines[yamlEnd].range.last || begins.single().range.last >= ends.single().range.first)
            throw MarkdownConflict("Missing, duplicate or incorrectly ordered BookTracker markers; the file was left unchanged.")
        val begin = begins.single()
        if (!begin.value.endsWith('\n')) throw MarkdownConflict("Invalid BookTracker marker line.")
        return Region(begin.range.last + 1, ends.single().range.first, if (begin.value.endsWith("\r\n")) "\r\n" else "\n")
    }

    fun merge(text: String, id: String, block: String): String {
        val r = region(text, id)
        return text.substring(0, r.start) + block.replace("\r\n", "\n").replace("\n", r.newline) + text.substring(r.end)
    }
}
