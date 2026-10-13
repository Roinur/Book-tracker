package com.roinur.booktracker

import org.junit.Assert.*
import org.junit.Test

class BookContentsParserTest {
    private fun line(text: String, y: Int, left: Int = 20, right: Int = 600) = BookContentsLine(text, left, y, right, y + 24)

    @Test fun numberedRowsAndSwedishTitlesKeepReadingOrder() {
        val result = bookParseContents(listOf(line("3 Den blå karbunkeln .... 226", 150),
            line("INNEHÅLLSFÖRTECKNING", 10), line("1 En studie i rött ...... 3", 50), line("2 De fyras tecken ··· 75", 100)))
        assertEquals(listOf(BookContentsEntry("En studie i rött", 3), BookContentsEntry("De fyras tecken", 75),
            BookContentsEntry("Den blå karbunkeln", 226)), result)
    }

    @Test fun separateNumberBlocksMatchByVerticalPositionNotOcrBlockOrder() {
        val result = bookParseContents(listOf(line("75", 100, 800, 850), line("3", 50, 800, 850),
            line("The Sign of the Four", 100), line("A Study in Scarlet", 50)))
        assertEquals(listOf(BookContentsEntry("A Study in Scarlet", 3), BookContentsEntry("The Sign of the Four", 75)), result)
    }

    @Test fun titlesWithoutPagesAreStillImportedAndNoPageIsInvented() {
        val result = bookParseContents(listOf(line("Contents", 10), line("A Study in Scarlet", 50),
            line("The Sign of the Four", 100), line("vi", 150)))
        assertEquals(listOf(BookContentsEntry("A Study in Scarlet"), BookContentsEntry("The Sign of the Four")), result)
        assertEquals(listOf(null, null), bookContentsEndPages(result, 1077))
    }

    @Test fun nearbyChapterNumbersOnTheLeftDoNotBecomeStartPages() {
        val result = bookParseContents(listOf(line("12", 50, 0, 15), line("The Final Problem", 50), line("438", 50, 800, 850)))
        assertEquals(listOf(BookContentsEntry("The Final Problem", 438)), result)
    }

    @Test fun numberColumnFragmentsDoNotBecomeTitles() {
        val result = bookParseContents(listOf(line("Introduction....vii", 20), line("..VI", 20, 800, 850),
            line("This Side of Paradise....", 100), line("1", 100, 800, 850),
            line("The Beautiful and Damned...", 200), line("..243", 200, 800, 850),
            line("The Great Gatsby...", 300), line("585", 300, 800, 850)))
        assertEquals(listOf(BookContentsEntry("Introduction"), BookContentsEntry("This Side of Paradise", 1),
            BookContentsEntry("The Beautiful and Damned", 243), BookContentsEntry("The Great Gatsby", 585)), result)
    }

    @Test fun slantedTitlesMatchTheirProjectedNumberRow() {
        val result = bookParseContents(listOf(
            BookContentsLine("First work", 20, 100, 600, 130, -0.08f),
            BookContentsLine("Second work", 20, 140, 600, 170, -0.08f),
            line("3", 62, 800, 850), line("75", 102, 800, 850)))
        assertEquals(listOf(BookContentsEntry("First work", 3), BookContentsEntry("Second work", 75)), result)
    }

    @Test fun inferredEndsUseTheNextSelectedWorkAndInclusiveLastPage() {
        val parts = listOf(BookContentsEntry("Scarlet", 3), BookContentsEntry("Sign", 75), BookContentsEntry("Adventures", 145))
        assertEquals(listOf(74, 144, 1077), bookContentsEndPages(parts, 1077))
        assertEquals(listOf(null, null, null), bookContentsEndPages(listOf(BookContentsEntry("First", 30),
            BookContentsEntry("Unknown"), BookContentsEntry("Outside book", 2000)), 1077))
    }

    @Test fun overlappingPhotosDeduplicateWithoutSortingOrDiscardingEarlierChoices() {
        val first = listOf(BookContentsEntry("Scarlet", 3), BookContentsEntry("Sign"))
        val next = listOf(BookContentsEntry("SIGN", 75), BookContentsEntry("Adventures", 145), BookContentsEntry("Scarlet", 3))
        assertEquals(listOf(BookContentsEntry("Scarlet", 3), BookContentsEntry("SIGN", 75), BookContentsEntry("Adventures", 145)),
            bookMergeContents(first, next))
    }
}
