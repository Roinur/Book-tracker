package com.roinur.booktracker

import androidx.compose.material3.Text

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
internal fun BookCover(book: BookRow, modifier: Modifier = Modifier) {
    BookCoverImage(
        coverUrl = book.coverUrl,
        title = book.title,

        modifier = modifier
    )
}

@Composable
internal fun BookCoverImage(
    coverUrl: String,
    title: String,

    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val localBitmap by produceState<ImageBitmap?>(initialValue = null, coverUrl) {
        value = null
        if (coverUrl.isNotBlank()) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val restored = com.roinur.booktracker.data.backup.BookPortableCovers.file(context, coverUrl)
                    val stream = if (restored.isFile) restored.inputStream()
                        else if (coverUrl.startsWith("content://") || coverUrl.startsWith("file://")) context.contentResolver.openInputStream(Uri.parse(coverUrl))
                        else null
                    stream?.use { input ->
                        BitmapFactory.decodeStream(input)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    when {
        localBitmap != null -> {
            Image(
                bitmap = localBitmap!!,
                contentDescription = "Cover for $title",
                modifier = modifier.clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop
            )
        }
        coverUrl.isNotBlank() -> {
        ThumbnailImage(
            thumbnailUrl = coverUrl,
            persistCover = true,

            contentDescription = "Cover for $title",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
        }
        else -> Box(
            modifier = modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title.trim().take(2).uppercase(Locale.US).ifBlank { "BK" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
internal fun BookEmptyStateText(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 14.dp)
    )
}

@Composable
internal fun BookPanel(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    contentSpacing: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val panelShape = RoundedCornerShape(8.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(panelShape)
            .background(containerColor, panelShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), panelShape)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(contentSpacing),
        content = content
    )
}

@Composable
internal fun BookSectionCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

internal fun bookStatusLine(book: BookRow, liveSeconds: Long = 0L): String {
    val progress = if (book.pageCount > 0) {
        "${book.currentPage.coerceAtMost(book.pageCount)}/${book.pageCount} pages"
    } else {
        "${book.currentPage} pages"
    }
    val totalTime = book.readingSeconds + liveSeconds
    val live = if (liveSeconds > 0L) " + ${bookFormatDuration(liveSeconds)}" else ""
    return "${book.status.label} - $progress - ${bookFormatDuration(totalTime)}$live"
}
