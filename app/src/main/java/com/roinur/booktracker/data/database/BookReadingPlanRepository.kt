package com.roinur.booktracker.data.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.roinur.booktracker.BookPlanTier
import com.roinur.booktracker.BookReadingPlan
import org.json.JSONArray

internal class BookReadingPlanRepository(private val database: BookTrackerDatabase) {
    fun list(): List<BookReadingPlan> {
        val db = database.readableDatabase
        return buildList {
            db.rawQuery("SELECT id, name, tiers_json, active FROM reading_plans ORDER BY rowid", null).use { plans ->
                while (plans.moveToNext()) {
                    val id = plans.getString(0)
                    val labels = decodeLabels(plans.getString(2))
                    val entries = Array(labels.size) { mutableListOf<Int>() }
                    db.rawQuery("SELECT book_id, tier_index FROM reading_plan_books WHERE plan_id = ? ORDER BY position, id", arrayOf(id)).use { rows ->
                        while (rows.moveToNext()) entries.getOrNull(rows.getInt(1))?.add(rows.getInt(0))
                    }
                    add(BookReadingPlan(id, plans.getString(1), labels.mapIndexed { index, label -> BookPlanTier(label, entries[index]) }, plans.getInt(3) != 0))
                }
            }
        }
    }

    fun save(plan: BookReadingPlan) {
        require(plan.name.isNotBlank() && plan.tiers.size == 6 && plan.tiers.all { it.name.isNotBlank() })
        require(plan.bookIds.distinct().size == plan.bookIds.size)
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("id", plan.id); put("name", plan.name.trim())
                put("tiers_json", JSONArray(plan.tiers.map { it.name.trim() }).toString())
            }
            // Updating a label must not replace the parent row and cascade-delete its entries.
            if (db.update("reading_plans", values, "id = ?", arrayOf(plan.id)) == 0)
                db.insertOrThrow("reading_plans", null, values)
            db.delete("reading_plan_books", "plan_id = ?", arrayOf(plan.id))
            plan.tiers.forEachIndexed { tier, row -> row.bookIds.forEachIndexed { position, book ->
                // A concurrently deleted book is simply left out of this plan.
                db.execSQL("INSERT INTO reading_plan_books(plan_id, book_id, tier_index, position) SELECT ?, id, ?, ? FROM books WHERE id = ?",
                    arrayOf(plan.id, tier, position, book))
            } }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun activate(id: String) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            check(android.database.DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM reading_plans WHERE id = ?", arrayOf(id)) == 1L)
            db.execSQL("UPDATE reading_plans SET active = CASE WHEN id = ? THEN 1 ELSE 0 END", arrayOf(id))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun delete(id: String) { database.writableDatabase.delete("reading_plans", "id = ?", arrayOf(id)) }

    companion object {
        fun decodeLabels(json: String): List<String> {
            val array = JSONArray(json)
            require(array.length() == 6)
            return (0 until array.length()).map { array.getString(it).also { name -> require(name.isNotBlank()) } }
        }

        fun ensureSchema(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS reading_plans (
                id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, tiers_json TEXT NOT NULL,
                active INTEGER NOT NULL DEFAULT 0 CHECK(active IN (0,1)))""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS reading_plan_books (
                id INTEGER PRIMARY KEY AUTOINCREMENT, plan_id TEXT NOT NULL, book_id INTEGER NOT NULL,
                tier_index INTEGER NOT NULL CHECK(tier_index BETWEEN 0 AND 5), position INTEGER NOT NULL,
                UNIQUE(plan_id, book_id),
                FOREIGN KEY(plan_id) REFERENCES reading_plans(id) ON DELETE CASCADE,
                FOREIGN KEY(book_id) REFERENCES books(id) ON DELETE CASCADE)""")
        }
    }
}
