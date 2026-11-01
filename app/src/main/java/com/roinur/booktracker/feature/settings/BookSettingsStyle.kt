package com.roinur.booktracker

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Match the established Book List controls without changing buttons elsewhere in the app.
internal val BookSettingsButtonShape = RoundedCornerShape(8.dp)

@Composable
internal fun BookSettingsHeading(title: String, icon: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(painterResource(icon), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun BookSettingsAbout() {
    BookPanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Image(painterResource(R.drawable.app_icon), null, Modifier.size(64.dp).clip(BookSettingsButtonShape))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(APP_TITLE, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Your personal reading journal.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    BookPanel {
        BookSettingsHeading("App version", R.drawable.ic_info_24)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AboutVersionRow("Version", BuildConfig.VERSION_NAME)
        AboutVersionRow("Build", BuildConfig.VERSION_CODE.toString())
    }
}

@Composable
private fun AboutVersionRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
