package com.roinur.booktracker

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.roinur.booktracker.data.backup.drive.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.withLock

private fun driveLocalDate(value: String): String = runCatching {
    java.time.Instant.parse(value).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm"))
}.getOrDefault(value)

@Composable
internal fun BookDriveBackupSettings(allowed: Boolean, onRestore: (String) -> Unit) {
    val context = LocalContext.current
    val state = remember(context) { DriveBackupState(context) }
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(state) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        state.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { state.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val account = remember(revision) { state.account }
    val enabled = remember(revision) { state.enabled }
    var busy by remember { mutableStateOf(false) }
    var files by remember { mutableStateOf<List<DriveBackupFile>>(emptyList()) }
    var showBackups by rememberSaveable { mutableStateOf(false) }
    var confirmWriter by remember { mutableStateOf(false) }
    var selectedAccount by rememberSaveable { mutableStateOf("") }
    val client = remember(context) { Identity.getAuthorizationClient(context) }

    fun error(error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        state.status(if (error is com.google.android.gms.common.api.ApiException) {
            "Google authorization failed (${error.statusCode}). Register this app's package/SHA-1 in Google Cloud, enable Drive API and add your test account."
        } else error.message ?: "Drive operation failed; existing backups are unchanged.")
        busy = false
    }

    fun accept(result: AuthorizationResult) {
        val token = result.accessToken
        if (token.isNullOrBlank() || GoogleDriveTransport.SCOPE !in result.grantedScopes) {
            state.status("Drive permission was not granted. No data uploaded."); busy = false; return
        }
        scope.launch {
            try {
                val discovered = withContext(Dispatchers.IO) { GoogleDriveTransport(token, context.packageName).listVerified() }
                // Connecting never uploads or enables automatic backup, especially on a new phone.
                if (state.account != selectedAccount) {
                    state.preferences.edit().putBoolean("enabled", false).remove("fingerprint").remove("last_success").remove("latest_id").apply()
                    DriveBackupSync.syncSchedule(context)
                }
                state.preferences.edit().putString("account", selectedAccount).apply()
                files = discovered
                state.status(if (discovered.isEmpty()) "Connected. Enable automatic backup to protect your library."
                    else "Connected. ${discovered.size} verified backups found. Restore before enabling uploads on a new phone.")
            } catch (exception: Exception) { error(exception) }
            finally { busy = false }
        }
    }

    val resolution = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) { state.status("Google connection cancelled; no data uploaded."); busy = false }
        else try { accept(client.getAuthorizationResultFromIntent(result.data)) } catch (exception: Exception) { error(exception) }
    }
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val name = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (result.resultCode != Activity.RESULT_OK || name.isNullOrBlank()) { busy = false }
        else {
            selectedAccount = name
            val request = AuthorizationRequest.builder().setAccount(Account(name, "com.google"))
                .setRequestedScopes(listOf(Scope(GoogleDriveTransport.SCOPE))).build()
            client.authorize(request).addOnSuccessListener { authorization ->
                try {
                if (authorization.hasResolution()) authorization.pendingIntent?.let {
                    resolution.launch(IntentSenderRequest.Builder(it.intentSender).build())
                } ?: run { busy = false; state.status("Google did not provide an authorization dialog.") }
                else accept(authorization)
                } catch (exception: Exception) { error(exception) }
            }.addOnFailureListener { error(it) }
        }
    }

    fun perform(action: (GoogleDriveTransport, String) -> Unit) {
        val target = state.account
        busy = true
        scope.launch {
            try { withContext(Dispatchers.IO) {
                val token = DriveBackupSync.authorize(context, target)
                check(state.account == target) { "Connected account changed." }
                action(GoogleDriveTransport(token, context.packageName), token)
            } } catch (exception: Exception) { error(exception) }
            finally { busy = false }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Google Drive", style = MaterialTheme.typography.titleMedium)
        Text("Books, sessions, notes, collections, goals and covers.", style = MaterialTheme.typography.bodySmall)
        if (allowed && account.isNotBlank()) Text(account, style = MaterialTheme.typography.bodyMedium)
        Text(if (allowed) state.status else "Another backup or import is in progress.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (allowed) Text("Last verified upload: ${state.lastSuccess.takeIf { it.isNotBlank() }?.let(::driveLocalDate) ?: "Never"}", style = MaterialTheme.typography.bodySmall)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)))
        OutlinedButton(onClick = {
            busy = true
            try { chooser.launch(AccountManager.newChooseAccountIntent(null, null, arrayOf("com.google"), null, null, null, null)) }
            catch (exception: Exception) { error(exception) }
        }, enabled = allowed && !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (account.isBlank()) "Connect Google Drive" else "Reconnect / change Google account")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Automatic backup", Modifier.weight(1f))
            Switch(checked = enabled, enabled = allowed && !busy && account.isNotBlank(), onCheckedChange = { on ->
                if (on) confirmWriter = true
                else { state.preferences.edit().putBoolean("enabled", false).apply(); DriveBackupSync.syncSchedule(context); state.status("Automatic backup paused") }
            })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Wi-Fi / unmetered network only", Modifier.weight(1f))
            Switch(checked = state.wifiOnly, enabled = allowed && !busy, onCheckedChange = {
                state.preferences.edit().putBoolean("wifi_only", it).apply(); DriveBackupSync.syncSchedule(context)
                if (state.enabled) DriveBackupSync.enqueue(context)
            })
        }
        Button(onClick = { perform { _, token -> DriveBackupSync.upload(context, token, account, force = true) } },
            enabled = allowed && !busy && enabled, modifier = Modifier.fillMaxWidth()) { Text("Back up now") }
        OutlinedButton(onClick = { perform { transport, _ ->
            val discovered = transport.listVerified()
            scope.launch { files = discovered; showBackups = true }
        } }, enabled = allowed && !busy && account.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Restore from Google Drive") }
        if (account.isNotBlank()) TextButton(onClick = {
            state.preferences.edit().clear().apply(); DriveBackupSync.syncSchedule(context); files = emptyList()
        }, enabled = allowed && !busy) { Text("Disconnect (keep cloud backups)") }
        Text("Keeps five verified copies in Book Tracker Backups. Files are readable to anyone you give access to in Drive.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (confirmWriter && allowed) AlertDialog(shape = RoundedCornerShape(8.dp), onDismissRequest = { confirmWriter = false }, title = { Text("Use this phone as backup source?") },
        text = { Text("This uploads this phone's library. If you are recovering a lost phone, restore first. Existing verified versions are retained; connecting alone never overwrites them.") },
        confirmButton = { TextButton(onClick = {
            confirmWriter = false
            perform { transport, _ ->
                DriveBackupSync.lock.withLock {
                    val latest = transport.listVerified().firstOrNull()?.id.orEmpty()
                    check(state.preferences.edit().putString("latest_id", latest).remove("fingerprint").putBoolean("enabled", true).commit()) { "Could not save Drive settings." }
                    DriveBackupSync.syncSchedule(context); DriveBackupSync.enqueue(context)
                }
            }
        }, enabled = allowed && !busy) { Text("Enable backup") } }, dismissButton = { TextButton(onClick = { confirmWriter = false }) { Text("Cancel") } })

    if (showBackups && allowed) AlertDialog(shape = RoundedCornerShape(8.dp), onDismissRequest = { showBackups = false }, title = { Text("Verified cloud backups") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (files.isEmpty()) Text("No verified backup found for this app and Google account.")
            files.forEach { file -> OutlinedButton(enabled = allowed && !busy, onClick = {
                showBackups = false
                perform { transport, _ ->
                    val text = transport.download(file.id)
                    val decoded = BookCloudBackup.decode(text)
                    scope.launch { onRestore(decoded.payload) }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(driveLocalDate(file.createdAt)) } }
        } }, confirmButton = { TextButton(onClick = { showBackups = false }) { Text("Close") } })
}
