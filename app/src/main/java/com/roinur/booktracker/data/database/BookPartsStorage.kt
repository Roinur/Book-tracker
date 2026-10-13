package com.roinur.booktracker.data.database

import com.roinur.booktracker.BookPart
import com.roinur.booktracker.validateBookParts
import org.json.JSONArray
import org.json.JSONObject

internal object BookPartsStorage {
    fun encode(parts: List<BookPart>): String = JSONArray().apply {
        validateBookParts(parts).forEach { part -> put(JSONObject().put("title", part.title).put("start_page", part.startPage).put("end_page", part.endPage).put("cover_url", part.coverUrl).put("end_inclusive", true).put("rating", part.rating)) }
    }.toString()

    fun decode(raw: String, totalPages: Int = 0): List<BookPart> {
        if (raw.isBlank()) return emptyList()
        val array = JSONArray(raw)
        return validateBookParts((0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            require(item.keys().asSequence().all { it in setOf("title", "start_page", "end_page", "cover_url", "end_inclusive", "rating") }) { "Unknown book part fields." }
            val start = item.getLong("start_page")
            val end = item.getLong("end_page")
            require(start in 0..Int.MAX_VALUE.toLong() && end in 0..Int.MAX_VALUE.toLong()) { "Invalid book part page." }
            val inclusive = item.optBoolean("end_inclusive")
            // Old exports used a next-page boundary. Convert once on read, then save explicit inclusive endpoints.
            require(inclusive || end > start) { "Invalid book part page." }
            BookPart(item.getString("title"), (if (inclusive) start.toInt() else start.toInt().coerceAtLeast(1)),
                (if (!inclusive && start > 0) end - 1 else end).toInt(), item.optString("cover_url"), item.optInt("rating"))
        }, totalPages)
    }
}
