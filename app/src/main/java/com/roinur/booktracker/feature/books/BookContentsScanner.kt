package com.roinur.booktracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private val contentsSaver = listSaver<List<BookContentsEntry>, String>(
    save = { entries -> entries.flatMap { listOf(it.title, it.startPage?.toString().orEmpty()) } },
    restore = { values -> values.chunked(2).map { BookContentsEntry(it[0], it[1].toIntOrNull()) } }
)
private fun BookContentsEntry.scanKey() = "${title.lowercase()}|$startPage"

@Composable
internal fun BookContentsScannerDialog(onDismiss: () -> Unit, onAdd: (List<BookContentsEntry>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val alive = remember { AtomicBoolean(true) }
    var entries by rememberSaveable(stateSaver = contentsSaver) { mutableStateOf(emptyList<BookContentsEntry>()) }
    var excluded by rememberSaveable { mutableStateOf(listOf<String>()) }
    var review by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraAllowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraAllowed = granted
        if (!granted) error = "Allow camera access or choose a photo."
    }
    DisposableEffect(Unit) { onDispose { alive.set(false); recognizer.close() } }
    LaunchedEffect(Unit) { if (!cameraAllowed) permission.launch(Manifest.permission.CAMERA) }

    fun process(release: () -> Unit = {}, loadBitmap: () -> Bitmap) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val bitmap = loadBitmap()
                        try { bookRecognizeContents(bitmap, recognizer) } finally { bitmap.recycle() }
                    }
                }
                if (alive.get()) result.fold(onSuccess = { scanned ->
                    if (scanned.isEmpty()) error = "No titles found. Try a sharper photo."
                    else {
                        val deselected = entries.filter { it.scanKey() in excluded }
                        val merged = bookMergeContents(entries, scanned)
                        excluded = merged.filter { entry -> deselected.any { old -> old.title.equals(entry.title, ignoreCase = true) &&
                            (old.startPage == entry.startPage || old.startPage == null || entry.startPage == null) } }.map { it.scanKey() }
                        entries = merged; review = true; error = ""
                    }
                }, onFailure = { error = "Could not read this photo. ${it.localizedMessage.orEmpty()}" })
            } finally { release(); if (alive.get()) busy = false }
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true; error = ""
            process { bookContentsBitmap(context, uri) }
        }
    }
    val shape = RoundedCornerShape(8.dp)
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(16.dp).imePadding().fillMaxWidth()
            .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.86f)
            .clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_scan_24), null, Modifier.size(24.dp), MaterialTheme.colorScheme.primary)
                Text(if (review) "Choose parts" else "Scan contents", Modifier.weight(1f).padding(start = 10.dp),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onDismiss, enabled = !busy, shape = shape) { Text("Close") }
            }
            if (review) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${entries.count { it.scanKey() !in excluded }} selected", Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { excluded = if (excluded.isEmpty()) entries.map { it.scanKey() } else emptyList() }, shape = shape) {
                        Text(if (excluded.isEmpty()) "Deselect all" else "Select all")
                    }
                }
                LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(bottom = 4.dp)) {
                    itemsIndexed(entries) { index, entry ->
                        val selected = entry.scanKey() !in excluded
                        Row(Modifier.fillMaxWidth().fittedClickable(shape) {
                            excluded = if (selected) excluded + entry.scanKey() else excluded - entry.scanKey()
                        }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(24.dp).clip(RoundedCornerShape(5.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                                if (selected) Icon(painterResource(R.drawable.ic_check_24), "Selected", Modifier.size(18.dp), MaterialTheme.colorScheme.onPrimary)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(entry.startPage?.let { "p.$it" } ?: "Pages can be set later", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            } else {
                if (cameraAllowed) BookContentsCamera(Modifier.weight(1f).fillMaxWidth().aspectRatio(0.75f).clip(shape),
                    onReady = { capture = it }, onError = { error = it })
                else Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    TextButton(onClick = { permission.launch(Manifest.permission.CAMERA) }, shape = shape) {
                        BookButtonLabel(R.drawable.ic_camera_24, "Allow camera")
                    }
                }
                Text("Capture one contents page at a time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Reading titles…", style = MaterialTheme.typography.bodySmall)
            }
            if (error.isNotBlank()) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (review) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { review = false; capture = null; error = "" }, enabled = !busy,
                        modifier = Modifier.weight(1f), shape = shape) { BookButtonLabel(R.drawable.ic_camera_24, "Scan another") }
                    Button(onClick = { onAdd(entries.filter { it.scanKey() !in excluded }) },
                        enabled = !busy && entries.any { it.scanKey() !in excluded }, modifier = Modifier.weight(1f), shape = shape) {
                        Text("Add parts")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { gallery.launch(arrayOf("image/*")) }, enabled = !busy,
                        modifier = Modifier.weight(1f), shape = shape) { BookButtonLabel(R.drawable.ic_image_24, "Photo") }
                    Button(onClick = {
                        val camera = capture ?: return@Button
                        busy = true; error = ""
                        camera.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                if (!alive.get()) { image.close(); return }
                                process(release = { image.close() }) {
                                    val bitmap = image.toBitmap()
                                    val rotation = image.imageInfo.rotationDegrees
                                    if (rotation == 0) bitmap else try {
                                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
                                            Matrix().apply { setRotate(rotation.toFloat()) }, true)
                                            .also { if (it !== bitmap) bitmap.recycle() }
                                    } catch (failure: Exception) { bitmap.recycle(); throw failure }
                                }
                            }
                            override fun onError(exception: ImageCaptureException) {
                                if (alive.get()) { busy = false; error = "Could not capture this page. Try again." }
                            }
                        })
                    }, enabled = !busy && capture != null, modifier = Modifier.weight(1f), shape = shape) {
                        BookButtonLabel(R.drawable.ic_camera_24, "Capture")
                    }
                }
                if (entries.isNotEmpty()) TextButton(onClick = { review = true }, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = shape) {
                    Text("Review ${entries.size} titles")
                }
            }
        }
    }
}

@Composable
private fun BookContentsCamera(modifier: Modifier, onReady: (ImageCapture) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val alive = remember { AtomicBoolean(true) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val preview = remember { Preview.Builder().build() }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setTargetResolution(android.util.Size(1600, 2200)).build() }
    val readyCallback by rememberUpdatedState(onReady)
    val errorCallback by rememberUpdatedState(onError)
    DisposableEffect(Unit) { onDispose { alive.set(false); provider?.unbind(preview, capture) } }
    AndroidView(modifier = modifier, factory = { viewContext ->
        PreviewView(viewContext).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (alive.get()) runCatching {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    preview.setSurfaceProvider(surfaceProvider)
                    cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                    readyCallback(capture)
                }.onFailure { errorCallback("Camera unavailable. Choose a photo instead.") }
            }, ContextCompat.getMainExecutor(context))
        }
    })
}

/** Bound allocations even for 50MP photos, and respect camera/gallery EXIF orientation. */
private fun bookContentsBitmap(context: Context, uri: Uri): Bitmap {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2800) sample *= 2
    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        ?: error("Cannot decode photo")
    val orientation = runCatching { resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } }.getOrNull()
    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
        }
    }
    if (matrix.isIdentity) return bitmap
    return try { Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { if (it !== bitmap) bitmap.recycle() } }
    catch (failure: Exception) { bitmap.recycle(); throw failure }
}
