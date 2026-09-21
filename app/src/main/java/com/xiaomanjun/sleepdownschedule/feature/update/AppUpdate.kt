package com.xiaomanjun.sleepdownschedule.feature.update

import com.xiaomanjun.sleepdownschedule.*

import com.xiaomanjun.sleepdownschedule.core.identity.AppDistribution
import com.xiaomanjun.sleepdownschedule.core.identity.applyAppNotificationIcon

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.graphics.drawable.Icon
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

private const val GiteeOwner = "xiaomanjun233"
private const val GiteeRepository = "SleepDown-Schedule"
private const val GiteeApiBase = "https://gitee.com/api/v5"
private const val GiteeRepositoryUrl = "https://gitee.com/$GiteeOwner/$GiteeRepository"
private const val ApkMimeType = "application/vnd.android.package-archive"
private const val UpdatePreferences = "app_update_state"
private const val LastCheckDateKey = "last_check_date"
private const val LatestTagKey = "latest_tag"
private const val IncludeBetaKey = "include_beta"
private const val UpdateChannelKey = "update_channel"

data class GiteeReleaseInfo(
    val name: String,
    val tagName: String,
    val notes: String,
    val apkName: String?,
    val apkUrl: String?,
    val releaseUrl: String,
    val prerelease: Boolean,
    val packageKind: DownloadPackageKind = DownloadPackageKind.AppUpdate
)

enum class DownloadPackageKind {
    AppUpdate,
    CourseComponent
}

enum class AppUpdateChannel {
    Stable,
    Beta,
    Experimental
}

sealed interface GiteeUpdateCheckResult {
    data class UpdateAvailable(val release: GiteeReleaseInfo) : GiteeUpdateCheckResult
    data class UpToDate(val release: GiteeReleaseInfo) : GiteeUpdateCheckResult
}

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(
        val releaseTag: String,
        val releaseName: String,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateDownloadState {
        val progressPercent: Int?
            get() = totalBytes.takeIf { it > 0L }
                ?.let { ((downloadedBytes * 100L) / it).toInt().coerceIn(0, 100) }
    }
    data class Completed(val releaseTag: String, val apk: File) : UpdateDownloadState
    data class Failed(val releaseTag: String, val message: String) : UpdateDownloadState
}

object GiteeAppUpdater {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable: StateFlow<Boolean> = _updateAvailable.asStateFlow()
    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    fun updateChannel(context: Context): AppUpdateChannel {
        val preferences = preferences(context)
        return preferences.getString(UpdateChannelKey, null)
            ?.let { stored -> AppUpdateChannel.entries.firstOrNull { it.name == stored } }
            ?: if (preferences.getBoolean(IncludeBetaKey, false)) {
                AppUpdateChannel.Beta
            } else {
                AppUpdateChannel.Stable
            }
    }

    fun setUpdateChannel(context: Context, channel: AppUpdateChannel) {
        preferences(context).edit {
            putString(UpdateChannelKey, channel.name)
            putBoolean(IncludeBetaKey, channel == AppUpdateChannel.Beta)
            remove(LastCheckDateKey)
            remove(LatestTagKey)
        }
        _updateAvailable.value = false
    }

    fun includesBeta(context: Context): Boolean = updateChannel(context) == AppUpdateChannel.Beta

    fun setIncludesBeta(context: Context, enabled: Boolean) {
        setUpdateChannel(context, if (enabled) AppUpdateChannel.Beta else AppUpdateChannel.Stable)
    }

    fun restoreCachedStatus(context: Context, currentVersionName: String) {
        if (!AppDistribution.supportsSelfUpdate) {
            _updateAvailable.value = false
            return
        }
        val latestTag = preferences(context).getString(LatestTagKey, null)
        val channel = updateChannel(context)
        _updateAvailable.value = latestTag?.let {
            channel.acceptsTag(it) && isVersionNewer(it, currentVersionName)
        } == true
    }

    fun shouldRunDailyCheck(context: Context, date: LocalDate = LocalDate.now()): Boolean =
        AppDistribution.supportsSelfUpdate &&
            preferences(context).getString(LastCheckDateKey, null) != date.toString()

    fun markDailyCheckStarted(context: Context, date: LocalDate = LocalDate.now()) {
        preferences(context).edit {putString(LastCheckDateKey, date.toString())}
    }

    fun recordCheckResult(context: Context, result: GiteeUpdateCheckResult) {
        val release = when (result) {
            is GiteeUpdateCheckResult.UpdateAvailable -> result.release
            is GiteeUpdateCheckResult.UpToDate -> result.release
        }
        preferences(context).edit {putString(LatestTagKey, release.tagName)}
        _updateAvailable.value = result is GiteeUpdateCheckResult.UpdateAvailable
    }

    suspend fun checkForUpdate(context: Context, currentVersionName: String): Result<GiteeUpdateCheckResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                check(AppDistribution.supportsSelfUpdate) {
                    "当前应用商店发行版不支持应用内 APK 更新"
                }
                val releases = readReleaseObjects().map { it.toReleaseInfo() }
                val release = selectRelease(releases, updateChannel(context))
                    ?: error("当前更新渠道暂无可用版本")
                if (isVersionNewer(release.tagName, currentVersionName)) {
                    GiteeUpdateCheckResult.UpdateAvailable(release)
                } else {
                    GiteeUpdateCheckResult.UpToDate(release)
                }
            }
        }

    suspend fun findReleaseAsset(
        expectedNames: Set<String>,
        displayName: String,
        packageKind: DownloadPackageKind
    ): Result<GiteeReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedNames = expectedNames.mapTo(linkedSetOf()) { it.lowercase() }
            readReleaseObjects().forEach { release ->
                val asset = release.releaseAssets().firstOrNull {
                    it.name.lowercase() in normalizedNames
                } ?: return@forEach
                val sourceTag = release.string("tag_name").ifBlank { release.string("name") }
                require(sourceTag.isNotBlank()) { "Gitee 版本缺少版本标签" }
                val releasePage = release.string("html_url").ifBlank {
                    "$GiteeRepositoryUrl/releases/tag/${Uri.encode(sourceTag)}"
                }
                return@runCatching GiteeReleaseInfo(
                    name = displayName,
                    tagName = "course-component-$sourceTag-${asset.name.hashCode()}",
                    notes = release.string("body"),
                    apkName = asset.name,
                    apkUrl = asset.url,
                    releaseUrl = releasePage,
                    prerelease = true,
                    packageKind = packageKind
                )
            }
            error("暂未找到课程组件，请稍后重试")
        }
    }

    suspend fun downloadApk(context: Context, release: GiteeReleaseInfo): Result<File> {
        if (!AppDistribution.supportsSelfUpdate) {
            return Result.failure(IllegalStateException("当前应用商店发行版不支持应用内 APK 更新"))
        }
        val downloadUrl = release.apkUrl
            ?: return Result.failure(IllegalStateException("这个 Release 没有附带 APK 安装包"))
        startDownloadService(context, release, downloadUrl)
        return when (val terminal = downloadState.first { state ->
            when (state) {
                is UpdateDownloadState.Completed -> state.releaseTag == release.tagName
                is UpdateDownloadState.Failed -> state.releaseTag == release.tagName
                else -> false
            }
        }) {
            is UpdateDownloadState.Completed -> Result.success(terminal.apk)
            is UpdateDownloadState.Failed -> Result.failure(IllegalStateException(terminal.message))
            else -> error("unreachable")
        }
    }

    @Synchronized
    private fun startDownloadService(context: Context, release: GiteeReleaseInfo, downloadUrl: String) {
        val current = _downloadState.value
        if (current is UpdateDownloadState.Downloading) {
            if (current.releaseTag == release.tagName) return
            _downloadState.value = UpdateDownloadState.Failed(
                release.tagName,
                "已有其他版本正在下载，请等待完成后重试"
            )
            return
        }
        if (current is UpdateDownloadState.Completed && current.releaseTag == release.tagName && current.apk.exists()) return
        _downloadState.value = UpdateDownloadState.Downloading(release.tagName, release.name, 0L, -1L)
        val intent = Intent(context, UpdateDownloadForegroundService::class.java)
            .setAction(UpdateDownloadForegroundService.ACTION_DOWNLOAD)
            .putExtra(UpdateDownloadForegroundService.EXTRA_URL, downloadUrl)
            .putExtra(UpdateDownloadForegroundService.EXTRA_TAG, release.tagName)
            .putExtra(UpdateDownloadForegroundService.EXTRA_NAME, release.name)
            .putExtra(UpdateDownloadForegroundService.EXTRA_APK_NAME, release.apkName)
            .putExtra(UpdateDownloadForegroundService.EXTRA_PACKAGE_KIND, release.packageKind.name)
        ContextCompat.startForegroundService(context.applicationContext, intent)
    }

    internal fun publishDownloadState(state: UpdateDownloadState) {
        _downloadState.value = state
    }

    internal suspend fun performDownload(
        context: Context,
        url: String,
        tag: String,
        apkName: String?,
        onProgress: (Long, Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
            updateDir.listFiles()?.forEach { it.delete() }
            val safeName = apkName
                ?.takeIf { it.endsWith(".apk", ignoreCase = true) }
                ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
                ?: "SleepDown-${tag.replace(Regex("[^A-Za-z0-9._-]"), "_")}.apk"
            val target = File(updateDir, safeName)
            downloadToFile(url, target, onProgress)
            require(target.length() > 0L) { "下载到的安装包为空" }
            target
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri()
        )

    fun launchInstaller(context: Context, apk: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(apkUri, ApkMimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    internal fun isVersionNewer(latest: String, current: String): Boolean {
        val latestVersion = ParsedVersion.parse(latest)
        val currentVersion = ParsedVersion.parse(current)
        val maxSize = maxOf(latestVersion.numbers.size, currentVersion.numbers.size)
        repeat(maxSize) { index ->
            val latestPart = latestVersion.numbers.getOrElse(index) { 0 }
            val currentPart = currentVersion.numbers.getOrElse(index) { 0 }
            if (latestPart != currentPart) return latestPart > currentPart
        }
        if (latestVersion.isPrerelease != currentVersion.isPrerelease) {
            return !latestVersion.isPrerelease
        }
        if (latestVersion.stage != currentVersion.stage) return latestVersion.stage > currentVersion.stage
        return latestVersion.sequence > currentVersion.sequence
    }

    internal fun selectRelease(releases: List<GiteeReleaseInfo>, includeBeta: Boolean): GiteeReleaseInfo? =
        selectRelease(
            releases,
            if (includeBeta) AppUpdateChannel.Beta else AppUpdateChannel.Stable
        )

    internal fun selectRelease(
        releases: List<GiteeReleaseInfo>,
        channel: AppUpdateChannel
    ): GiteeReleaseInfo? =
        releases.filter { channel.accepts(it) }
            .maxWithOrNull { left, right ->
                when {
                    isVersionNewer(left.tagName, right.tagName) -> 1
                    isVersionNewer(right.tagName, left.tagName) -> -1
                    else -> 0
                }
            }

    private fun AppUpdateChannel.accepts(release: GiteeReleaseInfo): Boolean {
        val version = ParsedVersion.parse(release.tagName)
        return when (this) {
            AppUpdateChannel.Stable -> !release.prerelease && !version.isPrerelease
            AppUpdateChannel.Beta -> !version.isExperimental
            AppUpdateChannel.Experimental -> version.isExperimental
        }
    }

    private fun AppUpdateChannel.acceptsTag(tag: String): Boolean {
        val version = ParsedVersion.parse(tag)
        return when (this) {
            AppUpdateChannel.Stable -> !version.isPrerelease
            AppUpdateChannel.Beta -> !version.isExperimental
            AppUpdateChannel.Experimental -> version.isExperimental
        }
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(UpdatePreferences, Context.MODE_PRIVATE)

    private fun JsonObject.toReleaseInfo(): GiteeReleaseInfo {
        val tag = string("tag_name").ifBlank { string("name") }
        require(tag.isNotBlank()) { "Gitee Release 缺少版本标签" }
        val asset = releaseAssets().firstOrNull(ReleaseAsset::isAppUpdateApk)
        val releasePage = string("html_url").ifBlank {
            "$GiteeRepositoryUrl/releases/tag/${Uri.encode(tag)}"
        }
        return GiteeReleaseInfo(
            name = string("name").ifBlank { tag },
            tagName = tag,
            notes = string("body"),
            apkName = asset?.name,
            apkUrl = asset?.url,
            releaseUrl = releasePage,
            prerelease = (this["prerelease"] as? JsonPrimitive)?.booleanOrNull ?: false
        )
    }

    private fun readReleaseObjects(): List<JsonObject> {
        val releases = mutableListOf<JsonObject>()
        var page = 1
        do {
            val endpoint = "$GiteeApiBase/repos/$GiteeOwner/$GiteeRepository/releases?page=$page&per_page=100"
            val entries = json.parseToJsonElement(readText(endpoint)) as? JsonArray
                ?: error("Gitee 返回了无法识别的版本列表")
            releases += entries.map { it.jsonObjectOrThrow() }
            page++
        } while (entries.size == 100)
        return releases
    }

    private fun JsonObject.releaseAssets(): List<ReleaseAsset> {
        val containers = listOfNotNull(this["assets"], this["attach_files"])
        return containers.flatMap { it.collectReleaseAssets() }.distinctBy { it.url }
    }

    private fun JsonElement.collectReleaseAssets(): List<ReleaseAsset> = when (this) {
        is JsonArray -> flatMap { it.collectReleaseAssets() }
        is JsonObject -> {
            val name = string("name").ifBlank { string("filename") }
            val url = listOf("browser_download_url", "download_url", "url")
                .asSequence()
                .map(::string)
                .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
            val current = if (url != null) listOf(ReleaseAsset(name, url)) else emptyList()
            current + values.flatMap { child ->
                if (child is JsonArray || child is JsonObject) child.collectReleaseAssets() else emptyList()
            }
        }
        else -> emptyList()
    }

    private fun readText(url: String): String {
        val connection = openConnection(url)
        return connection.useResponse { input -> input.bufferedReader(Charsets.UTF_8).use { it.readText() } }
    }

    private fun downloadToFile(url: String, target: File, onProgress: (Long, Long) -> Unit) {
        val connection = openConnection(url, connectTimeoutMillis = 20_000, readTimeoutMillis = 120_000)
        val totalBytes = connection.contentLengthLong
        connection.useResponse { input ->
            target.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var downloaded = 0L
                var lastReportedAt = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    downloaded += count
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastReportedAt >= 180L || downloaded == totalBytes) {
                        onProgress(downloaded, totalBytes)
                        lastReportedAt = now
                    }
                }
                onProgress(downloaded, totalBytes)
            }
        }
    }

    private fun openConnection(
        url: String,
        connectTimeoutMillis: Int = 12_000,
        readTimeoutMillis: Int = 20_000
    ): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = connectTimeoutMillis
        readTimeout = readTimeoutMillis
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json, application/octet-stream")
        setRequestProperty("User-Agent", "SleepDown-Schedule/${BuildConfig.VERSION_NAME}")
    }

    private inline fun <T> HttpURLConnection.useResponse(block: (java.io.InputStream) -> T): T {
        try {
            val status = responseCode
            if (status !in 200..299) {
                val detail = errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                throw IllegalStateException("Gitee 请求失败（$status）${detail.take(160)}")
            }
            return inputStream.use(block)
        } finally {
            disconnect()
        }
    }
}

class UpdateDownloadForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var activeTag: String? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_DOWNLOAD) return START_NOT_STICKY
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val tag = intent.getStringExtra(EXTRA_TAG).orEmpty()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { tag }
        val apkName = intent.getStringExtra(EXTRA_APK_NAME)
        val packageKind = intent.getStringExtra(EXTRA_PACKAGE_KIND)
            ?.let { runCatching { DownloadPackageKind.valueOf(it) }.getOrNull() }
            ?: DownloadPackageKind.AppUpdate
        if (url.isBlank() || tag.isBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (downloadJob?.isActive == true) {
            if (activeTag != tag) {
                GiteeAppUpdater.publishDownloadState(
                    UpdateDownloadState.Failed(tag, "已有其他版本正在下载，请等待完成后重试")
                )
            }
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, progressNotification(name, null, packageKind))
        activeTag = tag
        downloadJob = serviceScope.launch {
            try {
                var lastPercent: Int? = null
                GiteeAppUpdater.performDownload(this@UpdateDownloadForegroundService, url, tag, apkName) { downloaded, total ->
                    val state = UpdateDownloadState.Downloading(tag, name, downloaded, total)
                    GiteeAppUpdater.publishDownloadState(state)
                    val percent = state.progressPercent
                    if (percent != lastPercent || percent == null) {
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIFICATION_ID, progressNotification(name, percent, packageKind))
                        lastPercent = percent
                    }
                }.fold(
                    onSuccess = { apk ->
                        GiteeAppUpdater.publishDownloadState(UpdateDownloadState.Completed(tag, apk))
                        finishForeground(completedNotification(name, apk, packageKind))
                    },
                    onFailure = { error ->
                        val message = error.message ?: "更新下载失败"
                        GiteeAppUpdater.publishDownloadState(UpdateDownloadState.Failed(tag, message))
                        finishForeground(failedNotification(name, message, packageKind))
                    }
                )
            } finally {
                activeTag = null
                stopSelf(startId)
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        downloadJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun progressNotification(
        name: String,
        progress: Int?,
        packageKind: DownloadPackageKind
    ): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            6101,
            packageManager.getLaunchIntentForPackage(packageName) ?: Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .applyAppNotificationIcon(this)
            .setContentTitle(if (packageKind == DownloadPackageKind.CourseComponent) "正在下载课程组件" else "正在下载更新")
            .setContentText(if (progress == null) name else "$name · $progress%")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setColor(Notification.COLOR_DEFAULT)
            .requestPromotedOngoing(if (progress == null) "下载中" else "$progress%")
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setStyle(downloadProgressStyle(progress))
        } else {
            builder.setProgress(100, progress ?: 0, progress == null)
        }
        return builder.build()
    }

    @Suppress("NewApi")
    private fun downloadProgressStyle(progress: Int?): Notification.ProgressStyle =
        Notification.ProgressStyle()
            // 与课程进度实时活动一致：用单个白色小圆点作为 tracker 图标，
            // 不再叠加 Point，避免进度条上出现两个重叠的进度图示。
            .setProgressTrackerIcon(
                Icon.createWithResource(this, R.drawable.ic_live_dot)
            )
            .setProgressSegments(
                listOf(
                    Notification.ProgressStyle.Segment(100)
                )
            )
            .setProgress(progress ?: 0)
            .setProgressIndeterminate(progress == null)

    private fun completedNotification(
        name: String,
        apk: File,
        packageKind: DownloadPackageKind
    ): Notification {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
        val installIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, ApkMimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val install = PendingIntent.getActivity(
            this,
            6102,
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .applyAppNotificationIcon(this)
            .setContentTitle(if (packageKind == DownloadPackageKind.CourseComponent) "课程组件下载完成" else "更新下载完成")
            .setContentText("点击安装 $name")
            .setContentIntent(install)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setColor(Notification.COLOR_DEFAULT)
            .requestPromotedOngoing("待安装")
            .let { builder ->
                if (Build.VERSION.SDK_INT >= 36) {
                    builder.setStyle(downloadProgressStyle(100))
                } else {
                    builder.setProgress(100, 100, false)
                }
            }
            .build()
    }

    private fun failedNotification(
        name: String,
        message: String,
        packageKind: DownloadPackageKind
    ): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .applyAppNotificationIcon(this)
            .setContentTitle(if (packageKind == DownloadPackageKind.CourseComponent) "课程组件下载失败" else "更新下载失败")
            .setContentText("$name · $message")
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ERROR)
            .build()

    private fun finishForeground(notification: Notification) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        stopForeground(STOP_FOREGROUND_DETACH)
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "应用更新", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "显示应用更新包的下载进度"
                setShowBadge(false)
            }
        )
    }

    private fun Notification.Builder.requestPromotedOngoing(shortText: String): Notification.Builder = apply {
        // 不设置 largeIcon，避免卡片里出现第二个应用图标（与课程实时活动一致）。
        runCatching {
            javaClass.getMethod("setRequestPromotedOngoing", java.lang.Boolean.TYPE).invoke(this, true)
            extras.putBoolean("android.requestPromotedOngoing", true)
            javaClass.getMethod("setShortCriticalText", String::class.java).invoke(this, shortText)
        }
    }

    companion object {
        val ACTION_DOWNLOAD = "${BuildConfig.APPLICATION_ID}.action.DOWNLOAD_UPDATE"
        const val EXTRA_URL = "update_url"
        const val EXTRA_TAG = "update_tag"
        const val EXTRA_NAME = "update_name"
        const val EXTRA_APK_NAME = "update_apk_name"
        const val EXTRA_PACKAGE_KIND = "update_package_kind"
        private const val CHANNEL_ID = "app_update_live_progress"
        private const val NOTIFICATION_ID = 20260720
    }
}

private data class ReleaseAsset(val name: String, val url: String)

private data class ParsedVersion(
    val numbers: List<Int>,
    val stage: Int,
    val sequence: Int,
    val isExperimental: Boolean
) {
    val isPrerelease: Boolean get() = stage < 5
    companion object {
        fun parse(raw: String): ParsedVersion {
            val normalized = raw.trim().removePrefix("v").removePrefix("V")
            val main = Regex("\\d+(?:\\.\\d+)*").find(normalized)
                ?: error("无法识别版本号：$raw")
            val numbers = main.value.split('.').map { it.toInt() }
            val suffix = normalized.substring(main.range.last + 1).substringBefore('+')
            val pre = Regex("(?i)(experimental|exp|dev|alpha|beta|preview|rc)[\\s._-]*(\\d*)").find(suffix)
            val stage = when (pre?.groupValues?.get(1)?.lowercase()) {
                "experimental", "exp" -> 0
                "dev" -> 0
                "alpha" -> 1
                "beta" -> 2
                "preview" -> 3
                "rc" -> 4
                else -> 5
            }
            val stageName = pre?.groupValues?.get(1)?.lowercase()
            return ParsedVersion(
                numbers = numbers,
                stage = stage,
                sequence = pre?.groupValues?.get(2)?.toIntOrNull() ?: 0,
                isExperimental = stageName == "experimental" || stageName == "exp"
            )
        }
    }
}

private fun ReleaseAsset.isAppUpdateApk(): Boolean {
    val normalizedName = name.lowercase()
    val normalizedUrl = url.substringBefore('?').lowercase()
    val isApk = normalizedName.endsWith(".apk") || normalizedUrl.endsWith(".apk")
    val isCourseComponent = normalizedName.contains("coloros-course-component") ||
        normalizedName.contains("wakeup-proxy")
    return isApk && !isCourseComponent
}

private fun JsonObject.string(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonElement.jsonObjectOrThrow(): JsonObject =
    this as? JsonObject ?: error("Gitee 返回了无法识别的数据")
