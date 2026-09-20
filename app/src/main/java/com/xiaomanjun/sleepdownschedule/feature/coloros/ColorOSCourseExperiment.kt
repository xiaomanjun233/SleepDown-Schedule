package com.xiaomanjun.sleepdownschedule.feature.coloros

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.model.NotificationMode
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ColorOSDeviceStatus(
    val manufacturer: String,
    val brand: String,
    val colorOSVersion: String?,
    val isColorOSFamily: Boolean
)

data class ColorOSCourseDiagnostics(
    val device: ColorOSDeviceStatus,
    val proxyInstalled: Boolean,
    val proxyIsSleepDown: Boolean,
    val officialWakeUpConflict: Boolean,
    val proxyProviderAccessible: Boolean,
    val sourceProviderAccessible: Boolean,
    val exportValid: Boolean,
    val todayCourseCount: Int,
    val tomorrowCourseCount: Int,
    val lastRefreshAt: Long,
    val lastSystemQueryAt: Long,
    val lastSystemQueryPath: String,
    val lastSystemQueryCaller: String,
    val error: String?
) {
    fun asText(): String = buildString {
        appendLine("设备：${device.manufacturer} ${device.brand}")
        appendLine("系统支持：${yesNo(device.isColorOSFamily)}${device.colorOSVersion?.let { "（$it）" }.orEmpty()}")
        appendLine("课程组件：${if (proxyIsSleepDown) "已安装" else if (proxyInstalled) "被其他课程应用占用" else "未安装"}")
        appendLine("组件连接：${if (proxyProviderAccessible) "正常" else "未连接"}")
        appendLine("课程读取：${if (exportValid) "正常（今天 $todayCourseCount 门，明天 $tomorrowCourseCount 门）" else "异常"}")
        appendLine("最近同步：${lastRefreshAt.takeIf { it > 0 } ?: "无"}")
        appendLine("最近系统读取：${lastSystemQueryAt.takeIf { it > 0 } ?: "无"}")
        if (lastSystemQueryAt > 0) {
            appendLine("读取项目：$lastSystemQueryPath")
        }
        error?.let { append("问题：$it") }
    }

    private fun yesNo(value: Boolean) = if (value) "是" else "否"
}

object ColorOSCourseExperiment {
    private const val KEY_ENABLED = "experiment_enabled"
    private const val KEY_TEST_PREVIEW_EXPIRES_AT = "test_preview_expires_at"
    private const val TEST_PREVIEW_DURATION_MS = 3 * 60 * 1_000L
    private val previewHandler = Handler(Looper.getMainLooper())
    private var previewCleanup: Runnable? = null

    fun deviceStatus(): ColorOSDeviceStatus {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        val colorOSVersion = systemProperty("ro.build.version.oplusrom")
            ?: systemProperty("ro.build.version.opporom")
            ?: Build.DISPLAY.orEmpty().takeIf {
                it.contains("coloros", ignoreCase = true) || it.contains("oplus", ignoreCase = true)
            }
        val familyNames = listOf(manufacturer, brand).map(String::lowercase)
        val isFamily = familyNames.any { value ->
            value.contains("oppo") || value.contains("oneplus") ||
                value.contains("realme") || value.contains("oplus")
        } || colorOSVersion != null
        return ColorOSDeviceStatus(manufacturer, brand, colorOSVersion, isFamily)
    }

    fun isAvailable(): Boolean = BuildConfig.SLEEPDOWN_EXP_BUILD && deviceStatus().isColorOSFamily

    fun isEnabled(context: Context): Boolean = isAvailable() &&
        ColorOSCourseBridge.preferences(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        val accepted = enabled && isAvailable()
        val preferences = ColorOSCourseBridge.preferences(context)
        preferences.edit().putBoolean(KEY_ENABLED, accepted).apply()
        if (!accepted) {
            preferences.edit().remove(KEY_TEST_PREVIEW_EXPIRES_AT).apply()
            previewCleanup?.let(previewHandler::removeCallbacks)
            previewCleanup = null
        }
        ColorOSCourseBridge.notifyScheduleChanged(
            context,
            if (accepted) "experiment_enabled" else "experiment_disabled"
        )
        return accepted
    }

    fun suppressLiveUpdate(context: Context, config: ScheduleConfigEntity): ScheduleConfigEntity =
        if (isEnabled(context) && config.notificationMode == NotificationMode.LIVE_UPDATE) {
            config.copy(notificationMode = NotificationMode.STANDARD)
        } else {
            config
        }

    suspend fun diagnose(context: Context): ColorOSCourseDiagnostics = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val packageManager = appContext.packageManager
        val proxyPackage = packageInfo(packageManager, ColorOSCourseContract.PROXY_PACKAGE)
        val proxyInstalled = proxyPackage != null
        val metadataMatches = runCatching {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(
                ColorOSCourseContract.PROXY_PACKAGE,
                PackageManager.GET_META_DATA
            ).metaData?.getString(ColorOSCourseContract.PROXY_METADATA_KEY) ==
                ColorOSCourseContract.PROXY_METADATA_VERSION
        }.getOrDefault(false)
        val signatureMatches = proxyPackage?.let { proxy ->
            val current = packageInfo(packageManager, appContext.packageName)
            current != null && signingDigests(current).isNotEmpty() &&
                signingDigests(current) == signingDigests(proxy)
        } == true
        val proxyIsSleepDown = proxyInstalled && metadataMatches && signatureMatches

        var error: String? = null
        val sourceRows = linkedMapOf<String, ProviderRow>()
        listOf("has_init", "show_table_id", "table_list", "course_list", "next_course_list")
            .forEach { path ->
                runCatching { query(appContext, ColorOSCourseContract.sourceUri(path)) }
                    .onSuccess { sourceRows[path] = it }
                    .onFailure { if (error == null) error = "$path: ${it.message ?: it.javaClass.simpleName}" }
            }
        val sourceProviderAccessible = sourceRows.size == 5 && sourceRows.values.all { it.code == 0 }
        val todayCount = sourceRows["course_list"]?.data?.jsonArraySizeOrNull() ?: 0
        val tomorrowCount = sourceRows["next_course_list"]?.data?.jsonArraySizeOrNull() ?: 0
        val exportValid = sourceProviderAccessible &&
            sourceRows["has_init"]?.data?.isJsonObject() == true &&
            sourceRows["show_table_id"]?.data?.isJsonObject() == true &&
            sourceRows["table_list"]?.data?.isJsonArray() == true &&
            sourceRows["course_list"]?.data?.isJsonArray() == true &&
            sourceRows["next_course_list"]?.data?.isJsonArray() == true

        val proxyAccessible = if (proxyIsSleepDown) {
            runCatching { query(appContext, ColorOSCourseContract.proxyUri("has_init")).code == 0 }
                .onFailure { if (error == null) error = "proxy: ${it.message ?: it.javaClass.simpleName}" }
                .getOrDefault(false)
        } else {
            false
        }
        val preferences = ColorOSCourseBridge.preferences(appContext)
        ColorOSCourseDiagnostics(
            device = deviceStatus(),
            proxyInstalled = proxyInstalled,
            proxyIsSleepDown = proxyIsSleepDown,
            officialWakeUpConflict = proxyInstalled && !proxyIsSleepDown,
            proxyProviderAccessible = proxyAccessible,
            sourceProviderAccessible = sourceProviderAccessible,
            exportValid = exportValid,
            todayCourseCount = todayCount,
            tomorrowCourseCount = tomorrowCount,
            lastRefreshAt = preferences.getLong(ColorOSCourseBridge.KEY_LAST_REFRESH_AT, 0L),
            lastSystemQueryAt = preferences.getLong(ColorOSCourseBridge.KEY_LAST_PROXY_QUERY_AT, 0L),
            lastSystemQueryPath = preferences.getString(ColorOSCourseBridge.KEY_LAST_PROXY_QUERY_PATH, "").orEmpty(),
            lastSystemQueryCaller = preferences.getString(ColorOSCourseBridge.KEY_LAST_PROXY_QUERY_CALLER, "").orEmpty(),
            error = error
        )
    }

    suspend fun testFluidCloud(context: Context): ColorOSCourseDiagnostics {
        val diagnostics = diagnose(context)
        if (diagnostics.proxyProviderAccessible && diagnostics.exportValid && isEnabled(context)) {
            startTestPreview(context)
        }
        ColorOSCourseBridge.notifyScheduleChanged(context, "manual_test")
        return diagnostics
    }

    internal fun appendTestPreview(
        context: Context,
        json: String,
        date: LocalDate,
        zoneId: ZoneId,
        nowMillis: Long = System.currentTimeMillis()
    ): String = ColorOSCourseTestPreview.append(
        json = json,
        date = date,
        zoneId = zoneId,
        nowMillis = nowMillis,
        expiresAtMillis = ColorOSCourseBridge.preferences(context)
            .getLong(KEY_TEST_PREVIEW_EXPIRES_AT, 0L)
    )

    private fun startTestPreview(context: Context) {
        val appContext = context.applicationContext
        val expiresAt = System.currentTimeMillis() + TEST_PREVIEW_DURATION_MS
        ColorOSCourseBridge.preferences(appContext).edit()
            .putLong(KEY_TEST_PREVIEW_EXPIRES_AT, expiresAt)
            .apply()
        previewCleanup?.let(previewHandler::removeCallbacks)
        previewCleanup = Runnable {
            ColorOSCourseBridge.preferences(appContext).edit()
                .remove(KEY_TEST_PREVIEW_EXPIRES_AT)
                .apply()
            ColorOSCourseBridge.notifyScheduleChanged(appContext, "test_preview_finished")
        }.also { previewHandler.postDelayed(it, TEST_PREVIEW_DURATION_MS) }
    }

    private fun query(context: Context, uri: android.net.Uri): ProviderRow {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            require(cursor.moveToFirst()) { "Provider returned no rows" }
            val codeIndex = cursor.getColumnIndex("code")
            val dataIndex = cursor.getColumnIndex("data")
            require(codeIndex >= 0 && dataIndex >= 0) { "Provider columns are missing" }
            return ProviderRow(cursor.getInt(codeIndex), cursor.getString(dataIndex).orEmpty())
        }
        error("Provider returned null")
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(packageManager: PackageManager, packageName: String): PackageInfo? =
        runCatching {
            packageManager.getPackageInfo(
                packageName,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    PackageManager.GET_SIGNATURES
                }
            )
        }.getOrNull()

    @Suppress("DEPRECATION")
    private fun signingDigests(packageInfo: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            packageInfo.signatures.orEmpty()
        }
        return signatures.mapTo(linkedSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }
    }

    private fun systemProperty(name: String): String? = runCatching {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java)
            .invoke(null, name)
            ?.toString()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }.getOrNull()

    private fun String.isJsonObject(): Boolean = runCatching {
        Json.parseToJsonElement(this).jsonObject
    }.isSuccess

    private fun String.isJsonArray(): Boolean = runCatching {
        Json.parseToJsonElement(this).jsonArray
    }.isSuccess

    private fun String.jsonArraySizeOrNull(): Int? = runCatching {
        Json.parseToJsonElement(this).jsonArray.size
    }.getOrNull()

    private data class ProviderRow(val code: Int, val data: String)
}

internal object ColorOSCourseTestPreview {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun append(
        json: String,
        date: LocalDate,
        zoneId: ZoneId,
        nowMillis: Long,
        expiresAtMillis: Long
    ): String {
        if (expiresAtMillis <= nowMillis) return json
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        if (date != now.toLocalDate()) return json
        val expiresAt = Instant.ofEpochMilli(expiresAtMillis).atZone(zoneId)
        val existing = runCatching { Json.parseToJsonElement(json).jsonArray }.getOrNull()
            ?: return json
        return buildJsonArray {
            existing.forEach { add(it) }
            add(buildJsonObject {
                put("id", 9_000_000_000_000L + expiresAtMillis % 1_000_000L)
                put("courseName", "SleepDown 流体云测试")
                put("room", "实验预览")
                put("teacher", "SleepDown")
                put("startTime", now.minusSeconds(30).toLocalTime().format(timeFormatter))
                put("endTime", expiresAt.toLocalTime().format(timeFormatter))
                put("color", "#ff3f8cff")
                put("extra", "测试课程将在 3 分钟后自动结束")
                put("startTimestamp", now.toEpochSecond() - 30L)
                put("endTimestamp", expiresAt.toEpochSecond())
            })
        }.toString()
    }
}
