package com.roinur.booktracker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun BookPeriodRecapDialog(recap: BookPeriodRecap, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val palette = BookRecapPalette(scheme.background.toArgb(), scheme.surface.toArgb(), scheme.onSurface.toArgb(),
        scheme.onSurfaceVariant.toArgb(), scheme.primary.toArgb(), scheme.primaryContainer.toArgb(),
        scheme.onPrimaryContainer.toArgb(), scheme.outlineVariant.toArgb())
    var file by remember(recap, palette) { mutableStateOf<File?>(null) }
    var failure by remember(recap, palette) { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var pendingSavePath by rememberSaveable { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val source = pendingSavePath?.let(::File); pendingSavePath = null
        if (uri != null && source != null) scope.launch {
            saving = true
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output -> source.inputStream().use { it.copyTo(output) } }
                        ?: error("Could not open destination")
                }
                Toast.makeText(context, "Reading recap saved", Toast.LENGTH_SHORT).show()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failure = "Could not save image. Please choose a destination and try again." }
            finally { saving = false }
        }
    }
    LaunchedEffect(recap, palette) {
        try { file = renderBookPeriodRecap(context, recap, palette) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failure = "Could not create the recap. Your reading data is unchanged." }
    }
    val description = "${recap.heading}. ${recap.start} to ${recap.end}. ${recap.pages} pages. " +
        "${bookFormatDuration(recap.seconds)} reading time. ${recap.finishedBooks} books finished. " +
        "${recap.activeDays} active days. Longest streak ${recap.longestStreak} days. " +
        "Most pages in a day ${recap.bestPageDay}. Most minutes in a day ${recap.bestMinuteDay}. " +
        recap.books.take(6).joinToString { it.book.title }
    val shape = RoundedCornerShape(8.dp)
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(14.dp).fillMaxWidth().height(LocalConfiguration.current.screenHeightDp.dp * 0.88f)
            .clip(shape).background(scheme.surfaceContainerHigh).border(1.dp, scheme.outlineVariant, shape).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Reading recap", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onDismiss, enabled = !saving) { Text("Close") }
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val ready = file
                if (ready != null) BookReadingPosterPreview(ready, Modifier.fillMaxSize(), description)
                else if (failure == null) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text("Creating your recap…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (failure != null) Text(failure!!, color = scheme.error, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(enabled = file != null && !saving, onClick = {
                    runCatching {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.readinglistexports", requireNotNull(file))
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newUri(context.contentResolver, "Reading recap", uri))
                        Toast.makeText(context, "Image copied", Toast.LENGTH_SHORT).show()
                    }.onFailure { failure = "Could not copy image. Try Save instead." }
                }) { Text("Copy image") }
                TextButton(enabled = file != null && !saving, onClick = {
                    pendingSavePath = file?.path
                    save.launch("Book-Tracker-${recap.range.name.lowercase(java.util.Locale.ROOT)}-${recap.end}.png")
                }) {
                    Text(if (saving) "Saving…" else "Save")
                }
                TextButton(enabled = file != null && !saving, onClick = {
                    runCatching {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.readinglistexports", requireNotNull(file))
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newUri(context.contentResolver, "Reading recap", uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share reading recap"))
                    }.onFailure { failure = "No app could open the share sheet. Try Save instead." }
                }) { Text("Share") }
            }
        }
    }
}
