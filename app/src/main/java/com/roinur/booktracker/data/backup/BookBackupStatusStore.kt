package com.roinur.booktracker.data.backup

import android.content.Context

internal data class BookBackupStatus(val lastSuccessAtMs: Long = 0L, val lastFailureAtMs: Long = 0L, val error: String = "") {
    fun succeeded(atMs: Long) = copy(lastSuccessAtMs = atMs, lastFailureAtMs = 0L, error = "")
    fun failed(atMs: Long, reason: String) = copy(lastFailureAtMs = atMs, error = reason)
}

/** Records only backups that were written, checksum-verified and finalized in the selected folder. */
internal class BookBackupStatusStore(context: Context) {
    private val prefs = context.getSharedPreferences("book_backup_status", Context.MODE_PRIVATE)

    fun load(folder: String): BookBackupStatus = if (prefs.getString("folder", "") == folder) {
        BookBackupStatus(prefs.getLong("success_ms", 0L), prefs.getLong("failure_ms", 0L), prefs.getString("error", "").orEmpty())
    } else BookBackupStatus()

    fun record(folder: String, status: BookBackupStatus) {
        prefs.edit().putString("folder", folder).putLong("success_ms", status.lastSuccessAtMs)
            .putLong("failure_ms", status.lastFailureAtMs).putString("error", status.error).apply()
    }
}
