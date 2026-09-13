package com.roinur.booktracker

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

internal fun saveCoverBitmapToInternalStorage(context: Context, bitmap: Bitmap): String {
    val file = File(context.filesDir, "book_cover_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
    }
    return Uri.fromFile(file).toString()
}

