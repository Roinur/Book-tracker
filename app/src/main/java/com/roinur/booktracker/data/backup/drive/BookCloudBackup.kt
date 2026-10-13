package com.roinur.booktracker.data.backup.drive

import com.roinur.booktracker.LegacyMigration
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** The cloud envelope contains the same verified, portable backup as file export. */
internal object BookCloudBackup {
    const val FORMAT = "BOOK_TRACKER_CLOUD_BACKUP_V1"
    const val MAX_BYTES = 128 * 1024 * 1024
    data class Decoded(val snapshot: JSONObject, val payload: String)

    fun encode(snapshot: JSONObject): String {
        val payload = snapshot.toString()
        LegacyMigration.previewTrackerBackup(payload.toByteArray(Charsets.UTF_8))
        return JSONObject().put("format", FORMAT).put("created_at", Instant.now().toString())
            .put("sha256", sha256(payload)).put("payload", payload).toString().also {
                require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Cloud backup exceeds the 128 MB limit." }
            }
    }

    fun decode(text: String): Decoded {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Cloud backup is too large." }
        val envelope = JSONObject(text)
        require(envelope.getString("format") == FORMAT) { "This is not a Book Tracker cloud backup." }
        val payload = envelope.getString("payload")
        require(sha256(payload) == envelope.getString("sha256")) { "Cloud backup checksum mismatch. Nothing was restored." }
        return Decoded(LegacyMigration.previewTrackerBackup(payload.toByteArray(Charsets.UTF_8)).root, payload)
    }

    private fun sha256(text: String) = LegacyMigration.hash(text.toByteArray(Charsets.UTF_8))

    fun fingerprint(snapshot: JSONObject): String {
        val content = JSONObject(snapshot.toString()).apply {
            remove("exported_at")
            // Both native checksums incorporate the export timestamp, not just library changes.
            remove("content_sha256"); remove("integrity_sha256")
        }
        return sha256(canonical(content))
    }

    private fun canonical(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") {
            JSONObject.quote(it) + ":" + canonical(value.opt(it))
        }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.opt(it)) }
        is String -> JSONObject.quote(value)
        null, JSONObject.NULL -> "null"
        else -> value.toString()
    }
}
