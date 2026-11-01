package com.roinur.booktracker

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/** Opaque RGB PNG written in scanlines, without allocating a full-height bitmap. */
internal class BookPosterPngWriter(output: OutputStream, private val width: Int, private val height: Int) : Closeable {
    private val destination = DataOutputStream(output)
    private val deflater = Deflater(Deflater.BEST_SPEED)
    private var rowsWritten = 0
    private var finished = false
    private fun chunk(type: String, bytes: ByteArray, count: Int = bytes.size) {
        val name = type.toByteArray(Charsets.US_ASCII)
        destination.writeInt(count); destination.write(name); destination.write(bytes, 0, count)
        val crc = CRC32().apply { update(name); update(bytes, 0, count) }
        destination.writeInt(crc.value.toInt())
    }
    private val idat = object : OutputStream() {
        private val buffer = ByteArray(32768)
        private var used = 0
        override fun write(value: Int) { buffer[used++] = value.toByte(); if (used == buffer.size) flush() }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            var start = offset; var remaining = length
            while (remaining > 0) {
                val count = minOf(remaining, buffer.size - used)
                bytes.copyInto(buffer, used, start, start + count)
                used += count; start += count; remaining -= count
                if (used == buffer.size) flush()
            }
        }
        override fun flush() { if (used > 0) { chunk("IDAT", buffer, used); used = 0 } }
    }
    private val compressed = DeflaterOutputStream(idat, deflater, 32768)
    private val row = ByteArray(1 + width * 3)
    init {
        require(width in 1..10000 && height > 0)
        destination.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
        val header = ByteArrayOutputStream()
        DataOutputStream(header).use { it.writeInt(width); it.writeInt(height); it.write(byteArrayOf(8, 2, 0, 0, 0)) }
        chunk("IHDR", header.toByteArray())
    }
    fun writePixels(pixels: IntArray, rows: Int) {
        check(!finished)
        require(rows >= 0 && rows <= height - rowsWritten && pixels.size.toLong() >= width.toLong() * rows)
        repeat(rows) { y ->
            var offset = 1 // PNG filter 0: each row is independently encoded RGB.
            repeat(width) { x ->
                val color = pixels[y * width + x]
                row[offset++] = (color ushr 16).toByte(); row[offset++] = (color ushr 8).toByte(); row[offset++] = color.toByte()
            }
            compressed.write(row)
        }
        rowsWritten += rows
    }
    fun finish() {
        check(!finished && rowsWritten == height) { "Incomplete poster image" }
        compressed.finish(); idat.flush(); chunk("IEND", byteArrayOf()); destination.flush(); finished = true
    }
    override fun close() { deflater.end() }
}
