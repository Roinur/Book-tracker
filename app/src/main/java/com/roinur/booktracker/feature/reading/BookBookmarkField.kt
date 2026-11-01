package com.roinur.booktracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Composable
internal fun bookBookmarkColor(): Color = if (MaterialTheme.colorScheme.surface.luminance() < .4f) Color(0xFF62C98B) else Color(0xFF17663A)

@Composable
internal fun BookBookmarkField(checked: Boolean, page: String, onChecked: (Boolean) -> Unit, onPage: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.weight(1f).fittedClickable(RoundedCornerShape(8.dp)) { onChecked(!checked) }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = null)
            Text("Continue from page", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
        }
        if (checked) OutlinedTextField(page, { onPage(it.filter(Char::isDigit).take(6)) },
            label = { Text("Bookmark") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = page.toIntOrNull() == null, modifier = Modifier.width(100.dp))
    }
}
