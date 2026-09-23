package com.xiaomanjun.sleepdownschedule.feature.experimental

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.feature.reminder.LiveUpdateKind
import com.xiaomanjun.sleepdownschedule.feature.reminder.LiveUpdatePayload
import com.xiaomanjun.sleepdownschedule.feature.reminder.LiveUpdatePhase
import com.xiaomanjun.sleepdownschedule.feature.reminder.LiveUpdateStatus
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/** Optional Xiaomi layer. The existing live update remains the notification fallback. */
internal object XiaomiSuperIsland {
    private const val Prefs = "experimental_notification_modes"
    private const val Enabled = "xiaomi_super_island_enabled"
    private const val FocusParameter = "miui.focus.param"
    private const val SmallPicture = "miui.focus.pic_small"
    private val sequence = AtomicLong(System.currentTimeMillis())

    fun isXiaomiDevice(manufacturer: String, brand: String): Boolean =
        listOf(manufacturer, brand).any { value ->
            value.equals("xiaomi", true) || value.equals("redmi", true) || value.equals("poco", true)
        }

    fun isSystemSupported(): Boolean = runCatching {
        val systemProperties = Class.forName("android.os.SystemProperties")
        systemProperties.getMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
            .invoke(null, "persist.sys.feature.island", false) as Boolean
    }.getOrDefault(false)

    fun isEnabled(context: Context): Boolean = BuildConfig.SLEEPDOWN_EXPERIMENTAL_FEATURES &&
        isXiaomiDevice(Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty()) &&
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).getBoolean(Enabled, false)

    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        val accepted = enabled && BuildConfig.SLEEPDOWN_EXPERIMENTAL_FEATURES &&
            isXiaomiDevice(Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty())
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit().putBoolean(Enabled, accepted).apply()
        return accepted
    }

    fun isShizukuRunning(): Boolean = XiaomiShizukuBridge.isRunning()
    fun isShizukuAuthorized(): Boolean = XiaomiShizukuBridge.isAuthorized()
    fun requestShizukuPermission(onResult: (Boolean) -> Unit) = XiaomiShizukuBridge.requestPermission(onResult)
    fun restoreInterruptedBypass(context: Context): Boolean = XiaomiShizukuBridge.restoreIfInterrupted(context)

    fun post(context: Context, notification: Notification, action: () -> Unit) {
        if (isEnabled(context) && notification.extras.containsKey(FocusParameter)) {
            XiaomiShizukuBridge.postWithTemporaryBypass(context, action)
        } else action()
    }

    fun decorate(
        context: Context,
        notification: Notification,
        payload: LiveUpdatePayload,
        status: LiveUpdateStatus,
        shortText: String
    ) {
        if (!isEnabled(context) || !isSystemSupported()) return
        notification.extras.putString(FocusParameter, parameters(payload, status, shortText))
        notification.extras.putBundle("miui.focus.pics", Bundle().apply {
            putParcelable(SmallPicture, Icon.createWithResource(context, R.drawable.ic_school_import))
            putParcelable("miui.focus.pic_small_dark", Icon.createWithResource(context, R.drawable.ic_school_import))
        })
    }

    internal fun parameters(
        payload: LiveUpdatePayload,
        status: LiveUpdateStatus,
        shortText: String
    ): String {
        val detail = if (payload.kind == LiveUpdateKind.TOMORROW) {
            payload.location.ifBlank { payload.timeText }
        } else listOf(payload.timeText, payload.location).filter(String::isNotBlank).joinToString(" · ")
        val beforeClass = status.phase == LiveUpdatePhase.BEFORE_CLASS
        val left = JSONObject().put("type", 1).put("textInfo", JSONObject()
            .put("title", payload.name).put("content", "").put("showHighlightColor", false))
        val right = JSONObject().put("frontTitle", "").put("title", status.statusText)
            .put("content", "").put("showHighlightColor", false)
        val island = JSONObject()
            .put("islandProperty", 1)
            .put("islandTimeout", 3600)
            .put("bigIslandArea", JSONObject().put("templateNo", 2)
                .put("imageTextInfoLeft", left).put("textInfo", right))
            .put("smallIslandArea", JSONObject().put("picInfo", JSONObject()
                .put("type", 1).put("pic", SmallPicture)
                .put("picDark", "miui.focus.pic_small_dark")))
        val card = JSONObject()
            .put("type", 2).put("title", payload.name).put("content", detail)
            .put("showDivider", true)
        return JSONObject().put("param_v2", JSONObject()
            .put("protocol", 1)
            .put("business", "course_reminder")
            .put("enableFloat", beforeClass)
            .put("updatable", true)
            .put("sequence", sequence.incrementAndGet())
            .put("aodTitle", shortText)
            .put("baseInfo", card)
            .put("param_island", island)
        ).toString()
    }
}
