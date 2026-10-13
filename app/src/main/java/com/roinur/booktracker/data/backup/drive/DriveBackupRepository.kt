package com.roinur.booktracker.data.backup.drive

internal data class DriveBackupFile(val id: String, val name: String, val createdAt: String)

internal interface DriveBackupTransport {
    fun listVerified(): List<DriveBackupFile>
    fun uploadPending(text: String): DriveBackupFile
    fun download(id: String): String
    fun markVerified(id: String)
    fun delete(id: String)
}

/** Never changes the old healthy backup before read-back validation succeeds. */
internal class DriveBackupRepository(private val transport: DriveBackupTransport) {
    fun publish(text: String, expectedLatestId: String): DriveBackupFile {
        BookCloudBackup.decode(text)
        val before = transport.listVerified()
        check(before.firstOrNull()?.id.orEmpty() == expectedLatestId) {
            "Another device changed the cloud backup. Review Drive backups before uploading from this phone."
        }
        val pending = transport.uploadPending(text)
        try {
            val readBack = transport.download(pending.id)
            check(readBack == text) { "Drive read-back did not match the upload. The previous backup is unchanged." }
            BookCloudBackup.decode(readBack)
            // Detect a concurrent device before promoting this pending upload.
            check(transport.listVerified().firstOrNull()?.id.orEmpty() == expectedLatestId) {
                "Cloud backup changed during upload. The previous backup is unchanged."
            }
            transport.markVerified(pending.id)
        } catch (error: Exception) {
            runCatching { transport.delete(pending.id) } // Only our own unpublished attempt.
            throw error
        }
        // Five verified generations. Cleanup failure does not invalidate a successful backup.
        before.drop(4).forEach { runCatching { transport.delete(it.id) } }
        return pending
    }
}
