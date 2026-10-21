package com.xiaomanjun.sleepdownschedule.core.analytics

import com.xiaomanjun.sleepdownschedule.*

import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import java.time.LocalDate
import java.util.UUID

internal class InstallationAnalytics(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
    private val client = RemoteConfigClient()

    fun sync() {
        val installationId = installationId()
        val payload = InstallationPayload(
            installationId = installationId,
            versionCode = BuildConfig.VERSION_CODE.toLong(),
            versionName = BuildConfig.VERSION_NAME,
            androidApi = Build.VERSION.SDK_INT,
            deviceModel = Build.MODEL.orEmpty().trim().take(128),
            deviceBrand = Build.MANUFACTURER.orEmpty().trim().take(64)
        )
        if (!prefs.getBoolean(KeyRegistered, false)) {
            val registered = runCatching {
                client.postInstallation("/api/v1/installations/register", payload)
            }.getOrDefault(false)
            if (registered) prefs.edit { putBoolean(KeyRegistered, true) }
        }
        val today = LocalDate.now().toString()
        if (!shouldPing(prefs.getString(KeyLastPingDate, null), today)) return
        val pinged = runCatching {
            client.postInstallation("/api/v1/installations/ping", payload)
        }.getOrDefault(false)
        if (pinged) prefs.edit { putString(KeyLastPingDate, today) }
    }

    internal fun installationId(): String {
        prefs.getString(KeyInstallationId, null)?.takeIf(::isValidInstallationId)?.let { return it }
        return UUID.randomUUID().toString().also { generated ->
            prefs.edit(commit = true) { putString(KeyInstallationId, generated) }
        }
    }

    companion object {
        internal fun isValidInstallationId(value: String): Boolean =
            runCatching { UUID.fromString(value).toString().equals(value, ignoreCase = true) }.getOrDefault(false)

        internal fun shouldPing(lastSuccessfulDate: String?, today: String): Boolean =
            lastSuccessfulDate != today

        const val PrefName = "sleepdown_installation_analytics"
        const val KeyInstallationId = "installation_id_v1"
        const val KeyRegistered = "registered_v1"
        const val KeyLastPingDate = "last_successful_ping_date_v1"
    }
}
