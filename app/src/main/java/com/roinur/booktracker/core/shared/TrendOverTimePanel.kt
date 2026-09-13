package com.roinur.booktracker

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlin.math.ceil
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val TREND_COLORS = listOf(
    Color(0xFF0072B2),
    Color(0xFFE69F00),
    Color(0xFF009E73),
    Color(0xFFCC79A7),
    Color(0xFFD55E00)
)

private val TREND_TARGET_KIND_SAVER = Saver<TrendTargetKind, String>(
    save = { it.name },
    restore = { saved -> runCatching { TrendTargetKind.valueOf(saved) }.getOrDefault(TrendTargetKind.TAGS) }
)
private val STATS_RANGE_SAVER = Saver<StatsRange, String>(
    save = { it.name },
    restore = { saved -> runCatching { StatsRange.valueOf(saved) }.getOrDefault(StatsRange.ALL_TIME) }
)
private val TREND_SCALE_SAVER = Saver<TrendScale, String>(
    save = { it.name },
    restore = { saved -> runCatching { TrendScale.valueOf(saved) }.getOrDefault(TrendScale.SHARE) }
)
private val TREND_SIGNAL_SAVER = Saver<TrendSignal, String>(
    save = { it.name },
    restore = { saved -> runCatching { TrendSignal.valueOf(saved) }.getOrDefault(TrendSignal.ALL) }
)
private val TREND_TARGET_IDS_SAVER = Saver<List<Long>, LongArray>(
    save = { it.toLongArray() },
    restore = { it.toList() }
)
private val TREND_COLOR_SLOTS_SAVER = Saver<Map<Long, Int>, LongArray>(
    save = { slots -> slots.entries.flatMap { listOf(it.key, it.value.toLong()) }.toLongArray() },
    restore = { values -> values.asList().chunked(2).mapNotNull { pair -> pair.getOrNull(1)?.let { pair[0] to it.toInt() } }.toMap() }
)

@Composable
internal fun TrendOverTimePanel(
    targetProvider: suspend (TrendTargetKind, Boolean) -> List<TrendTarget>,
    snapshotProvider: suspend (TrendRequest) -> TrendSnapshot,
    modifier: Modifier = Modifier
) {
    var targetKind by rememberSaveable(stateSaver = TREND_TARGET_KIND_SAVER) { mutableStateOf(TrendTargetKind.TAGS) }
    var includeMisc by rememberSaveable { mutableStateOf(false) }
    var viewAll by rememberSaveable { mutableStateOf(false) }
    var uniqueOnly by rememberSaveable { mutableStateOf(false) }
    var selectedRange by rememberSaveable(stateSaver = STATS_RANGE_SAVER) { mutableStateOf(StatsRange.ALL_TIME) }
    var metricName by rememberSaveable { mutableStateOf(TrendMetric.PAGES.name) }
    val selectedMetric = TrendMetric.valueOf(metricName)
    var selectedScale by rememberSaveable(stateSaver = TREND_SCALE_SAVER) { mutableStateOf(TrendScale.SHARE) }
    var selectedSignal by rememberSaveable(stateSaver = TREND_SIGNAL_SAVER) { mutableStateOf(TrendSignal.ALL) }
    val bucketMode = TrendBucketMode.ADAPTIVE
    val ratingAdjustment = RatingAdjustment.BALANCED
    var availableTargets by remember { mutableStateOf<List<TrendTarget>>(emptyList()) }
    var selectedTargetIds by rememberSaveable(stateSaver = TREND_TARGET_IDS_SAVER) { mutableStateOf<List<Long>>(emptyList()) }
    var selectedColorSlots by rememberSaveable(stateSaver = TREND_COLOR_SLOTS_SAVER) { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var targetsLoading by remember { mutableStateOf(false) }
    var snapshotLoading by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf<TrendSnapshot?>(null) }
    var snapshotRequest by remember { mutableStateOf<TrendRequest?>(null) }
    var minimumShare by rememberSaveable { mutableFloatStateOf(5f) }
    var minimumReads by rememberSaveable { mutableFloatStateOf(0f) }
    var minimumDefaultsKey by rememberSaveable { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showTargetPicker by remember { mutableStateOf(false) }
    var selectionInitialized by rememberSaveable { mutableStateOf(false) }
    val snapshotCache = remember { mutableStateMapOf<TrendRequest, TrendSnapshot>() }
    val selectionMemory = remember { mutableStateMapOf<String, List<Long>>() }
    val colorMemory = remember { mutableStateMapOf<String, Map<Long, Int>>() }

    val effectiveIncludeMisc = targetKind == TrendTargetKind.TAGS && includeMisc
    val selectionKey = "${targetKind.name}:$effectiveIncludeMisc"
    LaunchedEffect(targetKind, effectiveIncludeMisc) {
        targetsLoading = true
        errorMessage = null
        try {
            val loadedTargets = targetProvider(targetKind, effectiveIncludeMisc)
            availableTargets = loadedTargets
            val availableIds = loadedTargets.mapTo(hashSetOf()) { it.id }
            val rememberedSelection = selectionMemory[selectionKey]
            val remembered = rememberedSelection.orEmpty().filter { it in availableIds }
            val retained = selectedTargetIds.filter { it in availableIds }
            val restored = if (
                rememberedSelection?.isEmpty() == true ||
                (selectionInitialized && rememberedSelection == null && selectedTargetIds.isEmpty())
            ) {
                emptyList()
            } else {
                val preferred = remembered.ifEmpty { retained }.distinct().take(5)
                if (preferred.isNotEmpty()) {
                    preferred
                } else {
                    loadedTargets.map { it.id }.distinct().take(
                        if (targetKind == TrendTargetKind.TAGS) 5 else 2
                    )
                }
            }
            val restoredColors = assignTrendColorSlots(
                ids = restored,
                existing = colorMemory[selectionKey] ?: selectedColorSlots
            )
            selectedTargetIds = restored
            selectedColorSlots = restoredColors
            selectionMemory[selectionKey] = restored
            colorMemory[selectionKey] = restoredColors
            selectionInitialized = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            availableTargets = emptyList()
            errorMessage = error.message ?: "unknown error"
        } finally {
            targetsLoading = false
        }
    }

    val normalizedTargetIds = selectedTargetIds.distinct().take(5)
    val currentRequest = if (!viewAll && normalizedTargetIds.isEmpty()) {
        null
    } else {
        TrendRequest(
            targetKind = targetKind,
            targetIds = if (viewAll) emptyList() else normalizedTargetIds,
            range = selectedRange,
            viewAll = viewAll,
            includeMisc = effectiveIncludeMisc,
            bucketMode = bucketMode,
            metric = selectedMetric
        )
    }
    val displayedSnapshot = snapshot.takeIf { snapshotRequest == currentRequest }
    val transitionSnapshot = snapshot.takeIf {
        val previousRequest = snapshotRequest
        previousRequest != null &&
            currentRequest != null &&
            previousRequest.targetKind == currentRequest.targetKind
    }
    val renderedSnapshot = displayedSnapshot ?: transitionSnapshot
    val renderedRequest = if (displayedSnapshot != null) currentRequest else snapshotRequest
    val requestPending = currentRequest != null && snapshotRequest != currentRequest
    LaunchedEffect(currentRequest) {
        if (currentRequest == null) {
            snapshot = null
            snapshotRequest = null
            snapshotLoading = false
            return@LaunchedEffect
        }
        val request = currentRequest
        snapshotCache[request]?.let {
            snapshot = it
            snapshotRequest = request
            snapshotLoading = false
            return@LaunchedEffect
        }
        snapshotLoading = true
        errorMessage = null
        try {
            val loadedSnapshot = snapshotProvider(request)
            snapshotCache[request] = loadedSnapshot
            snapshot = loadedSnapshot
            snapshotRequest = request
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            snapshot = null
            snapshotRequest = request
            errorMessage = error.message ?: "unknown error"
        } finally {
            snapshotLoading = false
        }
    }

    if (showTargetPicker) {
        TrendTargetPicker(
            title = if (targetKind == TrendTargetKind.TAGS) "Choose collections" else "Choose authors",
            targets = availableTargets,
            selectedIds = selectedTargetIds,
            selectedColorSlots = selectedColorSlots,
            onConfirm = { ids, colorSlots ->
                selectedTargetIds = ids
                selectedColorSlots = colorSlots
                selectionMemory[selectionKey] = ids
                colorMemory[selectionKey] = colorSlots
                viewAll = ids.isEmpty()
                if (ids.isNotEmpty()) uniqueOnly = false
                showTargetPicker = false
            },
            onDismiss = { showTargetPicker = false }
        )
    }



    val readTotals = renderedSnapshot?.series.orEmpty().map { it.totalMatchingReads() }
    val minimumAvailableReads = readTotals.minOrNull()?.toFloat() ?: 0f
    val maximumAvailableReads = readTotals.maxOrNull()?.toFloat() ?: 0f
    LaunchedEffect(viewAll, targetKind, selectedRange, effectiveIncludeMisc, displayedSnapshot) {
        if (!viewAll) {
            minimumDefaultsKey = null
            return@LaunchedEffect
        }
        val currentSnapshot = displayedSnapshot ?: return@LaunchedEffect
        val defaultsKey = "${targetKind.name}:${selectedRange.name}:$effectiveIncludeMisc:${selectedMetric.name}"
        if (minimumDefaultsKey == defaultsKey) return@LaunchedEffect
        if (targetKind == TrendTargetKind.TAGS) {
            val totalReadsInRange = currentSnapshot.series.firstOrNull()
                ?.points
                ?.sumOf { it.totalAmount.toDouble() }?.toFloat()
                ?: 0f
            minimumShare = 5f
            minimumReads = ceil(totalReadsInRange * 0.05f)
        } else {
            minimumShare = 0f
            minimumReads = 0f
        }
        minimumDefaultsKey = defaultsKey
    }
    val effectiveMinimum = when (selectedScale) {
        TrendScale.SHARE -> minimumShare.coerceIn(0f, 100f)
        TrendScale.READS -> minimumReads.coerceIn(minimumAvailableReads, maximumAvailableReads.coerceAtLeast(minimumAvailableReads))
    }
    val minimumEligibleSeries = remember(renderedSnapshot, selectedScale, effectiveMinimum) {
        renderedSnapshot?.series.orEmpty().filter {
            it.meetsMinimum(selectedScale, effectiveMinimum)
        }
    }
    val uniqueScores = remember(
        renderedSnapshot,
        minimumEligibleSeries,
        effectiveMinimum,
        selectedScale,
        selectedSignal,
        ratingAdjustment,
        viewAll,
        uniqueOnly
    ) {
        if (!viewAll || !uniqueOnly) {
            emptyList()
        } else {
            val valuesByTargetId = minimumEligibleSeries.associate { series ->
                series.target.id to trendValues(series, selectedScale, selectedSignal, ratingAdjustment)
            }
            scoreUniqueTrends(
                series = minimumEligibleSeries,
                valuesByTargetId = valuesByTargetId,
                significanceMode = when (selectedScale) {
                    TrendScale.READS -> UniqueTrendSignificanceMode.READS
                    TrendScale.SHARE -> UniqueTrendSignificanceMode.SHARE
                },
                scale = selectedScale,
                signal = selectedSignal,
                ratingAdjustment = ratingAdjustment
            )
        }
    }
    val uniqueSelection = remember(uniqueScores, selectedScale, effectiveMinimum, uniqueOnly) {
        if (uniqueOnly) selectMeaningfulUniqueTrends(uniqueScores) else UniqueTrendSelection(emptySet(), uniqueScores)
    }
    // Explicit Compare selections are user intent. Keep them visible in Unique mode as long
    // as they still pass the active Reads/Share minimum, even when the automatic curation
    // would otherwise leave them out.
    val minimumEligibleTargetIds = remember(minimumEligibleSeries) {
        minimumEligibleSeries.mapTo(linkedSetOf()) { it.target.id }
    }
    val selectedUniqueOverrides = remember(normalizedTargetIds, minimumEligibleTargetIds) {
        normalizedTargetIds.filterTo(linkedSetOf()) { it in minimumEligibleTargetIds }
    }
    val uniqueTargetIds = remember(uniqueSelection.targetIds, selectedUniqueOverrides, uniqueOnly) {
        if (uniqueOnly) {
            (uniqueSelection.targetIds + selectedUniqueOverrides).toCollection(linkedSetOf())
        } else {
            emptySet()
        }
    }
    val visibleViewAllCount = if (viewAll && uniqueOnly) {
        uniqueTargetIds.size
    } else {
        minimumEligibleSeries.size
    }
    val chartContentAlpha by animateFloatAsState(
        targetValue = if (displayedSnapshot == null && requestPending && renderedSnapshot != null) {
            0.42f
        } else {
            1f
        },
        animationSpec = tween(durationMillis = 180),
        label = "trend-chart-refresh-alpha"
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            TrendPanelHeader(
                targetKind = targetKind,
                includeMisc = includeMisc,
                onIncludeMiscChange = { includeMisc = it }
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrendTargetKind.entries.forEach { kind ->
                        FilterChip(
                            selected = targetKind == kind,
                            onClick = { targetKind = kind },
                            label = { Text(kind.label) }
                        )
                    }
                }
                if (viewAll) {
                    Row(
                        modifier = Modifier.width(144.dp).height(32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = when (selectedScale) {
                                TrendScale.SHARE -> "≥ ${effectiveMinimum.roundToInt()}%"
                                TrendScale.READS -> "≥ ${effectiveMinimum.roundToInt()}"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                        Slider(
                            value = effectiveMinimum,
                            onValueChange = { value ->
                                when (selectedScale) {
                                    TrendScale.SHARE -> minimumShare = value.roundToInt().toFloat()
                                    TrendScale.READS -> minimumReads = value.roundToInt().toFloat()
                                }
                            },
                            valueRange = when (selectedScale) {
                                TrendScale.SHARE -> 0f..100f
                                TrendScale.READS -> minimumAvailableReads..if (
                                    maximumAvailableReads > minimumAvailableReads
                                ) maximumAvailableReads else minimumAvailableReads + 1f
                            },
                            enabled = selectedScale == TrendScale.SHARE || maximumAvailableReads > minimumAvailableReads,
                            modifier = Modifier.weight(1f).height(32.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Compare",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StatsRange.entries, key = { it.name }) { range ->
                    FilterChip(
                        selected = selectedRange == range,
                        onClick = { selectedRange = range },
                        label = { Text(range.label) }
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrendScaleSwitch(
                    selected = selectedScale,
                    onSelected = { selectedScale = it },
                    metric = selectedMetric,
                    onMetric = { metricName = it.name }
                )
                if (viewAll) {
                    TrendViewModeSwitch(
                        uniqueOnly = uniqueOnly,
                        onUniqueOnlyChange = { uniqueOnly = it }
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(TrendSignal.entries, key = { it.name }) { signal ->
                        FilterChip(
                            selected = selectedSignal == signal,
                            onClick = { selectedSignal = signal },
                            label = { Text(signal.label) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    TextButton(
                        onClick = {
                            errorMessage = null
                            viewAll = !viewAll
                            uniqueOnly = false
                        },
                        enabled = !targetsLoading && availableTargets.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = if (viewAll) "Compare" else "View all",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    TextButton(
                        onClick = { showTargetPicker = true },
                        enabled = !targetsLoading && availableTargets.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = if (targetsLoading) "Loading" else "Choose",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        item {
            when {
                !viewAll && normalizedTargetIds.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Choose at least one item to view.")
                }
                renderedSnapshot == null && (snapshotLoading || requestPending) -> Box(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                errorMessage != null && renderedSnapshot == null -> Box(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Could not load trends: $errorMessage",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                renderedSnapshot == null || renderedSnapshot.buckets.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No reading data exists in this time range.")
                }
                viewAll && visibleViewAllCount == 0 -> Box(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (uniqueOnly) "No distinctive trends meet this minimum."
                        else "No trends meet this minimum."
                    )
                }
                else -> Box(modifier = Modifier.fillMaxWidth()) {
                    TrendLineChart(
                        snapshot = renderedSnapshot,
                        scale = selectedScale,
                        signal = selectedSignal,
                        ratingAdjustment = ratingAdjustment,
                        // A retained ghost snapshot must keep its own rendering semantics.
                        // Otherwise View all's background lines briefly receive Compare colors.
                        viewAll = renderedRequest?.viewAll ?: viewAll,
                        emphasizedTargetIds = normalizedTargetIds,
                        emphasizedColorSlots = selectedColorSlots,
                        minimum = effectiveMinimum,
                        allowedTargetIds = uniqueTargetIds.takeIf { uniqueOnly },
                        modifier = Modifier.fillMaxWidth().alpha(chartContentAlpha)
                    )
                    if (snapshotLoading || requestPending) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .size(22.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = buildString {
                    if (viewAll) {
                        if (uniqueOnly) {
                            append("$visibleViewAllCount unique trends shown. ")
                        } else {
                            append("$visibleViewAllCount lines shown. ")
                        }
                        append("Drag to follow the nearest outlier. ")
                    }
                    append(
                        trendDescription(
                            selectedScale,
                            selectedSignal,
                            metric = selectedMetric,
                            normalizedReadRate = renderedSnapshot?.series.orEmpty()
                                .any { series -> series.points.any { it.readNormalizationFactor != 1f } }
                        )
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.trendDataPrivacyMask()
            )
        }
    }
}

@Composable
private fun TrendPanelHeader(
    targetKind: TrendTargetKind,
    includeMisc: Boolean,
    onIncludeMiscChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Reading Trends",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Local reading interests over time",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Include misc", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(6.dp))
                Switch(
                    checked = includeMisc,
                    onCheckedChange = onIncludeMiscChange
                )
            }
        }
    }
}

@Composable
private fun TrendViewModeSwitch(
    uniqueOnly: Boolean,
    onUniqueOnlyChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.width(176.dp).height(36.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(2.dp)) {
            listOf(false to "All trends", true to "Unique").forEach { (unique, label) ->
                val selected = uniqueOnly == unique
                Surface(
                    onClick = { onUniqueOnlyChange(unique) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendScaleSwitch(
    selected: TrendScale,
    onSelected: (TrendScale) -> Unit,
    metric: TrendMetric,
    onMetric: (TrendMetric) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
    Surface(
        modifier = Modifier.width(164.dp).height(36.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(2.dp)) {
            val segmentWidth = maxWidth / 2
            val thumbOffset by animateDpAsState(
                targetValue = if (selected == TrendScale.SHARE) segmentWidth else 0.dp,
                animationSpec = tween(durationMillis = 180),
                label = "trend-scale-thumb"
            )
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
            )
            Row(modifier = Modifier.fillMaxSize()) {
                TrendScale.entries.forEach { scale ->
                    val isSelected = selected == scale
                    Surface(
                        onClick = { if (scale == TrendScale.READS && isSelected) menuOpen = true else onSelected(scale) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Transparent,
                        contentColor = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (scale == TrendScale.READS) "${metric.label} ▾" else scale.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        TrendMetric.entries.forEach { option ->
            DropdownMenuItem(text = { Text(option.label) }, onClick = { onMetric(option); menuOpen = false })
        }
    }
    }
}

@Composable
private fun TrendTargetPicker(
    title: String,
    targets: List<TrendTarget>,
    selectedIds: List<Long>,
    selectedColorSlots: Map<Long, Int>,
    onConfirm: (List<Long>, Map<Long, Int>) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var draft by remember(selectedIds) { mutableStateOf(selectedIds.distinct().take(5)) }
    var draftColorSlots by remember(selectedIds, selectedColorSlots) {
        mutableStateOf(assignTrendColorSlots(draft, selectedColorSlots))
    }
    val filtered = remember(query, targets) {
        val term = query.trim()
        if (term.isBlank()) targets else targets.filter {
            it.name.contains(term, ignoreCase = true) || it.type.contains(term, ignoreCase = true)
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${draft.size} selected · up to 5",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Transparent
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filtered, key = { it.id }) { target ->
                            val selected = target.id in draft
                            val selectionColor = TREND_COLORS[
                                (draftColorSlots[target.id] ?: 0).coerceIn(TREND_COLORS.indices)
                            ]
                            Surface(
                                onClick = {
                                    if (selected) {
                                        draft = draft - target.id
                                        draftColorSlots = draftColorSlots - target.id
                                    } else {
                                        val updated = draft + target.id
                                        draft = updated
                                        draftColorSlots = assignTrendColorSlots(updated, draftColorSlots)
                                    }
                                },
                                enabled = selected || draft.size < 5,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) selectionColor.copy(alpha = 0.18f) else Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(
                                                if (selected) selectionColor else MaterialTheme.colorScheme.outline,
                                                CircleShape
                                            )
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .trendDataPrivacyMask()
                                    ) {
                                        Text(target.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = "${target.type} · ${target.entryCount} entries",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { onConfirm(draft, assignTrendColorSlots(draft, draftColorSlots)) },
                        enabled = draft.size in 0..5
                    ) {
                        Text(if (draft.isEmpty()) "View all" else "Compare")
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendLineChart(
    snapshot: TrendSnapshot,
    scale: TrendScale,
    signal: TrendSignal,
    ratingAdjustment: RatingAdjustment,
    viewAll: Boolean,
    emphasizedTargetIds: List<Long>,
    emphasizedColorSlots: Map<Long, Int>,
    minimum: Float,
    allowedTargetIds: Set<Long>?,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLow
    val allLinesColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.11f)
    val highlightedColor = MaterialTheme.colorScheme.primary
    var selectedBucketIndex by remember(snapshot) { mutableIntStateOf(snapshot.buckets.lastIndex.coerceAtLeast(0)) }
    var highlightedTargetId by remember(snapshot, viewAll) { mutableStateOf<Long?>(null) }
    var insightTargetId by remember(snapshot) { mutableStateOf<Long?>(null) }
    var insightBucketIndex by remember(snapshot) { mutableIntStateOf(0) }
    var trackedInsightTargetId by remember(snapshot) { mutableStateOf<Long?>(null) }
    var longPressTracking by remember(snapshot) { mutableStateOf(false) }
    val visibleSeries = remember(snapshot, scale, viewAll, minimum, allowedTargetIds) {
        if (viewAll) {
            snapshot.series.filter {
                it.meetsMinimum(scale, minimum) &&
                    (allowedTargetIds == null || it.target.id in allowedTargetIds)
            }
        } else {
            snapshot.series
        }
    }
    val renderedValues = remember(visibleSeries, scale, signal, ratingAdjustment) {
        visibleSeries.associate {
            it.target.id to trendValues(it, scale, signal, ratingAdjustment)
        }
    }
    val rawMax = renderedValues.values.maxOfOrNull { values -> values.maxOrNull() ?: 0f }
        ?.coerceAtLeast(0f) ?: 0f
    val (yMin, yMax) = when {
        signal == TrendSignal.AVERAGE_RATING -> {
            val observed = renderedValues.values.flatten().filter { it > 0f }
            if (observed.isEmpty()) {
                1f to 5f
            } else {
                val sorted = observed.sorted()
                val observedMin = if (viewAll) {
                    sorted[((sorted.lastIndex * 0.05f).roundToInt()).coerceIn(sorted.indices)]
                } else {
                    sorted.first()
                }
                val observedMax = if (viewAll) {
                    sorted[((sorted.lastIndex * 0.95f).roundToInt()).coerceIn(sorted.indices)]
                } else {
                    sorted.last()
                }
                val center = (observedMin + observedMax) / 2f
                val minimumSpan = if (viewAll) 0.5f else 0.25f
                val span = max(minimumSpan, observedMax - observedMin)
                var lower = (center - span * 0.65f).coerceAtLeast(1f)
                var upper = (center + span * 0.65f).coerceAtMost(5f)
                if (upper - lower < minimumSpan) {
                    if (lower <= 1f) {
                        upper = 1f + minimumSpan
                    } else {
                        lower = (upper - minimumSpan).coerceAtLeast(1f)
                    }
                }
                lower to upper
            }
        }
        scale == TrendScale.SHARE -> 0f to max(10f, ceil(rawMax / 10f) * 10f).coerceAtMost(100f)
        else -> 0f to max(1f, ceil(rawMax))
    }
    val selectedBucket = snapshot.buckets.getOrNull(selectedBucketIndex)
    val emphasizedColorById = emphasizedTargetIds.take(5).associateWith { id ->
        TREND_COLORS[(emphasizedColorSlots[id] ?: 0).coerceIn(TREND_COLORS.indices)]
    }

    fun bucketIndexAt(x: Float, width: Float): Int {
        val bucketCount = snapshot.buckets.size
        if (bucketCount <= 0 || width <= 0f) return 0
        return if (bucketCount == 1) 0 else {
            ((x.coerceIn(0f, width) / width) * (bucketCount - 1)).roundToInt().coerceIn(0, bucketCount - 1)
        }
    }

    fun nearestSeriesAt(bucketIndex: Int, y: Float, height: Float): TrendSeries? {
        if (height <= 0f) return null
        val touchedValue = yMin + (1f - y.coerceIn(0f, height) / height) * (yMax - yMin)
        return visibleSeries.minByOrNull { series ->
            abs((renderedValues[series.target.id]?.getOrNull(bucketIndex) ?: 0f) - touchedValue)
        }
    }

    fun updateSelection(x: Float, y: Float, width: Float, height: Float) {
        if (snapshot.buckets.isEmpty() || width <= 0f || height <= 0f) return
        selectedBucketIndex = bucketIndexAt(x, width)
        if (viewAll) {
            highlightedTargetId = nearestSeriesAt(selectedBucketIndex, y, height)?.target?.id
        }
    }

    fun trackInsight(x: Float, y: Float, width: Float, height: Float): Long? {
        if (snapshot.buckets.isEmpty() || width <= 0f || height <= 0f) return null
        val bucketIndex = bucketIndexAt(x, width)
        val series = nearestSeriesAt(bucketIndex, y, height) ?: return null
        selectedBucketIndex = bucketIndex
        if (viewAll) highlightedTargetId = series.target.id
        insightBucketIndex = bucketIndex
        trackedInsightTargetId = series.target.id
        return series.target.id
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(24.dp),
            color = surfaceColor
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = if (yMin > 0f) {
                            "${formatTrendValue(yMin, scale, signal)}–${formatTrendValue(yMax, scale, signal)}"
                        } else {
                            formatTrendValue(yMax, scale, signal)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.trendDataPrivacyMask()
                    )
                    Text(
                        text = selectedBucket?.let { formatTrendBucket(it, snapshot.granularity) }.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.trendDataPrivacyMask()
                    )
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .pointerInput(snapshot, scale, signal, ratingAdjustment, viewAll, yMin, yMax) {
                            detectTapGestures(
                                onTap = { tap ->
                                    updateSelection(tap.x, tap.y, size.width.toFloat(), size.height.toFloat())
                                }
                            )
                        }
                        .pointerInput(snapshot, scale, signal, ratingAdjustment, viewAll, yMin, yMax) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    updateSelection(start.x, start.y, size.width.toFloat(), size.height.toFloat())
                                },
                                onDrag = { change, _ ->
                                    updateSelection(
                                        change.position.x,
                                        change.position.y,
                                        size.width.toFloat(),
                                        size.height.toFloat()
                                    )
                                    change.consume()
                                }
                            )
                        }
                        .pointerInput(snapshot, scale, signal, ratingAdjustment, viewAll, yMin, yMax) {
                            var trackedId: Long? = null
                            detectDragGesturesAfterLongPress(
                                onDragStart = { start ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    longPressTracking = true
                                    trackedId = trackInsight(
                                        start.x,
                                        start.y,
                                        size.width.toFloat(),
                                        size.height.toFloat()
                                    )
                                },
                                onDrag = { change, _ ->
                                    trackedId = trackInsight(
                                        change.position.x,
                                        change.position.y,
                                        size.width.toFloat(),
                                        size.height.toFloat()
                                    ) ?: trackedId
                                    change.consume()
                                },
                                onDragEnd = {
                                    longPressTracking = false
                                    insightTargetId = trackedId
                                    trackedInsightTargetId = null
                                },
                                onDragCancel = {
                                    longPressTracking = false
                                    trackedInsightTargetId = null
                                }
                            )
                        }
                ) {
                    val left = 4.dp.toPx()
                    val right = size.width - 4.dp.toPx()
                    val top = 4.dp.toPx()
                    val bottom = size.height - 4.dp.toPx()
                    repeat(5) { index ->
                        val y = top + (bottom - top) * index / 4f
                        drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
                    }
                    val bucketCount = snapshot.buckets.size
                    fun pointOffset(index: Int, value: Float): Offset {
                        val x = if (bucketCount <= 1) (left + right) / 2f else left + (right - left) * index / (bucketCount - 1f)
                        val fraction = ((value - yMin) / (yMax - yMin).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
                        val y = bottom - (bottom - top) * fraction
                        return Offset(x, y)
                    }
                    if (bucketCount > 0) {
                        val selectedX = pointOffset(selectedBucketIndex.coerceIn(0, bucketCount - 1), yMin).x
                        drawLine(
                            color = if (longPressTracking) {
                                highlightedColor.copy(alpha = 0.72f)
                            } else {
                                gridColor.copy(alpha = 0.8f)
                            },
                            start = Offset(selectedX, top),
                            end = Offset(selectedX, bottom),
                            strokeWidth = if (longPressTracking) 2.dp.toPx() else 1.dp.toPx()
                        )
                    }
                    fun drawSeries(series: TrendSeries, color: Color, strokeWidth: Float, showPoint: Boolean) {
                        val values = renderedValues[series.target.id].orEmpty()
                        val path = Path()
                        var previousOffset: Offset? = null
                        values.forEachIndexed { pointIndex, value ->
                            val offset = pointOffset(pointIndex, value)
                            val previous = previousOffset
                            if (previous == null) {
                                path.moveTo(offset.x, offset.y)
                            } else {
                                val midpointX = (previous.x + offset.x) / 2f
                                path.cubicTo(midpointX, previous.y, midpointX, offset.y, offset.x, offset.y)
                            }
                            previousOffset = offset
                        }
                        drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        if (showPoint) {
                            values.getOrNull(selectedBucketIndex)?.let { value ->
                                val offset = pointOffset(selectedBucketIndex, value)
                                drawCircle(color, radius = 5.dp.toPx(), center = offset)
                                drawCircle(surfaceColor, radius = 2.dp.toPx(), center = offset)
                            }
                        }
                    }

                    if (viewAll) {
                        visibleSeries.forEach { series ->
                            if (series.target.id !in emphasizedColorById && series.target.id != highlightedTargetId) {
                                drawSeries(series, allLinesColor, 1.dp.toPx(), showPoint = false)
                            }
                        }
                        visibleSeries.forEach { series ->
                            emphasizedColorById[series.target.id]?.let { color ->
                                drawSeries(series, color, 3.dp.toPx(), showPoint = true)
                            }
                        }
                        highlightedTargetId?.let { targetId ->
                            visibleSeries.firstOrNull { it.target.id == targetId }?.let { series ->
                                drawSeries(series, highlightedColor, 4.dp.toPx(), showPoint = true)
                            }
                        }
                    } else {
                        visibleSeries.forEachIndexed { seriesIndex, series ->
                            drawSeries(
                                series = series,
                                color = emphasizedColorById[series.target.id]
                                    ?: TREND_COLORS[seriesIndex % TREND_COLORS.size],
                                strokeWidth = 3.dp.toPx(),
                                showPoint = true
                            )
                        }
                    }
                    trackedInsightTargetId?.let { targetId ->
                        val series = visibleSeries.firstOrNull { it.target.id == targetId }
                        val value = renderedValues[targetId]?.getOrNull(selectedBucketIndex)
                        if (series != null && value != null) {
                            val color = emphasizedColorById[targetId]
                                ?: if (viewAll) highlightedColor else TREND_COLORS[visibleSeries.indexOf(series).coerceAtLeast(0) % TREND_COLORS.size]
                            val offset = pointOffset(selectedBucketIndex, value)
                            drawCircle(surfaceColor, radius = 9.dp.toPx(), center = offset)
                            drawCircle(color, radius = 7.dp.toPx(), center = offset, style = Stroke(width = 2.dp.toPx()))
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        snapshot.buckets.firstOrNull()?.let { formatTrendBucket(it, snapshot.granularity) }.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.trendDataPrivacyMask()
                    )
                    Text(
                        snapshot.buckets.lastOrNull()?.let { formatTrendBucket(it, snapshot.granularity) }.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.trendDataPrivacyMask()
                    )
                }
            }
        }

        val legendSeries = if (viewAll) {
            buildList {
                highlightedTargetId?.let { id ->
                    visibleSeries.firstOrNull { it.target.id == id }?.let(::add)
                }
                emphasizedTargetIds.forEach { id ->
                    visibleSeries.firstOrNull { it.target.id == id }
                        ?.takeIf { candidate -> none { it.target.id == candidate.target.id } }
                        ?.let(::add)
                }
            }
        } else {
            visibleSeries
        }
        legendSeries.forEachIndexed { index, series ->
            val value = renderedValues[series.target.id]?.getOrNull(selectedBucketIndex) ?: 0f
            val color = when {
                viewAll && series.target.id == highlightedTargetId -> highlightedColor
                viewAll -> emphasizedColorById[series.target.id] ?: allLinesColor
                else -> emphasizedColorById[series.target.id] ?: TREND_COLORS[index % TREND_COLORS.size]
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(10.dp).background(color, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = series.target.name,
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(series.target.id, selectedBucketIndex, haptic) {
                            detectTapGestures(
                                onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    insightBucketIndex = selectedBucketIndex
                                    insightTargetId = series.target.id
                                }
                            )
                        }
                        .trendDataPrivacyMask(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatTrendValue(value, scale, signal),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.trendDataPrivacyMask()
                )
            }
        }
    }

    val insightSeries = visibleSeries.firstOrNull { it.target.id == insightTargetId }
    if (insightSeries != null) {
        val insight = buildTrendInsight(
            series = insightSeries,
            bucketIndex = insightBucketIndex,
            granularity = snapshot.granularity,
            scale = scale,
            signal = signal,
            ratingAdjustment = ratingAdjustment
        )
        Popup(
            alignment = Alignment.Center,
            onDismissRequest = { insightTargetId = null },
            properties = PopupProperties(focusable = true)
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 330.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = insightSeries.target.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.trendDataPrivacyMask()
                    )
                    Text(
                        text = insight.periodLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.trendDataPrivacyMask()
                    )
                    Text(
                        text = insight.changeText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.trendDataPrivacyMask()
                    )
                    Text(
                        text = insight.driverText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.trendDataPrivacyMask()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { insightTargetId = null }) { Text("Close") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.trendDataPrivacyMask(): Modifier = this

private fun formatTrendBucket(key: String, granularity: TrendBucketGranularity): String = runCatching {
    when (granularity) {
        TrendBucketGranularity.FOUR_HOURS -> {
            val date = LocalDate.parse(key.substringBefore(' '))
            val hour = key.substringAfter(' ').toInt()
            val endHour = (hour + 4).coerceAtMost(24)
            "${date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))}, ${hour.toString().padStart(2, '0')}:00–${endHour.toString().padStart(2, '0')}:00"
        }
        TrendBucketGranularity.DAY -> LocalDate.parse(key).format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
        TrendBucketGranularity.WEEK -> {
            val start = LocalDate.parse(key)
            val end = start.plusDays(6)
            "${start.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))}–${end.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))}"
        }
        TrendBucketGranularity.MONTH -> LocalDate.parse("$key-01").format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))
        TrendBucketGranularity.QUARTER -> key.replace("-Q", " Q")
        TrendBucketGranularity.HALF_YEAR -> key.replace("-H1", " Jan–Jun").replace("-H2", " Jul–Dec")
        TrendBucketGranularity.YEAR -> key
    }
}.getOrDefault(key)

private data class TrendInsightUi(
    val periodLabel: String,
    val changeText: String,
    val driverText: String
)

private fun buildTrendInsight(
    series: TrendSeries,
    bucketIndex: Int,
    granularity: TrendBucketGranularity,
    scale: TrendScale,
    signal: TrendSignal,
    ratingAdjustment: RatingAdjustment
): TrendInsightUi {
    val safeIndex = bucketIndex.coerceIn(series.points.indices)
    val point = series.points[safeIndex]
    val values = trendValues(series, scale, signal, ratingAdjustment)
    val current = values.getOrElse(safeIndex) { 0f }
    val previous = values.getOrNull(safeIndex - 1)
    val changeText = if (previous == null) {
        "${formatTrendValue(current, scale, signal)} in the first available period."
    } else {
        val delta = current - previous
        val direction = when {
            delta > 0.0001f -> "rose"
            delta < -0.0001f -> "fell"
            else -> "was unchanged"
        }
        when {
            direction == "was unchanged" -> "The selected value was unchanged at ${formatTrendValue(current, scale, signal)}."
            signal == TrendSignal.AVERAGE_RATING ->
                "Average rating $direction from ${formatTrendValue(previous, scale, signal)} to ${formatTrendValue(current, scale, signal)} (${signedDecimal(delta)})."
            scale == TrendScale.SHARE ->
                "The share $direction from ${formatTrendValue(previous, scale, signal)} to ${formatTrendValue(current, scale, signal)} (${signedWhole(delta)} percentage points)."
            previous >= 1f -> {
                val relative = (delta / previous) * 100f
                "Reads $direction from ${formatTrendValue(previous, scale, signal)} to ${formatTrendValue(current, scale, signal)} (${signedWhole(relative)}%)."
            }
            previous > 0f ->
                "Reads $direction from ${formatTrendValue(previous, scale, signal)} to ${formatTrendValue(current, scale, signal)}. The earlier value was too small for a useful percentage comparison."
            else -> "Reads $direction to ${formatTrendValue(current, scale, signal)} from zero in the previous period."
        }
    }
    val lowRatings = point.rating1Count + point.rating2Count
    val highRatings = point.rating4Count + point.rating5Count
    val rawDriverText = when (signal) {
        TrendSignal.ALL ->
            "This period contained ${point.matchingReads} matching reads out of ${point.totalReads} total reads."
        TrendSignal.POSITIVE ->
            "This period contained ${point.positiveRatings} positive ratings out of ${point.ratedEntries} rated matching reads."
        TrendSignal.AVERAGE_RATING -> when {
            highRatings > lowRatings -> "Mostly supported by $highRatings four- or five-star ratings across ${point.ratedEntries} rated matching reads."
            lowRatings > highRatings -> "Mostly pulled down by $lowRatings one- or two-star ratings across ${point.ratedEntries} rated matching reads."
            point.ratedEntries > 0 -> "Based on ${point.ratedEntries} rated matching reads in this period."
            else -> "No new matching ratings were recorded in this period."
        }
    }
    val contextNotes = buildList {
        if (scale == TrendScale.READS && point.readNormalizationFactor != 1f) {
            add("The graph shows a 30-day equivalent rate; these are the raw period counts.")
        }
        if (signal != TrendSignal.ALL && point.ratedEntries in 1..2) {
            add("Low sample: only ${point.ratedEntries} rated ${if (point.ratedEntries == 1) "entry" else "entries"} contributed.")
        }
        if (signal == TrendSignal.AVERAGE_RATING && point.ratedEntries == 0 && current > 0f) {
            add("No new rating evidence was added, so the last observed average is retained.")
        }
    }
    return TrendInsightUi(
        periodLabel = formatTrendBucket(point.bucketKey, granularity),
        changeText = changeText,
        driverText = buildString {
            append(rawDriverText)
            contextNotes.forEach { note -> append(' ').append(note) }
        }
    )
}

private fun signedWhole(value: Float): String = if (value >= 0f) "+${value.roundToInt()}" else value.roundToInt().toString()

private fun signedDecimal(value: Float): String = String.format(
    Locale.getDefault(),
    if (value >= 0f) "+%.2f" else "%.2f",
    value
)

private fun formatTrendValue(value: Float, scale: TrendScale, signal: TrendSignal): String = when {
    signal == TrendSignal.AVERAGE_RATING -> String.format(Locale.getDefault(), "%.2f", value)
    scale == TrendScale.SHARE -> "${value.roundToInt()}%"
    else -> if (kotlin.math.abs(value - value.roundToInt()) > 0.01f) String.format(Locale.getDefault(), "%.1f", value) else value.roundToInt().toString()
}

private fun TrendSeries.totalMatchingReads(): Float = points.sumOf { it.matchingAmount.toDouble() }.toFloat()

private fun TrendSeries.readShare(): Float {
    val totalReads = points.sumOf { it.totalAmount.toDouble() }.toFloat()
    return if (totalReads > 0) totalMatchingReads() * 100f / totalReads else 0f
}

private fun TrendSeries.meetsMinimum(scale: TrendScale, minimum: Float): Boolean = when (scale) {
    TrendScale.READS -> totalMatchingReads() >= minimum
    TrendScale.SHARE -> readShare() >= minimum
}

private fun assignTrendColorSlots(
    ids: List<Long>,
    existing: Map<Long, Int>
): Map<Long, Int> {
    val assigned = linkedMapOf<Long, Int>()
    val used = mutableSetOf<Int>()
    ids.distinct().take(TREND_COLORS.size).forEach { id ->
        existing[id]
            ?.takeIf { it in TREND_COLORS.indices && used.add(it) }
            ?.let { assigned[id] = it }
    }
    ids.distinct().take(TREND_COLORS.size).forEach { id ->
        if (id !in assigned) {
            val slot = TREND_COLORS.indices.first { it !in used }
            used += slot
            assigned[id] = slot
        }
    }
    return assigned
}

private fun trendDescription(
    scale: TrendScale,
    signal: TrendSignal,
    normalizedReadRate: Boolean,
    metric: TrendMetric
): String {
    val description = when (scale to signal) {
    TrendScale.READS to TrendSignal.ALL -> "Matching reads in each period. Every re-read counts once in the period when it happened."
    TrendScale.READS to TrendSignal.POSITIVE -> "Matching reads rated 4 or 5 in each period, including re-reads."
    TrendScale.READS to TrendSignal.AVERAGE_RATING -> "Average non-zero rating for matching reads in each period. Empty periods retain the latest observed average."
    TrendScale.SHARE to TrendSignal.ALL -> "Cumulative share of all reads, including re-reads. Empty periods retain the latest share."
    TrendScale.SHARE to TrendSignal.POSITIVE -> "Cumulative share of rated matching reads that received 4 or 5."
    TrendScale.SHARE to TrendSignal.AVERAGE_RATING -> "Cumulative average rating for matching reads. Empty periods retain the latest average."
    else -> "Trend values for the selected view."
    }
    val metricDescription = if (signal == TrendSignal.AVERAGE_RATING) description else description.replace("reads", when(metric) {
        TrendMetric.PAGES -> "pages"; TrendMetric.TIME -> "minutes"; TrendMetric.SESSIONS -> "sessions"
    }).replace("Every re-read counts once in the period when it happened.", "").replace("including re-reads", "across saved sessions")
    return if (normalizedReadRate && scale == TrendScale.READS && signal != TrendSignal.AVERAGE_RATING) {
        "$metricDescription Long All Time buckets are displayed as comparable 30-day rates, not raw totals."
    } else {
        metricDescription
    }
}
