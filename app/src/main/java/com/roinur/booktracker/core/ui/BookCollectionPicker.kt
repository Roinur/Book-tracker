package com.roinur.booktracker

import androidx.compose.material3.Text

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun BookFadingRow(
    horizontalArrangement: Arrangement.Horizontal,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    val state = rememberLazyListState()
    val background = MaterialTheme.colorScheme.background
    LazyRow(state = state, horizontalArrangement = horizontalArrangement,
        modifier = Modifier.fillMaxWidth().drawWithContent {
            drawContent()
            val width = 22.dp.toPx().coerceAtMost(size.width / 4f)
            if (state.canScrollBackward) drawRect(
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(background, background.copy(alpha = 0f)), 0f, width),
                size = androidx.compose.ui.geometry.Size(width, size.height))
            if (state.canScrollForward) drawRect(
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(background.copy(alpha = 0f), background), size.width - width, size.width),
                topLeft = androidx.compose.ui.geometry.Offset(size.width - width, 0f),
                size = androidx.compose.ui.geometry.Size(width, size.height))
        }, content = content)
}

@Composable
internal fun BookCollectionPicker(
    selected: List<String>,
    suggestions: List<String>,
    onAddExisting: (String) -> Unit,
    onRemove: (String) -> Unit,
    onCreate: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Collections", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        BookFadingRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(selected, key = { "selected_${it.lowercase(Locale.US)}" }) { collection ->
                FilterChip(
                    selected = true,
                    onClick = { onRemove(collection) },
                    label = { Text("$collection ×") }
                )
            }
            item("new_collection") {
                FilterChip(
                    selected = false,
                    onClick = onCreate,
                    label = { Text("+") }
                )
            }
        }
        val available = suggestions.filterNot { suggestion ->
            selected.any { it.equals(suggestion, ignoreCase = true) }
        }
        if (available.isNotEmpty()) {
            BookFadingRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(available, key = { "suggest_${it.lowercase(Locale.US)}" }) { collection ->
                    FilterChip(
                        selected = false,
                        onClick = { onAddExisting(collection) },
                        label = { Text(collection) }
                    )
                }
            }
        }
    }
}
