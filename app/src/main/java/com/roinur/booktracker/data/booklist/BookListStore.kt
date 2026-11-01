package com.roinur.booktracker.data.booklist

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Local account/binding/baseline state is deliberately excluded from portable backups. */
internal class BookListStore(context: Context) {
    val preferences = context.getSharedPreferences("book_list_sync", Context.MODE_PRIVATE)
    private val file = AtomicFile(File(context.filesDir, "book_list_baseline.json"))
    val fileId get() = preferences.getString("file_id", "").orEmpty()
    val account get() = preferences.getString("account", "").orEmpty()
    val enabled get() = preferences.getBoolean("enabled", false)
    val ready get() = preferences.getBoolean("ready", false)
    val status get() = preferences.getString("status", "Create a separate Book List from this library.").orEmpty()
    val lastSuccess get() = preferences.getString("last_success", "").orEmpty()
    val instance: String get() = preferences.getString("instance", null) ?: UUID.randomUUID().toString().also {
        check(preferences.edit().putString("instance", it).commit())
    }
    fun status(value: String) { preferences.edit().putString("status", value).apply() }
    fun baseline(): Map<String, BookListRecord> {
        if (!file.baseFile.exists()) return emptyMap()
        val root = JSONObject(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        require(root.getString("file_id") == fileId && root.getString("account") == account) { "Book List baseline does not match the linked account." }
        return decodeRecords(root.getJSONArray("records"))
    }
    fun saveBaseline(records: Collection<BookListRecord>) {
        val root = JSONObject().put("file_id", fileId).put("account", account).put("records", encodeRecords(records))
        val out = file.startWrite()
        try { out.write(root.toString().toByteArray()); file.finishWrite(out) } catch (error: Throwable) { file.failWrite(out); throw error }
    }
    fun conflicts(): List<BookListConflict> = JSONArray(preferences.getString("conflicts", "[]")).let { rows ->
        (0 until rows.length()).map { i -> rows.getJSONObject(i).let {
            BookListConflict(it.getString("key"), it.getString("title"), it.getString("field"), it.getString("app"), it.getString("sheet"))
        } }
    }
    fun conflicts(values: List<BookListConflict>) {
        val rows = JSONArray().apply { values.forEach { put(JSONObject().put("key", it.key).put("title", it.title).put("field", it.field).put("app", it.appValue).put("sheet", it.sheetValue)) } }
        check(preferences.edit().putString("conflicts", rows.toString()).commit())
    }
    fun choices(): Map<String, String> = JSONObject(preferences.getString("choices", "{}").orEmpty()).let { root -> root.keys().asSequence().associateWith { root.getString(it) } }
    fun choose(conflict: BookListConflict, useSheet: Boolean) {
        val choices = JSONObject(preferences.getString("choices", "{}").orEmpty()).put(conflict.choiceKey, if (useSheet) "sheet" else "app")
        check(preferences.edit().putString("choices", choices.toString()).commit())
    }
    companion object {
        fun encodeRecords(records: Collection<BookListRecord>) = JSONArray().apply { records.forEach { put(JSONObject().put("key", it.key).put("values", JSONArray(it.values))) } }
        fun decodeRecords(rows: JSONArray): Map<String, BookListRecord> = (0 until rows.length()).map { i -> rows.getJSONObject(i).let {
            val v = it.getJSONArray("values"); BookListRecord(it.getString("key"), (0 until v.length()).map(v::getString))
        } }.associateBy { it.key }
    }
}
