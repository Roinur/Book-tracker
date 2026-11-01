package com.roinur.booktracker

import android.accounts.Account
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.roinur.booktracker.data.database.BookTrackerDatabase
import com.roinur.booktracker.data.markdown.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BookMarkdownExportSettings(allowed: Boolean) {
    val context = LocalContext.current
    val state = remember(context) { MarkdownExportState(context) }
    val client = remember(context) { Identity.getAuthorizationClient(context) }
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(state) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        state.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { state.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val enabled = remember(revision) { state.enabled }
    var pending by remember { mutableIntStateOf(0) }
    val queueDatabase = remember(context) { BookTrackerDatabase(context) }
    DisposableEffect(queueDatabase) { onDispose { queueDatabase.close() } }
    LaunchedEffect(state, queueDatabase) {
        while (true) {
            pending = withContext(Dispatchers.IO) { BookMarkdownOutbox(queueDatabase).count() }
            delay(1500)
        }
    }
    var busy by rememberSaveable { mutableStateOf(false) }
    var selectedAccount by rememberSaveable { mutableStateOf("") }
    var configuredFolder by rememberSaveable { mutableStateOf(false) }
    var candidate by remember { mutableStateOf<MarkdownFolder?>(null) }
    fun error(error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        state.status(if (error is com.google.android.gms.common.api.ApiException)
            "Google folder selection failed (${error.statusCode}). Check Drive/Picker API registration and Google Play services, then choose the folder again."
        else error.message ?: "Folder selection failed. Local notes and existing Drive files are unchanged.")
        busy = false
    }
    fun accept(result: AuthorizationResult) {
        val token = result.accessToken
        val ids = result.tokenResponseParams?.getString("picked_file_ids").orEmpty().split(',').filter { it.isNotBlank() }
        if (token.isNullOrBlank() || GoogleMarkdownTransport.SCOPE !in result.grantedScopes || ids.size != 1) {
            state.status("No single Drive folder was granted. Choose the existing Böcker/BookTracker folder; nothing was uploaded.")
            busy = false; return
        }
        if (configuredFolder && ids.single() != BuildConfig.MARKDOWN_EXPORT_FOLDER_HINT) {
            state.status("The selected folder differs from the configured vault. Nothing was changed."); busy = false; return
        }
        scope.launch {
            try {
                val folder = withContext(Dispatchers.IO) { GoogleMarkdownTransport(token).folder(ids.single()) }
                require(folder.name == "BookTracker") { "Choose the existing folder named BookTracker inside Böcker in your vault. No folder was created or changed." }
                candidate = folder
            } catch (exception: Exception) { error(exception) }
            finally { busy = false }
        }
    }
    val resolution = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) { busy = false; state.status("Folder selection cancelled; export settings are unchanged.") }
        else try { accept(client.getAuthorizationResultFromIntent(result.data)) } catch (exception: Exception) { error(exception) }
    }
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val account = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (result.resultCode != Activity.RESULT_OK || account.isNullOrBlank()) busy = false
        else {
            selectedAccount = account
            val request = AuthorizationRequest.builder().setAccount(Account(account, "com.google"))
                .setRequestedScopes(listOf(Scope(GoogleMarkdownTransport.SCOPE))).setOptOutIncludingGrantedScopes(true)
                .setPrompt(AuthorizationRequest.Prompt.CONSENT)
                .addResourceParameter(AuthorizationRequest.ResourceParameter.PICKER_OAUTH_TRIGGER, "true")
                .addResourceParameter(AuthorizationRequest.ResourceParameter.PICKER_MIMETYPES, "application/vnd.google-apps.folder")
                .addResourceParameter(AuthorizationRequest.ResourceParameter.PICKER_ALLOW_MULTIPLE, "false")
                .addResourceParameter(AuthorizationRequest.ResourceParameter.PICKER_ALLOW_FOLDER_SELECTION, "true")
                .apply { if (configuredFolder) addResourceParameter(AuthorizationRequest.ResourceParameter.PICKER_FILE_IDS, BuildConfig.MARKDOWN_EXPORT_FOLDER_HINT) }
                .build()
            client.authorize(request).addOnSuccessListener { authorization ->
                try {
                    if (authorization.hasResolution()) {
                        val intent = requireNotNull(authorization.pendingIntent) { "Google did not provide a folder picker." }
                        resolution.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                    } else accept(authorization)
                } catch (exception: Exception) { error(exception) }
            }.addOnFailureListener { error(it) }
        }
    }
    fun choose(configured: Boolean) {
        configuredFolder = configured; busy = true
        try { chooser.launch(AccountManager.newChooseAccountIntent(null, null, arrayOf("com.google"), null, null, null, null)) }
        catch (exception: Exception) { error(exception) }
    }
    fun queueAll() {
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { BookTrackerDatabase(context).use { BookMarkdownOutbox(it).allPending() } }
                state.status("Existing books queued. Waiting for confirmed upload to Drive.")
                BookMarkdownSync.enqueue(context)
            } catch (exception: Exception) { error(exception) }
            finally { busy = false }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BookSettingsHeading("Obsidian · Markdown export", R.drawable.ic_linked_notes_24)
        Text("One-way book notes to your existing vault. Independent of Drive backups; no Obsidian edits are imported.", style = MaterialTheme.typography.bodySmall)
        if (state.ready) {
            Text(state.account, style = MaterialTheme.typography.bodyMedium)
            Text("Target: ${state.label}", style = MaterialTheme.typography.bodySmall)
            TextButton(shape = BookSettingsButtonShape, onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://drive.google.com/drive/folders/${state.folder}"))) }) { Text("Open export folder") }
        } else Text("Expected: My Drive/Zettelkasten/Böcker/BookTracker", style = MaterialTheme.typography.bodySmall)
        if (BuildConfig.MARKDOWN_EXPORT_FOLDER_HINT.isNotBlank()) OutlinedButton(shape = BookSettingsButtonShape, onClick = { choose(true) }, enabled = allowed && !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Choose configured Zettelkasten folder")
        }
        OutlinedButton(shape = BookSettingsButtonShape, onClick = { choose(false) }, enabled = allowed && !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.ready) "Change folder / renew Google permission" else "Choose existing Drive export folder")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Automatic Markdown export", Modifier.weight(1f))
            Switch(checked = enabled, enabled = allowed && !busy && state.ready, onCheckedChange = { on ->
                scope.launch {
                    busy = true
                    try {
                        withContext(Dispatchers.IO) {
                            if (on) BookTrackerDatabase(context).use { BookMarkdownOutbox(it).allPending() }
                            state.enable(on)
                        }
                        BookMarkdownSync.schedule(context)
                        if (on) BookMarkdownSync.enqueue(context)
                    } catch (exception: Exception) { error(exception) }
                    finally { busy = false }
                }
            })
        }
        Text("Enabling uploads pending books, quotes and notes. Disabling stops future export and keeps all existing files.", style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Export on Wi-Fi / unmetered only", Modifier.weight(1f))
            Switch(checked = state.wifiOnly, enabled = allowed && !busy, onCheckedChange = {
                val wifi = it
                scope.launch {
                    try { withContext(Dispatchers.IO) { state.wifiOnly(wifi) }; BookMarkdownSync.reschedule(context) }
                    catch (exception: Exception) { error(exception) }
                }
            })
        }
        Text("Pending books: $pending${if (!enabled) " (export off)" else ""}", style = MaterialTheme.typography.bodySmall)
        Text("Last confirmed export: ${state.lastSuccess.ifBlank { "Never" }}", style = MaterialTheme.typography.bodySmall)
        val status = if (enabled && pending > 0 && state.status in listOf("All Markdown exports confirmed on Google Drive", "Up to date; no pending Markdown exports"))
            "Saved changes queued; waiting for network/background work" else state.status
        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(shape = BookSettingsButtonShape, onClick = ::queueAll, enabled = allowed && !busy && enabled, modifier = Modifier.weight(1f)) { Text("Export existing books") }
            OutlinedButton(shape = BookSettingsButtonShape, onClick = { state.status("Retry queued; waiting for network/background work"); BookMarkdownSync.enqueue(context) },
                enabled = allowed && !busy && enabled, modifier = Modifier.weight(1f)) { Text("Retry pending") }
        }
        Text("Only the marked BookTracker block is updated. Put your own notes and [[links]] outside it. Missing or conflicting files are left for review.", style = MaterialTheme.typography.bodySmall)
    }
    candidate?.let { folder ->
        AlertDialog(shape = RoundedCornerShape(10.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)), onDismissRequest = { candidate = null }, title = { Text("Use this existing export folder?") },
            text = { Column {
                Text("Account: $selectedAccount\nFolder: ${folder.name}\n\n${if (configuredFolder) "This is the documented Zettelkasten/Böcker/BookTracker folder." else "Confirm this is Böcker/BookTracker inside your existing vault. No new vault or folder will be created."}\n\nSelection alone does not upload. Automatic export will be off until you enable it.")
                TextButton(shape = BookSettingsButtonShape, onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://drive.google.com/drive/folders/${folder.id}"))) }) { Text("Check folder location in Drive") }
            } },
            confirmButton = { TextButton(shape = BookSettingsButtonShape, onClick = {
                val target = MarkdownTarget(selectedAccount, folder.id)
                scope.launch {
                    busy = true
                    try {
                        withContext(Dispatchers.IO) {
                            state.select(target, if (configuredFolder) "Zettelkasten/Böcker/BookTracker" else "BookTracker (selected existing Drive folder)")
                            BookTrackerDatabase(context).use { BookMarkdownOutbox(it).allPending() }
                        }
                        BookMarkdownSync.schedule(context); candidate = null
                    } catch (exception: Exception) { error(exception) }
                    finally { busy = false }
                }
            }, enabled = !busy) { Text("Use folder") } },
            dismissButton = { TextButton(shape = BookSettingsButtonShape, onClick = { candidate = null }) { Text("Cancel") } })
    }
}
