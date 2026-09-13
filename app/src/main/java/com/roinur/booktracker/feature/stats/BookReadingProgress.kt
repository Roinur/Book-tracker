package com.roinur.booktracker

import androidx.compose.material3.Text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun BookReadingProgressDialog(
    period: String,
    pagesRead: Int,
    totalMinutes: Int,
    pagesPerHour: Float,
    booksRead: Int,
    bestPageDay: Int,
    bestMinuteDay: Int,
    longestStreak: Int,
    sessionCount: Int,
    quoteCount: Int,
    thoughtCount: Int,
    wordCount: Int,
    noteCount: Int,
    onOpenGlobalNotes: (BookNoteKind) -> Unit,
    onOpenGlobalSessions: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.84f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Reading progress", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                BookReadingProgressPanel(
                    period = period,
                    pagesRead = pagesRead,
                    totalMinutes = totalMinutes,
                    pagesPerHour = pagesPerHour,
                    booksRead = booksRead,
                    bestPageDay = bestPageDay,
                    bestMinuteDay = bestMinuteDay,
                    longestStreak = longestStreak,
                    sessionCount = sessionCount,
                    quoteCount = quoteCount,
                    thoughtCount = thoughtCount,
                    wordCount = wordCount,
                    noteCount = noteCount,
                    onOpenGlobalNotes = onOpenGlobalNotes,
                    onOpenGlobalSessions = onOpenGlobalSessions
                )
            }
        }
    }
}

@Composable
internal fun BookReadingProgressPanel(
    period: String,
    pagesRead: Int,
    totalMinutes: Int,
    pagesPerHour: Float,
    booksRead: Int,
    bestPageDay: Int,
    bestMinuteDay: Int,
    longestStreak: Int,
    sessionCount: Int,
    quoteCount: Int,
    thoughtCount: Int,
    wordCount: Int,
    noteCount: Int,
    onOpenGlobalNotes: (BookNoteKind) -> Unit,
    onOpenGlobalSessions: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(period, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BookProgressNumber("Pages", pagesRead.toString(), Modifier.weight(1f))
            BookProgressNumber("Read time", bookFormatStatsReadTime(totalMinutes * 60L), Modifier.weight(1f))
            BookProgressNumber("Pages/h", if (pagesPerHour > 0f) String.format(Locale.US, "%.1f", pagesPerHour) else "–", Modifier.weight(1f))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        BookProgressMetricRow("pages", "Most pages in a day", bestPageDay.toString(), "pages")
        BookProgressMetricRow("time", "Most time in a day", bestMinuteDay.toString(), "min")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Text("All time", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BookProgressMetricRow("library", "Books finished", booksRead.toString(), "")
        BookProgressMetricRow("streak", "Longest streak", longestStreak.toString(), "days")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BookNoteStatTile(BookNoteKind.NOTE, noteCount, { onOpenGlobalNotes(BookNoteKind.NOTE) }, Modifier.weight(1f))
            BookNoteStatTile(BookNoteKind.QUOTE, quoteCount, { onOpenGlobalNotes(BookNoteKind.QUOTE) }, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BookNoteStatTile(BookNoteKind.THOUGHT, thoughtCount, { onOpenGlobalNotes(BookNoteKind.THOUGHT) }, Modifier.weight(1f))
            BookNoteStatTile(BookNoteKind.WORD, wordCount, { onOpenGlobalNotes(BookNoteKind.WORD) }, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().fittedClickable(RoundedCornerShape(8.dp), onClick = onOpenGlobalSessions)
            .padding(vertical = 12.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BookMiniIcon("stats", MaterialTheme.colorScheme.primary, Modifier.size(22.dp))
            Text("Sessions", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(sessionCount.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Icon(painterResource(R.drawable.ic_chevron_right_24), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun BookProgressNumber(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun BookProgressMetricRow(icon: String, label: String, value: String, unit: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BookMiniIcon(icon, MaterialTheme.colorScheme.primary, Modifier.size(22.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(listOf(value, unit).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun BookNoteStatTile(kind: BookNoteKind, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Row(modifier.heightIn(min = 64.dp).clip(shape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f))
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), shape)
        .fittedClickable(shape, onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BookReadingActionIcon(kind, MaterialTheme.colorScheme.primary, Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(kind.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(painterResource(R.drawable.ic_chevron_right_24), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun BookGoalRing(label: String, current: Int, target: Int, onClick: () -> Unit) {
    CircularProgressIndicator(progress = { if (target > 0) (current.toFloat() / target).coerceIn(0f, 1f) else 0f },
        modifier = Modifier.size(25.dp).fittedClickable(CircleShape, onClick = onClick).semantics { contentDescription = "$label goal" },
        strokeWidth = 3.dp, color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}
