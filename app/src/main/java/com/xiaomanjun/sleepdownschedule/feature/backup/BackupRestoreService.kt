package com.xiaomanjun.sleepdownschedule.feature.backup

import com.xiaomanjun.sleepdownschedule.app.startup.*
import com.xiaomanjun.sleepdownschedule.feature.schedule.*

import com.xiaomanjun.sleepdownschedule.core.wallpaper.*
import com.xiaomanjun.sleepdownschedule.feature.importing.*
import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler

import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.agent.*
import com.xiaomanjun.sleepdownschedule.feature.widget.*

import com.xiaomanjun.sleepdownschedule.core.identity.AppIdentity

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

enum class BackupRestoreFaultPoint {
    AFTER_VALIDATED,
    AFTER_STAGED,
    BEFORE_DATABASE_COMMIT,
    AFTER_DATABASE_COMMIT,
    BEFORE_PREFERENCES_COMMIT,
    AFTER_PREFERENCES_COMMIT,
    BEFORE_CLEANUP
}

fun interface BackupRestoreFailureInjector {
    fun check(point: BackupRestoreFaultPoint)
}

object NoBackupRestoreFailureInjector : BackupRestoreFailureInjector {
    override fun check(point: BackupRestoreFaultPoint) = Unit
}

class BackupRestoreRequiresConfirmationException(message: String) : IllegalStateException(message)

data class BackupRestoreResult(
    val operationId: String,
    val state: BackupRestoreState,
    val warnings: List<String> = emptyList(),
    val resumed: Boolean = false
)

/**
 * Orchestrates the frozen READ_ONLY → VALIDATED → STAGED → DB_COMMITTED → PREFS_COMMITTED →
 * FINALIZED boundary. The service intentionally has no UI dependency; Preview/confirmation calls
 * this service only after the user has explicitly accepted Replace.
 */
class BackupRestoreService(
    context: Context,
    private val database: AppDatabase
) {
    private val appContext = context.applicationContext

    suspend fun readTargetSnapshot(): BackupImportTargetSnapshot = database.withTransaction {
        val knownPreferenceFiles = listOf(
            "app_icon_preferences",
            "day_agent_preferences",
            "ai_import_settings",
            "ai_import_history"
        )
        BackupImportTargetSnapshot(
            scheduleIds = database.scheduleProfileDao().getProfiles().mapTo(linkedSetOf()) { it.id },
            courseIds = database.courseDao().getAllCourses().mapTo(linkedSetOf()) { it.id },
            schemeIds = database.periodSchemeDao().getAllSchemes().mapTo(linkedSetOf()) { it.id },
            messageIds = database.agentDao().getAllMessages().mapTo(linkedSetOf()) { it.id },
            hasPreferences = knownPreferenceFiles.any {
                appContext.getSharedPreferences(it, Context.MODE_PRIVATE).all.isNotEmpty()
            } || AiImportHistoryStore.loadForBackup(appContext).isNotEmpty(),
            hasWidgetAppearances = database.widgetAppearanceDao().getAll().isNotEmpty()
        )
    }

    suspend fun restore(
        archive: DecodedBackupArchive,
        operationId: String = newBackupRestoreOperationId(),
        replaceConfirmed: Boolean,
        existingTarget: BackupImportTargetSnapshot? = null,
        failureInjector: BackupRestoreFailureInjector = NoBackupRestoreFailureInjector
    ): BackupRestoreResult = withContext(Dispatchers.IO) {
        AppIdentity.requireTrustedBackupSource(
            archive.manifest.sourcePackageName,
            appContext.packageName
        )
        val journal = BackupRestoreJournal(appContext.filesDir, operationId)
        val fingerprint = archiveFingerprint(archive)
        var marker = journal.readMarker()
        if (marker == null) {
            val target = existingTarget ?: readTargetSnapshot()
            val plan = BackupImportPlanBuilder.build(archive, operationId, target)
            if (plan.requiresExplicitReplaceConfirmation && !replaceConfirmed) {
                throw BackupRestoreRequiresConfirmationException("当前应用已有数据，恢复前必须明确确认 Replace")
            }
            marker = BackupRestoreMarker(
                operationId = operationId,
                archiveFingerprint = fingerprint,
                state = BackupRestoreState.READ_ONLY,
                plan = plan
            )
            journal.writeMarker(marker)
            journal.writePreferences(archive.preferences)
            journal.writePayload(BackupRestorePayload.fromArchive(archive))
            marker = marker.copy(state = BackupRestoreState.VALIDATED)
            journal.writeMarker(marker)
        } else {
            require(marker.archiveFingerprint == fingerprint) {
                "同一 restore operationId 不能替换为另一份备份"
            }
        }

        if (marker.state == BackupRestoreState.FINALIZED) {
            return@withContext BackupRestoreResult(
                operationId = operationId,
                state = BackupRestoreState.FINALIZED,
                warnings = planWarnings(marker.plan),
                resumed = true
            )
        }

        failureInjector.check(BackupRestoreFaultPoint.AFTER_VALIDATED)
        var dbMayBeCommitted = marker.state.ordinal >= BackupRestoreState.DB_COMMITTED.ordinal
        var createdPathsInMemory = emptySet<String>()
        try {
            if (marker.state.ordinal < BackupRestoreState.DB_COMMITTED.ordinal) {
                val stage = BackupAssetStager.stage(appContext.filesDir, archive, operationId)
                val restoredAssets = BackupPrivateAssetRestorer.prepare(
                    context = appContext,
                    operationId = operationId,
                    archive = archive,
                    stage = stage
                )
                createdPathsInMemory = restoredAssets.newlyCreatedPaths
                val rows = BackupRoomRestoreMapper.map(
                    archive = archive,
                    plan = marker.plan,
                    assetUrisById = restoredAssets.urisByAssetId,
                    attachmentFileNamesByAssetId = restoredAssets.attachmentFileNamesByAssetId
                )
                marker = marker.copy(
                    state = BackupRestoreState.STAGED,
                    finalAssetPathsById = restoredAssets.pathsByAssetId,
                    newlyCreatedAssetPaths = restoredAssets.newlyCreatedPaths,
                    dbCommitStarted = false
                )
                journal.writeMarker(marker)
                failureInjector.check(BackupRestoreFaultPoint.AFTER_STAGED)
                failureInjector.check(BackupRestoreFaultPoint.BEFORE_DATABASE_COMMIT)
                marker = marker.copy(dbCommitStarted = true)
                journal.writeMarker(marker)
                BackupRoomReplaceTransaction(database).replace(rows)
                dbMayBeCommitted = true
                marker = marker.copy(
                    state = BackupRestoreState.DB_COMMITTED,
                    dbCommitStarted = false
                )
                journal.writeMarker(marker)
                failureInjector.check(BackupRestoreFaultPoint.AFTER_DATABASE_COMMIT)
            }
            finishCommitted(
                journal = journal,
                marker = marker,
                failureInjector = failureInjector,
                resumed = false
            )
        } catch (error: Throwable) {
            val commitUncertain = dbMayBeCommitted || marker.dbCommitStarted ||
                marker.state.ordinal >= BackupRestoreState.DB_COMMITTED.ordinal
            if (!commitUncertain) {
                val paths = marker.newlyCreatedAssetPaths + createdPathsInMemory
                runCatching { BackupPrivateAssetRestorer.deleteNewlyCreatedFiles(paths, appContext) }
                runCatching { journal.deleteOperation() }
            }
            throw error
        }
    }

    /** Called from Application startup; only journaled, already-confirmed operations are resumed. */
    suspend fun resumePending(): List<BackupRestoreResult> = withContext(Dispatchers.IO) {
        val root = File(appContext.filesDir, ".sleepdown_restore")
        if (!root.isDirectory) return@withContext emptyList()
        root.listFiles().orEmpty()
            .filter(File::isDirectory)
            .mapNotNull { operationDirectory ->
                val operationId = operationDirectory.name
                val journal = runCatching { BackupRestoreJournal(appContext.filesDir, operationId) }
                    .getOrNull() ?: return@mapNotNull null
                val marker = runCatching { journal.readMarker() }.getOrNull() ?: return@mapNotNull null
                runCatching {
                    when {
                        marker.state == BackupRestoreState.FINALIZED -> {
                            runCatching { journal.deleteOperation() }
                            BackupRestoreResult(operationId, BackupRestoreState.FINALIZED, resumed = true)
                        }
                        marker.state.ordinal < BackupRestoreState.DB_COMMITTED.ordinal &&
                            !marker.dbCommitStarted -> {
                            BackupPrivateAssetRestorer.deleteNewlyCreatedFiles(
                                marker.newlyCreatedAssetPaths,
                                appContext
                            )
                            journal.deleteOperation()
                            BackupRestoreResult(
                                operationId,
                                marker.state,
                                warnings = listOf("未提交的 restore staging 已安全清理"),
                                resumed = true
                            )
                        }
                        marker.state.ordinal < BackupRestoreState.DB_COMMITTED.ordinal -> {
                            val archive = restoreArchiveFromPayload(journal)
                            restore(
                                archive = archive,
                                operationId = operationId,
                                replaceConfirmed = true,
                                failureInjector = NoBackupRestoreFailureInjector
                            )
                        }
                        else -> finishCommitted(
                            journal = journal,
                            marker = marker,
                            failureInjector = NoBackupRestoreFailureInjector,
                            resumed = true
                        )
                    }
                }.getOrElse { error ->
                    BackupRestoreResult(
                        operationId = operationId,
                        state = marker.state,
                        warnings = listOf("restore resume 失败：${error.message.orEmpty()}"),
                        resumed = true
                    )
                }
            }
    }

    private suspend fun finishCommitted(
        journal: BackupRestoreJournal,
        marker: BackupRestoreMarker,
        failureInjector: BackupRestoreFailureInjector,
        resumed: Boolean
    ): BackupRestoreResult {
        var current = marker
        val warnings = planWarnings(current.plan).toMutableList()
        if (current.state.ordinal < BackupRestoreState.PREFS_COMMITTED.ordinal) {
            failureInjector.check(BackupRestoreFaultPoint.BEFORE_PREFERENCES_COMMIT)
            val preferences = journal.readPreferences()
            val files = finalFiles(current.finalAssetPathsById)
            BackupPreferencesApplier.apply(
                context = appContext,
                preferences = preferences,
                scheduleRoomIdsByStableId = current.plan.scheduleIds,
                finalAssetFilesById = files
            )
            current = current.copy(state = BackupRestoreState.PREFS_COMMITTED)
            journal.writeMarker(current)
            failureInjector.check(BackupRestoreFaultPoint.AFTER_PREFERENCES_COMMIT)
        }
        if (current.state == BackupRestoreState.PREFS_COMMITTED) {
            failureInjector.check(BackupRestoreFaultPoint.BEFORE_CLEANUP)
            val cleanupWarnings = cleanupCommittedState(journal, current)
            if (cleanupWarnings.isNotEmpty()) {
                warnings += cleanupWarnings
                return BackupRestoreResult(
                    operationId = current.operationId,
                    state = BackupRestoreState.PREFS_COMMITTED,
                    warnings = warnings,
                    resumed = resumed
                )
            }
            current = current.copy(state = BackupRestoreState.FINALIZED)
            journal.writeMarker(current)
            runCatching { journal.deleteOperation() }
                .onFailure { warnings += "restore staging 清理失败：${it.message.orEmpty()}" }
        }
        return BackupRestoreResult(
            operationId = current.operationId,
            state = current.state,
            warnings = warnings,
            resumed = resumed
        )
    }

    private suspend fun cleanupCommittedState(
        journal: BackupRestoreJournal,
        marker: BackupRestoreMarker
    ): List<String> = withContext(Dispatchers.IO) {
        val warnings = mutableListOf<String>()
        suspend fun guarded(label: String, block: suspend () -> Unit) {
            try {
                block()
            } catch (error: Throwable) {
                warnings += "$label：${error.message.orEmpty()}"
            }
        }
        guarded("课表壁纸清理失败") {
            val referenced = database.configDao().getAllConfigs().mapNotNull { it.wallpaperUri }
            cleanupUnreferencedScheduleWallpapers(appContext, referenced)
        }
        guarded("widget 壁纸清理失败") {
            WidgetAppearanceRepository(appContext, database).run {
                ensureDefaults()
                cleanupUnreferencedFiles()
            }
        }
        guarded("Agent 附件清理失败") {
            val referenced = database.agentDao().getAllMessageContents()
                .mapNotNull { parseAgentMessageContent(it).attachmentFileName }
                .toSet()
            val directory = File(appContext.filesDir, "agent_attachments")
            val existing = directory.listFiles().orEmpty().filter(File::isFile).mapTo(linkedSetOf()) { it.name }
            orphanedAgentAttachmentNames(existing, referenced).forEach { File(directory, it).delete() }
        }
        guarded("AI history context 清理失败") {
            val retained = journal.readPreferences().aiImportHistory.mapTo(linkedSetOf()) { it.id }
            AiImportHistoryStore.cleanupUnreferencedContextFiles(appContext, retained)
        }
        guarded("schedule snapshot 清理失败") {
            val ids = database.scheduleProfileDao().getProfiles().map { it.id }
            // Snapshot files are derived caches and are intentionally not part of the archive.
            ScheduleSnapshotStore.cleanupUnreferenced(appContext, ids)
        }
        guarded("未引用 restore 辅助资源清理失败") {
            marker.finalAssetPathsById.values
                .map(::File)
                .filter { it.parentFile?.name == "sleepdown_restore_assets" }
                .forEach { if (it.isFile) it.delete() }
        }
        guarded("课程提醒刷新失败") {
            val snapshot = ScheduleRepository(database).activeSnapshot()
            NotificationScheduler.refreshToday(
                appContext,
                snapshot.courses,
                snapshot.config,
                snapshot.periods
            )
        }
        guarded("桌面小组件刷新失败") {
            TodayCoursesWidgetProvider.refreshAll(appContext)
        }
        warnings
    }

    private fun finalFiles(paths: Map<String, String>): Map<String, File> {
        val root = appContext.filesDir.canonicalFile
        return paths.mapValues { (_, path) ->
            val file = File(path).canonicalFile
            val rootPath = root.path
            require(file.path == rootPath || file.path.startsWith(rootPath + File.separator)) {
                "restore final asset 路径越界"
            }
            file
        }
    }

    private fun restoreArchiveFromPayload(journal: BackupRestoreJournal): DecodedBackupArchive {
        val payload = journal.readPayload()
        val descriptors = payload.manifest.assets.associateBy { it.assetId }
        val assets = payload.assets.map { metadata ->
            val descriptor = descriptors[metadata.assetId]
                ?: error("restore payload asset 缺少 manifest descriptor")
            val bytes = if (descriptor.present) {
                val file = File(journal.directory, descriptor.relativePath).canonicalFile
                val root = journal.directory.canonicalFile
                require(file.path.startsWith(root.path + File.separator)) { "restore payload asset 路径越界" }
                require(file.isFile && file.length() <= BackupCodecLimits.MAX_ASSET_BYTES) {
                    "restore payload staged asset 缺失或过大"
                }
                file.readBytes()
            } else {
                null
            }
            BackupAsset(
                assetId = metadata.assetId,
                category = metadata.category,
                purpose = metadata.purpose,
                mediaType = metadata.mediaType,
                bytes = bytes,
                optional = metadata.optional,
                ownerId = metadata.ownerId,
                missingReason = metadata.missingReason
            )
        }
        return DecodedBackupArchive(
            manifest = payload.manifest,
            data = payload.data,
            preferences = payload.preferences,
            checksums = payload.checksums,
            assets = assets
        )
    }

    private fun planWarnings(plan: BackupImportPlan): List<String> = plan.warnings.map { it.message }

    private fun archiveFingerprint(archive: DecodedBackupArchive): String {
        val material = buildString {
            append(archive.manifest.formatVersion).append('|')
            append(archive.manifest.createdAt).append('|')
            archive.checksums.entries.toSortedMap().forEach { (name, digest) ->
                append(name).append('=').append(digest).append(';')
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

}

internal fun newBackupRestoreOperationId(): String = "restore_${UUID.randomUUID()}"

/** Naming alias used by the future SAF/UI integration without duplicating restore semantics. */
typealias BackupService = BackupRestoreService
