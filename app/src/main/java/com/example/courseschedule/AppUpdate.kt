package com.example.courseschedule

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

data class GiteeReleaseInfo(
    val name: String,
    val tagName: String,
    val notes: String,
    val apkName: String?,
    val apkUrl: String?,
    val releaseUrl: String,
    val prerelease: Boolean
)

sealed interface GiteeUpdateCheckResult {
    data class UpdateAvailable(val release: GiteeReleaseInfo) : GiteeUpdateCheckResult
    data class UpToDate(val release: GiteeReleaseInfo) : GiteeUpdateCheckResult
}

object GiteeAppUpdater {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val _updateAvailable = MutableStateFlow(false)
    val updateAvailable: StateFlow<Boolean> = _updateAvailable.asStateFlow()

    fun restoreCachedStatus(context: Context, currentVersionName: String) {
        val latestTag = preferences(context).getString(LatestTagKey, null)
        _updateAvailable.value = latestTag?.let { isVersionNewer(it, currentVersionName) } == true
    }

    fun shouldRunDailyCheck(context: Context, date: LocalDate = LocalDate.now()): Boolean =
        preferences(context).getString(LastCheckDateKey, null) != date.toString()

    fun markDailyCheckStarted(context: Context, date: LocalDate = LocalDate.now()) {
        preferences(context).edit().putString(LastCheckDateKey, date.toString()).apply()
    }

    fun recordCheckResult(context: Context, result: GiteeUpdateCheckResult) {
        val release = when (result) {
            is GiteeUpdateCheckResult.UpdateAvailable -> result.release
            is GiteeUpdateCheckResult.UpToDate -> result.release
        }
        preferences(context).edit().putString(LatestTagKey, release.tagName).apply()
        _updateAvailable.value = result is GiteeUpdateCheckResult.UpdateAvailable
    }

    suspend fun checkForUpdate(currentVersionName: String): Result<GiteeUpdateCheckResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val endpoint = "$GiteeApiBase/repos/$GiteeOwner/$GiteeRepository/releases/latest"
                val root = json.parseToJsonElement(readText(endpoint)).jsonObjectOrThrow()
                val release = root.toReleaseInfo()
                if (isVersionNewer(release.tagName, currentVersionName)) {
                    GiteeUpdateCheckResult.UpdateAvailable(release)
                } else {
                    GiteeUpdateCheckResult.UpToDate(release)
                }
            }
        }

    suspend fun downloadApk(context: Context, release: GiteeReleaseInfo): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val downloadUrl = requireNotNull(release.apkUrl) { "这个 Release 没有附带 APK 安装包" }
                val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
                updateDir.listFiles()?.forEach { it.delete() }
                val safeName = release.apkName
                    ?.takeIf { it.endsWith(".apk", ignoreCase = true) }
                    ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    ?: "SleepDown-${release.tagName.replace(Regex("[^A-Za-z0-9._-]"), "_")}.apk"
                val target = File(updateDir, safeName)
                downloadToFile(downloadUrl, target)
                require(target.length() > 0L) { "下载到的安装包为空" }
                target
            }
        }

    fun canRequestPackageInstalls(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
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
        return false
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(UpdatePreferences, Context.MODE_PRIVATE)

    private fun JsonObject.toReleaseInfo(): GiteeReleaseInfo {
        val tag = string("tag_name").ifBlank { string("name") }
        require(tag.isNotBlank()) { "Gitee Release 缺少版本标签" }
        val asset = releaseAssets().firstOrNull { asset ->
            asset.name.endsWith(".apk", ignoreCase = true) ||
                asset.url.substringBefore('?').endsWith(".apk", ignoreCase = true)
        }
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

    private fun downloadToFile(url: String, target: File) {
        val connection = openConnection(url, connectTimeoutMillis = 20_000, readTimeoutMillis = 120_000)
        connection.useResponse { input ->
            target.outputStream().buffered().use { output -> input.copyTo(output) }
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

private data class ReleaseAsset(val name: String, val url: String)

private data class ParsedVersion(val numbers: List<Int>, val isPrerelease: Boolean) {
    companion object {
        fun parse(raw: String): ParsedVersion {
            val normalized = raw.trim().removePrefix("v").removePrefix("V")
            val numbers = Regex("\\d+").findAll(normalized).map { it.value.toIntOrNull() ?: 0 }.toList()
            val prerelease = Regex("(?i)(alpha|beta|preview|rc|dev)").containsMatchIn(normalized)
            return ParsedVersion(numbers.ifEmpty { listOf(0) }, prerelease)
        }
    }
}

private fun JsonObject.string(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonElement.jsonObjectOrThrow(): JsonObject =
    this as? JsonObject ?: error("Gitee 返回了无法识别的数据")
