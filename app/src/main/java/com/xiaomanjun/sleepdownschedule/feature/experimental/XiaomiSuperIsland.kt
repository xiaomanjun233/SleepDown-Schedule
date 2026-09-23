package com.xiaomanjun.sleepdownschedule.feature.experimental

import android.app.Notification
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
import kotlin.math.ceil

internal enum class XiaomiIslandField(val label: String) {
    COURSE_NAME("课程名称"),
    LOCATION("上课地点"),
    COUNTDOWN("倒计时");

    companion object {
        fun fromSaved(value: Int, fallback: XiaomiIslandField): XiaomiIslandField =
            entries.getOrNull(value) ?: fallback
    }
}

internal data class XiaomiIslandFields(
    val left: XiaomiIslandField = XiaomiIslandField.COURSE_NAME,
    val right: XiaomiIslandField = XiaomiIslandField.LOCATION
)

/** Xiaomi focus notification metadata, isolated from the ordinary notification path. */
internal object XiaomiSuperIsland {
    private const val Prefs = "experimental_notification_modes"
    private const val Enabled = "xiaomi_super_island_enabled"
    private const val LeftField = "xiaomi_island_left_field"
    private const val RightField = "xiaomi_island_right_field"
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

    fun fields(context: Context): XiaomiIslandFields {
        val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
        return XiaomiIslandFields(
            left = XiaomiIslandField.fromSaved(prefs.getInt(LeftField, 0), XiaomiIslandField.COURSE_NAME),
            right = XiaomiIslandField.fromSaved(prefs.getInt(RightField, 1), XiaomiIslandField.LOCATION)
        )
    }

    fun setLeftField(context: Context, field: XiaomiIslandField) {
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit().putInt(LeftField, field.ordinal).apply()
    }

    fun setRightField(context: Context, field: XiaomiIslandField) {
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit().putInt(RightField, field.ordinal).apply()
    }

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
            payload, status, shortText, fields(context), System.currentTimeMillis(), context.packageName
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
        fields: XiaomiIslandFields = XiaomiIslandFields(),
        nowMillis: Long = System.currentTimeMillis(),
        packageName: String = "com.xiaomanjun.sleepdownschedule"
    ): String {
        val beforeClass = status.phase == LiveUpdatePhase.BEFORE_CLASS
        val inClass = status.phase == LiveUpdatePhase.IN_CLASS
        val timerAt = when (status.phase) {
            LiveUpdatePhase.IN_CLASS -> payload.endAtMillis()
            LiveUpdatePhase.BEFORE_CLASS, LiveUpdatePhase.BREAK -> status.nextTransitionAtMillis
            else -> null
        }?.takeIf { it > nowMillis }
        val minutes = timerAt?.let { ceil((it - nowMillis) / 60_000.0).toInt() }
        val countdownText = minutes?.let { "${it}分钟" } ?: status.statusText
        fun fieldText(field: XiaomiIslandField): String = when (field) {
            XiaomiIslandField.COURSE_NAME -> payload.name
            XiaomiIslandField.LOCATION -> payload.location.ifBlank { payload.timeText }
            XiaomiIslandField.COUNTDOWN -> countdownText
        }
        val left = JSONObject().put("type", 1).put("textInfo", JSONObject()
            .put("title", fieldText(fields.left)).put("content", "")
            .put("showHighlightColor", false).put("narrowFont", false))
        val right = JSONObject().put("frontTitle", "")
            .put("title", fieldText(fields.right)).put("content", "")
            .put("showHighlightColor", false).put("narrowFont", false)
        val bigIsland = JSONObject().put("templateNo", 2).put("imageTextInfoLeft", left)
        if (fields.right == XiaomiIslandField.COUNTDOWN && timerAt != null) {
            bigIsland.put("sameWidthDigitInfo", JSONObject()
                .put("content", if (inClass) "下课" else "上课")
                .put("showHighlightColor", false)
                .put("timerInfo", timerInfo(timerAt, nowMillis)))
            right.put("title", "")
        }
        bigIsland.put("textInfo", right)
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
            .put("type", 2).put("title", payload.name).put("content", detail)
            .put("subTitle", "").put("extraTitle", "").put("specialTitle", "")
            .put("subContent", "").put("picFunction", "")
            .put("showDivider", true).put("showContentDivider", false)
            .put("colorTitle", "#111111").put("colorTitleDark", "#ffffff")
            .put("colorContent", "#333333").put("colorContentDark", "#cccccc")
        val hint = JSONObject()
            .put("type", 2)
            .put("content", when (status.phase) {
                LiveUpdatePhase.BEFORE_CLASS -> "即将上课"
                LiveUpdatePhase.IN_CLASS -> "距离下课"
                LiveUpdatePhase.BREAK -> "课间"
                LiveUpdatePhase.FINISHED -> "已下课"
                LiveUpdatePhase.TOMORROW -> "明日课程"
            })
            .put("title", "")
            .put("timerInfo", timerInfo(timerAt, nowMillis))
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
                    "intent:#Intent;component=$packageName/.MainActivity;launchFlags=0x14000000;end"))
        return JSONObject().put("param_v2", JSONObject()
            .put("protocol", 1)
            .put("business", "course_reminder")
            .put("enableFloat", beforeClass && timerAt != null)
            .put("islandFirstFloat", !beforeClass)
            .put("updatable", true)
            .put("outEffectSrc", "outer_glow")
            .put("reopen", "reopen")
            .put("sequence", sequence.incrementAndGet())
            .put("aodTitle", payload.name.ifBlank { shortText })
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
