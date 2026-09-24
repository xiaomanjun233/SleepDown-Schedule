package com.xiaomanjun.sleepdownschedule.feature.experimental

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.core.identity.currentLiveUpdateIconResId
import com.xiaomanjun.sleepdownschedule.feature.reminder.LiveUpdateKind
import com.xiaomanjun.sleepdownschedule.feature.reminder.LiveUpdatePayload
import com.xiaomanjun.sleepdownschedule.feature.reminder.LiveUpdatePhase
import com.xiaomanjun.sleepdownschedule.feature.reminder.LiveUpdateStatus
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/** Xiaomi focus notification metadata, isolated from the ordinary notification path. */
internal object XiaomiSuperIsland {
    private const val Prefs = "experimental_notification_modes"
    private const val Enabled = "xiaomi_super_island_enabled"
    private const val PreviewUntil = "xiaomi_island_preview_until"
    const val ChannelId = "course_reminder_island"
    private const val FocusParameter = "miui.focus.param"
    private const val SmallPicture = "miui.focus.pic_small"
    private val sequence = AtomicLong(0)

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
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit()
            .putBoolean(Enabled, accepted)
            .apply { if (!accepted) remove(PreviewUntil) }
            .apply()
        return accepted
    }

    fun ensureChannel(context: Context) {
        if (!isEnabled(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(
            ChannelId, "课程提醒超级岛", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "课程提醒超级岛通知"
            setShowBadge(true)
        })
    }

    fun markPreview(context: Context, expiresAtMillis: Long) {
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit()
            .putLong(PreviewUntil, expiresAtMillis).apply()
    }

    fun hasActivePreview(context: Context): Boolean = isEnabled(context) &&
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
            .getLong(PreviewUntil, 0L) > System.currentTimeMillis()

    fun clearPreview(context: Context) {
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit().remove(PreviewUntil).apply()
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
        if (!isEnabled(context)) return
        // Shizuku only improves delivery. The selected island format is always sent.
        notification.extras.putString(FocusParameter, parameters(
            payload, status, shortText, System.currentTimeMillis(), context.packageName
        ))
        val appIcon = Icon.createWithResource(context, currentLiveUpdateIconResId(context))
        notification.extras.putBundle("miui.focus.pics", Bundle().apply {
            putParcelable("miui.focus.pic_app_icon", appIcon)
            putParcelable("miui.focus.pic_app_icon_dark", appIcon)
            putParcelable(SmallPicture, appIcon)
            putParcelable("miui.focus.pic_small_dark", appIcon)
        })
    }

    internal fun parameters(
        payload: LiveUpdatePayload,
        status: LiveUpdateStatus,
        shortText: String,
        nowMillis: Long = System.currentTimeMillis(),
        packageName: String = "com.xiaomanjun.sleepdownschedule"
    ): String {
        val beforeClass = status.phase == LiveUpdatePhase.BEFORE_CLASS
        val timerAt = status.nextTransitionAtMillis?.takeIf { beforeClass && it > nowMillis }
        val courseName = payload.name.ifBlank { shortText }
        val islandStatus = when (status.phase) {
            LiveUpdatePhase.BEFORE_CLASS -> payload.location
            LiveUpdatePhase.IN_CLASS -> "已上课"
            LiveUpdatePhase.BREAK -> "课间"
            LiveUpdatePhase.FINISHED -> "已下课"
            LiveUpdatePhase.TOMORROW -> "明日课程"
        }
        val left = JSONObject().put("type", 1).put("textInfo", JSONObject()
            .put("title", courseName).put("content", "")
            .put("showHighlightColor", false).put("narrowFont", false))
        // Nexio's working summary: template 2, course name on the left and room/state on the right.
        val bigIsland = JSONObject().put("templateNo", 2)
            .put("imageTextInfoLeft", left)
            .put("textInfo", JSONObject().put("frontTitle", "")
                .put("title", islandStatus)
                .put("content", "")
                .put("showHighlightColor", false).put("narrowFont", false))
        val island = JSONObject()
            .put("islandProperty", 1)
            .put("islandTimeout", 3600)
            .put("bigIslandArea", bigIsland)
            .put("smallIslandArea", JSONObject().put("picInfo", JSONObject()
                .put("type", 1).put("pic", SmallPicture)
                .put("picDark", "miui.focus.pic_small_dark")))
        val detail = if (payload.kind == LiveUpdateKind.TOMORROW) {
            payload.location.ifBlank { payload.timeText }
        } else listOf(payload.timeText, payload.location).filter(String::isNotBlank).joinToString(" · ")
        val card = JSONObject()
            .put("type", 2).put("title", courseName).put("content", detail)
            .put("subTitle", "").put("extraTitle", "").put("specialTitle", "")
            .put("subContent", "").put("picFunction", "")
            .put("showDivider", true).put("showContentDivider", false)
            .put("colorTitle", "#111111").put("colorTitleDark", "#ffffff")
            .put("colorContent", "#333333").put("colorContentDark", "#cccccc")
        val hint = JSONObject()
            .put("type", 2)
            .put("content", when {
                timerAt != null -> "即将上课"
                status.phase == LiveUpdatePhase.TOMORROW -> status.statusText
                beforeClass -> status.statusText
                else -> "现在"
            })
            .put("title", if (timerAt != null) "" else islandStatus)
            .put("subContent", "地点")
            .put("subTitle", payload.location)
            .put("colorContent", "#666666").put("colorContentDark", "#aaaaaa")
            .put("colorTitle", "#222222").put("colorTitleDark", "#eeeeee")
            .put("colorSubContent", "#666666").put("colorSubContentDark", "#aaaaaa")
            .put("colorSubTitle", "#222222").put("colorSubTitleDark", "#eeeeee")
            .put("actionInfo", JSONObject()
                .put("actionTitle", "查看课表")
                .put("actionIntentType", 1)
                .put("actionIntent",
                    "intent:#Intent;component=$packageName/.MainActivity;end"))
            .put("timerInfo", timerInfo(timerAt, nowMillis))
        return JSONObject().put("param_v2", JSONObject()
            .put("protocol", 1)
            .put("business", "course_reminder")
            .put("enableFloat", true)
            .put("updatable", true)
            .put("reopen", "reopen")
            .put("sequence", sequence.incrementAndGet())
            .put("baseInfo", card)
            .put("picInfo", JSONObject().put("type", 1).put("pic", ""))
            .put("hintInfo", hint)
            .put("param_island", island)
        ).toString()
    }

    private fun timerInfo(timerAt: Long?, nowMillis: Long): JSONObject = JSONObject().apply {
        put("timerType", if (timerAt != null) -1 else 0)
        put("timerWhen", timerAt ?: 0L)
        put("timerTotal", 0L)
        put("timerSystemCurrent", if (timerAt != null) nowMillis else 0L)
    }
}
