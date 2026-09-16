package com.roinur.booktracker.data.backup

import com.roinur.booktracker.data.database.BookTrackerDatabase

import android.database.sqlite.SQLiteDatabase
import com.roinur.booktracker.LegacyMigration
import com.roinur.booktracker.LegacyImportPreview
import com.roinur.booktracker.LegacyArchiveInfo
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Owns backup snapshots and import orchestration; shares the app database connection. */
internal class BookBackupService(private val database: BookTrackerDatabase, private val context: android.content.Context? = null) {
    fun exportBackupJson(): JSONObject {
        val root = exportSnapshot()
        context?.let { root.put("cover_assets", BookPortableCovers.export(it, root.getJSONArray("books"))) }
        return LegacyMigration.sealTrackerBackup(root)
    }

    private fun exportSnapshot(): JSONObject {
        val snapshot = database.readableDatabase
        snapshot.beginTransactionNonExclusive()
        try {
            val backup = JSONObject().apply {
                put("format", LegacyMigration.BACKUP_FORMAT)
                put("bookly_sources", LegacyMigration.exportArchives(snapshot))
                put("bookly_links", exportTable(snapshot, "bookly_links"))
                put("exported_at", Instant.now().toString())
                put("books", exportTable(snapshot, "books"))
                put("reading_sessions", exportTable(snapshot, "reading_sessions"))
                put("reading_notes", exportTable(snapshot, "reading_notes"))
                put("reading_goals", exportTable(snapshot, "reading_goals"))
            }
            snapshot.setTransactionSuccessful()
            return LegacyMigration.sealTrackerBackup(backup)
        } finally {
            snapshot.endTransaction()
        }
    }

    fun importBackupJson(root: JSONObject, source: ByteArray = root.toString().toByteArray(Charsets.UTF_8)): String {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val result = LegacyMigration.importTrackerBackup(db, root, source)
            if (root.optJSONArray("cover_assets")?.length()?.let { it > 0 } == true) {
                BookPortableCovers.restore(requireNotNull(context) { "Cover storage unavailable" }, root)
            }
            db.setTransactionSuccessful()
            return result
        } finally { db.endTransaction() }
    }

    fun importLegacy(plan: LegacyImportPreview): String = LegacyMigration.importLegacy(database.writableDatabase, plan)
    fun legacyArchives(): List<LegacyArchiveInfo> = LegacyMigration.archives(database.readableDatabase)
    fun legacySource(hash: String): ByteArray = LegacyMigration.source(database.readableDatabase, hash)

    private fun exportTable(snapshot: SQLiteDatabase, table: String): JSONArray {
        val rows = JSONArray()
        snapshot.rawQuery("SELECT * FROM $table", emptyArray()).use { cursor ->
            while (cursor.moveToNext()) {
                val row = JSONObject()
                cursor.columnNames.forEachIndexed { index, name ->
                    when (cursor.getType(index)) {
                        android.database.Cursor.FIELD_TYPE_INTEGER -> row.put(name, cursor.getLong(index))
                        android.database.Cursor.FIELD_TYPE_FLOAT -> row.put(name, cursor.getDouble(index))
                        android.database.Cursor.FIELD_TYPE_STRING -> row.put(name, cursor.getString(index).orEmpty())
                        android.database.Cursor.FIELD_TYPE_NULL -> row.put(name, JSONObject.NULL)
                        else -> row.put(name, cursor.getString(index).orEmpty())
                    }
                }
                rows.put(row)
            }
        }
        return rows
    }

}
