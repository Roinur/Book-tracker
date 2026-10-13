package com.roinur.booktracker.data.backup.drive

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

internal class DriveHttpException(val code: Int) : IOException(when (code) {
    401 -> "Google authorization expired. Reconnect Google Drive."
    403 -> "Google Drive denied access. Check Drive API, OAuth registration, permissions and available storage."
    429 -> "Google Drive rate limit. Backup will retry later."
    else -> "Google Drive request failed (HTTP $code)."
})

/** Dedicated client: no image interceptors, API keys or logged tokens. */
internal class GoogleDriveTransport(private val token: String, private val channel: String) : DriveBackupTransport {
    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.file"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val TEXT = "application/json; charset=utf-8".toMediaType()
        private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
    }

    private fun properties(verified: Boolean? = null) = JSONObject().put("book_kind", "library_v1")
        .put("book_channel", channel).apply { verified?.let { put("book_verified", it.toString()) } }

    private fun markerQuery() = "appProperties has { key='book_kind' and value='library_v1' } and " +
        "appProperties has { key='book_channel' and value='${channel.replace("'", "\\'")}' }"

    private fun request(method: String, path: String, query: Map<String, String> = emptyMap(), body: RequestBody? = null): String {
        val url = "https://www.googleapis.com/$path".toHttpUrl().newBuilder().apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").method(method, body).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw DriveHttpException(response.code)
            val responseBody = response.body ?: return@use ""
            require(responseBody.contentLength() <= BookCloudBackup.MAX_BYTES) { "Drive response is too large." }
            responseBody.source().use { source ->
                val buffer = okio.Buffer()
                while (true) {
                    val read = source.read(buffer, 8192)
                    if (read < 0) break
                    require(buffer.size <= BookCloudBackup.MAX_BYTES) { "Drive response is too large." }
                }
                buffer.readUtf8()
            }
        }
    }

    private fun list(query: String): List<JSONObject> {
        val rows = mutableListOf<JSONObject>()
        var page = ""
        do {
            val result = JSONObject(request("GET", "drive/v3/files", buildMap {
                put("q", "trashed=false and ($query)")
                put("fields", "nextPageToken,files(id,name,createdTime)")
                put("orderBy", "createdTime desc")
                put("pageSize", "100")
                if (page.isNotBlank()) put("pageToken", page)
            }))
            val files = result.optJSONArray("files") ?: JSONArray()
            for (index in 0 until files.length()) rows += files.getJSONObject(index)
            page = result.optString("nextPageToken")
            check(rows.size <= 2000) { "Too many cloud backups. Review the Drive folder manually." }
        } while (page.isNotBlank())
        return rows
    }

    override fun listVerified(): List<DriveBackupFile> = list("${markerQuery()} and mimeType!='application/vnd.google-apps.folder' and " +
        "appProperties has { key='book_verified' and value='true' }")
        .map { DriveBackupFile(it.getString("id"), it.getString("name"), it.getString("createdTime")) }

    private fun folder(): String {
        list("${markerQuery()} and mimeType='application/vnd.google-apps.folder'").firstOrNull()?.let { return it.getString("id") }
        val metadata = JSONObject().put("name", if (channel.endsWith(".debug")) "Book Tracker Test Backups" else "Book Tracker Backups")
            .put("mimeType", "application/vnd.google-apps.folder").put("appProperties", properties())
        return JSONObject(request("POST", "drive/v3/files", mapOf("fields" to "id"), metadata.toString().toRequestBody(JSON))).getString("id")
    }

    override fun uploadPending(text: String): DriveBackupFile {
        val name = "Book-Tracker-${Instant.now().toString().replace(':', '-')}-${UUID.randomUUID().toString().take(8)}.json"
        val metadata = JSONObject().put("name", name).put("parents", JSONArray(listOf(folder())))
            .put("appProperties", properties(false))
        val body = MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(metadata.toString().toRequestBody(JSON)).addPart(text.toRequestBody(TEXT)).build()
        val result = JSONObject(request("POST", "upload/drive/v3/files", mapOf("uploadType" to "multipart", "fields" to "id,name,createdTime"), body))
        return DriveBackupFile(result.getString("id"), result.getString("name"), result.getString("createdTime"))
    }

    override fun download(id: String): String = request("GET", "drive/v3/files/$id", mapOf("alt" to "media"))
    override fun markVerified(id: String) {
        request("PATCH", "drive/v3/files/$id", mapOf("fields" to "id"),
            JSONObject().put("appProperties", properties(true)).toString().toRequestBody(JSON))
    }
    override fun delete(id: String) { request("DELETE", "drive/v3/files/$id") }
}
