package com.roinur.booktracker

import org.junit.Assert.assertEquals
import org.junit.Test

class BookCollectionSelectionTest {
    private val first = BookRow(1, "", "Republic", "Plato", 300, 120, BookStatus.READING,
        0, "", "", "", "Classic, Philosophy", false, "", "", "", "", 0)
    private val second = first.copy(id = 2, collections = "Classic, Religion")
    private val books = listOf(first, second)

    @Test fun bookFirstHighlightsItsCollectionsAndAllowsRemoval() {
        val selected = BookCollectionSelection().toggleBook(1)
        assertEquals(setOf("classic", "philosophy"), selected.selectedCollections(books))
        val removed = selected.toggleCollection("Philosophy", books)
        assertEquals(setOf("classic"), removed.selectedCollections(books))
        assertEquals(mapOf("philosophy" to false), removed.changes)
    }

    @Test fun collectionFirstRemainsSelectedWhenBooksAreChosen() {
        val selection = BookCollectionSelection().toggleCollection("Novel", books).toggleBook(1).toggleBook(2)
        assertEquals(setOf("classic", "novel"), selection.selectedCollections(books))
        assertEquals(mapOf("novel" to true), selection.changes)
    }

    @Test fun multipleBooksHighlightOnlyCommonCollectionsWithoutSchedulingOtherRemovals() {
        val selection = BookCollectionSelection().toggleBook(1).toggleBook(2).toggleCollection("Classic", books)
        assertEquals(emptySet<String>(), selection.selectedCollections(books))
        assertEquals(mapOf("classic" to false), selection.changes)
    }

    @Test fun mixedMembershipBecomesFullThenAbsentWhenToggled() {
        val selection = BookCollectionSelection().toggleBook(1).toggleBook(2)
        assertEquals(setOf("philosophy", "religion"), selection.mixedCollections(books))
        val added = selection.toggleCollection("Philosophy", books)
        assertEquals(setOf("classic", "philosophy"), added.selectedCollections(books))
        assertEquals(setOf("religion"), added.mixedCollections(books))
        assertEquals("Classic, Religion, Philosophy", added.previewCollections(second, listOf("Classic", "Philosophy", "Religion")))
        val removed = added.toggleCollection("Philosophy", books)
        assertEquals(setOf("religion"), removed.mixedCollections(books))
        assertEquals("Classic", removed.previewCollections(first, listOf("Classic", "Philosophy", "Religion")))
        assertEquals("Classic, Religion", removed.previewCollections(second, listOf("Classic", "Philosophy", "Religion")))
    }

    @Test fun previewLeavesUnselectedBooksUntouched() {
        val selection = BookCollectionSelection().toggleBook(1).toggleCollection("Novel", books)
            .toggleCollection("Philosophy", books)
        assertEquals("Classic, Novel", selection.previewCollections(first, listOf("Novel")))
        assertEquals(second.collections, selection.previewCollections(second, listOf("Novel")))
    }
}
