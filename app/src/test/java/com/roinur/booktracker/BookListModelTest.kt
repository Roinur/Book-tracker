package com.roinur.booktracker

import com.roinur.booktracker.data.booklist.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BookListModelTest {
    private val id = UUID.randomUUID().toString()
    private fun record(title: String = "Book") = BookListRecord(id,
        listOf("Author", title, "200", "Unknown", "Unread", "", "0", "0", "", "", "", "0", ""))
    private fun BookListRecord.change(field: BookListField, value: String) = copy(values = values.toMutableList().also { it[field.ordinal] = value })

    @Test fun independentEditsMergeWithoutChoosingAWinnerForTheWholeBook() {
        val base = record(); val app = base.change(BookListField.TITLE, "New title"); val sheet = base.change(BookListField.PRIORITY, "9")
        val merged = BookListMerger.merge(mapOf(id to base), mapOf(id to app), mapOf(id to sheet))
        assertTrue(merged.conflicts.isEmpty()); assertEquals("New title", merged.records.getValue(id).title)
        assertEquals("9", merged.records.getValue(id)[BookListField.PRIORITY])
    }
    @Test fun conflictChoicesApplyOnlyToTheValuesTheUserActuallyReviewed() {
        val base = record(); val app = record("App title"); val sheet = record("Sheet title")
        val c = BookListMerger.merge(mapOf(id to base), mapOf(id to app), mapOf(id to sheet)).conflicts.single()
        val choices = mapOf(c.choiceKey to "sheet")
        assertEquals(sheet, BookListMerger.merge(mapOf(id to base), mapOf(id to app), mapOf(id to sheet), choices).records[id])
        assertEquals(1, BookListMerger.merge(mapOf(id to base), mapOf(id to app), mapOf(id to record("Edited again")), choices).conflicts.size)
    }
    @Test fun missingSheetRowsNeverDeleteLibraryBooks() {
        val b = record()
        assertEquals(b, BookListMerger.merge(mapOf(id to b), mapOf(id to b), emptyMap()).records[id])
        assertTrue(BookListMerger.merge(mapOf(id to b), emptyMap(), mapOf(id to b)).records.isEmpty())
        assertEquals(1, BookListMerger.merge(mapOf(id to b), emptyMap(), mapOf(id to record("Changed"))).conflicts.size)
    }
    @Test fun newRowsAndReorderingUseIdentitiesNotTitlesOrPositions() {
        val first = record(); val second = record().copy(key = UUID.randomUUID().toString())
        val result = BookListMerger.merge(emptyMap(), mapOf(id to first), linkedMapOf(second.key to second, id to first))
        assertTrue(result.conflicts.isEmpty()); assertEquals(2, result.records.size)
    }
    @Test fun legacyWhitespaceIsEquivalentWithoutRewritingTheStoredBook() {
        val book = BookRow(1,"","  Title "," Author  ",200,0,BookStatus.WISHLIST,0,"","","","",false,"","","","",0,listSyncKey=id)
        val record = BookListRecord.fromBook(book)
        assertEquals("Title",record.title); assertEquals("Author",record[BookListField.AUTHOR])
        assertEquals(record,BookListRecord.parse(id,record.cells().map(Any::toString)))
        assertEquals("  Title ",book.title)
    }
    @Test fun parserKeepsActualPagesIndependentOfFinishedStatusAndHoursAsSeconds() {
        val source = record().change(BookListField.STATUS, "Completed").change(BookListField.READ_PAGES,"180")
            .change(BookListField.HOURS,"2.5").change(BookListField.FINISHED,"2024-07-30").change(BookListField.ISBN,"9780140449136")
        val parsed = BookListRecord.parse(id, source.values)
        assertEquals("180",parsed[BookListField.READ_PAGES]); assertEquals("9000",parsed[BookListField.HOURS])
        assertEquals(parsed, BookListRecord.parse(id, parsed.cells().map(Any::toString)))
    }
    @Test fun malformedNumbersStatusesAndKeysAreRejectedRatherThanCoerced() {
        for ((field,value) in listOf(BookListField.PAGES to "-1", BookListField.PAGES to "2.5", BookListField.PRIORITY to "11", BookListField.HOURS to "NaN", BookListField.STATUS to "Maybe")) {
            assertThrows(IllegalArgumentException::class.java) { BookListRecord.parse(id, record().change(field,value).values) }
        }
        assertThrows(IllegalArgumentException::class.java) { BookListRecord.parse("1",record().values) }
    }
    @Test fun ownershipUsesRealCheckboxValuesAndPreservesUnknown() {
        for ((value,cell) in listOf("Owned" to true,"Not owned" to false,"Unknown" to "")) {
            val record=record().change(BookListField.OWNERSHIP,value)
            assertEquals(cell,record.cells()[3])
            assertEquals(value,BookListRecord.parse(id,record.cells().map(Any::toString))[BookListField.OWNERSHIP])
        }
    }
    @Test fun formulasRespectSwedishAndEnglishSeparatorsWithoutArrayLiteralAmbiguity() {
        val swedish=BookListSheetFormulas.formulas("sv_SE")
        val english=BookListSheetFormulas.formulas("en_US")
        assertEquals("=COUNTIF(E2:E10001;\"Completed\")",swedish["AI3"])
        assertEquals("=COUNTIF(E2:E10001,\"Completed\")",english["AI3"])
        assertTrue(swedish.getValue("P2").contains("HSTACK(A2:A10001;B2:B10001;C2:C10001;IF(ISNUMBER(F2:F10001);F2:F10001;0);"))
        assertTrue(swedish.getValue("P2").contains(";4;FALSE;1;TRUE;2;TRUE"))
        assertTrue(swedish.getValue("W3").contains("E2:E10001=\"Unread\""))
        assertTrue(swedish.getValue("W3").contains("${'$'}Y${'$'}1=\"Smaller\""))
        assertTrue(swedish.getValue("W3").contains("C2:C10001<${'$'}X${'$'}1;C2:C10001>${'$'}X${'$'}1"))
        assertFalse(swedish.values.any { it.contains('{') })
    }
    @Test fun expectationScalePreservesOldUnitsAndCompletionUsesTheActualRating() {
        val book=BookRow(1,"","Book","Author",200,0,BookStatus.READING,3,"","","","",false,"","","","",0,listSyncKey=id,readingPriority=9)
        val expected=BookListRecord.fromBook(book)
        assertEquals(4.5,expected.cells()[5])
        assertEquals(expected,BookListRecord.parse(id,expected.cells().map(Any::toString)))
        val finished=BookListRecord.fromBook(book.copy(status=BookStatus.FINISHED))
        assertEquals(3.0,finished.cells()[5]);assertEquals("6",finished[BookListField.PRIORITY])
    }
    @Test fun checkboxOnlyRowsAreNotBooksButRowsWithIdentityOrContentStillAre() {
        val row=MutableList(14) { "" }
        row[3]="false";assertTrue(BookListRecord.isEmptyRow(row))
        row[3]="true";assertTrue(BookListRecord.isEmptyRow(row))
        row[1]="Book";assertFalse(BookListRecord.isEmptyRow(row))
        row[1]="";row[13]=id;assertFalse(BookListRecord.isEmptyRow(row))
    }
}
