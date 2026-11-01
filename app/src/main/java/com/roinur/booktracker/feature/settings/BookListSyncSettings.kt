package com.roinur.booktracker

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roinur.booktracker.data.booklist.*
import com.roinur.booktracker.data.backup.drive.DriveBackupState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.concurrent.withLock

@Composable
internal fun BookListSyncSettings(allowed: Boolean, onChanged: () -> Unit) {
    val context = LocalContext.current
    val store = remember { BookListStore(context) }
    val drive = remember { DriveBackupState(context) }
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(store, drive) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        drive.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { store.preferences.unregisterOnSharedPreferenceChangeListener(listener); drive.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var busy by remember { mutableStateOf(false) }
    var createConfirmation by remember { mutableStateOf(false) }
    var unlinkConfirmation by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf(false) }
    val account = remember(revision) { drive.account }
    val ready = remember(revision) { store.ready }
    val enabled = remember(revision) { store.enabled }
    val conflicts = remember(revision) { store.conflicts().filter { it.choiceKey !in store.choices() } }
    val wifiOnly = remember(revision) { drive.wifiOnly }
    LaunchedEffect(account, wifiOnly) { BookListSync.schedule(context) }
    fun perform(create: Boolean = false) {
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { BookListSync.run(context, create) }
                BookListSync.schedule(context)
                onChanged()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                store.status(error.message ?: "Book List sync failed. Your library is retained.")
            } finally { busy = false }
        }
    }
    val available = allowed && !busy && account.isNotBlank() && (store.account.isBlank() || account == store.account)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(R.drawable.ic_list_24), null, tint = MaterialTheme.colorScheme.primary)
            Text("Book List", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Text("A personal Google Sheet linked to your library.", style = MaterialTheme.typography.bodyMedium)
        Text(when {
            account.isBlank() -> "Connect your account in Settings → Google Drive backup to create your list."
            store.account.isNotBlank() && store.account != account -> "Reconnect ${store.account} to use this list."
            else -> store.status
        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!ready) {
            Button(onClick = { createConfirmation = true }, enabled = available, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Text(if (store.fileId.isBlank()) "Create new Book List" else "Finish Book List setup")
            }
            Text("Creates a separate spreadsheet. Your existing lists are untouched.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Two-way sync", Modifier.weight(1f))
                Switch(enabled = available, checked = enabled, onCheckedChange = {
                    store.preferences.edit().putBoolean("enabled", it).apply(); BookListSync.schedule(context)
                    if (it) BookListSync.enqueue(context)
                })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.google.com/spreadsheets/d/${store.fileId}/edit"))) }
                        .onFailure { store.status("No app available to open Google Sheets.") }
                }, enabled = allowed && !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("Open list") }
                Button(onClick = { perform() }, enabled = available, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("Sync now") }
            }
            if (store.lastSuccess.isNotBlank()) Text("Last synced: " + runCatching {
                Instant.parse(store.lastSuccess).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d · HH:mm"))
            }.getOrDefault(store.lastSuccess), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (conflicts.isNotEmpty()) OutlinedButton(onClick = { review = true }, enabled = available, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Text("Review ${conflicts.size} conflicting ${if(conflicts.size == 1) "field" else "fields"}")
            }
            Text("Syncs after app edits, when you open the app and periodically in the background. Sheet-only reading totals do not create dated sessions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (store.fileId.isNotBlank()) TextButton(shape = BookSettingsButtonShape, onClick = { unlinkConfirmation = true }, enabled = allowed && !busy) { Text("Unlink (keep list and library)") }
    }
    val shape = RoundedCornerShape(10.dp)
    if (createConfirmation) AlertDialog(onDismissRequest = { createConfirmation = false }, shape = shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        title = { Text(if (store.fileId.isBlank()) "Create linked Book List?" else "Finish setup?") },
        text = { Text("Creates one Book List tab with your library, reading status, weight and finished books. Changes sync both ways. Other spreadsheets are never linked or edited.") },
        confirmButton = { TextButton(shape = BookSettingsButtonShape, onClick = { createConfirmation = false; perform(true) }, enabled = available) { Text("Create and link") } },
        dismissButton = { TextButton(shape = BookSettingsButtonShape, onClick = { createConfirmation = false }) { Text("Cancel") } })
    if (unlinkConfirmation) AlertDialog(onDismissRequest = { unlinkConfirmation = false }, shape = shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        title = { Text("Unlink Book List?") }, text = { Text("Stops syncing. The spreadsheet in Drive and every book in the app are kept.") },
        confirmButton = { TextButton(shape = BookSettingsButtonShape, onClick = {
            busy = true; unlinkConfirmation = false
            scope.launch { withContext(Dispatchers.IO) { BookListSync.lock.withLock { store.preferences.edit().clear().commit() } }; BookListSync.schedule(context); busy = false }
        }) { Text("Unlink") } }, dismissButton = { TextButton(shape = BookSettingsButtonShape, onClick = { unlinkConfirmation = false }) { Text("Cancel") } })
    val conflict = conflicts.firstOrNull()
    if (review && conflict != null) AlertDialog(onDismissRequest = { review = false }, shape = shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        title = { Text(conflict.title, maxLines = 2) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(conflict.field, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text("App", style = MaterialTheme.typography.labelMedium)
            Text(conflict.appValue.ifBlank { "Empty" })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Book List", style = MaterialTheme.typography.labelMedium)
            Text(conflict.sheetValue.ifBlank { "Empty" })
            Text("Choose which value to keep.", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(shape = BookSettingsButtonShape, onClick = {
            store.choose(conflict, true)
            if(conflicts.size == 1) { review = false; perform() }
        }) { Text("Keep Book List") } },
        dismissButton = { TextButton(shape = BookSettingsButtonShape, onClick = {
            store.choose(conflict, false)
            if(conflicts.size == 1) { review = false; perform() }
        }) { Text("Keep app") } })
}
