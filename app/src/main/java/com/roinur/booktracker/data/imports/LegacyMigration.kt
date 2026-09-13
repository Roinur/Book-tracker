package com.roinur.booktracker

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.Instant

data class LegacyImportPreview(
    val source: ByteArray,
    val hash: String,
    val tables: JSONObject,
    val counts: Map<String, Int>,
    val owners: Map<String, Map<String, Set<String>>>,
    val bookIds: Set<String>,
    val unlinkedContent: Int
) {
    val summary: String get() = "${counts["BookModel"] ?: 0} books · ${counts["ThoughtModel"] ?: 0} thoughts · ${counts["ReadingSessionModel"] ?: 0} sessions"
}

data class LegacyArchiveInfo(val hash: String, val importedAt: String, val counts: String)

data class TrackerBackupPreview(
    val source: ByteArray,
    val root: JSONObject,
    val format: String,
    val exportedAt: String,
    val bookCount: Int,
    val sessionCount: Int,
    val noteCount: Int,
    val goalCount: Int,
    val legacyArchiveCount: Int,
    val fileSha256: String,
    val contentSha256: String,
    val integrityProtected: Boolean
)

/** Lossless source archive + a deliberately limited projection into Book Tracker's UI. */
internal object LegacyMigration {
    const val BACKUP_FORMAT = "BOOK_TRACKER_BACKUP_V3"
    private const val BACKUP_FORMAT_V2 = "BOOK_TRACKER_BACKUP_V2"
    private const val BACKUP_FORMAT_V1 = "BOOK_TRACKER_BACKUP_V1"
    private val trackerSections = listOf("books", "reading_sessions", "reading_notes", "reading_goals", "bookly_sources", "bookly_links")
    private val linkFields = mapOf("ThoughtModel" to "thoughtList", "ReadingSessionModel" to "sessionList",
        "QuoteModel" to "quoteList", "DefinitionModel" to "definitionList")

    fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    fun readLimited(input: java.io.InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            require(output.size() + read <= 32 * 1024 * 1024) { "File exceeds 32 MB; nothing imported." }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    fun decode(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")

    fun sealTrackerBackup(root: JSONObject): JSONObject {
        root.put("content_sha256", trackerContentHash(root))
        root.put("integrity_sha256", trackerIntegrityHash(root))
        return root
    }

    fun previewTrackerBackup(bytes: ByteArray): TrackerBackupPreview {
        require(bytes.size <= 32 * 1024 * 1024) { "Import exceeds the 32 MB limit. Nothing imported." }
        val root = JSONObject(decode(bytes))
        val format = root.optString("format")
        require(format in setOf(BACKUP_FORMAT_V1, BACKUP_FORMAT_V2, BACKUP_FORMAT)) { "Unsupported backup format." }
        val allowed = mutableSetOf("format", "exported_at", "books", "reading_sessions", "reading_notes", "bookly_sources", "bookly_links")
        if (format == BACKUP_FORMAT) allowed += setOf("reading_goals", "content_sha256", "integrity_sha256")
        require(root.keys().asSequence().all { it in allowed }) { "Unknown backup sections; restore cancelled rather than discard data." }
        listOf("books", "reading_sessions", "reading_notes").forEach { name ->
            val array = root.getJSONArray(name)
            repeat(array.length()) { array.getJSONObject(it) }
        }
        if (format != BACKUP_FORMAT_V1) {
            root.getJSONArray("bookly_sources")
            root.getJSONArray("bookly_links")
        }
        if (format == BACKUP_FORMAT) {
            root.getJSONArray("reading_goals")
            val contentHash = trackerContentHash(root)
            require(root.getString("content_sha256") == contentHash) { "Backup content checksum mismatch. Nothing imported." }
            require(root.getString("integrity_sha256") == trackerIntegrityHash(root)) { "Backup integrity checksum mismatch. Nothing imported." }
        }
        return TrackerBackupPreview(
            source = bytes.copyOf(),
            root = root,
            format = format,
            exportedAt = root.optString("exported_at"),
            bookCount = root.getJSONArray("books").length(),
            sessionCount = root.getJSONArray("reading_sessions").length(),
            noteCount = root.getJSONArray("reading_notes").length(),
            goalCount = root.optJSONArray("reading_goals")?.length() ?: 0,
            legacyArchiveCount = root.optJSONArray("bookly_sources")?.length() ?: 0,
            fileSha256 = hash(bytes),
            contentSha256 = trackerContentHash(root),
            integrityProtected = format == BACKUP_FORMAT
        )
    }

    private fun trackerContentHash(root: JSONObject): String {
        val content = JSONObject()
        trackerSections.forEach { section -> content.put(section, root.optJSONArray(section) ?: JSONArray()) }
        return hash(canonicalJson(content).toByteArray(Charsets.UTF_8))
    }

    private fun trackerIntegrityHash(root: JSONObject): String {
        val copy = JSONObject(root.toString())
        copy.remove("integrity_sha256")
        return hash(canonicalJson(copy).toByteArray(Charsets.UTF_8))
    }

    private fun canonicalJson(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(prefix = "{", postfix = "}") { key ->
            "${JSONObject.quote(key)}:${canonicalJson(value.get(key))}"
        }
        is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") { canonicalJson(value.get(it)) }
        is String -> JSONObject.quote(value)
        is Boolean, is Number -> value.toString()
        else -> JSONObject.quote(value.toString())
    }

    fun ensureSchema(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS bookly_sources (
            source_sha256 TEXT PRIMARY KEY NOT NULL, imported_at TEXT NOT NULL,
            source_bytes BLOB NOT NULL, counts_json TEXT NOT NULL)""")
        // No cascade: deleting a projected record must never delete the original archive.
        db.execSQL("""CREATE TABLE IF NOT EXISTS bookly_links (
            source_sha256 TEXT NOT NULL, model TEXT NOT NULL, source_id TEXT NOT NULL,
            target_table TEXT NOT NULL, target_id INTEGER NOT NULL, resolution TEXT NOT NULL,
            PRIMARY KEY(source_sha256, model, source_id))""")
        db.execSQL("CREATE TABLE IF NOT EXISTS tracker_import_receipts (sha256 TEXT PRIMARY KEY NOT NULL)")
    }

    fun preview(bytes: ByteArray): LegacyImportPreview {
        require(bytes.size <= 32 * 1024 * 1024) { "Import exceeds the 32 MB limit. Original file is unchanged." }
        val root = JSONObject(decode(bytes))
        val tables = root.getJSONObject("tables")
        listOf("BookModel", "ThoughtModel", "ReadingSessionModel").forEach { tables.getJSONArray(it) }
        val counts = tables.keys().asSequence().associateWith { name ->
            val array = tables.getJSONArray(name)
            repeat(array.length()) { array.getJSONObject(it) }
            array.length()
        }.toSortedMap()
        val books = rows(tables, "BookModel")
        // Ambiguous identifiers must fail before any database mutation.
        val ids = books.map { requiredId(it) }
        require(ids.toSet().size == ids.size) { "Duplicate legacy import book IDs; import cancelled without changes." }
        val owners = linkFields.mapValues { (_, field) ->
            val map = mutableMapOf<String, MutableSet<String>>()
            books.forEach { book ->
                val list = book.optJSONArray(field) ?: JSONArray()
                repeat(list.length()) { index ->
                    val ref = list.getJSONObject(index)
                    val id = requiredId(ref)
                    map.getOrPut(id) { mutableSetOf() }.add(requiredId(book))
                }
            }
            map
        }
        var unlinked = 0
        linkFields.keys.forEach { model ->
            val entries = rows(tables, model)
            val entryIds = entries.map { requiredId(it) }
            require(entryIds.toSet().size == entryIds.size) { "Duplicate $model IDs; import cancelled without changes." }
            entries.forEach { row ->
                val direct = text(row, "bookId").takeIf { it in ids }
                val listOwners = owners[model]?.get(requiredId(row)).orEmpty()
                require(direct == null || listOwners.isEmpty() || listOwners == setOf(direct)) {
                    "Conflicting book links in $model. Original file is unchanged."
                }
                if (direct == null && listOwners.size != 1) unlinked++
            }
        }
        return LegacyImportPreview(bytes.copyOf(), hash(bytes), tables, counts, owners, ids.toSet(), unlinked)
    }

    /** Reconcile completion dates from reading evidence without modifying the source archive. */
    fun restoreMissingCompletionDates(db: SQLiteDatabase) {
        val originals = mutableMapOf<Long, String>()
        db.rawQuery("SELECT source_sha256, source_id, target_id FROM bookly_links WHERE model = 'BookModel' AND target_table = 'books'", null).use { c ->
            val sources = mutableMapOf<String, Map<String, JSONObject>>()
            while (c.moveToNext()) {
                val hash = c.getString(0)
                val books = sources.getOrPut(hash) {
                    runCatching { rows(JSONObject(decode(source(db, hash))).getJSONObject("tables"), "BookModel").associateBy { text(it, "localId") } }.getOrDefault(emptyMap())
                }
                books[c.getString(1)]?.let { original ->
                    originals[c.getLong(2)] = legacyCompletionDate(text(original, "manualFinishDate"), integer(original, "bookFinishedInLong"))
                }
            }
        }
        val books = db.rawQuery("SELECT id, finished_at FROM books WHERE status = 'FINISHED' AND COALESCE(finished_at_manual, 0) = 0", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0) to c.getString(1).orEmpty()) }
        }
        db.beginTransaction()
        try {
            books.forEach { (id, existing) ->
                fun dates(sql: String) = db.rawQuery(sql, arrayOf(id.toString())).use { c ->
                    buildList { while (c.moveToNext()) add(c.getString(0).orEmpty()) }
                }
                val sessions = dates("SELECT COALESCE(NULLIF(ended_at, ''), started_at) FROM reading_sessions WHERE book_id = ? AND pages_read > 0")
                val notes = dates("SELECT created_at FROM reading_notes WHERE book_id = ?")
                val completed = resolveCompletionDate(sessions, notes, originals[id].orEmpty(), existing)
                if (completed.isNotBlank() && completed != existing) db.update("books", ContentValues().apply { put("finished_at", completed) }, "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun archives(db: SQLiteDatabase): List<LegacyArchiveInfo> = db.rawQuery(
        "SELECT source_sha256, imported_at, counts_json FROM bookly_sources ORDER BY imported_at DESC", null
    ).use { c -> buildList { while (c.moveToNext()) add(LegacyArchiveInfo(c.getString(0), c.getString(1), c.getString(2))) } }

    fun totalReadingSeconds(db: SQLiteDatabase): Long = android.database.DatabaseUtils.longForQuery(db, """
        SELECT COALESCE((SELECT SUM(reading_seconds) FROM books), 0) +
            COALESCE((SELECT SUM(remainder) FROM (
                SELECT SUM(COALESCE(duration_milliseconds, duration_seconds * 1000)) % 1000 AS remainder
                FROM reading_sessions GROUP BY book_id
            )), 0) / 1000
        """.trimIndent(), null)

    fun source(db: SQLiteDatabase, hash: String): ByteArray = db.rawQuery(
        "SELECT source_bytes FROM bookly_sources WHERE source_sha256 = ?", arrayOf(hash)
    ).use { c -> require(c.moveToFirst()) { "legacy import archive not found." }; c.getBlob(0) }

    private fun exists(db: SQLiteDatabase, hash: String): Boolean = db.rawQuery(
        "SELECT 1 FROM bookly_sources WHERE source_sha256 = ?", arrayOf(hash)
    ).use { it.moveToFirst() }

    fun importLegacy(db: SQLiteDatabase, selection: LegacyImportPreview): String {
        // Reparse the preserved bytes, so a mutable preview can never alter what is imported.
        val plan = preview(selection.source)
        db.beginTransaction()
        try {
            if (exists(db, plan.hash)) return "This exact legacy import file is already preserved. No duplicates added."
            db.insertOrThrow("bookly_sources", null, ContentValues().apply {
                put("source_sha256", plan.hash); put("imported_at", Instant.now().toString())
                put("source_bytes", plan.source); put("counts_json", JSONObject(plan.counts).toString())
            })
            val collections = rows(plan.tables, "CollectionModel").associate { text(it, "localId") to text(it, "name") }
            val sessions = rows(plan.tables, "ReadingSessionModel")
            val targetBooks = mutableMapOf<String, Long>()
            rows(plan.tables, "BookModel").forEach { book ->
                val id = requiredId(book)
                val ownSessions = sessions.filter { owner(plan, "ReadingSessionModel", it) == id }
                val totalMs = ownSessions.fold(0L) { acc, row -> Math.addExact(acc, integer(row, "readTime")?.coerceAtLeast(0) ?: 0) }
                val starts = ownSessions.mapNotNull { integer(it, "startDate")?.takeIf { value -> value > 0 } }
                val collectionIds = book.optJSONArray("collectionsId") ?: JSONArray()
                val names = (0 until collectionIds.length()).mapNotNull { collections[collectionIds.optString(it)] }
                val values = ContentValues().apply {
                    put("isbn", ""); put("title", text(book, "name").ifEmpty { "Untitled (legacy import)" })
                    put("authors", text(book, "author")); put("page_count", integer(book, "totalPages") ?: 0)
                    put("current_page", integer(book, "currentPage") ?: 0)
                    put("rating", integer(book, "rating") ?: 0)
                    put("status", when {
                        book.optBoolean("isBookFinsihed") -> "FINISHED"
                        book.optBoolean("isBookAbandoned") || book.optBoolean("doNotFinish") -> "PAUSED"
                        else -> "READING"
                    })
                    put("cover_url", text(book, "coverUrl").ifEmpty { text(book, "imageUrl") })
                    put("collections", names.joinToString(", "))
                    put("added_at", iso(integer(book, "creationDate")))
                    if (book.optBoolean("isBookFinsihed")) {
                        put("finished_at", legacyCompletionDate(text(book, "manualFinishDate"), integer(book, "bookFinishedInLong")))
                    }
                    put("started_at", iso(starts.minOrNull())); put("last_read_at", iso(starts.maxOrNull()))
                    put("reading_seconds", totalMs / 1000)
                }
                val target = db.insertOrThrow("books", null, values)
                targetBooks[id] = target
                link(db, plan.hash, "BookModel", id, "books", target, "localId")
            }
            var noteCount = 0
            var sessionCount = 0
            for ((model, kind) in mapOf("ThoughtModel" to "THOUGHT", "QuoteModel" to "QUOTE", "DefinitionModel" to "WORD")) {
                rows(plan.tables, model).forEach { row ->
                    val targetBook = targetBooks[owner(plan, model, row)] ?: return@forEach
                    val body = when(model) {
                        "ThoughtModel" -> text(row, "thought")
                        "QuoteModel" -> text(row, "quote")
                        else -> listOf(text(row, "word"), text(row, "definition")).filter { it.isNotEmpty() }.joinToString("\n\n")
                    }
                    val target = db.insertOrThrow("reading_notes", null, ContentValues().apply {
                        put("book_id", targetBook); put("created_at", iso(integer(row, "dateAdded")))
                        put("page", text(row, "pageNumber").toIntOrNull()?.coerceAtLeast(0) ?: 0)
                        put("kind", kind); put("note", body); put("duration_seconds", 0)
                    })
                    link(db, plan.hash, model, requiredId(row), "reading_notes", target, resolution(plan, model, row))
                    noteCount++
                }
            }
            sessions.forEach { row ->
                val targetBook = targetBooks[owner(plan, "ReadingSessionModel", row)] ?: return@forEach
                val ms = integer(row, "readTime")
                val target = db.insertOrThrow("reading_sessions", null, ContentValues().apply {
                    put("book_id", targetBook); put("started_at", iso(integer(row, "startDate")))
                    put("ended_at", iso(integer(row, "endDate"))) // Unknown end stays unknown.
                    put("duration_seconds", (ms ?: 0).coerceAtLeast(0) / 1000)
                    if (ms == null) putNull("duration_milliseconds") else put("duration_milliseconds", ms)
                    put("pages_read", integer(row, "numberOfPages") ?: 0); put("page_reached", 0)
                })
                link(db, plan.hash, "ReadingSessionModel", requiredId(row), "reading_sessions", target, resolution(plan, "ReadingSessionModel", row))
                sessionCount++
            }
            check(source(db, plan.hash).contentEquals(plan.source)) { "Archive verification failed." }
            restoreMissingCompletionDates(db)
            db.setTransactionSuccessful()
            return "Added ${targetBooks.size} books, $noteCount notes and $sessionCount sessions. All ${plan.counts.values.sum()} source records preserved; ${plan.unlinkedContent} unlinked content records remain in the archive."
        } finally { db.endTransaction() }
    }

    private fun owner(plan: LegacyImportPreview, model: String, row: JSONObject): String? =
        text(row, "bookId").takeIf { it in plan.bookIds }
            ?: plan.owners[model]?.get(requiredId(row))?.singleOrNull()

    private fun resolution(plan: LegacyImportPreview, model: String, row: JSONObject): String =
        if (text(row, "bookId") in plan.bookIds) "bookId" else "BookModel.${linkFields[model]}"

    private fun link(db: SQLiteDatabase, hash: String, model: String, id: String, table: String, target: Long, resolution: String) {
        db.insertOrThrow("bookly_links", null, ContentValues().apply {
            put("source_sha256", hash); put("model", model); put("source_id", id)
            put("target_table", table); put("target_id", target); put("resolution", resolution)
        })
    }

    fun exportArchives(db: SQLiteDatabase): JSONArray = JSONArray().apply {
        archives(db).forEach { archive ->
            put(JSONObject().put("sha256", archive.hash).put("imported_at", archive.importedAt)
                .put("source_base64", Base64.encodeToString(source(db, archive.hash), Base64.NO_WRAP)))
        }
    }

    /** Validate the entire backup, then append with remapped IDs. Never clear an existing library. */
    fun importTrackerBackup(db: SQLiteDatabase, root: JSONObject, bytes: ByteArray): String {
        val preview = previewTrackerBackup(bytes)
        require(canonicalJson(root) == canonicalJson(preview.root)) { "Backup changed after validation. Nothing imported." }
        val sourceHash = preview.contentSha256
        val archiveArray = if (root.optString("format") != BACKUP_FORMAT_V1) root.getJSONArray("bookly_sources") else JSONArray()
        val sourcePlans = (0 until archiveArray.length()).map { index ->
            val archive = archiveArray.getJSONObject(index)
            val payload = Base64.decode(archive.getString("source_base64"), Base64.DEFAULT)
            require(hash(payload) == archive.getString("sha256")) { "legacy import source checksum mismatch. Nothing imported." }
            preview(payload) to archive.getString("imported_at")
        }
        val backupLinks = if (root.optString("format") != BACKUP_FORMAT_V1) root.getJSONArray("bookly_links") else JSONArray()
        val tableColumns = linkedMapOf(
            "books" to setOf("id","isbn","title","authors","page_count","current_page","status","rating","notes","cover_url","source_url","collections","pinned","added_at","started_at","finished_at","finished_at_manual","last_read_at","reading_seconds"),
            "reading_sessions" to setOf("id","book_id","started_at","ended_at","duration_seconds","duration_milliseconds","pages_read","page_reached","exclude_from_statistics"),
            "reading_notes" to setOf("id","book_id","created_at","page","duration_seconds","kind","note","autosave_key")
        )
        if (root.has("reading_goals")) {
            tableColumns["reading_goals"] = setOf("id", "daily_minutes", "daily_pages", "yearly_books", "updated_at", "monthly_minutes", "monthly_pages", "daily_metric", "monthly_metric")
        }
        val prepared = tableColumns.mapValues { (table, columns) ->
            val entries = root.getJSONArray(table)
            val records = (0 until entries.length()).map { entries.getJSONObject(it) }
            records.forEach { record ->
                require(record.keys().asSequence().all { it in columns }) { "Unknown $table backup fields. File retained; import cancelled rather than discard data." }
                require(record.getLong("id") > 0) { "Invalid backup row ID." }
            }
            require(records.map { it.getLong("id") }.distinct().size == records.size) { "Duplicate $table backup IDs." }
            records
        }
        db.beginTransaction()
        try {
            val imported = db.rawQuery("SELECT 1 FROM tracker_import_receipts WHERE sha256 = ?", arrayOf(sourceHash)).use { it.moveToFirst() }
            if (imported) return "This backup has already been imported. No duplicates added."
            if (preview.format == BACKUP_FORMAT) {
                val existingBooks = android.database.DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM books", null)
                require(existingBooks == 0L) {
                    "Safe restore only runs into an empty library. Existing data was kept; nothing imported."
                }
            }
            val maps = mutableMapOf<String, MutableMap<Long,Long>>()
            prepared.forEach { (table, rows) ->
                val idMap = mutableMapOf<Long,Long>()
                rows.forEach { row ->
                    val values = ContentValues()
                    row.keys().forEach { key ->
                        if (key != "id") when (val value = row.get(key)) {
                            JSONObject.NULL -> values.putNull(key)
                            is Number -> values.put(key, value.toString().toLongOrNull()
                                ?: throw IllegalArgumentException("Non-integer backup value $key; no changes saved."))
                            is String -> values.put(key, value)
                            else -> throw IllegalArgumentException("Unsupported backup field $key; no changes saved.")
                        }
                    }
                    if (table == "reading_sessions" || table == "reading_notes") {
                        val bookId = maps["books"]?.get(row.getLong("book_id"))
                            ?: throw IllegalArgumentException("Backup refers to a missing book; no changes saved.")
                        values.put("book_id", bookId)
                    }
                    val targetId = if (table == "reading_goals") {
                        if (!row.has("daily_metric")) {
                            values.put("daily_metric", if (row.optInt("daily_pages") > 0 && row.optInt("daily_minutes") == 0) "PAGES" else "TIME")
                        }
                        db.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                    } else {
                        db.insertOrThrow(table, null, values)
                    }
                    require(targetId != -1L) { "Could not restore $table; no changes saved." }
                    idMap[row.getLong("id")] = targetId
                }
                maps[table] = idMap
            }
            val newlyArchived = mutableSetOf<String>()
            sourcePlans.forEach { (plan, importedAt) ->
                if (!exists(db, plan.hash)) {
                    db.insertOrThrow("bookly_sources", null, ContentValues().apply {
                        put("source_sha256", plan.hash); put("imported_at", importedAt)
                        put("source_bytes", plan.source); put("counts_json", JSONObject(plan.counts).toString())
                    })
                    newlyArchived += plan.hash
                }
                check(source(db, plan.hash).contentEquals(plan.source))
            }
            repeat(backupLinks.length()) { index ->
                val item = backupLinks.getJSONObject(index)
                val sha = item.getString("source_sha256")
                require(exists(db, sha)) { "Missing archived legacy import source." }
                if (sha in newlyArchived) {
                    val table = item.getString("target_table")
                    require(table in tableColumns) { "Invalid legacy import mapping table." }
                    // A user may have deleted a projection since import. Source bytes still survive.
                    val target = maps[table]?.get(item.getLong("target_id"))
                    if (target != null) link(db,sha,item.getString("model"),item.getString("source_id"),table,target,item.getString("resolution"))
                }
            }
            db.insertOrThrow("tracker_import_receipts", null, ContentValues().apply { put("sha256", sourceHash) })
            restoreMissingCompletionDates(db)
            db.setTransactionSuccessful()
            return "Backup added: ${prepared.getValue("books").size} books. Existing library and ${sourcePlans.size} legacy import archives preserved."
        } finally { db.endTransaction() }
    }

    private fun rows(tables: JSONObject, name: String): List<JSONObject> {
        val array = tables.optJSONArray(name) ?: return emptyList()
        return (0 until array.length()).map { array.getJSONObject(it) }
    }
    private fun requiredId(row: JSONObject): String = text(row,"localId").also { require(it.isNotEmpty()) { "Missing legacy import localId; no changes saved." } }
    private fun text(row: JSONObject, key: String): String = if (row.isNull(key)) "" else row.get(key).toString()
    private fun integer(row: JSONObject, key: String): Long? = if (row.isNull(key)) null else row.get(key).toString().toLongOrNull()
    private fun iso(ms: Long?): String = ms?.takeIf { it > 0 }?.let { runCatching { Instant.ofEpochMilli(it).toString() }.getOrDefault("") }.orEmpty()
}
