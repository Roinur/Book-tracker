package com.roinur.booktracker.data.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.roinur.booktracker.BookGoal
import com.roinur.booktracker.BookGoalMode
import java.time.LocalDate

internal class BookGoalRepository(private val database: BookTrackerDatabase) {
    fun list(): Map<Int, BookGoal> = buildMap {
        database.readableDatabase.rawQuery("SELECT book_id, mode, daily_pages, target_date, created_date FROM book_goals", null).use { cursor ->
            while (cursor.moveToNext()) {
                val goal = BookGoal(cursor.getInt(0), BookGoalMode.valueOf(cursor.getString(1)), cursor.getInt(2),
                    cursor.getString(3).takeIf { it.isNotBlank() }?.let(LocalDate::parse), LocalDate.parse(cursor.getString(4)))
                put(goal.bookId, goal)
            }
        }
    }

    fun save(goal: BookGoal) {
        require(goal.mode != BookGoalMode.DAILY_PAGES || goal.dailyPages > 0)
        require(goal.mode != BookGoalMode.DEADLINE || goal.targetDate != null)
        val values = ContentValues().apply {
            put("book_id", goal.bookId); put("mode", goal.mode.name); put("daily_pages", goal.dailyPages)
            put("target_date", goal.targetDate?.toString().orEmpty()); put("created_date", goal.createdDate.toString())
        }
        database.writableDatabase.insertWithOnConflict("book_goals", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            .also { check(it != -1L) { "Could not save book goal" } }
    }

    fun remove(bookId: Int) { database.writableDatabase.delete("book_goals", "book_id = ?", arrayOf(bookId.toString())) }

    companion object {
        fun ensureSchema(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS book_goals (
                id INTEGER PRIMARY KEY AUTOINCREMENT, book_id INTEGER NOT NULL UNIQUE,
                mode TEXT NOT NULL, daily_pages INTEGER NOT NULL DEFAULT 0,
                target_date TEXT NOT NULL DEFAULT '', created_date TEXT NOT NULL,
                FOREIGN KEY(book_id) REFERENCES books(id) ON DELETE CASCADE
            )""")
        }
    }
}
