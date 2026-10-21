package com.xiaomanjun.sleepdownschedule.feature.backup

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BackupRestoreInProgressException : IllegalStateException("已有恢复正在进行，请等待完成后再试")

/**
 * All service instances share the same live data and private asset directories. Keep ownership
 * through rollback/final cleanup as well as commit. Reject submissions instead of queuing them:
 * replaying a duplicate after the first operation removed its journal would replace data again.
 */
internal object BackupRestoreCoordinator {
    private val mutex = Mutex()

    suspend fun <T> restore(block: suspend () -> T): T {
        if (!mutex.tryLock()) throw BackupRestoreInProgressException()
        return try {
            block()
        } finally {
            mutex.unlock()
        }
    }

    // Recovery must inspect journals only after any live restore has finished with its files.
    suspend fun <T> recover(block: suspend () -> T): T = mutex.withLock { block() }
}
