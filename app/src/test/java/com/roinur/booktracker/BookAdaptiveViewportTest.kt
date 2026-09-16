package com.roinur.booktracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookAdaptiveViewportTest {
    @Test fun referencePhoneKeepsOriginalScale() {
        assertEquals(1f, bookViewportScale(420f, 2800f / 3f), 0.0001f)
    }
    @Test fun smallerViewportStillFitsReferenceDesign() {
        for ((width, height) in listOf(360f to 740f, 320f to 568f, 393f to 803f, 420f to 885f)) {
            val scale = bookViewportScale(width, height)
            assertTrue(420f * scale <= width + 0.01f)
            assertTrue((2800f / 3f) * scale <= height + 0.01f)
        }
    }
    @Test fun largerDevicesAreNotEnlarged() {
        assertEquals(1f, bookViewportScale(800f, 1200f), 0f)
    }
}
