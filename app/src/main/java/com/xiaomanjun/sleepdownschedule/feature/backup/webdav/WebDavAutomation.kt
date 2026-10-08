package com.xiaomanjun.sleepdownschedule.feature.backup.webdav

import android.content.Context
import androidx.core.content.edit
import androidx.work.*
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import com.xiaomanjun.sleepdownschedule.feature.backup.BackupCodec
import com.xiaomanjun.sleepdownschedule.feature.backup.BackupExportService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit

internal data class WebDavAutomationState(
    val backup: Boolean = false,
    val restoreCheck: Boolean = false,
    val intervalHours: Int = 24,
    val lastUploadAt: Long = 0,
    val lastUploadedName: String = "",
    val lastReviewedAt: Long = 0,
    val pending: WebDavEntry? = null,
    val acknowledged: String = "",
    val status: String = "",
    val lastCheckAt: Long = 0
)

internal fun WebDavEntry.changeKey() = "$name\n${etag.orEmpty()}\n${modified.orEmpty()}\n${size ?: -1}"
internal fun WebDavEntry.modifiedMillis(): Long? = runCatching {
    ZonedDateTime.parse(modified, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
}.getOrNull()

internal fun newestRestoreCandidate(entries: List<WebDavEntry>, state: WebDavAutomationState): WebDavEntry? =
    entries.filter { entry ->
        val time = entry.modifiedMillis()
        time != null && time > maxOf(state.lastUploadAt, state.lastReviewedAt) &&
            entry.name != state.lastUploadedName && entry.changeKey() != state.acknowledged
    }.maxWithOrNull(compareBy<WebDavEntry> { it.modifiedMillis() }.thenBy { it.name })

/** Local opt-ins are intentionally outside the ordinary backup preference whitelist. */
internal object WebDavAutomation {
    val frequencies = listOf(6 to "每 6 小时", 12 to "每 12 小时", 24 to "每天", 168 to "每周")
    private val mutable = MutableStateFlow(WebDavAutomationState())
    val state = mutable.asStateFlow()
    private fun prefs(context: Context) = context.getSharedPreferences("webdav_automation", Context.MODE_PRIVATE)

    @Synchronized fun load(context: Context): WebDavAutomationState {
        val p = prefs(context)
        val pending = runCatching { JSONObject(p.getString("pending", "")!!).let {
            WebDavEntry(it.getString("name"), it.optLong("size", -1).takeIf { value -> value >= 0 },
                it.optString("modified").ifBlank { null }, it.optString("etag").ifBlank { null })
        } }.getOrNull()
        return WebDavAutomationState(p.getBoolean("backup", false), p.getBoolean("restore", false),
            p.getInt("hours", 24).takeIf { value -> frequencies.any { it.first == value } } ?: 24,
            p.getLong("uploadAt", 0), p.getString("uploadedName", "").orEmpty(), p.getLong("reviewedAt", 0),
            pending, p.getString("acknowledged", "").orEmpty(), p.getString("status", "").orEmpty(),
            p.getLong("checkAt", 0)).also { mutable.value = it }
    }

    @Synchronized fun update(context: Context, transform: (WebDavAutomationState) -> WebDavAutomationState) {
        val next = transform(load(context))
        prefs(context).edit {
            putBoolean("backup", next.backup); putBoolean("restore", next.restoreCheck); putInt("hours", next.intervalHours)
            putLong("uploadAt", next.lastUploadAt); putString("uploadedName", next.lastUploadedName)
            putLong("reviewedAt", next.lastReviewedAt); putString("acknowledged", next.acknowledged)
            putString("status", next.status); putLong("checkAt", next.lastCheckAt)
            putString("pending", next.pending?.let { JSONObject().put("name", it.name).put("size", it.size ?: -1)
                .put("modified", it.modified.orEmpty()).put("etag", it.etag.orEmpty()).toString() }.orEmpty())
        }
        mutable.value = next
    }

    fun acknowledge(context: Context, entry: WebDavEntry) = update(context) {
        it.copy(acknowledged = entry.changeKey(), lastReviewedAt = maxOf(it.lastReviewedAt, entry.modifiedMillis() ?: 0))
    }

    fun connectionSaved(context: Context, connection: WebDavConnection) {
        val profile = java.security.MessageDigest.getInstance("SHA-256").digest(
            (WebDavClient.folderUrl(connection.address).toString() + "\n" + connection.username).toByteArray())
            .joinToString("") { "%02x".format(it) }
        val p = prefs(context)
        val previous = p.getString("profile", null)
        if (previous != null && previous != profile) {
            // Opt-ins and pending restores belong to one remote folder/account.
            update(context) { WebDavAutomationState(intervalHours = it.intervalHours) }
            schedule(context)
        }
        p.edit { putString("profile", profile) }
    }

    fun configure(context: Context, backup: Boolean, restore: Boolean, hours: Int) {
        update(context) { it.copy(backup = backup, restoreCheck = restore, intervalHours = hours,
            pending = if (restore) it.pending else null) }
        schedule(context)
        checkOnForeground(context, force = true)
    }

    fun schedule(context: Context) {
        val settings = load(context)
        val manager = WorkManager.getInstance(context)
        if (!settings.backup && !settings.restoreCheck) {
            manager.cancelUniqueWork("webdav-periodic"); manager.cancelUniqueWork("webdav-check")
            return
        }
        manager.enqueueUniquePeriodicWork("webdav-periodic", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<WebDavBackupWorker>(settings.intervalHours.toLong(), TimeUnit.HOURS)
                .setConstraints(networkConstraints()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build())
    }

    fun checkOnForeground(context: Context, force: Boolean = false) {
        val settings = load(context)
        if (!settings.restoreCheck || (!force && System.currentTimeMillis() - settings.lastCheckAt < 15 * 60_000L)) return
        WorkManager.getInstance(context).enqueueUniqueWork("webdav-check", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<WebDavBackupWorker>().setInputData(workDataOf("checkOnly" to true))
                .setConstraints(networkConstraints()).build())
    }
    private fun networkConstraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
}

/** WorkManager schedules opportunistically; no exact alarms, wake locks or new sync protocol. */
class WebDavBackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = mutex.withLock {
        val app = applicationContext as CourseScheduleApp
        val initial = WebDavAutomation.load(app)
        if (!initial.backup && !initial.restoreCheck) return@withLock Result.success()
        try {
            val connection = withContext(Dispatchers.IO) { WebDavCredentials.read(app) }
                ?: return@withLock fail("请在 WebDAV 页面重新保存连接")
            val client = WebDavClient(connection)
            if (initial.restoreCheck) {
                val candidate = newestRestoreCandidate(client.list(), WebDavAutomation.load(app))
                WebDavAutomation.update(app) { current -> current.copy(lastCheckAt = System.currentTimeMillis(),
                    pending = if (current.restoreCheck) candidate ?: current.pending else null) }
            }
            val settings = WebDavAutomation.load(app)
            if (settings.backup && !inputData.getBoolean("checkOnly", false) &&
                System.currentTimeMillis() - settings.lastUploadAt >= TimeUnit.HOURS.toMillis(settings.intervalHours.toLong())) {
                val file = withContext(Dispatchers.IO) { File.createTempFile("webdav-auto-", ".sleepdown", app.cacheDir) }
                try {
                    val archive = BackupExportService(app, app.database).export()
                    withContext(Dispatchers.IO) { file.outputStream().use { BackupCodec.write(it, archive) } }
                    currentCoroutineContext().ensureActive()
                    val name = "SleepDown-auto-${System.currentTimeMillis()}-${UUID.randomUUID()}.sleepdown"
                    client.upload(file, name)
                    WebDavAutomation.update(app) { it.copy(lastUploadAt = System.currentTimeMillis(), lastUploadedName = name,
                        status = "自动备份已完成") }
                } finally { withContext(NonCancellable + Dispatchers.IO) { file.delete() } }
            } else WebDavAutomation.update(app) { it.copy(status = "远端检查已完成") }
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            val reason = (error as? WebDavFailure)?.reason
            WebDavAutomation.update(app) { it.copy(status = reason?.message ?: "自动备份检查未完成，请检查连接和存储") }
            if (reason in setOf(WebDavFailure.Reason.NETWORK, WebDavFailure.Reason.TIMEOUT) && runAttemptCount < 2)
                Result.retry() else Result.failure()
        }
    }

    private fun fail(message: String): Result {
        WebDavAutomation.update(applicationContext) { it.copy(status = message) }
        return Result.failure()
    }
    companion object { private val mutex = Mutex() }
}
