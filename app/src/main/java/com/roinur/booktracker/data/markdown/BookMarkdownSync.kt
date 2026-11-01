package com.roinur.booktracker.data.markdown

import android.accounts.Account
import android.content.Context
import androidx.work.*
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.roinur.booktracker.data.database.BookTrackerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.io.IOException
import kotlin.concurrent.withLock

/** Installation-specific state, deliberately excluded from portable library backups. No tokens are saved. */
internal class MarkdownExportState(context: Context) : MarkdownBindings {
    val preferences = context.applicationContext.getSharedPreferences("book_markdown_export", Context.MODE_PRIVATE)
    val enabled get() = preferences.getBoolean("enabled", false)
    val account get() = preferences.getString("account", "").orEmpty()
    val folder get() = preferences.getString("folder", "").orEmpty()
    val label get() = preferences.getString("label", "").orEmpty()
    val epoch get() = preferences.getString("epoch", "").orEmpty()
    val status get() = preferences.getString("status", "Not configured. Choose the existing export folder in your vault.").orEmpty()
    val lastSuccess get() = preferences.getString("last_success", "").orEmpty()
    val wifiOnly get() = preferences.getBoolean("wifi_only", false)
    val target get() = MarkdownTarget(account, folder)
    val ready get() = account.isNotBlank() && folder.isNotBlank()
    fun status(value: String) { preferences.edit().putString("status", value).apply() }
    fun select(target: MarkdownTarget, label: String) {
        check(preferences.edit().putBoolean("enabled", false).putString("account", target.account)
            .putString("folder", target.folder).putString("label", label).putString("epoch", UUID.randomUUID().toString())
            .remove("last_success").putString("status", "Folder selected. Automatic Markdown export is off.").commit())
    }
    fun enable(enabled: Boolean) {
        check(!enabled || ready) { "Choose an existing export folder first." }
        check(preferences.edit().putBoolean("enabled", enabled).putString("epoch", UUID.randomUUID().toString())
            .putString("status", if (enabled) "Export queued; waiting for network/background work" else "Export paused. Existing Drive files are kept.").commit())
    }
    fun wifiOnly(value: Boolean) {
        check(preferences.edit().putBoolean("wifi_only", value).putString("epoch", UUID.randomUUID().toString()).commit())
    }
    private fun key(target: MarkdownTarget, identity: String) = "file_" + MessageDigest.getInstance("SHA-256")
        .digest("${target.account}\u0000${target.folder}\u0000$identity".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    override fun binding(target: MarkdownTarget, identity: String): MarkdownBinding? {
        val value = preferences.getString(key(target, identity), null) ?: return null
        return MarkdownBinding(value.substringAfter(':'), value.startsWith("confirmed:"))
    }
    override fun bind(target: MarkdownTarget, identity: String, binding: MarkdownBinding) {
        check(preferences.edit().putString(key(target, identity), (if (binding.confirmed) "confirmed:" else "reserved:") + binding.fileId).commit()) {
            "Could not durably save the export file identity. No new file will be created without it."
        }
    }
    fun confirmed() { check(preferences.edit().putString("last_success", Instant.now().toString()).commit()) }
}

internal object BookMarkdownSync {
    private const val PERIODIC = "book-markdown-periodic"
    private const val PENDING = "book-markdown-pending"
    val lock = ReentrantLock()
    private fun constraints(state: MarkdownExportState) = Constraints.Builder()
        .setRequiredNetworkType(if (state.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build()
    fun schedule(context: Context) {
        val state = MarkdownExportState(context); val work = WorkManager.getInstance(context)
        if (!state.enabled || !state.ready) { work.cancelUniqueWork(PERIODIC); work.cancelUniqueWork(PENDING); return }
        work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<BookMarkdownWorker>(15, TimeUnit.MINUTES).setConstraints(constraints(state)).build())
    }
    fun enqueue(context: Context) {
        val state = MarkdownExportState(context)
        if (!state.enabled || !state.ready) return
        // SQLite is the durable queue. KEEP coalesces typing/autosaves without interrupting an upload.
        runCatching { WorkManager.getInstance(context).enqueueUniqueWork(PENDING, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<BookMarkdownWorker>().setConstraints(constraints(state)).setInitialDelay(10, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()) }
            .onFailure { state.status("Notes are saved locally. Background export could not be scheduled; reopen Settings and retry.") }
    }
    fun reschedule(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PENDING)
        schedule(context); enqueue(context)
    }
    fun authorize(context: Context, account: String): String {
        val request = AuthorizationRequest.builder().setAccount(Account(account, "com.google"))
            .setRequestedScopes(listOf(Scope(GoogleMarkdownTransport.SCOPE))).setOptOutIncludingGrantedScopes(true).build()
        val result = Tasks.await(Identity.getAuthorizationClient(context).authorize(request), 40, TimeUnit.SECONDS)
        check(!result.hasResolution()) { "Choose the Markdown export folder again to renew Google permission. Local notes are safe." }
        return requireNotNull(result.accessToken?.takeIf { it.isNotBlank() }) { "Google did not grant Markdown export permission." }
    }
}

class BookMarkdownWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val state = MarkdownExportState(applicationContext)
        if (!state.enabled || !state.ready) return@withContext Result.success()
        val target = state.target; val epoch = state.epoch; val job = coroutineContext
        fun active() { job.ensureActive(); check(state.enabled && state.target == target && state.epoch == epoch) { "Export settings changed; queued files were left untouched." } }
        try {
            BookMarkdownSync.lock.withLock {
                active()
                BookTrackerDatabase(applicationContext).use { database ->
                    val queue = BookMarkdownOutbox(database)
                    if (queue.count() == 0) { state.status("Up to date; no pending Markdown exports"); return@withContext Result.success() }
                    queue.prepare()
                    val engine = BookMarkdownEngine(GoogleMarkdownTransport(BookMarkdownSync.authorize(applicationContext, target.account)), state)
                    var after = 0; var failures = 0; var retry = false; var firstError = ""
                    while (true) {
                        active()
                        val item = queue.next(after) ?: break
                        after = item.bookId
                        state.status("Exporting ${BookMarkdownDocument.filename(item.identity)}; ${queue.count()} pending")
                        try {
                            engine.export(target, item) { active(); if (!queue.current(item)) throw IOException("The local book changed during export; the latest version remains queued.") }
                            active(); queue.acknowledge(item); state.confirmed()
                        } catch (error: Exception) {
                            if (error is kotlinx.coroutines.CancellationException) throw error
                            active(); failures++
                            if (firstError.isEmpty()) firstError = "${BookMarkdownDocument.filename(item.identity)}: ${error.message ?: "Export failed"}"
                            retry = retry || (error is IOException && (error !is MarkdownHttpError || error.code == 429 || error.code >= 500))
                        }
                    }
                    val pending = queue.count()
                    state.status(if (failures > 0) "$pending pending. $firstError" else if (pending > 0) "$pending pending; newer changes will be exported next" else "All Markdown exports confirmed on Google Drive")
                    if (retry || (pending > 0 && failures == 0)) Result.retry() else if (failures > 0) Result.failure() else Result.success()
                }
            }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (state.enabled && state.target == target && state.epoch == epoch) state.status(error.message ?: "Export failed; local notes and existing files are kept.")
            if ((error is IOException && (error !is MarkdownHttpError || error.code == 429 || error.code >= 500)) || error is java.util.concurrent.TimeoutException ||
                (error as? java.util.concurrent.ExecutionException)?.cause?.let { (it as? com.google.android.gms.common.api.ApiException)?.statusCode in listOf(7, 8, 15) } == true)
                Result.retry() else Result.failure()
        }
    }
}
