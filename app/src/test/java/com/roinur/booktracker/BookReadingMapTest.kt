package com.roinur.booktracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

class BookReadingMapTest {
    @Test fun sharedBookAppearsOnceAndConnectsBothFamilies() {
        val book = BookRow(1, "", "Republic", "Plato", 300, 120, BookStatus.READING,
            0, "", "", "", "Classics, Philosophy", false, "", "", "", "", 0)
        val map = BookReadingMap.from(listOf(book))
        assertEquals(1, map.books.size)
        assertEquals(setOf("Classics", "Philosophy"), map.books.single().families.toSet())
        assertTrue(map.families.all { 1 in it.bookIds })
    }

    @Test fun unreadBooksAppearInTheReadingMap() {
        val unread = BookRow(2, "", "To read", "", 200, 0, BookStatus.WISHLIST,
            0, "", "", "", "Philosophy", false, "", "", "", "", 0)
        assertEquals(BookStatus.WISHLIST, BookReadingMap.from(listOf(unread)).books.single().book.status)
    }

    @Test fun filteringKeepsNodePositionsAndViewportStable() {
        val read = BookRow(3, "", "Read", "", 200, 40, BookStatus.READING,
            0, "", "", "", "Philosophy", false, "", "", "", "", 0)
        val unread = BookRow(4, "", "Unread", "", 200, 0, BookStatus.WISHLIST,
            0, "", "", "", "Philosophy", false, "", "", "", "", 0)
        val all = BookReadingMap.from(listOf(read, unread))
        val filtered = all.filtered(ReadingMapFilter.READ)
        assertEquals(listOf(3), filtered.books.map { it.book.id })
        assertEquals(all.books.first { it.book.id == 3 }.x, filtered.books.single().x)
        assertEquals(all.minX, filtered.minX)
        assertEquals(all.maxY, filtered.maxY)
        assertEquals(setOf(3), filtered.families.single().bookIds)
    }

    @Test fun collectionTypeFilterKeepsOverlappingBooksAndOriginalLayout() {
        val republic = BookRow(5, "", "Republic", "Plato", 300, 120, BookStatus.READING,
            0, "", "", "", "Classics, Philosophy", false, "", "", "", "", 0)
        val novel = BookRow(6, "", "Novel", "", 200, 20, BookStatus.READING,
            0, "", "", "", "Classics", false, "", "", "", "", 0)
        val all = BookReadingMap.from(listOf(republic, novel), mapOf(
            "classics" to BookCollectionType.TYPE,
            "philosophy" to BookCollectionType.THEME))
        val themes = all.filtered(ReadingMapFilter.ALL, BookCollectionType.THEME)
        assertEquals(listOf("Philosophy"), themes.families.map { it.name })
        assertEquals(listOf(5), themes.books.map { it.book.id })
        assertEquals(listOf("Philosophy"), themes.books.single().families)
        assertEquals(all.books.first { it.book.id == 5 }.x, themes.books.single().x)
        assertEquals(all.minX, themes.minX)
        assertEquals(all.maxY, themes.maxY)
        assertEquals(setOf(5, 6), all.filtered(ReadingMapFilter.ALL, BookCollectionType.TYPE)
            .books.map { it.book.id }.toSet())
    }

    @Test fun pinchKeepsThePointUnderTheFingers() {
        val viewport = IntSize(900, 1200)
        val fingers = Offset(600f, 400f)
        val oldPan = Offset(30f, -20f)
        val oldZoom = 1.2f
        val newZoom = 1.8f
        val nextPan = mapPanAfterZoom(oldPan, fingers, viewport, newZoom / oldZoom, Offset.Zero)
        val worldX = (fingers.x - viewport.width / 2f - oldPan.x) / (0.92f * oldZoom * 0.9f)
        val worldY = (fingers.y - viewport.height / 2f - oldPan.y) / (0.92f * oldZoom * 0.9f)
        val before = Offset(viewport.width / 2f + worldX * 0.92f * oldZoom * 0.9f + oldPan.x,
            viewport.height / 2f + worldY * 0.92f * oldZoom * 0.9f + oldPan.y)
        val after = Offset(viewport.width / 2f + worldX * 0.92f * newZoom * 0.9f + nextPan.x,
            viewport.height / 2f + worldY * 0.92f * newZoom * 0.9f + nextPan.y)
        assertEquals(fingers.x, before.x, 0.001f)
        assertEquals(fingers.y, before.y, 0.001f)
        assertEquals(fingers.x, after.x, 0.001f)
        assertEquals(fingers.y, after.y, 0.001f)
    }

    @Test fun labelChoosesAnotherSideRatherThanOverlapping() {
        val area = androidx.compose.ui.geometry.Rect(0f, 0f, 300f, 300f)
        val below = androidx.compose.ui.geometry.Rect(80f, 125f, 180f, 155f)
        val label = readingMapLabelBounds(Offset(130f, 100f), 20f, 100f, 30f, 5f, area, listOf(below))!!
        assertTrue(!label.overlaps(below))
        assertEquals(45f, label.top, 0.001f)
    }

    @Test fun crowdedLabelsAreHiddenInsteadOfOverlappingOrLeavingViewport() {
        val area = androidx.compose.ui.geometry.Rect(0f, 0f, 100f, 100f)
        assertEquals(null, readingMapLabelBounds(Offset(50f, 50f), 20f, 80f, 30f, 5f, area, listOf(area)))
    }

    @Test fun focusedClusterStaysAboveThePreviewAndBelowControls() {
        val map = BookReadingMap(emptyList(), emptyList(), -500f, -500f, 500f, 500f)
        val viewport = IntSize(1200, 1800)
        val points = listOf(Offset(-150f, -100f), Offset(150f, 100f))
        val camera = readingMapFocusCamera(map, viewport, points, 3f)
        val fit = 1.2f * 0.92f
        points.forEach { point ->
            val screen = Offset(600f, 900f) + point * fit * camera.zoom + camera.pan
            assertTrue(screen.x >= 132f && screen.x <= 1068f)
            assertTrue(screen.y >= 312f && screen.y <= 1230f)
        }
    }
}
