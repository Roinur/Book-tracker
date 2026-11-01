package com.roinur.booktracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.roinur.booktracker.BookFitText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

@Composable
internal fun BookStatsPeriodSelector(selectedRange: BookStatsRange, onSelect: (BookStatsRange) -> Unit,
    onRecap: (BookStatsRange) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        BookStatsRange.entries.forEach { range ->
            val selected = selectedRange == range
            val shape = RoundedCornerShape(9.dp)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).height(40.dp)
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, shape)
                .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
                .semantics { this.selected = selected }
                .fittedCombinedClickable(shape, onClick = { onSelect(range) }, onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRecap(range)
                },
                    onLongClickLabel = "Show ${range.label.lowercase()} reading recap")) {
                Text(range.label, style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1)
            }
        }
    }
}
