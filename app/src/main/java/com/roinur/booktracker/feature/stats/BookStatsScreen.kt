package com.roinur.booktracker

import com.roinur.booktracker.BookFitText as Text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun BookStatsContent(
    statsBooks: List<BookRow>,
    sessions: List<BookReadingSessionWithBook>,
    finishedYearBooks: List<BookRow>,
    onOpenDaySessions: (LocalDate) -> Unit,
    stats: BookStats,
    goals: BookReadingGoals,
    activeBook: BookRow?,
    activeStartedAtMs: Long,
    activePausedAtMs: Long,
    activePausedTotalMs: Long,
    nowMs: Long,
    modifier: Modifier = Modifier,
    onContinue: (BookRow) -> Unit,
    onOpenGlobalNotes: (BookNoteKind) -> Unit,
    onOpenGlobalSessions: () -> Unit,
    onDaySelected: (DailyActivityPoint) -> Unit,
    onOpenTrends: () -> Unit,
    onSaveGoals: (BookReadingGoals) -> Boolean
) {
    var rangeName by rememberSaveable { mutableStateOf(BookStatsRange.WEEK.name) }
    var metricName by rememberSaveable { mutableStateOf(BookGraphMetric.TIME.name) }
    var showReadingProgress by rememberSaveable { mutableStateOf(false) }
    var showReadingGoals by rememberSaveable { mutableStateOf(false) }
    var showYearBooks by remember { mutableStateOf(false) }
    val selectedRange = runCatching { BookStatsRange.valueOf(rangeName) }.getOrDefault(BookStatsRange.WEEK)
    val selectedMetric = runCatching { BookGraphMetric.valueOf(metricName) }.getOrDefault(BookGraphMetric.TIME)
    val streak = remember(stats.dailyPageActivity) { bookReadingStreak(stats.dailyPageActivity) }
    val longestStreak = remember(stats.dailyPageActivity) { bookLongestReadingStreak(stats.dailyPageActivity) }
    val sourcePoints = if (selectedMetric == BookGraphMetric.TIME) stats.dailyActivity else stats.dailyPageActivity
    var periodOffset by rememberSaveable { mutableStateOf(0) }
    val periodEnd = when (selectedRange) {
        BookStatsRange.WEEK -> LocalDate.now().minusWeeks(periodOffset.toLong())
        BookStatsRange.MONTH -> LocalDate.now().minusMonths(periodOffset.toLong())
        BookStatsRange.YEAR -> LocalDate.now().minusYears(periodOffset.toLong())
        BookStatsRange.ALL_TIME -> LocalDate.now()
    }
    val periodStart = when (selectedRange) {
        BookStatsRange.WEEK -> periodEnd.minusDays(6)
        BookStatsRange.MONTH -> periodEnd.minusDays(29)
        BookStatsRange.YEAR -> periodEnd.minusDays(364)
        BookStatsRange.ALL_TIME -> stats.dailyActivity.minOfOrNull { it.date } ?: periodEnd
    }
    var breakdownMetric by remember { mutableStateOf<BookGraphMetric?>(null) }
    var breakdownAllTime by remember { mutableStateOf(false) }
    var selectedHeatmapDate by remember(selectedRange, periodOffset) { mutableStateOf<LocalDate?>(null) }
    val selectedHeatmapPoint = sourcePoints.firstOrNull { it.date == selectedHeatmapDate }
    val rangePoints = remember(sourcePoints, selectedRange, periodEnd) {
        bookPointsForRange(sourcePoints, selectedRange, periodEnd)
    }
    val rangeMinutePoints = remember(stats.dailyActivity, selectedRange, periodEnd) {
        bookPointsForRange(stats.dailyActivity, selectedRange, periodEnd)
    }
    val rangePagePoints = remember(stats.dailyPageActivity, selectedRange, periodEnd) {
        bookPointsForRange(stats.dailyPageActivity, selectedRange, periodEnd)
    }
    val activeLiveSeconds = if (activeBook != null) {
        bookActiveElapsedSeconds(activeStartedAtMs, nowMs, activePausedAtMs, activePausedTotalMs)
    } else {
        0L
    }
    val progressPages = rangePagePoints.sumOf { it.pagesRead }.coerceAtLeast(0)
    val progressMinutes = rangeMinutePoints.sumOf { it.pagesRead }.coerceAtLeast(0)
    val progressSeconds = progressMinutes * 60L
    val pagesPerHour = if (progressSeconds > 0L) {
        progressPages.toFloat() * 3600f / progressSeconds.toFloat()
    } else {
        0f
    }
    val bestPageDay = rangePagePoints.maxOfOrNull { it.pagesRead }?.coerceAtLeast(0) ?: 0
    val bestMinuteDay = rangeMinutePoints.maxOfOrNull { it.pagesRead }?.coerceAtLeast(0) ?: 0

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val chartHeight = ((maxHeight * 0.19f).coerceIn(132.dp, 198.dp) - 20.dp)
        Column(
            modifier = Modifier.fillMaxSize().padding(bottom = 110.dp).layout { measurable, constraints ->
                // Measure all sections before fitting them into the space above the actions.
                val content = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                val scale = minOf(1f, constraints.maxHeight.toFloat() / content.height.coerceAtLeast(1))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    content.placeWithLayer(((constraints.maxWidth - content.width * scale) / 2).roundToInt(), 0) {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                }
            },
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
        BookPanel(contentPadding = PaddingValues(horizontal = 10.dp, vertical = 11.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BookStatCell(
                    icon = "pages",
                    label = "Pages read",
                    value = if (stats.totalPages > 0) "${stats.pagesRead}" else "-",
                    modifier = Modifier.weight(1f).fittedClickable { breakdownAllTime = true; breakdownMetric = BookGraphMetric.PAGES }
                )
                BookVerticalDivider()
                BookStatCell(
                    icon = "time",
                    label = "Read time",
                    value = bookFormatStatsReadTime(stats.readingSeconds + activeLiveSeconds),
                    supporting = if (pagesPerHour > 0f) String.format(Locale.US, "%.1f pages/h", pagesPerHour) else "- pages/h",
                    modifier = Modifier.weight(1f).fittedClickable { breakdownAllTime = true; breakdownMetric = BookGraphMetric.TIME }
                )
                BookVerticalDivider()
                BookStatCell(
                    icon = "streak",
                    label = "Current streak",
                    value = streak.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val today = LocalDate.now()
                val dailyPoints = if (goals.dailyMetric == BookGoalMetric.TIME) stats.dailyActivity else stats.dailyPageActivity
                val monthlyPoints = if (goals.monthlyMetric == BookGoalMetric.TIME) stats.dailyActivity else stats.dailyPageActivity
                Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BookGoalRing("Daily", dailyPoints.firstOrNull { it.date == today }?.pagesRead ?: 0,
                        if (goals.dailyMetric == BookGoalMetric.TIME) goals.dailyMinutes else goals.dailyPages) { showReadingGoals = true }
                    BookGoalRing("Monthly", monthlyPoints.filter { it.date.year == today.year && it.date.month == today.month }.sumOf { it.pagesRead },
                        if (goals.monthlyMetric == BookGoalMetric.TIME) goals.monthlyMinutes else goals.monthlyPages) { showReadingGoals = true }
                    BookGoalRing("Yearly", stats.finishedThisYear, goals.yearlyBooks) { showYearBooks = true }
                }
                if (activeBook != null) {
                    TextButton(onClick = { onContinue(activeBook) }, modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)) {
                        Text("Resume · ${bookFormatDuration(activeLiveSeconds)}", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
                TextButton(
                    onClick = { showReadingGoals = true },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        if (activeBook == null) "Reading goals" else "Goals",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                    Icon(
                        painterResource(R.drawable.ic_chevron_right_24),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (periodOffset == 0 || selectedRange == BookStatsRange.ALL_TIME) when (selectedRange) {
                    BookStatsRange.WEEK -> "This week"
                    BookStatsRange.MONTH -> "This month"
                    BookStatsRange.YEAR -> "This year"
                    BookStatsRange.ALL_TIME -> "All time"
                } else "${periodStart.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))} – ${periodEnd.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))}",
                    modifier = Modifier.weight(1f), style = if (periodOffset == 0) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium, maxLines = 1)
                if (selectedRange != BookStatsRange.ALL_TIME) {
                    IconButton(onClick = { periodOffset++ }) { Icon(painterResource(R.drawable.ic_chevron_right_24), "Previous period", Modifier.size(22.dp).graphicsLayer(rotationZ = 180f)) }
                    IconButton(onClick = { periodOffset-- }, enabled = periodOffset > 0) { Icon(painterResource(R.drawable.ic_chevron_right_24), "Next period", Modifier.size(22.dp)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                BookStatsRange.entries.forEach { range ->
                    val selected = selectedRange == range
                    val shape = RoundedCornerShape(9.dp)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(shape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape
                            )
                            .fittedClickable(shape) { rangeName = range.name; periodOffset = 0 }
                    ) {
                        Text(
                            range.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
            BookLineChart(
                title = when {
                    selectedMetric == BookGraphMetric.PAGES && selectedRange == BookStatsRange.YEAR -> "Pages per month"
                    selectedMetric == BookGraphMetric.PAGES -> "Pages per day"
                    selectedRange == BookStatsRange.YEAR -> "Minutes per month"
                    else -> "Minutes per day"
                },
                points = rangePoints,
                anchorDate = periodEnd,
                range = selectedRange,
                metric = selectedMetric,
                onPointSelected = onDaySelected,
                metricSelector = {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BookGraphMetric.entries.forEach { metric ->
                            TextButton(onClick = { metricName = metric.name }) {
                                Text(
                                    metric.label,
                                    color = if (selectedMetric == metric) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (selectedMetric == metric) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            val totalValue = rangePoints.sumOf { it.pagesRead }
            val dailyAverage = if (rangePoints.isEmpty()) 0 else (totalValue.toFloat() / rangePoints.size.toFloat()).roundToInt()
            val bestDay = rangePoints.maxByOrNull { it.pagesRead }
            val unit = if (selectedMetric == BookGraphMetric.TIME) "m" else "p"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BookStatCell("Total", "$totalValue$unit", Modifier.weight(1f).fittedClickable { breakdownAllTime = false; breakdownMetric = selectedMetric })
                BookVerticalDivider()
                BookStatCell("Average", "$dailyAverage$unit", Modifier.weight(1f))
                BookVerticalDivider()
                BookStatCell(
                    "Best day",
                    if (bestDay == null || bestDay.pagesRead <= 0) "-" else "${bestDay.pagesRead}$unit",
                    Modifier.weight(1f),
                    supporting = bestDay?.date?.format(DateTimeFormatter.ofPattern("MMM d", Locale.US)).orEmpty()
                )
            }
        }
        // Reserve the same area for every range so fitting never changes the UI scale.
        Column(modifier = Modifier.height(139.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Reading heatmap", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    selectedHeatmapDate?.let { date ->
                        "${date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))}\n${selectedHeatmapPoint?.pagesRead ?: 0} ${if (selectedMetric == BookGraphMetric.PAGES) "pages" else "minutes"}"
                    }.orEmpty(),
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2
                )
            }
            ActivityHeatmap(
                range = when (selectedRange) {
                    BookStatsRange.WEEK -> StatsRange.WEEK
                    BookStatsRange.MONTH -> StatsRange.MONTH
                    BookStatsRange.YEAR -> StatsRange.YEAR
                    BookStatsRange.ALL_TIME -> StatsRange.ALL_TIME
                },
                points = if (selectedMetric == BookGraphMetric.PAGES) stats.dailyPageActivity else stats.dailyActivity,
                valueUnit = if (selectedMetric == BookGraphMetric.PAGES) "pages" else "minutes",
                onDaySelected = { point ->
                    if (selectedHeatmapDate == point.date) onOpenDaySessions(point.date)
                    else { selectedHeatmapDate = point.date; onDaySelected(point) }
                },
                compact = true,
                startOverride = periodStart,
                endOverride = periodEnd,
                showSelectionLabel = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
        }
        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Button(
                onClick = { showReadingProgress = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .requiredHeight(46.dp)
            ) {
                Text("Reading progress")
            }
            OutlinedButton(onClick = onOpenTrends, modifier = Modifier.fillMaxWidth().requiredHeight(46.dp)) { Text("Reading trends") }
        }
    }
    breakdownMetric?.let { metric ->
        val rows = if (breakdownAllTime) statsBooks.map { book ->
            book.title to if (metric == BookGraphMetric.PAGES) (book.currentPage.coerceAtLeast(0)).toLong() else book.readingSeconds
        } else sessions.filter { row ->
            val date = bookLocalDate(row.session.activityAt)
            !row.session.excludeFromStatistics && date != null && !date.isBefore(periodStart) && !date.isAfter(periodEnd)
        }.groupBy { it.session.bookId }.values.map { group ->
            group.first().bookTitle to group.sumOf { if (metric == BookGraphMetric.PAGES) it.session.pagesRead.toLong() else it.session.durationSeconds }
        }
        Dialog(onDismissRequest = { breakdownMetric = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxWidth().padding(16.dp).heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.78f)
                .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (metric == BookGraphMetric.PAGES) "Pages by book" else "Time by book", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { breakdownMetric = null }) { Text("Close") }
                }
                Text(if (breakdownAllTime) "All time" else "$periodStart – $periodEnd", style = MaterialTheme.typography.labelMedium)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val visible = rows.filter { it.second > 0 }.sortedByDescending { it.second }
                    if (visible.isEmpty()) item { Text("No reading in this period.") }
                    items(visible) { (title, value) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(if (metric == BookGraphMetric.PAGES) "$value pages" else bookFormatDuration(value), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
    if (showReadingProgress) {
        BookReadingProgressDialog(
            period = when (selectedRange) { BookStatsRange.WEEK -> "This week"; BookStatsRange.MONTH -> "This month"; BookStatsRange.YEAR -> "This year"; BookStatsRange.ALL_TIME -> "All time" },
            pagesRead = progressPages,
            totalMinutes = progressMinutes,
            pagesPerHour = pagesPerHour,
            booksRead = stats.finishedBooks,
            bestPageDay = bestPageDay,
            bestMinuteDay = bestMinuteDay,
            longestStreak = longestStreak,
            sessionCount = stats.readingSessionCount,
            quoteCount = stats.quoteCount,
            thoughtCount = stats.thoughtCount,
            wordCount = stats.wordCount,
            noteCount = stats.noteCount,
            onOpenGlobalNotes = { kind ->
                showReadingProgress = false
                onOpenGlobalNotes(kind)
            },
            onOpenGlobalSessions = {
                showReadingProgress = false
                onOpenGlobalSessions()
            },
            onDismiss = { showReadingProgress = false }
        )
    }
    if (showYearBooks) {
        Dialog(onDismissRequest = { showYearBooks = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxWidth().padding(16.dp).heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.78f)
                .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${LocalDate.now().year} · ${finishedYearBooks.size} books", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { showYearBooks = false }) { Text("Close") }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (finishedYearBooks.isEmpty()) item { Text("No finished books this year.") }
                    items(finishedYearBooks, key = { it.id }) { book ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(book.title, style = MaterialTheme.typography.titleSmall)
                            Text(runCatching { Instant.parse(book.finishedAt).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)) }.getOrDefault(book.finishedAt),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }
    if (showReadingGoals) {
        val today = LocalDate.now()
        BookReadingGoalsDialog(
            goals = goals,
            todayMinutes = stats.dailyActivity.firstOrNull { it.date == today }?.pagesRead ?: 0,
            todayPages = stats.dailyPageActivity.firstOrNull { it.date == today }?.pagesRead ?: 0,
            finishedThisYear = stats.finishedThisYear,
            monthMinutes = stats.dailyActivity.filter { it.date.year == today.year && it.date.month == today.month }.sumOf { it.pagesRead },
            monthPages = stats.dailyPageActivity.filter { it.date.year == today.year && it.date.month == today.month }.sumOf { it.pagesRead },
            onSave = { updated -> onSaveGoals(updated).also { if (it) showReadingGoals = false } },
            onDismiss = { showReadingGoals = false }
        )
    }
}
