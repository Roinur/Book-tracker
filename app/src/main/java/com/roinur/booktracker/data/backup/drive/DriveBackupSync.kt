package com.roinur.booktracker.data.backup.drive

import android.accounts.Account
import android.content.Context
import androidx.work.*
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.roinur.booktracker.data.backup.BookBackupService
import com.roinur.booktracker.data.database.BookTrackerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class DriveBackupState(context: Context) {
    // Installation-specific grants and account state must never be included in portable backups.
    val preferences = context.applicationContext.getSharedPreferences("book_drive_backup", Context.MODE_PRIVATE)
    val account: String get() = preferences.getString("account", "").orEmpty()
    val enabled: Boolean get() = preferences.getBoolean("enabled", false)
    val wifiOnly: Boolean get() = preferences.getBoolean("wifi_only", true)
    val status: String get() = preferences.getString("status", "Not connected").orEmpty()
    val lastSuccess: String get() = preferences.getString("last_success", "").orEmpty()
    val lastFingerprint: String get() = preferences.getString("fingerprint", "").orEmpty()
    val expectedLatestId: String get() = preferences.getString("latest_id", "").orEmpty()
    fun status(text: String) { preferences.edit().putString("status", text).apply() }
}

internal object DriveBackupSync {
    val lock = ReentrantLock()
    private const val PERIODIC = "book-drive-periodic"
    private const val PENDING = "book-drive-pending"

    fun syncSchedule(context: Context) {
        val state = DriveBackupState(context)
        val manager = WorkManager.getInstance(context)
        if (!state.enabled || state.account.isBlank()) {
            manager.cancelUniqueWork(PERIODIC); manager.cancelUniqueWork(PENDING)
            return
        }
        manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<BookDriveBackupWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints(state)).build())
    }

    fun enqueue(context: Context) {
        val state = DriveBackupState(context)
        if (!state.enabled || state.account.isBlank()) return
        state.status("Changes queued; waiting for network/background work")
        WorkManager.getInstance(context).enqueueUniqueWork(PENDING, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<BookDriveBackupWorker>().setConstraints(constraints(state))
                .setInitialDelay(15, TimeUnit.SECONDS).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }

    private fun constraints(state: DriveBackupState) = Constraints.Builder()
        .setRequiredNetworkType(if (state.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build()

    /** IO only; access tokens are never stored or logged. */
    fun authorize(context: Context, account: String): String {
        val request = AuthorizationRequest.builder().setAccount(Account(account, "com.google"))
            .setRequestedScopes(listOf(Scope(GoogleDriveTransport.SCOPE))).build()
        val result = Tasks.await(Identity.getAuthorizationClient(context).authorize(request), 40, TimeUnit.SECONDS)
        check(!result.hasResolution()) { "Open Settings → Google Drive and reconnect to renew permission." }
        return requireNotNull(result.accessToken?.takeIf(String::isNotBlank)) { "Google did not grant Drive access." }
    }

    fun upload(context: Context, token: String, expectedAccount: String, force: Boolean = false): DriveBackupFile? = lock.withLock {
        val state = DriveBackupState(context)
        check(state.enabled && state.account == expectedAccount) { "Drive backup is paused or the connected account changed." }
        state.status("Preparing library backup…")
        val snapshot = BookTrackerDatabase(context.applicationContext).use { BookBackupService(it, context).exportBackupJson() }
        val fingerprint = BookCloudBackup.fingerprint(snapshot)
        if (!force && fingerprint == state.lastFingerprint) {
            state.status("Up to date; no changes to upload")
            return null
        }
        val text = BookCloudBackup.encode(snapshot)
        check(state.enabled && state.account == expectedAccount) { "Drive backup settings changed during preparation." }
        state.status("Uploading and verifying backup…")
        val uploaded = DriveBackupRepository(GoogleDriveTransport(token, context.packageName)).publish(text, state.expectedLatestId)
        check(state.account == expectedAccount) { "Connected account changed during upload." }
        check(state.preferences.edit().putString("latest_id", uploaded.id).putString("fingerprint", fingerprint)
            .putString("last_success", Instant.now().toString()).putString("status", "Library backed up and verified").commit()) {
            "Backup uploaded, but local status could not be saved. Reconnect before uploading again."
        }
        uploaded
    }
}

class BookDriveBackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val state = DriveBackupState(applicationContext)
        if (!state.enabled || state.account.isBlank()) return@withContext Result.success()
        try {
            val account = state.account
            DriveBackupSync.upload(applicationContext, DriveBackupSync.authorize(applicationContext, account), account)
            Result.success()
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            state.status(error.message ?: "Google Drive backup failed; previous backups are unchanged.")
            val authError = (error as? java.util.concurrent.ExecutionException)?.cause as? com.google.android.gms.common.api.ApiException
            when {
                error is java.util.concurrent.TimeoutException || authError?.statusCode in listOf(7, 8, 15) -> Result.retry()
                error is DriveHttpException && error.code in listOf(401, 403) -> Result.failure()
                error is DriveHttpException && (error.code == 429 || error.code >= 500) -> Result.retry()
                error is java.io.IOException -> Result.retry()
                else -> Result.failure()
            }
        }
    }
}
