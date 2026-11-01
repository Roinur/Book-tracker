package com.roinur.booktracker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class BookReadingListFormat { TEXT, IMAGE }

@Composable
internal fun BookReadingListFormatDialog(onDismiss: () -> Unit, onChoose: (BookReadingListFormat) -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    AlertDialog(onDismissRequest = onDismiss, shape = shape, tonalElevation = 0.dp,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        title = { Text("Export reading list") },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onChoose(BookReadingListFormat.TEXT) }, shape = shape,
                    modifier = Modifier.weight(1f).heightIn(min = 58.dp)) {
                    BookButtonLabel(R.drawable.ic_copy_24, "Text")
                }
                OutlinedButton(onClick = { onChoose(BookReadingListFormat.IMAGE) }, shape = shape,
                    modifier = Modifier.weight(1f).heightIn(min = 58.dp)) {
                    BookButtonLabel(R.drawable.ic_image_24, "Image")
                }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
internal fun BookReadingListImageDialog(
    books: List<BookRow>, sessions: List<BookReadingSessionRow>, options: BookReadingListOptions, onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var failure by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<File?>(null) }
    val saveImage = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val file = pendingSave; pendingSave = null
        if (uri != null && file != null) scope.launch {
            saving = true
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
                        ?: error("Could not open destination")
                }.isSuccess
            }
            saving = false
            Toast.makeText(context, if (ok) "Image saved" else "Could not save image", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit) {
        try {
            val groups = withContext(Dispatchers.Default) { buildBookReadingList(books, sessions, options = options) }
            files = exportBookReadingPoster(context, groups, options) { current, total -> progress = "$current / $total" }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { failure = error.message ?: "Could not create image" }
    }
    val file = files.firstOrNull()
    val shape = RoundedCornerShape(8.dp)
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(14.dp).fillMaxWidth()
            .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.88f)
            .clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Reading list", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onDismiss, enabled = !saving) { Text("Close") }
            }
            if (failure != null) Text(failure!!, color = MaterialTheme.colorScheme.error)
            else if (files.isEmpty()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text("Creating image${if (progress.isBlank()) "…" else " · $progress"}")
                }
            } else {
                if (file != null) BookReadingPosterPreview(file, Modifier.fillMaxWidth().weight(1f, fill = false))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(enabled = file != null, onClick = {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.readinglistexports", file!!)
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newUri(context.contentResolver, "Reading list", uri))
                        Toast.makeText(context, "Image copied", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy image") }
                    TextButton(enabled = file != null && !saving, onClick = {
                        pendingSave = file; saveImage.launch(file!!.name)
                    }) { Text(if (saving) "Saving…" else "Save") }
                    TextButton(onClick = {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.readinglistexports", file!!)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newUri(context.contentResolver, "Reading list", uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "Share reading list"))
                    }) { Text("Share") }
                }
            }
        }
    }
}

/** Only decode the visible strips, including for posters taller than the GPU texture limit. */
@Composable
internal fun BookReadingPosterPreview(file: File, modifier: Modifier, description: String = "Reading list image") {
    val bounds by produceState<Pair<Int, Int>?>(null, file) {
        value = withContext(Dispatchers.IO) {
            val info = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, info)
            if (info.outWidth > 0 && info.outHeight > 0) info.outWidth to info.outHeight else null
        }
    }
    val dimensions = bounds
    if (dimensions == null) Box(modifier.heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp))
    } else LazyColumn(modifier) {
        items((dimensions.second + 1199) / 1200) { index ->
            val start = index * 1200
            val end = minOf(start + 1200, dimensions.second)
            val preview by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file, index) {
                value = withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    val decoder = BitmapRegionDecoder.newInstance(file.path, false)
                    try { decoder?.decodeRegion(Rect(0, start, dimensions.first, end),
                        BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap() }
                    finally { decoder?.recycle() }
                }
            }
            Box(Modifier.fillMaxWidth().aspectRatio(dimensions.first.toFloat() / (end - start))) {
                preview?.let { Image(it, if (index == 0) description else null, Modifier.fillMaxSize()) }
            }
        }
    }
}
