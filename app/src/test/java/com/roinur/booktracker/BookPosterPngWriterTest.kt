package com.roinur.booktracker

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.util.Random
import java.util.zip.CRC32
import java.util.zip.InflaterInputStream
import org.junit.Assert.*
import org.junit.Test

class BookPosterPngWriterTest {
    private data class Png(val width: Int, val height: Int, val scanlines: ByteArray, val chunks: Int)
    private fun decode(bytes: ByteArray): Png {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val signature = ByteArray(8); input.readFully(signature)
        assertArrayEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10), signature)
        val compressed = ByteArrayOutputStream()
        var width = 0; var height = 0; var chunks = 0
        while (true) {
            val length = input.readInt()
            val name = ByteArray(4); input.readFully(name)
            val data = ByteArray(length); input.readFully(data)
            val crc = input.readInt()
            assertEquals(CRC32().apply { update(name); update(data) }.value.toInt(), crc)
            when (String(name, Charsets.US_ASCII)) {
                "IHDR" -> {
                    val header = DataInputStream(ByteArrayInputStream(data))
                    width = header.readInt(); height = header.readInt()
                    assertEquals(8, header.readUnsignedByte()); assertEquals(2, header.readUnsignedByte())
                }
                "IDAT" -> { compressed.write(data); chunks++ }
                "IEND" -> break
            }
        }
        assertEquals(0, input.available())
        return Png(width, height, InflaterInputStream(ByteArrayInputStream(compressed.toByteArray())).use { it.readBytes() }, chunks)
    }

    @Test fun encodesIndependentStripsIntoOneRgbImageWithValidChecksums() {
        val output = ByteArrayOutputStream()
        BookPosterPngWriter(output, 2, 3).use {
            it.writePixels(intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt()), 1)
            it.writePixels(intArrayOf(0xFF0000FF.toInt(), 0xFFFFFFFF.toInt(), 0, 0xFF123456.toInt()), 2)
            it.finish()
        }
        val png = decode(output.toByteArray())
        assertEquals(2, png.width); assertEquals(3, png.height)
        assertArrayEquals(intArrayOf(0,255,0,0,0,255,0, 0,0,0,255,255,255,255, 0,0,0,0,18,52,86),
            png.scanlines.map { it.toInt() and 255 }.toIntArray())
    }

    @Test fun largeStreamsUseMultipleCompressedChunksWithoutSplittingTheImage() {
        val output = ByteArrayOutputStream()
        val random = Random(17)
        BookPosterPngWriter(output, 500, 100).use { png ->
            repeat(100) { png.writePixels(IntArray(500) { random.nextInt() }, 1) }
            png.finish()
        }
        val png = decode(output.toByteArray())
        assertEquals(500, png.width); assertEquals(100, png.height)
        assertEquals((1 + 500 * 3) * 100, png.scanlines.size)
        assertTrue(png.chunks > 1)
    }

    @Test fun rejectsIncompleteAndOversizedScanlineCounts() {
        BookPosterPngWriter(ByteArrayOutputStream(), 2, 3).use { png ->
            png.writePixels(intArrayOf(0, 0), 1)
            assertThrows(IllegalStateException::class.java) { png.finish() }
            assertThrows(IllegalArgumentException::class.java) { png.writePixels(IntArray(6), 3) }
        }
    }
}
