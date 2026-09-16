package com.roinur.booktracker
import org.junit.Assert.*
import org.junit.Test
class BookPageCalculatorTest {
    @Test fun convertsCompendiumPagesToBookProgress() {
        assertEquals(130, calculatedBookPage(100, "500", "530"))
        assertEquals(100, calculatedBookPage(100, "500", "500"))
    }
    @Test fun rejectsReversedMissingAndOverflowingRanges() {
        assertNull(calculatedBookPage(0, "530", "500"))
        assertNull(calculatedBookPage(0, "", "20"))
        assertNull(calculatedBookPage(0, "-1", "20"))
        assertNull(calculatedBookPage(Int.MAX_VALUE, "0", "1"))
    }
}
