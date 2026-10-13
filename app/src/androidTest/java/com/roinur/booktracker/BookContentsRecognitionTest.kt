package com.roinur.booktracker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue

class BookContentsRecognitionTest {
    @Test fun fitzgeraldPhotoRegression() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        assumeTrue("Local photo fixture not included", assets.list("private").orEmpty().contains("contents-fitzgerald.jpg"))
        val bitmap = assets.open("private/contents-fitzgerald.jpg").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })!!
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            assertEquals(listOf(BookContentsEntry("Introduction"), BookContentsEntry("This Side of Paradise", 1),
                BookContentsEntry("The Beautiful and Damned", 243), BookContentsEntry("The Great Gatsby", 585)), bookRecognizeContents(bitmap, recognizer))
        } finally { recognizer.close(); bitmap.recycle() }
    }

    @Test fun bundledModelReadsContentsTitlesAndPageNumbersWithoutDownloading() {
        val bitmap = Bitmap.createBitmap(1400, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 48f }
        canvas.drawText("CONTENTS", 60f, 90f, paint)
        canvas.drawText("A Study in Scarlet", 60f, 230f, paint)
        canvas.drawText("3", 1250f, 230f, paint)
        canvas.drawText("The Sign of the Four", 60f, 370f, paint)
        canvas.drawText("75", 1250f, 370f, paint)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val entries = bookRecognizeContents(bitmap, recognizer)
            assertEquals(listOf(BookContentsEntry("A Study in Scarlet", 3), BookContentsEntry("The Sign of the Four", 75)), entries)
        } finally { recognizer.close(); bitmap.recycle() }
    }
}
