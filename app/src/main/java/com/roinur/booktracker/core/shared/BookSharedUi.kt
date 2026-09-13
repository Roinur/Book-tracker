package com.roinur.booktracker

import androidx.compose.material3.Text

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.TextButton
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class ThemeMode(val label: String) {
    SYSTEM("Auto"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class StatsRange(val label: String) {
    TODAY("Today"),
    WEEK("Week"),
    MONTH("Month"),
    YEAR("Year"),
    ALL_TIME("All Time")
}

data class DailyActivityPoint(
    val date: LocalDate,
    val pagesRead: Int,
    val entriesRead: Int
)

@Composable
internal fun ApplySystemBars(
    darkContent: Boolean,
    barColor: Int
) {
    val view = LocalView.current
    if (view.isInEditMode) return

    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        val window = activity.window
        window.statusBarColor = barColor
        window.navigationBarColor = barColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = darkContent
            isAppearanceLightNavigationBars = darkContent
        }
    }
}

@Composable
internal fun LocalEntryHoldPopup(
    code: Int,
    rating: Int,
    modifier: Modifier = Modifier
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val maxWidth = (screenWidth - 24.dp).coerceAtLeast(220.dp)
    val popupWidth = (screenWidth * 0.94f).coerceIn(220.dp, maxWidth)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.width(popupWidth)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Drag to rate #$code",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                (0..5).forEach { value ->
                    val selected = value == rating
                    val holdButtonShape = RoundedCornerShape(14.dp)
                    Box(
                        modifier = Modifier
                            .weight(if (value == 0) 1.7f else 1f)
                            .clip(holdButtonShape)
                            .background(
                                when (value) {
                                    0 -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                                        alpha = if (selected) 0.96f else 0.72f
                                    )
                                    else -> MaterialTheme.colorScheme.primaryContainer.copy(
                                        alpha = if (selected) 0.68f else 0.34f
                                    )
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingShimmerOverlay(
                            active = selected,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .matchParentSize()
                                .clip(holdButtonShape)
                        )
                        Text(
                            text = if (value == 0) "Cancel" else "★",
                            style = if (value == 0) {
                                MaterialTheme.typography.labelMedium
                            } else {
                                MaterialTheme.typography.titleMedium
                            },
                            color = if (selected) {
                                if (value == 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    RATING_STAR_GOLD
                                }
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                            },
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntrySwipeDismissContainer(
    code: Int,
    isPinned: Boolean,
    isRead: Boolean,
    onTogglePinned: (Int) -> Unit,
    onToggleRead: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val swipeCommitTracker = rememberSwipeCommitTrackerState(
        key = code,
        config = SwipeCommitConfig(
            minHorizontalSwipePx = with(density) { 88.dp.toPx() },
            minGestureDurationMs = 160L,
            maxVerticalPerHorizontalRatio = 0.176327f,
            maxSwipeSpeedPxPerMs = 1.15f
        )
    )
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance -> distance * 0.45f },
        confirmValueChange = { target ->
            if (target != SwipeToDismissBoxValue.Settled && !swipeCommitTracker.deliberate) {
                return@rememberSwipeToDismissBoxState false
            }
            when (target) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onTogglePinned(code)
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onToggleRead(code)
                }
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        }
    )
    val commitReadyTarget = trackSwipeCommitFeedback(
        key = code,
        tracker = swipeCommitTracker,
        dismissTarget = dismissState.targetValue,
        feedbackEnabled = true,
        haptic = haptic
    )
    val direction = dismissState.dismissDirection
    val progress = dismissState.progress
    val swipeVisualActive = progress > 0.001f
    var swipeSnapshotPinned by remember(code) { mutableStateOf(isPinned) }
    var swipeSnapshotRead by remember(code) { mutableStateOf(isRead) }
    var swipeSnapshotCaptured by remember(code) { mutableStateOf(false) }
    LaunchedEffect(swipeCommitTracker.gestureActive, isPinned, isRead) {
        if (swipeCommitTracker.gestureActive && !swipeSnapshotCaptured) {
            swipeSnapshotPinned = isPinned
            swipeSnapshotRead = isRead
            swipeSnapshotCaptured = true
        } else if (!swipeCommitTracker.gestureActive) {
            swipeSnapshotCaptured = false
        }
    }
    val visualDirection = if (swipeVisualActive && direction != SwipeToDismissBoxValue.Settled) {
        direction
    } else {
        SwipeToDismissBoxValue.Settled
    }
    val visualPinnedState = if (swipeCommitTracker.gestureActive && swipeSnapshotCaptured) swipeSnapshotPinned else isPinned
    val visualReadState = if (swipeCommitTracker.gestureActive && swipeSnapshotCaptured) swipeSnapshotRead else isRead
    val backgroundSpec = when (visualDirection) {
        SwipeToDismissBoxValue.StartToEnd -> {
            val action = if (visualPinnedState) "Unpin" else "Pin"
            SwipeBackgroundSpec(
                label = action,
                glyph = "\uD83D\uDCCC",
                tint = if (visualPinnedState) UNREAD_STATE_COLOR else READ_STATE_COLOR
            )
        }
        SwipeToDismissBoxValue.EndToStart -> {
            val action = if (visualReadState) "Unread" else "Read"
            SwipeBackgroundSpec(
                label = action,
                glyph = if (visualReadState) "○" else "✓",
                tint = if (visualReadState) UNREAD_STATE_COLOR else READ_STATE_COLOR
            )
        }
        SwipeToDismissBoxValue.Settled -> SwipeBackgroundSpec("", "", MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val backgroundAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (!swipeVisualActive) 0f else (0.42f + (progress * 0.58f)).coerceIn(0f, 1f),
        label = "entrySwipeAlpha"
    )
    val contentScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 180),
        label = "entrySwipeContentScale"
    )
    val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = 1f - (progress * 0.035f),
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 180),
        label = "entrySwipeContentAlpha"
    )
    val backgroundColor by androidx.compose.animation.animateColorAsState(
        targetValue = when (visualDirection) {
            SwipeToDismissBoxValue.StartToEnd -> if (visualPinnedState) {
                UNREAD_STATE_COLOR
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
            SwipeToDismissBoxValue.EndToStart -> if (visualReadState) {
                UNREAD_STATE_COLOR
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
            SwipeToDismissBoxValue.Settled -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "entrySwipeColor"
    )

    SwipeToDismissBox(
        modifier = modifier.trackSwipeCommitGestures(
            gestureKey = code,
            tracker = swipeCommitTracker
        ),
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(backgroundColor.copy(alpha = backgroundAlpha))
            ) {
                SwipeCommitReadySwoosh(
                    commitReadyTarget = commitReadyTarget,
                    tint = backgroundSpec.tint,
                    modifier = Modifier.matchParentSize()
                )
                if (backgroundSpec.label.isNotBlank()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = backgroundSpec.glyph,
                            style = MaterialTheme.typography.titleMedium,
                            color = backgroundSpec.tint
                        )
                        Text(
                            text = backgroundSpec.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = backgroundSpec.tint,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        content = swipeContent@{
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = contentScale
                        scaleY = contentScale
                        alpha = contentAlpha
                    }
            ) {
                this@swipeContent.content()
            }
        }
    )
}

internal data class SwipeBackgroundSpec(
    val label: String,
    val glyph: String,
    val tint: Color
)

internal data class HeatmapCell(
    val date: LocalDate,
    val pagesRead: Int,
    val entriesRead: Int,
    val inRange: Boolean
)

@Composable
internal fun ActivityHeatmap(
    range: StatsRange,
    points: List<DailyActivityPoint>,
    onDaySelected: (DailyActivityPoint) -> Unit,
    valueUnit: String = "items",
    startOverride: LocalDate? = null,
    endOverride: LocalDate? = null,
    compact: Boolean = false,
    showSelectionLabel: Boolean = true,
    modifier: Modifier = Modifier
) {
    var selectedPoint by remember(points, range, startOverride, endOverride) { mutableStateOf<DailyActivityPoint?>(null) }
    val today = endOverride ?: LocalDate.now()
    val startDate = startOverride ?: when (range) {
        StatsRange.TODAY -> today
        StatsRange.WEEK -> today.minusDays(6)
        StatsRange.MONTH -> today.withDayOfMonth(1)
        StatsRange.YEAR -> today.withDayOfYear(1)
        StatsRange.ALL_TIME -> points.minByOrNull { it.date }?.date ?: today
    }
    val endDate = today
    val alignedStart = if (range == StatsRange.WEEK || range == StatsRange.MONTH) {
        startDate
    } else {
        startDate.minusDays((startDate.dayOfWeek.value - 1).toLong())
    }
    val alignedEnd = if (range == StatsRange.WEEK || range == StatsRange.MONTH) {
        endDate
    } else {
        endDate.plusDays((7 - endDate.dayOfWeek.value).toLong())
    }

    val pointsByDate = points
        .groupBy { it.date }
        .mapValues { (_, items) ->
            DailyActivityPoint(
                date = items.first().date,
                pagesRead = items.sumOf { it.pagesRead }.coerceAtLeast(0),
                entriesRead = items.sumOf { it.entriesRead }.coerceAtLeast(0)
            )
        }

    val cells = buildList {
        var cursor = alignedStart
        while (!cursor.isAfter(alignedEnd)) {
            val point = pointsByDate[cursor]
            add(
                HeatmapCell(
                    date = cursor,
                    pagesRead = point?.pagesRead ?: 0,
                    entriesRead = point?.entriesRead ?: 0,
                    inRange = !cursor.isBefore(startDate) && !cursor.isAfter(endDate)
                )
            )
            cursor = cursor.plusDays(1)
        }
    }
    val weekColumns = when (range) {
        StatsRange.WEEK -> cells.map { listOf(it) }
        StatsRange.MONTH -> if (compact) (0..6).map { day -> cells.filterIndexed { index, _ -> index % 7 == day } } else cells.chunked(7)
        else -> cells.chunked(7)
    }
    val maxPages = cells
        .asSequence()
        .filter { it.inRange }
        .maxOfOrNull { it.pagesRead }
        ?.coerceAtLeast(0)
        ?: 0

    fun activityLevel(pagesRead: Int): Int {
        if (pagesRead <= 0 || maxPages <= 0) return 0
        val ratio = pagesRead.toFloat() / maxPages.toFloat()
        return when {
            ratio >= 0.85f -> 4
            ratio >= 0.6f -> 3
            ratio >= 0.35f -> 2
            else -> 1
        }
    }

    val emptyColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    val levelColors = listOf(
        emptyColor,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
        MaterialTheme.colorScheme.primary.copy(alpha = 0.46f),
        MaterialTheme.colorScheme.primary.copy(alpha = 0.63f),
        MaterialTheme.colorScheme.primary.copy(alpha = 0.80f)
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (weekColumns.isEmpty()) {
            Text(
                text = "No activity yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val gap = if (compact && range != StatsRange.WEEK) 3.dp else 4.dp
                val maxCellSize = when (range) {
                    StatsRange.WEEK -> if (compact) 44.dp else 36.dp
                    StatsRange.MONTH -> if (compact) 16.dp else 24.dp
                    else -> if (compact) 10.dp else 17.dp
                }
                val cellSize = if (compact && (range == StatsRange.YEAR || range == StatsRange.ALL_TIME)) {
                    10.dp
                } else if (weekColumns.isEmpty()) {
                    12.dp
                } else {
                    ((maxWidth - gap * (weekColumns.size - 1)) / weekColumns.size)
                        .coerceIn(if (compact) 6.dp else 8.dp, maxCellSize)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = if (range == StatsRange.WEEK) Arrangement.SpaceBetween else Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.Top
                ) {
                    weekColumns.forEach { column ->
                        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                            column.forEach { cell ->
                                val level = activityLevel(cell.pagesRead).coerceIn(0, 4)
                                val color = if (cell.inRange) {
                                    levelColors[level]
                                } else {
                                    emptyColor.copy(alpha = 0.12f)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(cellSize)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(color)
                                        .border(
                                            width = if (selectedPoint?.date == cell.date) 2.dp else 0.5.dp,
                                            color = if (selectedPoint?.date == cell.date) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                            shape = RoundedCornerShape(3.dp)
                                        )
                                        .clickable(enabled = cell.inRange) {
                                            val point = DailyActivityPoint(
                                                date = cell.date,
                                                pagesRead = cell.pagesRead,
                                                entriesRead = cell.entriesRead
                                            )
                                            selectedPoint = point
                                            onDaySelected(point)
                                        }
                                )
                            }
                        }
                    }
                }
            }
            if (showSelectionLabel) selectedPoint?.let { point ->
                Text(
                    "${point.date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))}: ${point.pagesRead} $valueUnit",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (!compact) Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Less",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                levelColors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                }
                Text(
                    text = "More",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

internal object ThumbnailBitmapCache {
    private val maxItems = run {
        val maxMemMb = (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toInt()
        when {
            maxMemMb >= 512 -> 320
            maxMemMb >= 384 -> 280
            maxMemMb >= 256 -> 240
            else -> 180
        }
    }
    private val map = object : LinkedHashMap<String, ImageBitmap>(maxItems, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
            return size > maxItems
        }
    }

    @Synchronized
    fun get(url: String): ImageBitmap? = map[url]

    @Synchronized
    fun put(url: String, bitmap: ImageBitmap) {
        if (url.isBlank()) return
        map[url] = bitmap
    }

    @Synchronized
    fun clear() {
        map.clear()
    }
}

internal val thumbnailHttpClient: OkHttpClient by lazy {
    val requestDispatcher = okhttp3.Dispatcher().apply {
        maxRequests = 64
        maxRequestsPerHost = 12
    }
    OkHttpClient.Builder()
        .dispatcher(requestDispatcher)
        .connectionPool(ConnectionPool(12, 5, TimeUnit.MINUTES))
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()
}

@Composable
internal fun ThumbnailImage(
    thumbnailUrl: String,

    contentDescription: String,
    onClick: (() -> Unit)? = null,
    contentScale: ContentScale = ContentScale.Crop,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val initialBitmap = ThumbnailBitmapCache.get(thumbnailUrl)
    val thumbnailState by produceState(
        initialValue = initialBitmap to (initialBitmap != null || thumbnailUrl.isBlank()),
        thumbnailUrl
    ) {
        if (thumbnailUrl.isBlank()) {
            value = null to true
            return@produceState
        }

        val cached = ThumbnailBitmapCache.get(thumbnailUrl)
        if (cached != null) {
            value = cached to true
            return@produceState
        }

        value = null to false
        val fetched = withContext(Dispatchers.IO) {
            fetchThumbnailBitmap(
                url = thumbnailUrl
            )
        }
        if (fetched != null) {
            ThumbnailBitmapCache.put(thumbnailUrl, fetched)
        }
        value = fetched to true
    }

    val boxModifier = modifier
        .clip(MaterialTheme.shapes.small)
        .background(MaterialTheme.colorScheme.surfaceVariant)

        .let { base ->
            if (onClick != null) {
                base.fittedClickable(MaterialTheme.shapes.small, onClick = onClick)
            } else {
                base
            }
        }

    Box(
        modifier = boxModifier,
        contentAlignment = Alignment.Center
    ) {
        val imageBitmap = thumbnailState.first
        val loadFinished = thumbnailState.second
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else if (!loadFinished) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 1.75.dp
            )
        } else {
            Text(
                text = "No preview",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun buildThumbnailCandidateUrls(url: String): List<String> {
    val trimmed = url.trim()
    if (trimmed.isBlank()) return emptyList()
    return listOf(trimmed)
}

internal fun fetchThumbnailBitmapRawOnce(url: String): Bitmap? {
    val request = Request.Builder()
        .url(url)
        .header(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
        )
        .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
        .build()

    return thumbnailHttpClient.newCall(request).execute().use { rsp ->
        if (!rsp.isSuccessful) return null
        val bytes = rsp.body?.bytes() ?: return null
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}

internal fun fetchThumbnailBitmapOnce(url: String): ImageBitmap? {
    val bitmap = fetchThumbnailBitmapRawOnce(url) ?: return null
    return bitmap.asImageBitmap()
}

internal fun fetchThumbnailBitmap(url: String): ImageBitmap? {
    if (url.isBlank()) return null
    val candidates = buildThumbnailCandidateUrls(url)
    if (candidates.isEmpty()) return null

    candidates.forEach { candidateUrl ->
        repeat(2) { attempt ->
            val fetched = runCatching { fetchThumbnailBitmapOnce(candidateUrl) }.getOrNull()
            if (fetched != null) {
                return fetched
            }
            if (attempt == 0) {
                Thread.sleep(65)
            }
        }
    }
    return null
}



@Composable
internal fun Modifier.fittedClickable(
    shape: Shape = RoundedCornerShape(8.dp),
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clip(shape).clickable(
        enabled = enabled,
        interactionSource = interactionSource,
        indication = rememberRipple(bounded = true),
        onClick = onClick
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.fittedCombinedClickable(
    shape: Shape = RoundedCornerShape(8.dp),
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clip(shape).combinedClickable(
        enabled = enabled,
        interactionSource = interactionSource,
        indication = rememberRipple(bounded = true),
        onClick = onClick,
        onLongClick = onLongClick
    )
}

@Composable
internal fun ImmediateActionText(
    label: String,
    onAction: () -> Unit,
    enabled: Boolean = true,
    onPressStart: () -> Unit = {},
    runOnPressWhen: () -> Boolean = { false },
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelLarge,
    fontWeight: FontWeight? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
) {
    var firedOnPress by remember { mutableStateOf(false) }
    val actionScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    TextButton(
        onClick = {
            if (!enabled) return@TextButton
            if (firedOnPress) {
                firedOnPress = false
            } else {
                onAction()
            }
        },
        enabled = enabled,
        interactionSource = interactionSource,
        contentPadding = contentPadding,
        modifier = modifier.pointerInput(enabled, onPressStart, runOnPressWhen) {
            awaitEachGesture {
                if (!enabled) return@awaitEachGesture
                firedOnPress = false
                val down = awaitFirstDown(requireUnconsumed = false)
                val shouldRunOnPress = runOnPressWhen()
                onPressStart()

                if (shouldRunOnPress) {
                    firedOnPress = true
                    val press = PressInteraction.Press(down.position)
                    actionScope.launch {
                        interactionSource.emit(press)
                    }
                    actionScope.launch {
                        // Keep a short delay so the press animation is visible.
                        delay(32)
                        onAction()
                    }
                    val up = waitForUpOrCancellation()
                    actionScope.launch {
                        if (up == null) {
                            interactionSource.emit(PressInteraction.Cancel(press))
                        } else {
                            interactionSource.emit(PressInteraction.Release(press))
                        }
                    }
                } else {
                    waitForUpOrCancellation()
                }
            }
        }
    ) {
        Text(
            text = label,
            style = textStyle,
            fontWeight = fontWeight
        )
    }
}

@Composable
internal fun ThemeToggleWithAccentPicker(
    themeMode: ThemeMode,
    accentMode: AccentMode,
    onCycleThemeMode: () -> Unit,
    onAccentModeSelected: (AccentMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = ACCENT_PICKER_OPTIONS
    var pickerVisible by remember { mutableStateOf(false) }
    var highlightedIndex by remember { mutableStateOf<Int?>(null) }

    val iconSize = 40.dp
    val chipSize = 22.dp
    val chipSpacing = 8.dp
    val pillPadding = 10.dp
    val pillGap = 8.dp
    val pillHeight = 34.dp
    val pillWidth =
        (pillPadding * 2) + (chipSize * options.size) + (chipSpacing * (options.size - 1))
    val expandedWidth = iconSize + pillGap + pillWidth

    val density = androidx.compose.ui.platform.LocalDensity.current
    fun indexForX(x: Float): Int? {
        val iconPx = with(density) { iconSize.toPx() }
        val gapPx = with(density) { pillGap.toPx() }
        val paddingPx = with(density) { pillPadding.toPx() }
        val chipPx = with(density) { chipSize.toPx() }
        val slotPx = with(density) { (chipSize + chipSpacing).toPx() }

        val start = iconPx + gapPx + paddingPx
        val end = start + ((options.size - 1) * slotPx) + chipPx
        if (x < start || x > end) return null
        val idx = ((x - start) / slotPx).toInt()
        return idx.coerceIn(0, options.lastIndex)
    }

    Box(
        modifier = modifier
            .width(if (pickerVisible) expandedWidth else iconSize)
            .height(iconSize)
            .pointerInput(accentMode) {
                awaitEachGesture {

                    val down = awaitFirstDown(requireUnconsumed = false)


                    val longPress = awaitLongPressOrCancellation(down.id)
                    if (longPress == null) {
                        onCycleThemeMode()
                        return@awaitEachGesture
                    }

                    pickerVisible = true
                    highlightedIndex = indexForX(longPress.position.x)
                        ?: options.indexOfFirst { it.mode == accentMode }.takeIf { it >= 0 }

                    var released = false
                    while (!released) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                        if (change == null) {
                            released = true
                        } else if (!change.pressed) {
                            released = true
                        } else {
                            highlightedIndex = indexForX(change.position.x) ?: highlightedIndex
                        }
                    }

                    val picked = highlightedIndex
                    if (picked != null && picked in options.indices) {
                        onAccentModeSelected(options[picked].mode)
                    }
                    pickerVisible = false
                    highlightedIndex = null
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {

        if (pickerVisible) {
            Box(
                modifier = Modifier
                    .offset(x = iconSize + pillGap)
                    .height(pillHeight)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = pillPadding),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(chipSpacing)
                ) {
                    options.forEachIndexed { index, option ->
                        val selected = accentMode == option.mode
                        val hovered = highlightedIndex == index
                        val ringColor = when {
                            hovered -> MaterialTheme.colorScheme.primary
                            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                        if (option.color == null) {
                            val autoCircleColor = if (hovered || selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            }
                            Canvas(
                                modifier = Modifier
                                    .size(chipSize)
                                    .padding(1.dp)
                            ) {
                                val stroke = if (hovered || selected) 3f else 2f
                                val radius = (size.minDimension / 2f) - stroke
                                drawCircle(
                                    color = autoCircleColor,
                                    radius = radius,
                                    style = Stroke(width = stroke)
                                )
                                drawLine(
                                    color = autoCircleColor,
                                    start = Offset(size.width * 0.72f, size.height * 0.18f),
                                    end = Offset(size.width * 0.28f, size.height * 0.82f),
                                    strokeWidth = stroke
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(chipSize)
                                    .clip(CircleShape)
                                    .background(option.color)
                                    .border(
                                        width = if (hovered || selected) 2.2.dp else 1.2.dp,
                                        color = ringColor,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(CircleShape)
                .background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {

                Text(
                    text = themeModeSymbol(themeMode),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            
        }
    }
}

internal fun themeModeSymbol(mode: ThemeMode): String {
    return when (mode) {
        ThemeMode.LIGHT -> "☀"
        ThemeMode.DARK -> "☾"
        ThemeMode.SYSTEM -> "◐"
    }
}

internal data class AccentPickerOption(
    val mode: AccentMode,
    val color: Color?
)

internal val ACCENT_PICKER_OPTIONS = listOf(
    AccentPickerOption(AccentMode.AUTO, null),
    AccentPickerOption(AccentMode.RED, Color(0xFFE53935)),
    AccentPickerOption(AccentMode.ORANGE, Color(0xFFFB8C00)),
    AccentPickerOption(AccentMode.AMBER, Color(0xFFF9A825)),
    AccentPickerOption(AccentMode.GREEN, Color(0xFF43A047)),
    AccentPickerOption(AccentMode.TEAL, Color(0xFF00897B)),
    AccentPickerOption(AccentMode.BLUE, Color(0xFF1E88E5)),
    AccentPickerOption(AccentMode.INDIGO, Color(0xFF5E35B1)),
    AccentPickerOption(AccentMode.PINK, Color(0xFFD81B60))
)

internal fun accentColorForMode(mode: AccentMode): Color? {
    return ACCENT_PICKER_OPTIONS.firstOrNull { it.mode == mode }?.color
}

internal fun preferredOnAccent(color: Color): Color {
    val lum = (0.299f * color.red) + (0.587f * color.green) + (0.114f * color.blue)
    return if (lum >= 0.62f) Color(0xFF111111) else Color.White
}

internal fun applyAccentMode(
    baseScheme: ColorScheme,
    accentMode: AccentMode,
    isDark: Boolean
): ColorScheme {
    val accent = accentColorForMode(accentMode) ?: return baseScheme
    val onAccent = preferredOnAccent(accent)
    val container = accent.copy(alpha = if (isDark) 0.34f else 0.22f)
    return baseScheme.copy(
        primary = accent,
        onPrimary = onAccent,
        secondary = accent,
        tertiary = accent,
        primaryContainer = container,
        secondaryContainer = container,
        tertiaryContainer = container
    )
}

internal fun mapDragPositionToRating(localX: Float, rowWidthPx: Float): Int {
    val safeWidth = rowWidthPx.coerceAtLeast(1f)
    val activeWidth = (safeWidth * DRAG_RATING_ACTIVE_WIDTH_FRACTION).coerceAtLeast(1f)
    val startX = ((safeWidth - activeWidth) / 2f).coerceAtLeast(0f)
    val normalized = ((localX - startX) / activeWidth).coerceIn(0f, 1f)
    return (normalized * 6f).toInt().coerceIn(0, 5)
}

