package com.roinur.booktracker.data.markdown

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal class MarkdownHttpError(val code: Int) : IOException(when (code) {
    401 -> "Google permission expired. Choose the export folder again to renew access."
    403 -> "Google Drive denied access. Check the selected account, folder permission, Drive API and storage quota."
    429 -> "Google Drive is busy. Export remains queued and will retry."
    else -> "Google Drive request failed (HTTP $code). Export has not been confirmed."
})

/** Dedicated non-logging REST client. Never converts Markdown to a Google Workspace document. */
internal class GoogleMarkdownTransport(private val token: String,
    private val endpoint: String = "https://www.googleapis.com/",
    private val client: OkHttpClient = CLIENT) : MarkdownTransport {
    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val MAX_BYTES = 16L * 1024 * 1024
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val MARKDOWN = "text/markdown; charset=utf-8".toMediaType()
        private val CLIENT = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
        private fun id(value: String): String { require(Regex("[A-Za-z0-9_-]+").matches(value)) { "Invalid Drive file ID." }; return value }
        private fun quote(value: String) = value.replace("\\", "\\\\").replace("'", "\\'")
    }
    private data class Reply(val bytes: ByteArray, val etag: String) {
        fun text(): String = try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }
        catch (error: java.nio.charset.CharacterCodingException) { throw MarkdownConflict("The existing file is not valid UTF-8. Nothing was overwritten; correct its encoding and retry.") }
        fun json() = JSONObject(text())
    }
    private fun request(method: String, path: String, query: Map<String, String> = emptyMap(),
        body: RequestBody? = null, etag: String? = null): Reply {
        val url = endpoint.toHttpUrl().newBuilder().addPathSegments(path).apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").apply {
            etag?.let { header("If-Match", it) }
        }.method(method, body).build()
        return client.newCall(request).execute().use { response ->
            if (response.code == 412) throw MarkdownConflict("The file was edited in Obsidian or Drive during export. Nothing was overwritten by this request. Retry to merge the latest file.")
            if (!response.isSuccessful) throw MarkdownHttpError(response.code)
            val buffer = okio.Buffer()
            response.body?.source()?.let { source ->
                while (source.read(buffer, 8192) != -1L) require(buffer.size <= MAX_BYTES) { "The Markdown file is too large to merge safely." }
            }
            Reply(buffer.readByteArray(), response.header("ETag").orEmpty())
        }
    }
    private fun metadata(file: String): Reply {
        // v3 did not return an ETag in live verification. v2 still documents the file's resource ETag;
        // pair it with v2's conditional media update rather than guessing from a checksum/version.
        val info = request("GET", "drive/v2/files/${id(file)}", mapOf(
            "fields" to "id,title,mimeType,parents(id),labels(trashed),version,md5Checksum,etag,capabilities(canAddChildren,canEdit)",
            "supportsAllDrives" to "true")).json()
        val parents = info.optJSONArray("parents") ?: JSONArray()
        val ids = JSONArray()
        for (i in 0 until parents.length()) ids.put(parents.getJSONObject(i).getString("id"))
        info.put("name", info.optString("title")).put("parents", ids).put("trashed", info.optJSONObject("labels")?.optBoolean("trashed") ?: false)
        return Reply(info.toString().toByteArray(Charsets.UTF_8), info.optString("etag"))
    }

    override fun folder(id: String): MarkdownFolder {
        val folder = try { metadata(id).json() } catch (error: MarkdownHttpError) {
            if (error.code == 404) throw MarkdownConflict("The export folder is missing or has not been granted to this app. Choose the existing Drive export folder again; no replacement folder was created.")
            throw error
        }
        if (folder.optBoolean("trashed") || folder.optString("mimeType") != "application/vnd.google-apps.folder")
            throw MarkdownConflict("The selected export target is not an existing Drive folder. Choose Böcker/BookTracker inside your vault.")
        if (folder.optJSONObject("capabilities")?.optBoolean("canAddChildren") != true)
            throw MarkdownConflict("The selected Drive folder is read-only. Choose a folder you can write to.")
        return MarkdownFolder(folder.getString("id"), folder.getString("name"))
    }
    override fun find(folder: String, name: String): List<String> {
        val rows = mutableListOf<String>(); var page = ""
        do {
            val response = request("GET", "drive/v3/files", buildMap {
                put("q", "'${quote(id(folder))}' in parents and trashed=false and name='${quote(name)}'")
                put("fields", "nextPageToken,incompleteSearch,files(id)"); put("pageSize", "100")
                put("supportsAllDrives", "true"); put("includeItemsFromAllDrives", "true")
                if (page.isNotBlank()) put("pageToken", page)
            }).json()
            check(!response.optBoolean("incompleteSearch")) { "Drive could not finish searching for the existing file. Retry without creating another file." }
            val files = response.optJSONArray("files") ?: JSONArray()
            for (i in 0 until files.length()) rows += files.getJSONObject(i).getString("id")
            if (rows.size > 1) return rows
            page = response.optString("nextPageToken")
        } while (page.isNotEmpty())
        return rows
    }
    override fun generateId(): String = request("GET", "drive/v3/files/generateIds", mapOf("count" to "1", "space" to "drive", "type" to "files"))
        .json().getJSONArray("ids").getString(0)

    override fun read(folder: String, id: String): MarkdownRemote? {
        val before = try { metadata(id) } catch (error: MarkdownHttpError) { if (error.code == 404) return null else throw error }
        val info = before.json()
        if (info.optBoolean("trashed")) return null
        val parents = info.optJSONArray("parents") ?: JSONArray()
        if ((0 until parents.length()).none { parents.getString(it) == folder })
            throw MarkdownConflict("The exported file was moved outside the selected export folder. Restore it there before retrying.")
        if (info.optString("mimeType").startsWith("application/vnd.google-apps."))
            throw MarkdownConflict("The target is a Google Workspace document, not a plain Markdown file. Nothing was overwritten.")
        if (info.optJSONObject("capabilities")?.optBoolean("canEdit") != true)
            throw MarkdownConflict("The Markdown file is read-only. Restore edit permission and retry.")
        val bytes = request("GET", "drive/v3/files/${Companion.id(id)}", mapOf("alt" to "media", "supportsAllDrives" to "true"))
        val after = metadata(id)
        val checksum = MessageDigest.getInstance("MD5").digest(bytes.bytes).joinToString("") { "%02x".format(it) }
        if (info.optString("version").isBlank() || info.optString("version") != after.json().optString("version") ||
            info.optString("md5Checksum") != checksum || before.etag != after.etag)
            throw MarkdownConflict("The file changed while it was being read. Retry to read a consistent version.")
        if (after.etag.isBlank() || after.etag.startsWith("W/") || after.etag == "*")
            throw MarkdownConflict("Drive did not provide a strong file version. Export stopped to protect concurrent Obsidian edits.")
        return MarkdownRemote(id, info.getString("name"), bytes.text(), after.etag)
    }
    override fun create(folder: String, id: String, name: String, text: String) {
        val meta = JSONObject().put("id", Companion.id(id)).put("name", name).put("mimeType", "text/markdown")
            .put("parents", JSONArray(listOf(Companion.id(folder)))).put("appProperties", JSONObject().put("book_kind", "markdown_v1"))
        val body = MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(meta.toString().toRequestBody(JSON)).addPart(text.toRequestBody(MARKDOWN)).build()
        try { request("POST", "upload/drive/v3/files", mapOf("uploadType" to "multipart", "fields" to "id", "supportsAllDrives" to "true"), body) }
        catch (error: MarkdownHttpError) { if (error.code != 409) throw error } // Retry the SAME reserved ID; engine reads and verifies it.
    }
    override fun update(folder: String, document: MarkdownRemote, text: String) {
        require(document.etag.isNotBlank() && !document.etag.startsWith("W/") && document.etag != "*")
        request("PUT", "upload/drive/v2/files/${id(document.id)}", mapOf("uploadType" to "media", "supportsAllDrives" to "true"),
            text.toRequestBody(MARKDOWN), document.etag)
    }
}
