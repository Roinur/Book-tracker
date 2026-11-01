package com.roinur.booktracker

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.roinur.booktracker.data.database.BookReadingPlanRepository
import org.json.JSONArray
import org.json.JSONObject

/** Called inside the backup import transaction, after remapping book IDs. */
internal fun restoreReadingPlans(db: SQLiteDatabase, root: JSONObject, bookIds: Map<Long, Long>) {
    val plans = root.optJSONArray("reading_plans") ?: JSONArray()
    val assignments = root.optJSONArray("reading_plan_books") ?: JSONArray()
    val known = mutableSetOf<String>()
    var activeCount = 0
    repeat(plans.length()) { index ->
        val row = plans.getJSONObject(index)
        require(row.keys().asSequence().all { it in setOf("id", "name", "tiers_json", "active") })
        val id = row.getString("id")
        require(id.isNotBlank() && known.add(id) && row.getString("name").isNotBlank())
        BookReadingPlanRepository.decodeLabels(row.getString("tiers_json"))
        val active = row.optInt("active")
        require(active in 0..1)
        activeCount += active
        require(activeCount <= 1)
        db.insertOrThrow("reading_plans", null, ContentValues().apply {
            put("id", id); put("name", row.getString("name")); put("tiers_json", row.getString("tiers_json")); put("active", active)
        })
    }
    repeat(assignments.length()) { index ->
        val row = assignments.getJSONObject(index)
        require(row.keys().asSequence().all { it in setOf("id", "plan_id", "book_id", "tier_index", "position") })
        require(row.getString("plan_id") in known && row.getInt("tier_index") in 0..5 && row.getInt("position") >= 0)
        val book = requireNotNull(bookIds[row.getLong("book_id")]) { "Reading plan refers to a missing book" }
        db.insertOrThrow("reading_plan_books", null, ContentValues().apply {
            put("plan_id", row.getString("plan_id")); put("book_id", book)
            put("tier_index", row.getInt("tier_index")); put("position", row.getInt("position"))
        })
    }
}
