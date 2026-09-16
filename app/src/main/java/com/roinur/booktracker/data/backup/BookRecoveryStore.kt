package com.roinur.booktracker.data.backup

import com.roinur.booktracker.LegacyMigration
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

internal data class BookRecoveryCopy(val name: String, val createdAt: Long, val reason: String)

/** Private, verified checkpoints. Never rotates an old copy until the new one is durable. */
internal class BookRecoveryStore(private val directory: File) {
    @Synchronized fun save(bytes: ByteArray, reason: String): BookRecoveryCopy {
        LegacyMigration.previewTrackerBackup(bytes)
        check(directory.isDirectory || directory.mkdirs()) { "Could not create recovery folder." }
        val created = maxOf(System.currentTimeMillis(), (list().firstOrNull()?.createdAt ?: 0L) + 1L)
        val name = "${created}_${reason.replace(Regex("[^a-z-]"), "-")}_${UUID.randomUUID()}.json"
        val pending = File(directory, "$name.pending")
        val complete = File(directory, name)
        try {
            FileOutputStream(pending).use { it.write(bytes); it.fd.sync() }
            val verified = pending.readBytes()
            check(LegacyMigration.hash(bytes) == LegacyMigration.hash(verified)) { "Recovery checksum mismatch." }
            LegacyMigration.previewTrackerBackup(verified)
            check(pending.renameTo(complete)) { "Could not finalize recovery copy." }
        } finally { pending.delete() }
        list().drop(5).forEach { File(directory, it.name).delete() }
        return list().first { it.name == name }
    }
    @Synchronized fun list(): List<BookRecoveryCopy> = directory.listFiles().orEmpty()
        .filter { it.isFile && it.name.endsWith(".json") }
        .mapNotNull { file ->
            val parts = file.name.split('_', limit = 3)
            val time = parts.firstOrNull()?.toLongOrNull() ?: return@mapNotNull null
            BookRecoveryCopy(file.name, time, parts.getOrElse(1) { "checkpoint" }.replace('-', ' '))
        }.sortedWith(compareByDescending<BookRecoveryCopy> { it.createdAt }.thenByDescending { it.name })

    @Synchronized fun read(name: String): ByteArray {
        require(list().any { it.name == name }) { "Recovery copy not found." }
        return File(directory, name).readBytes().also { LegacyMigration.previewTrackerBackup(it) }
    }
}
