package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.os.Build
import com.xiaomanjun.sleepdownschedule.BuildConfig
import java.util.Locale

internal fun supportsColorOsGlow(
    experimentalFeatures: Boolean,
    manufacturer: String,
    sdk: Int,
    osVersion: String?
): Boolean {
    if (!experimentalFeatures || sdk < 31) return false
    if (manufacturer.lowercase(Locale.ROOT) !in setOf("oppo", "oneplus", "realme")) return false
    val major = osVersion?.trim()?.removePrefix("V")?.removePrefix("v")
        ?.substringBefore('.')?.toIntOrNull() ?: return false
    return major >= 17
}

internal fun isColorOsGlowDevice(): Boolean = supportsColorOsGlow(
    experimentalFeatures = BuildConfig.SLEEPDOWN_EXPERIMENTAL_FEATURES,
    manufacturer = Build.MANUFACTURER.orEmpty(),
    sdk = Build.VERSION.SDK_INT,
    osVersion = colorOsVersionProperty("ro.build.version.oplusrom")
        ?: colorOsVersionProperty("ro.build.version.opporom")
)

private fun colorOsVersionProperty(name: String): String? = runCatching {
    Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
        .invoke(null, name) as? String
}.getOrNull()?.takeIf { it.isNotBlank() }
