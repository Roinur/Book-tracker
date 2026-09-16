package com.roinur.booktracker
import org.junit.Assert.*
import org.junit.Test
class BookMetadataTest {
    @Test fun readsRealSwedishEditionWithoutTranslatorOrDates() {
        val xml = javaClass.getResource("/blek-laga.xml")!!.readText()
        val book = parseLibrisMetadata(xml, "9789178933419")!!
        assertEquals("Blek l\u00e5ga", book.title)
        assertEquals(286, book.pageCount)
        assertTrue(book.authors.startsWith("Vladimir"))
        assertFalse(book.authors.contains("1977"))
        assertFalse(book.authors.contains("Lundgren"))
        assertNull(parseLibrisMetadata(xml, "9789100116316"))
        assertEquals(286, parseLibrisMetadata(xml, "9178933412")!!.pageCount)
    }
    @Test fun fillsMissingFieldsWithoutReplacingKnownValues() {
        val a = BookSeed("x", "Title", "Author", 0, "", "first", "")
        val b = BookSeed("x", "Other", "Other", 286, "cover", "second", "")
        val merged = mergeBookMetadata("x", listOf(a, b))!!
        assertEquals("Title", merged.title)
        assertEquals("Author", merged.authors)
        assertEquals(286, merged.pageCount)
        assertEquals("cover", merged.coverUrl)
    }
}
