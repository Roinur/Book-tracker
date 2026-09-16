package com.roinur.booktracker

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun BookDisclosureHeader(title: String, icon: Int?, expanded: Boolean, count: String? = null, onClick: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, tween(160), label = "disclosure")
    Row(Modifier.fillMaxWidth().fittedClickable(RoundedCornerShape(8.dp), onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (icon != null) Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (count != null) Text(count, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(painterResource(R.drawable.ic_chevron_right_24), if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp).rotate(rotation))
    }
}
