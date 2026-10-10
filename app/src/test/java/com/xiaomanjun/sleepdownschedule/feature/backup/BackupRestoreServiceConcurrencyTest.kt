package com.xiaomanjun.sleepdownschedule.feature.backup

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import com.xiaomanjun.sleepdownschedule.APP_DATABASE_VERSION
import com.xiaomanjun.sleepdownschedule.AppDatabase
import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.ScheduleProfileEntity
import com.xiaomanjun.sleepdownschedule.data.local.createAppDatabase
import com.xiaomanjun.sleepdownschedule.defaultConfig
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Exercises the public service, real Room transactions, journals, and private asset files together. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BackupRestoreServiceConcurrencyTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var filesRoot: File
    private lateinit var archive: DecodedBackupArchive
    private val databaseName = "restore-concurrency-${UUID.randomUUID()}.db"

    @Before
    fun setUp() {
        filesRoot = Files.createTempDirectory("sleepdown-restore-concurrency").toFile()
        context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = filesRoot
        }
        database = createAppDatabase(context, databaseName)
        archive = wallpaperArchive()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
        filesRoot.deleteRecursively()
    }

    @Test
    fun sameAndDifferentOperationIdsAcrossServiceInstancesCannotAlterActiveStaging() =
        runBlocking(Dispatchers.IO) {
            assertCompetitorsRejectedWhilePaused(BackupRestoreFaultPoint.AFTER_STAGED)
        }

    @Test
    fun restoreStillOwnsTheGateAfterPreferencesCommitUntilCleanupStarts() =
        runBlocking(Dispatchers.IO) {
            assertCompetitorsRejectedWhilePaused(BackupRestoreFaultPoint.BEFORE_CLEANUP)
        }

    @Test
    fun failureRollsBackUncommittedAssetsAndReleasesGateForAnotherService() =
        runBlocking(Dispatchers.IO) {
            val operationId = "failed-restore"
            val failure = InjectedFailure()
            var createdPaths = emptySet<String>()
            val error = runCatching {
                newService().restore(
                    archive, operationId, replaceConfirmed = true,
                    failureInjector = BackupRestoreFailureInjector { point ->
                        if (point == BackupRestoreFaultPoint.AFTER_STAGED) {
                            createdPaths = journal(operationId).readMarker()!!.newlyCreatedAssetPaths
                            throw failure
                        }
                    }
                )
            }.exceptionOrNull()

            assertSame(failure, error)
            assertTrue(createdPaths.isNotEmpty())
            assertTrue(createdPaths.none { File(it).exists() })
            assertFalse(journal(operationId).directory.exists())
            assertTrue(database.configDao().getAllConfigs().isEmpty())
            restoreThroughPreferenceCommit("after-failure")
            assertCommittedWallpaperExists()
        }

    @Test
    fun cancellationKeepsGateUntilOwnerUnwindsThenAllowsAnotherService() =
        runBlocking(Dispatchers.IO) {
            val operationId = "cancelled-restore"
            val pause = PauseAt(BackupRestoreFaultPoint.AFTER_STAGED)
            val restore = launch {
                val ownerJob = currentCoroutineContext().job
                newService().restore(
                    archive, operationId, replaceConfirmed = true,
                    failureInjector = BackupRestoreFailureInjector { point ->
                        pause.check(point)
                        // Observe actual Job cancellation before the synchronous injector returns.
                        ownerJob.ensureActive()
                    }
                )
            }
            try {
                pause.awaitReached()
                val marker = journal(operationId).readMarker()!!
                assertTrue(marker.newlyCreatedAssetPaths.isNotEmpty())
                restore.cancel()

                // Cancelling the caller must not unlock while it still owns staged files.
                val competingError = runCatching {
                    withTimeout(TIMEOUT_MILLIS) {
                        newService().restore(archive, "during-cancellation", replaceConfirmed = true)
                    }
                }.exceptionOrNull()
                assertTrue(competingError.toString(), competingError is BackupRestoreInProgressException)
                assertTrue(marker.newlyCreatedAssetPaths.all { File(it).isFile })

                pause.release()
                withTimeout(TIMEOUT_MILLIS) { restore.join() }
                assertTrue(restore.isCancelled)
                assertFalse(journal(operationId).directory.exists())
                assertTrue(marker.newlyCreatedAssetPaths.none { File(it).exists() })
            } finally {
                pause.release()
                withTimeout(TIMEOUT_MILLIS) { restore.join() }
            }

            restoreThroughPreferenceCommit("after-cancellation")
            assertCommittedWallpaperExists()
        }

    @Test
    fun startupRecoveryWaitsWithoutDeletingAnActiveRestoresJournalOrAssets() =
        runBlocking(Dispatchers.IO) {
            val operationId = "active-during-recovery"
            val pause = PauseAt(BackupRestoreFaultPoint.AFTER_STAGED)
            val failure = InjectedFailure()
            val restore = async {
                runCatching {
                    newService().restore(
                        archive, operationId, replaceConfirmed = true,
                        failureInjector = BackupRestoreFailureInjector { point ->
                            pause.check(point)
                            if (point == BackupRestoreFaultPoint.AFTER_STAGED) throw failure
                        }
                    )
                }
            }
            try {
                pause.awaitReached()
                val before = fileSnapshot()
                assertTrue(journal(operationId).readMarker()!!.newlyCreatedAssetPaths.isNotEmpty())

                // Already on IO: UNDISPATCHED reaches the service's mutex suspension before
                // returning, so this does not depend on a sleep or the worker being scheduled.
                val recovery = async(start = CoroutineStart.UNDISPATCHED) {
                    newService().resumePending()
                }
                try {
                    assertFalse("Recovery must wait for the active restore", recovery.isCompleted)
                    assertEquals(before, fileSnapshot())

                    pause.release()
                    assertSame(failure, withTimeout(TIMEOUT_MILLIS) { restore.await() }.exceptionOrNull())
                    assertTrue(withTimeout(TIMEOUT_MILLIS) { recovery.await() }.isEmpty())
                    assertFalse(journal(operationId).directory.exists())
                } finally {
                    pause.release()
                    withTimeout(TIMEOUT_MILLIS) { recovery.join() }
                }
            } finally {
                pause.release()
                withTimeout(TIMEOUT_MILLIS) { restore.join() }
            }
        }

    @Test
    fun startupRecoveryCanReplayUncertainCommitWithoutReenteringThePublicGate() =
        runBlocking(Dispatchers.IO) {
            val operationId = "uncertain-commit"
            restoreThroughPreferenceCommit(operationId)
            val journal = journal(operationId)
            val committed = journal.readMarker()!!
            // Model a process death after Room commits but before its journal acknowledgment.
            journal.writeMarker(committed.copy(state = BackupRestoreState.STAGED, dbCommitStarted = true))

            val results = withTimeout(TIMEOUT_MILLIS) { newService().resumePending() }
            val result = results.single()
            assertEquals(operationId, result.operationId)
            assertTrue("Recovery did not finish the data commit: $result",
                result.state >= BackupRestoreState.PREFS_COMMITTED)
            // Generic Application deliberately omits app-owned widget/reminder infrastructure.
            // Their cleanup warnings may keep PREFS_COMMITTED; Room and files must still agree.
            assertCommittedWallpaperExists()
        }

    @Test
    fun retainedCommittedJournalBlocksAnotherOperationButAllowsItsOwnRetry() =
        runBlocking(Dispatchers.IO) {
            val operationId = "committed-awaiting-cleanup"
            restoreThroughPreferenceCommit(operationId)
            val before = fileSnapshot()
            val rowsBefore = database.configDao().getAllConfigs()
            val competitorEntered = AtomicBoolean(false)

            val error = runCatching {
                withTimeout(TIMEOUT_MILLIS) {
                    newService().restore(
                        archive, "must-not-replace-pending", replaceConfirmed = true,
                        failureInjector = BackupRestoreFailureInjector {
                            competitorEntered.set(true)
                            throw InjectedFailure()
                        }
                    )
                }
            }.exceptionOrNull()

            assertTrue("A committed pending restore must prevent a different restore", error is IllegalStateException)
            assertFalse(competitorEntered.get())
            assertEquals(before, fileSnapshot())
            assertEquals(rowsBefore, database.configDao().getAllConfigs())
            assertCommittedWallpaperExists()
            restoreThroughPreferenceCommit(operationId)
            assertCommittedWallpaperExists()
        }

    @Test
    fun startupCleanupCallbackRetainsOwnershipUntilItReturns() = runBlocking(Dispatchers.IO) {
        val callbackEntered = CountDownLatch(1)
        val finishCallback = CountDownLatch(1)
        val recovery = async {
            newService().resumePending {
                callbackEntered.countDown()
                check(finishCallback.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    "Test did not release startup cleanup"
                }
            }
        }
        try {
            assertTrue(callbackEntered.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
            val before = fileSnapshot()
            val error = runCatching {
                withTimeout(TIMEOUT_MILLIS) {
                    newService().restore(archive, "during-startup-cleanup", replaceConfirmed = true)
                }
            }.exceptionOrNull()
            assertTrue(error.toString(), error is BackupRestoreInProgressException)
            assertEquals(before, fileSnapshot())
            finishCallback.countDown()
            assertTrue(withTimeout(TIMEOUT_MILLIS) { recovery.await() }.isEmpty())
        } finally {
            finishCallback.countDown()
            withTimeout(TIMEOUT_MILLIS) { recovery.join() }
        }
        restoreThroughPreferenceCommit("after-startup-cleanup")
        assertCommittedWallpaperExists()
    }

    private suspend fun assertCompetitorsRejectedWhilePaused(point: BackupRestoreFaultPoint) =
        kotlinx.coroutines.coroutineScope {
            val operationId = "paused-${point.name.lowercase()}"
            val pause = PauseAt(point)
            val stopBeforeCleanup = InjectedFailure()
            val restore = async {
                runCatching {
                    newService().restore(
                        archive, operationId, replaceConfirmed = true,
                        failureInjector = BackupRestoreFailureInjector { reached ->
                            pause.check(reached)
                            if (reached == BackupRestoreFaultPoint.BEFORE_CLEANUP) throw stopBeforeCleanup
                        }
                    )
                }
            }
            try {
                pause.awaitReached()
                val before = fileSnapshot()
                val rowsBefore = database.configDao().getAllConfigs()
                val marker = journal(operationId).readMarker()!!
                assertTrue(marker.newlyCreatedAssetPaths.isNotEmpty())
                assertTrue(marker.newlyCreatedAssetPaths.all { File(it).isFile })

                for (competingId in listOf(operationId, "different-operation")) {
                    val competitorEntered = AtomicBoolean(false)
                    val error = runCatching {
                        withTimeout(TIMEOUT_MILLIS) {
                            newService().restore(
                                archive, competingId, replaceConfirmed = true,
                                failureInjector = BackupRestoreFailureInjector {
                                    competitorEntered.set(true)
                                    throw InjectedFailure()
                                }
                            )
                        }
                    }.exceptionOrNull()
                    assertTrue("Competing restore was not rejected: $error",
                        error is BackupRestoreInProgressException)
                    assertFalse("Competing service entered restore work", competitorEntered.get())
                    assertEquals("Competing restore changed journal or assets", before, fileSnapshot())
                    assertEquals(rowsBefore, database.configDao().getAllConfigs())
                }

                pause.release()
                assertSame(stopBeforeCleanup,
                    withTimeout(TIMEOUT_MILLIS) { restore.await() }.exceptionOrNull())
                assertEquals(BackupRestoreState.PREFS_COMMITTED, journal(operationId).readMarker()!!.state)
                assertCommittedWallpaperExists()
            } finally {
                pause.release()
                withTimeout(TIMEOUT_MILLIS) { restore.join() }
            }
        }

    private suspend fun restoreThroughPreferenceCommit(operationId: String) {
        val stopBeforeCleanup = InjectedFailure()
        val error = runCatching {
            withTimeout(TIMEOUT_MILLIS) {
                newService().restore(
                    archive, operationId, replaceConfirmed = true,
                    failureInjector = BackupRestoreFailureInjector { point ->
                        if (point == BackupRestoreFaultPoint.BEFORE_CLEANUP) throw stopBeforeCleanup
                    }
                )
            }
        }.exceptionOrNull()
        assertSame("A later restore must acquire the released gate", stopBeforeCleanup, error)
        assertEquals(BackupRestoreState.PREFS_COMMITTED, journal(operationId).readMarker()!!.state)
    }

    private suspend fun assertCommittedWallpaperExists() {
        val configs = database.configDao().getAllConfigs()
        assertEquals(1, configs.size)
        val uri = configs.single().wallpaperUri
        assertNotNull(uri)
        val file = File(requireNotNull(Uri.parse(uri).path))
        assertTrue("Room wallpaper URI points to a missing file: $uri", file.isFile)
        assertArrayEquals(WALLPAPER_BYTES, file.readBytes())
    }

    private fun newService() = BackupRestoreService(context, database)
    private fun journal(operationId: String) = BackupRestoreJournal(filesRoot, operationId)

    private fun fileSnapshot(): Map<String, List<Byte>> = filesRoot.walkTopDown()
        .filter(File::isFile)
        .associate { it.relativeTo(filesRoot).path to it.readBytes().toList() }

    private fun wallpaperArchive(): DecodedBackupArchive = BackupCodec.decode(BackupCodec.encode(
        BackupExportMapper.toArchive(
            metadata = BackupSourceMetadata(
                createdAt = "2026-10-10T00:00:00Z",
                sourceAppVersionName = "test",
                sourceVersionCode = 1,
                sourcePackageName = context.packageName,
                sourceDatabaseVersion = APP_DATABASE_VERSION,
                devicePlatform = "Android"
            ),
            snapshot = BackupRoomSnapshot(
                schedules = listOf(ScheduleProfileEntity(7, "Restored schedule", true)),
                configs = listOf(defaultConfig(7).copy(
                    wallpaperUri = "content://test/wallpaper", notificationsEnabled = false
                )),
                periods = listOf(PeriodEntity(1, "08:00", "08:45", 7))
            ),
            preferences = BackupPreferences(BackupFormatV1.PREFERENCES_VERSION),
            assetInputs = listOf(BackupExportAssetInput(
                sourceKey = "schedule-wallpaper:7:content://test/wallpaper",
                bytes = WALLPAPER_BYTES,
                mediaType = "image/png"
            ))
        )
    ))

    private class InjectedFailure : IllegalStateException("Deliberate restore fault")

    private class PauseAt(private val point: BackupRestoreFaultPoint) : BackupRestoreFailureInjector {
        private val reached = CountDownLatch(1)
        private val proceed = CountDownLatch(1)

        override fun check(point: BackupRestoreFaultPoint) {
            if (point != this.point) return
            reached.countDown()
            check(proceed.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                "Test did not release restore at $point"
            }
        }

        fun awaitReached() {
            assertTrue("Restore did not reach $point", reached.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        }

        fun release() = proceed.countDown()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
        val WALLPAPER_BYTES = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 1, 2, 3, 4)
    }
}
