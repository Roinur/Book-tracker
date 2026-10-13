package com.roinur.booktracker

import com.roinur.booktracker.data.backup.BookBackupStatus
import org.junit.Assert.*
import org.junit.Test

class BookBackupStatusTest {
    @Test fun failedBackupPreservesLastVerifiedSuccess() {
        val failed = BookBackupStatus().succeeded(1000).failed(2000, "Permission denied")
        assertEquals(1000L, failed.lastSuccessAtMs)
        assertEquals(2000L, failed.lastFailureAtMs)
        assertEquals("Permission denied", failed.error)
    }

    @Test fun nextVerifiedSuccessClearsPreviousFailure() {
        val successful = BookBackupStatus().failed(1000, "Storage full").succeeded(2000)
        assertEquals(2000L, successful.lastSuccessAtMs)
        assertEquals(0L, successful.lastFailureAtMs)
        assertEquals("", successful.error)
    }

    @Test fun failedFirstAttemptDoesNotClaimThereIsABackup() {
        assertEquals(0L, BookBackupStatus().failed(1000, "Failed checksum").lastSuccessAtMs)
    }
}
