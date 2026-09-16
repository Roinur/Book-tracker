package com.roinur.booktracker

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.BackHandler
import java.net.URLEncoder
import java.io.File
import java.util.UUID

@Composable
internal fun GoogleCoverSearchDialog(query: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    var browser by remember { mutableStateOf<WebView?>(null) }
    var candidate by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { browser?.stopLoading(); browser?.destroy() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler { if (candidate != null) candidate = null else if (browser?.canGoBack() == true) browser?.goBack() else onDismiss() }
        Column(Modifier.fillMaxSize().padding(12.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { if (browser?.canGoBack() == true) browser?.goBack() else onDismiss() }) { Text("Back") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            Text("Hold an image to preview it as a cover.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            AndroidView(modifier = Modifier.fillMaxWidth().weight(1f), factory = { ctx ->
                WebView(ctx).apply {
                    browser = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = request.url.scheme !in listOf("https", "http")
                    }
                    setOnLongClickListener {
                        val hit = hitTestResult
                        val url = hit.extra
                        if (hit.type in listOf(WebView.HitTestResult.IMAGE_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) && url != null) {
                            if (url.startsWith("https://") || url.startsWith("http://") || url.startsWith("data:image/")) {
                                candidate = url
                                error = null
                            }
                            true
                        } else false
                    }
                    loadUrl("https://www.google.com/search?tbm=isch&q=" + URLEncoder.encode(query + " book cover", "UTF-8"))
                }
            })
        }
    }
    candidate?.let { url ->
        AlertDialog(onDismissRequest = { candidate = null }, shape = RoundedCornerShape(8.dp), containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Use this cover?") },
            text = {
                if (url.startsWith("data:image/")) {
                    val bitmap = remember(url) { runCatching {
                        require(url.length < 12_000_000)
                        val bytes = Base64.decode(url.substringAfter(","), Base64.DEFAULT)
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        require(options.outWidth in 1..8192 && options.outHeight in 1..8192)
                        options.inJustDecodeBounds = false
                        options.inSampleSize = (maxOf(options.outWidth, options.outHeight) / 800).coerceAtLeast(1)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
                    }.getOrNull() }
                    if (bitmap != null) Image(bitmap, "Selected cover", Modifier.fillMaxWidth().height(280.dp), contentScale = ContentScale.Fit)
                    else Text("Preview unavailable. Try opening the full image.")
                } else ThumbnailImage(thumbnailUrl = url, contentDescription = "Selected cover", modifier = Modifier.fillMaxWidth().height(280.dp), contentScale = ContentScale.Fit)
            },
            confirmButton = { TextButton(onClick = {
                val selected = runCatching {
                    if (!url.startsWith("data:")) url else {
                        require(url.length < 12_000_000)
                        val bytes = Base64.decode(url.substringAfter(","), Base64.DEFAULT)
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        require(bounds.outWidth in 1..8192 && bounds.outHeight in 1..8192)
                        val dir = File(context.filesDir, "selected_covers").apply { mkdirs() }
                        File(dir, "${UUID.randomUUID()}.img").apply { writeBytes(bytes) }.let { android.net.Uri.fromFile(it).toString() }
                    }
                }.getOrNull()
                if (selected != null) { candidate = null; onPick(selected) }
                else { candidate = null; error = "Could not use this image. Open the result and choose its full image." }
            }) { Text("Use cover") } },
            dismissButton = { TextButton(onClick = { candidate = null }) { Text("Cancel") } })
    }
}
