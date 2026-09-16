package com.roinur.booktracker

import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

internal fun metadataIsbn(value: String): String {
    val clean = value.filter { it.isDigit() || it.uppercaseChar() == 'X' }.uppercase()
    if (clean.length != 10) return clean
    val base = "978" + clean.take(9)
    val sum = base.mapIndexed { i, c -> c.digitToInt() * if (i % 2 == 0) 1 else 3 }.sum()
    return base + ((10 - sum % 10) % 10)
}

internal fun mergeBookMetadata(isbn: String, sources: List<BookSeed>): BookSeed? {
    if (sources.isEmpty()) return null
    return BookSeed(isbn, sources.firstOrNull { it.title.isNotBlank() && !it.title.startsWith("ISBN ") }?.title.orEmpty(),
        sources.firstOrNull { it.authors.isNotBlank() }?.authors.orEmpty(),
        sources.firstOrNull { it.pageCount > 0 }?.pageCount ?: 0,
        sources.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl.orEmpty(),
        sources.firstOrNull { it.sourceUrl.isNotBlank() }?.sourceUrl.orEmpty(), "")
}

internal fun parseLibrisMetadata(xml: String, isbn: String): BookSeed? {
    require(!xml.contains("<!DOCTYPE", ignoreCase = true) && !xml.contains("<!ENTITY", ignoreCase = true))
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }
    val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
    val records = doc.getElementsByTagNameNS("*", "record")
    for (i in 0 until records.length) {
        val record = records.item(i) as Element
        val fields = record.getElementsByTagNameNS("*", "datafield")
        fun fields(tag: String) = (0 until fields.length).map { fields.item(it) as Element }.filter { it.getAttribute("tag") == tag }
        fun sub(field: Element, code: String): String {
            val nodes = field.getElementsByTagNameNS("*", "subfield")
            return (0 until nodes.length).map { nodes.item(it) as Element }.firstOrNull { it.getAttribute("code") == code }?.textContent.orEmpty()
        }
        if (fields("020").none { metadataIsbn(sub(it, "a").substringBefore(" ")) == metadataIsbn(isbn) }) continue
        val titleField = fields("245").firstOrNull() ?: continue
        fun clean(value: String) = value.trim().trimEnd('/', ':', ';', ',', ' ')
        val title = listOf(clean(sub(titleField, "a")), clean(sub(titleField, "b"))).filter { it.isNotBlank() }.joinToString(": ")
        val authors = (fields("100") + fields("700").filter { sub(it, "4") == "aut" }).map {
            val name = clean(sub(it, "a"))
            if (it.getAttribute("ind1") == "1" && name.contains(',')) name.substringAfter(',').trim() + " " + name.substringBefore(',').trim() else name
        }.filter { it.isNotBlank() }.distinct().joinToString(", ")
        val extent = fields("300").firstOrNull()?.let { sub(it, "a") }.orEmpty()
        val pages = Regex("(?i)([0-9]+)\\s*(?:sidor|s\\.|pages|p\\.)").find(extent)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val controls = record.getElementsByTagNameNS("*", "controlfield")
        val id = (0 until controls.length).map { controls.item(it) as Element }.firstOrNull { it.getAttribute("tag") == "001" }?.textContent.orEmpty()
        return BookSeed(isbn, title, authors, pages, "", if (id.isBlank()) "" else "https://libris.kb.se/$id", "")
    }
    return null
}
