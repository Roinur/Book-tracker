package com.roinur.booktracker

import androidx.compose.material3.Text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
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
    types: Map<String, BookCollectionType>,
    onAddExisting: (String) -> Unit,
    onRemove: (String) -> Unit,
    onEditType: (String) -> Unit,
    onCreate: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Collections", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("Hold to change type", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BookFadingRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(selected, key = { "selected_${it.lowercase(Locale.US)}" }) { collection ->
                BookCollectionTypeChip(
                    label = collectionTypeLabel(collection, types) + " ×",
                    selected = true,
                    onClick = { onRemove(collection) },
                    onLongClick = { onEditType(collection) }
                )
            }
            item("new_collection") {
                BookCollectionTypeChip("+", false, onCreate)
            }
        }
        val available = suggestions.filterNot { suggestion ->
            selected.any { it.equals(suggestion, ignoreCase = true) }
        }
        if (available.isNotEmpty()) {
            BookFadingRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(available, key = { "suggest_${it.lowercase(Locale.US)}" }) { collection ->
                    BookCollectionTypeChip(
                        label = collectionTypeLabel(collection, types),
                        selected = false,
                        onClick = { onAddExisting(collection) },
                        onLongClick = { onEditType(collection) }
                    )
                }
            }
        }
    }
}

private fun collectionTypeLabel(name: String, types: Map<String, BookCollectionType>): String {
    val type = types[name.lowercase(Locale.ROOT)] ?: BookCollectionType.OTHER
    return if (type == BookCollectionType.OTHER) name else "$name · ${type.label}"
}

@Composable
private fun BookCollectionTypeChip(label: String, selected: Boolean, onClick: () -> Unit,
                                   onLongClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(10.dp)
    val colors = MaterialTheme.colorScheme
    Row(Modifier.height(36.dp).clip(shape)
        .background(if (selected) colors.primaryContainer else colors.surfaceContainerLow)
        .border(1.dp, if (selected) colors.primary else colors.outline, shape)
        .fittedCombinedClickable(shape, onClick = onClick, onLongClick = onLongClick)
        .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onPrimaryContainer else colors.onSurface)
    }
}
