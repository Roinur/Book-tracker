package com.roinur.booktracker

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun BookListFieldsDialog(ownership: String, priority: Int, onDismiss: () -> Unit, onSave: (String, Int) -> Unit, finalRating: Int? = null) {
    var weight by rememberSaveable { mutableStateOf((finalRating?.takeIf { it>0 }?.toDouble() ?: if(finalRating==null) priority.takeIf { it>0 }?.div(2.0) else null)?.toString().orEmpty()) }
    val value=weight.replace(',','.').toDoubleOrNull()
    val valid=weight.isBlank() || value != null && value in 0.0..5.0 && value*2 == kotlin.math.floor(value*2)
    val shape = RoundedCornerShape(10.dp)
    AlertDialog(onDismissRequest = onDismiss, shape = shape, containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp, modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        title = { Text("Weight") },
        text = { OutlinedTextField(weight, { weight = it.filter { c -> c.isDigit() || c=='.' || c==',' }.take(3) }, label = { Text("Weight / 5") },
            supportingText = { Text(if(finalRating!=null) "Final rating from Book details." else "Expected rating. Replaced by your final rating.") },
            readOnly = finalRating!=null, isError = !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = RoundedCornerShape(8.dp)) },
        confirmButton = { TextButton(onClick = { onSave(ownership, if(finalRating!=null) priority else ((value ?: 0.0)*2).toInt()) },
            enabled = valid) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
