package com.roinur.booktracker

import androidx.compose.material3.Text

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import java.util.Locale
import kotlin.math.roundToInt
import com.roinur.booktracker.data.media.BookCoverThumbnails

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

    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    frostedBottomHeight: Dp = 0.dp,
    maxDimensionPx: Int = 1024
) {
    val context = LocalContext.current
    val thumbnailSize = maxDimensionPx.coerceIn(96, 1536)
    val localBitmap by produceState<ImageBitmap?>(
        initialValue = BookCoverThumbnails.cached(coverUrl, thumbnailSize), coverUrl, thumbnailSize
    ) {
        value = BookCoverThumbnails.cached(coverUrl, thumbnailSize)
        if (coverUrl.isNotBlank()) value = BookCoverThumbnails.load(context, coverUrl, thumbnailSize)
    }
    when {
        localBitmap != null -> {
            BookCoverArtwork(
                bitmap = localBitmap!!,
                contentDescription = "Cover for $title",
                modifier = modifier.clip(MaterialTheme.shapes.small),
                contentScale = contentScale,
                frostedBottomHeight = frostedBottomHeight
            )
        }
        coverUrl.isNotBlank() -> {
            Box(modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center) {
                Text("No preview", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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

/** Reuse one decoded bitmap for the sharp cover and its frosted lower edge. */
@Composable
internal fun BookCoverArtwork(
    bitmap: ImageBitmap, contentDescription: String?, modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop, frostedBottomHeight: Dp = 0.dp
) {
    var artworkSize by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier.onSizeChanged { artworkSize = it }) {
        Image(bitmap, contentDescription, Modifier.fillMaxSize(), contentScale = contentScale)
        if (frostedBottomHeight > 0.dp) {
            // Keep the blur layer as small as the footer, using the same crop as the sharp cover.
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(frostedBottomHeight).clipToBounds()) {
                Canvas(Modifier.fillMaxSize().blur(12.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle)) {
                    // Read measured size during drawing, so measurement does not recompose each tile.
                    if (artworkSize.height <= 0) return@Canvas
                    val factor = contentScale.computeScaleFactor(Size(bitmap.width.toFloat(), bitmap.height.toFloat()),
                        Size(artworkSize.width.toFloat(), artworkSize.height.toFloat()))
                    val width = (bitmap.width * factor.scaleX).roundToInt()
                    val height = (bitmap.height * factor.scaleY).roundToInt()
                    drawImage(bitmap, dstSize = IntSize(width, height), dstOffset = IntOffset(
                        (artworkSize.width - width) / 2,
                        ((artworkSize.height - height) / 2f - artworkSize.height + size.height).roundToInt()))
                }
            }
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
