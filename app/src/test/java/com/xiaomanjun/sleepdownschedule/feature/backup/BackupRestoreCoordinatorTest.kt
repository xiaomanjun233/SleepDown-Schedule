package com.xiaomanjun.sleepdownschedule.feature.backup

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class BackupRestoreCoordinatorTest {
    @Test fun duplicateIsRejectedRatherThanQueuedAndRecoveryWaitsForCleanup() = runBlocking {
        withTimeout(5_000) {
            val finish = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            val owner = launch(start = CoroutineStart.UNDISPATCHED) {
                BackupRestoreCoordinator.restore {
                    events += "staged"
                    finish.await()
                    events += "committed and cleaned"
                }
            }
            try {
                assertBusy()
                val recovery = async(start = CoroutineStart.UNDISPATCHED) {
                    BackupRestoreCoordinator.recover { events += "recovered" }
                }
                assertFalse(recovery.isCompleted)
                assertEquals(listOf("staged"), events)
                finish.complete(Unit)
                owner.join()
                recovery.await()
                assertEquals(listOf("staged", "committed and cleaned", "recovered"), events)
            } finally {
                finish.complete(Unit)
                owner.cancelAndJoin()
            }
        }
    }

    @Test fun cancellationKeepsOwnershipUntilRollbackFinishes() = runBlocking {
        withTimeout(5_000) {
            val cleanupStarted = CompletableDeferred<Unit>()
            val finishCleanup = CompletableDeferred<Unit>()
            val owner = launch(start = CoroutineStart.UNDISPATCHED) {
                BackupRestoreCoordinator.restore {
                    try {
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) {
                            cleanupStarted.complete(Unit)
                            finishCleanup.await()
                        }
                    }
                }
            }
            try {
                owner.cancel()
                cleanupStarted.await()
                assertBusy()
            } finally {
                finishCleanup.complete(Unit)
                owner.cancelAndJoin()
            }
            assertEquals("next restore", BackupRestoreCoordinator.restore { "next restore" })
        }
    }

    @Test fun cancellingWaitingRecoveryDoesNotReleaseLiveRestore() = runBlocking {
        withTimeout(5_000) {
            val owner = launch(start = CoroutineStart.UNDISPATCHED) {
                BackupRestoreCoordinator.restore { awaitCancellation() }
            }
            try {
                val recovery = launch(start = CoroutineStart.UNDISPATCHED) {
                    BackupRestoreCoordinator.recover { fail("cancelled recovery must not run") }
                }
                recovery.cancelAndJoin()
                assertBusy()
            } finally {
                owner.cancelAndJoin()
            }
            assertEquals("recovered", BackupRestoreCoordinator.recover { "recovered" })
        }
    }

    @Test fun failuresInRestoreAndRecoveryReleaseOwnership() = runBlocking {
        val failure = IllegalStateException("injected")
        try {
            BackupRestoreCoordinator.restore { throw failure }
            fail("injected restore failure")
        } catch (error: IllegalStateException) {
            assertSame(failure, error)
        }
        try {
            BackupRestoreCoordinator.recover { throw failure }
            fail("injected recovery failure")
        } catch (error: IllegalStateException) {
            assertSame(failure, error)
        }
        assertEquals("next restore", BackupRestoreCoordinator.restore { "next restore" })
    }

    private suspend fun assertBusy() {
        try {
            BackupRestoreCoordinator.restore { fail("a competing restore must never execute") }
            fail("competing restore must be rejected")
        } catch (_: BackupRestoreInProgressException) {
            // Expected: this invocation does not later replay after the owner completes.
        }
    }
}
