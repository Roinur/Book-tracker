package com.roinur.booktracker.data.booklist

import android.content.Context
import androidx.work.*
import com.roinur.booktracker.*
import com.roinur.booktracker.data.database.BookTrackerDatabase
import com.roinur.booktracker.data.backup.BookBackupService
import com.roinur.booktracker.data.backup.BookRecoveryStore
import com.roinur.booktracker.data.backup.drive.DriveBackupState
import com.roinur.booktracker.data.backup.drive.DriveBackupSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class BookListSyncEngine(
    private val repository: BookListRepository,
    private val store: BookListStore,
    private val transport: BookListTransport,
    private val checkpoint: () -> Unit,
    private val checkConnection: () -> Unit = {},
    private val activeBook: () -> Int? = { null }
) {
    fun create(account: String) {
        require(account.isNotBlank())
        checkConnection()
        check(!store.ready) { "A Book List is already linked. Open it instead." }
        if (store.fileId.isBlank()) {
            val id = transport.create(store.instance)
            check(store.preferences.edit().putString("account", account).putString("file_id", id).commit()) { "Could not save the new Book List connection." }
        }
        check(store.account == account) { "Reconnect the Google account used to create this Book List." }
        transport.verify(store.fileId, store.instance)
        val books = repository.ensureKeys()
        val records = books.map(BookListRecord::fromBook)
        require(records.size <= 10_000) { "Book List currently supports up to 10000 books." }
        checkConnection()
        transport.initialize(store.fileId, records)
        val written = parseRows(transport.read(store.fileId)).map { it.second }.associateBy { it.key }
        check(written == records.associateBy { it.key }) { "Book List verification failed. Existing library kept. Retry setup." }
        store.saveBaseline(records)
        check(store.preferences.edit().putBoolean("ready", true).putBoolean("enabled", true)
            .putString("last_success", Instant.now().toString()).putString("status", "Book List created and linked").commit())
    }

    private fun parseRows(raw: List<List<String>>): List<Pair<Int, BookListRecord>> {
        require(raw.firstOrNull() == BookListRecord.headers) { "Book List columns changed. Restore the original headers/order before syncing." }
        val rows = raw.drop(1).mapIndexedNotNull { index, cells ->
            if (BookListRecord.isEmptyRow(cells)) null else try {
                (index+2) to BookListRecord.parse(cells.getOrElse(13) { "" }, cells)
            } catch (error: Exception) { throw IllegalArgumentException("Row ${index+2}: ${error.message}") }
        }
        require(rows.map { it.second.key }.distinct().size == rows.size) { "Duplicate Book Tracker ID. Do not copy the hidden ID into another row." }
        return rows
    }

    /** Returns false when a user choice is required. No partial library merge in that case. */
    fun sync(): Boolean {
        check(store.ready) { "Finish creating Book List first." }
        checkConnection()
        val id = store.fileId
        transport.verify(id, store.instance)
        transport.prepareSync(id)
        var raw = transport.read(id)
        require(raw.firstOrNull() == BookListRecord.headers) { "Book List headers changed. Restore the original column names/order." }
        // Assign durable identities to new rows before importing anything into the library.
        val newIdentities = raw.drop(1).mapIndexedNotNull { index, cells ->
            if (BookListRecord.isEmptyRow(cells) || cells.getOrElse(13) { "" }.isNotBlank()) null else {
                val key = UUID.randomUUID().toString()
                try { BookListRecord.parse(key, cells) } catch (error: Exception) { throw IllegalArgumentException("Row ${index+2}: ${error.message}") }
                BookListCellWrite("'Book List'!N${index+2}", listOf(listOf(key)))
            }
        }
        if (newIdentities.isNotEmpty()) {
            // Check every keyed row too before making even an identity-only write.
            val patched = raw.map { it.toMutableList() }
            newIdentities.forEach { change ->
                val row = change.range.substringAfter('N').toInt()-1
                patched[row][13] = change.rows.single().single().toString()
            }
            parseRows(patched)
            check(transport.read(id) == raw) { "Book List changed during sync. Try again." }
            checkConnection(); transport.write(id, newIdentities)
            raw = transport.read(id)
        }
        val remoteRows = parseRows(raw)
        val remote = remoteRows.map { it.second }.associateBy { it.key }
        val expected = repository.ensureKeys()
        val local = expected.map(BookListRecord::fromBook).associateBy { it.key }
        val merged = BookListMerger.merge(store.baseline(), local, remote, store.choices())
        store.conflicts(merged.conflicts)
        if (merged.conflicts.isNotEmpty()) {
            store.status("${merged.conflicts.size} conflicting ${if (merged.conflicts.size == 1) "field" else "fields"}. Review changes before syncing.")
            return false
        }
        merged.records.values.forEach { BookListRecord.parse(it.key, it.cells().map(Any::toString)) }
        val changed = merged.records.values.filter { it != local[it.key] }
        val current = activeBook()
        if (current != null) expected.firstOrNull { it.id == current }?.let { active ->
            val before = BookListRecord.fromBook(active)
            merged.records[active.listSyncKey]?.let { after ->
                check(listOf(BookListField.PAGES, BookListField.READ_PAGES, BookListField.HOURS, BookListField.STATUS, BookListField.BOOKMARK).all { before[it] == after[it] }) {
                    "Finish the active reading session before importing its progress or status."
                }
            }
        }
        // Optimistic recheck protects edits made while the network request was in flight.
        check(transport.read(id) == raw) { "Book List changed during sync. Try again." }
        checkConnection()
        if (changed.isNotEmpty()) { checkpoint(); checkConnection(); repository.apply(expected, merged.records.values) }
        val actual = repository.books().map(BookListRecord::fromBook).associateBy { it.key }
        val writes = mutableListOf<BookListCellWrite>()
        remoteRows.forEach { (row, record) ->
            val after = actual[record.key]
            if (after == null) writes += BookListCellWrite("'Book List'!A$row:N$row", listOf(List(14) { "" }))
            else if (record != after) writes += BookListCellWrite("'Book List'!A$row:N$row", listOf(after.cells()))
        }
        var row = raw.size + 1
        actual.values.filter { it.key !in remote }.forEach {
            require(row <= 10001) { "Book List has too many rows. Remove empty trailing rows and retry." }
            writes += BookListCellWrite("'Book List'!A$row:N$row", listOf(it.cells())); row++
        }
        check(transport.read(id) == raw) { "Book List changed before upload. Local changes are safe; sync again." }
        checkConnection(); transport.write(id, writes)
        transport.finishSync(id)
        val verified = parseRows(transport.read(id)).map { it.second }.associateBy { it.key }
        check(verified == actual) { "Book List changed during upload. Changes retained; sync again to reconcile." }
        store.saveBaseline(actual.values)
        check(store.preferences.edit().remove("choices").putString("last_success", Instant.now().toString())
            .putString("status", "Synced · ${actual.size} ${if(actual.size == 1) "book" else "books"}").commit())
        return true
    }
}

internal object BookListSync {
    val lock = ReentrantLock()
    private const val PERIODIC = "book-list-periodic"
    private const val PENDING = "book-list-pending"
    fun schedule(context: Context) {
        val store = BookListStore(context); val manager = WorkManager.getInstance(context)
        if (!store.enabled || !store.ready || store.account != DriveBackupState(context).account) {
            manager.cancelUniqueWork(PERIODIC); manager.cancelUniqueWork(PENDING); return
        }
        manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<BookListSyncWorker>(15, TimeUnit.MINUTES).setConstraints(constraints(context)).build())
    }
    fun enqueue(context: Context) {
        val store = BookListStore(context)
        if (!store.ready || !store.enabled || store.account != DriveBackupState(context).account) return
        store.preferences.edit().putString("pending_generation", UUID.randomUUID().toString()).apply()
        WorkManager.getInstance(context).enqueueUniqueWork(PENDING, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<BookListSyncWorker>().setConstraints(constraints(context)).setInitialDelay(15, TimeUnit.SECONDS).build())
    }
    private fun constraints(context: Context) = Constraints.Builder().setRequiredNetworkType(
        if (DriveBackupState(context).wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build()

    fun run(context: Context, create: Boolean = false) = lock.withLock {
        val store = BookListStore(context)
        val account = DriveBackupState(context).account
        require(account.isNotBlank()) { "Connect Google Drive first." }
        if (!create) require(store.account == account) { "Reconnect the Google account linked to Book List." }
        val token = DriveBackupSync.authorize(context, account)
        BookTrackerDatabase(context).use { database ->
            val engine = BookListSyncEngine(BookListRepository(database), store, GoogleBookListTransport(token, context.packageName),
                checkpoint = {
                    val bytes = BookBackupService(database, context).exportBackupJson(false).toString().toByteArray()
                    BookRecoveryStore(File(context.filesDir, "recovery_copies")).save(bytes, "before-book-list-sync")
                }, checkConnection = {
                    check(DriveBackupState(context).account == account && (store.account == account || create && store.account.isBlank())) { "Google account changed. Sync stopped." }
                }, activeBook = { context.getSharedPreferences(BOOK_TRACKER_PREFS, Context.MODE_PRIVATE).getInt(KEY_BOOK_ACTIVE_ID, -1).takeIf { it > 0 } })
            if (create) { engine.create(account); true } else engine.sync()
        }
    }
}

class BookListSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = BookListStore(applicationContext)
        if (!store.enabled || !store.ready) return@withContext Result.success()
        val generation = store.preferences.getString("pending_generation", "")
        try {
            val completed = BookListSync.run(applicationContext)
            if (completed && generation != store.preferences.getString("pending_generation", "")) Result.retry() else Result.success()
        }
        catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            store.status(error.message ?: "Book List sync failed; library retained.")
            if (error is java.io.IOException) Result.retry() else Result.failure()
        }
    }
}
