package com.xiaomanjun.sleepdownschedule.feature.coloros

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.model.NotificationMode
import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest

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
        appendLine("设备厂商：${device.manufacturer} / ${device.brand}")
        appendLine("ColorOS / OPlus：${if (device.isColorOSFamily) "是" else "否"}${device.colorOSVersion?.let { "（$it）" }.orEmpty()}")
        appendLine("兼容组件已安装：${yesNo(proxyInstalled)}")
        appendLine("SleepDown 实验组件：${yesNo(proxyIsSleepDown)}")
        appendLine("WakeUp 冲突：${yesNo(officialWakeUpConflict)}")
        appendLine("WakeUp Provider 可访问：${yesNo(proxyProviderAccessible)}")
        appendLine("SleepDown Provider 可访问：${yesNo(sourceProviderAccessible)}")
        appendLine("课程导出：${if (exportValid) "正常（今天 $todayCourseCount 门，明天 $tomorrowCourseCount 门）" else "异常"}")
        appendLine("最近 refresh：${lastRefreshAt.takeIf { it > 0 } ?: "无"}")
        appendLine("最近可识别系统查询：${lastSystemQueryAt.takeIf { it > 0 } ?: "无"}")
        if (lastSystemQueryAt > 0) {
            appendLine("查询方：$lastSystemQueryCaller")
            appendLine("查询路径：$lastSystemQueryPath")
        }
        error?.let { append("错误：$it") }
    }

    private fun yesNo(value: Boolean) = if (value) "是" else "否"
}

object ColorOSCourseExperiment {
    private const val KEY_ENABLED = "experiment_enabled"

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
        ColorOSCourseBridge.preferences(context).edit().putBoolean(KEY_ENABLED, accepted).apply()
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
        ColorOSCourseBridge.notifyScheduleChanged(context, "manual_test")
        return diagnostics
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
