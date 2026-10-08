package com.xiaomanjun.sleepdownschedule.feature.experimental

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
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
    private const val LeftMode = "xiaomi_island_left_mode"
    private const val RightMode = "xiaomi_island_right_mode"
    private const val AodMode = "xiaomi_island_aod_mode"
    private const val ExpandGlow = "xiaomi_island_expand_glow"
    private const val Sequence = "xiaomi_island_sequence"
    const val ChannelId = "course_reminder_island"
    private const val FocusParameter = "miui.focus.param"
    private const val SmallPicture = "miui.focus.pic_small"
    const val DndActionKey = "miui.focus.action_dnd"
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

    /** Xiaomi exposes this switch separately from Android's notification permission. */
    fun focusPermission(context: Context): Boolean? = runCatching {
        val extras = Bundle().apply { putString("package", context.packageName) }
        context.contentResolver.call(
            Uri.parse("content://miui.statusbar.notification.public"),
            "canShowFocus", null, extras
        )?.takeIf { it.containsKey("canShowFocus") }?.getBoolean("canShowFocus")
    }.getOrNull()

    fun isSelected(context: Context): Boolean = BuildConfig.SLEEPDOWN_EXPERIMENTAL_FEATURES &&
        isXiaomiDevice(Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty()) &&
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).getBoolean(Enabled, false)

    fun isEnabled(context: Context): Boolean = isSelected(context) && hasPrivilege(context)

    fun hasPrivilege(context: Context): Boolean =
        XiaomiShizukuBridge.isAuthorized() || XiaomiRootBridge.isAuthorized(context)

    internal data class Options(
        val left: Int = 0,
        val right: Int = 1,
        val aod: Int = 0,
        val expandGlow: Boolean = true
    )

    fun options(context: Context): Options = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).run {
        Options(
            left = getInt(LeftMode, 0).coerceIn(0, 2),
            right = getInt(RightMode, 1).coerceIn(0, 2),
            aod = getInt(AodMode, 0).coerceIn(0, 1),
            expandGlow = getBoolean(ExpandGlow, true)
        )
    }

    fun setLeft(context: Context, mode: Int) {
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit().putInt(LeftMode, mode.coerceIn(0, 2)).apply()
    }

    fun setRight(context: Context, mode: Int) {
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit().putInt(RightMode, mode.coerceIn(0, 2)).apply()
    }

    fun setAod(context: Context, mode: Int) {
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit().putInt(AodMode, mode.coerceIn(0, 1)).apply()
    }

    fun setExpandGlow(context: Context, enabled: Boolean) {
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit().putBoolean(ExpandGlow, enabled).apply()
    }

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
            // Match Nexio: an app-managed course DND rule must not suppress its own island.
            if (manager.isNotificationPolicyAccessGranted) setBypassDnd(true)
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
    fun restoreInterruptedBypass(context: Context): Boolean {
        val shizukuRestored = XiaomiShizukuBridge.restoreIfInterrupted(context)
        val rootRestored = XiaomiRootBridge.restoreIfInterrupted(context)
        return shizukuRestored && rootRestored
    }
    fun isRootAuthorized(context: Context): Boolean = XiaomiRootBridge.isAuthorized(context)
    fun requestRootAuthorization(context: Context): Boolean = XiaomiRootBridge.requestAuthorization(context)

    fun post(context: Context, notification: Notification, action: () -> Unit): Boolean {
        return if (isEnabled(context) && notification.extras.containsKey(FocusParameter)) {
            if (XiaomiShizukuBridge.isAuthorized()) XiaomiShizukuBridge.postWithTemporaryBypass(context, action)
            else XiaomiRootBridge.postWithTemporaryBypass(context, action)
        } else { action(); true }
    }

    fun decorate(
        context: Context,
        notification: Notification,
        payload: LiveUpdatePayload,
        status: LiveUpdateStatus,
        shortText: String,
        actionTitle: String = "开启勿扰"
    ) {
        if (!isEnabled(context)) return
        notification.extras.putString(FocusParameter, parameters(
            payload, status, shortText, System.currentTimeMillis(), options(context), actionTitle,
            nextSequence(context)
        ))
        val appIcon = Icon.createWithResource(context, currentLiveUpdateIconResId(context))
        notification.extras.putBundle("miui.focus.pics", Bundle().apply {
            putParcelable("miui.focus.pic_app_icon", appIcon)
            putParcelable("miui.focus.pic_app_icon_dark", appIcon)
            putParcelable(SmallPicture, appIcon)
            putParcelable("miui.focus.pic_small_dark", appIcon)
        })
    }

    private fun nextSequence(context: Context): Long = synchronized(sequence) {
        val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
        val next = maxOf(sequence.get(), prefs.getLong(Sequence, 0L), System.currentTimeMillis()) + 1L
        sequence.set(next)
        prefs.edit().putLong(Sequence, next).commit()
        next
    }

    internal fun parameters(
        payload: LiveUpdatePayload,
        status: LiveUpdateStatus,
        shortText: String,
        nowMillis: Long = System.currentTimeMillis(),
        options: Options = Options(),
        actionTitle: String = "开启勿扰",
        sequenceValue: Long = sequence.incrementAndGet()
    ): String {
        val beforeClass = status.phase == LiveUpdatePhase.BEFORE_CLASS
        val timerAt = status.nextTransitionAtMillis?.takeIf {
            it > nowMillis && status.phase in setOf(
                LiveUpdatePhase.BEFORE_CLASS, LiveUpdatePhase.IN_CLASS, LiveUpdatePhase.BREAK
            )
        }
        val timerTarget = when (status.phase) {
            LiveUpdatePhase.BEFORE_CLASS, LiveUpdatePhase.BREAK -> "上课"
            LiveUpdatePhase.IN_CLASS -> if (timerAt != payload.endAtMillis()) "课间" else "下课"
            else -> ""
        }
        val courseName = payload.name.ifBlank { shortText }
        val islandStatus = when (status.phase) {
            LiveUpdatePhase.BEFORE_CLASS -> payload.location
            LiveUpdatePhase.IN_CLASS -> "已上课"
            LiveUpdatePhase.BREAK -> "课间"
            LiveUpdatePhase.FINISHED -> "已下课"
            LiveUpdatePhase.TOMORROW -> "明日课程"
        }
        // The left text component cannot run a system timer. Show the next milestone there;
        // the expanded card and optional right digit component own the live countdown.
        val nextMilestoneText = if (timerAt != null) "距$timerTarget" else islandStatus
        val islandText: (Int) -> String = { mode -> when (mode) {
            0 -> courseName
            1 -> payload.location.ifBlank { courseName }
            else -> nextMilestoneText
        } }
        val leftText = islandText(options.left)
        val rightText = islandText(options.right)
        val aodText = if (options.aod == 1) payload.location.ifBlank { courseName } else courseName
        val left = JSONObject().put("type", 1).put("textInfo", JSONObject()
            .put("title", leftText).put("content", "")
            .put("showHighlightColor", false).put("narrowFont", false))
        val bigIsland = JSONObject().put("templateNo", 2)
            .put("imageTextInfoLeft", left)
            .put("textInfo", JSONObject().put("frontTitle", "")
                .put("title", if (options.right == 2 && timerAt != null) "" else rightText)
                .put("content", "")
                .put("showHighlightColor", false).put("narrowFont", false))
        if (options.right == 2 && timerAt != null) {
            bigIsland.put("sameWidthDigitInfo", JSONObject()
                .put("content", timerTarget)
                .put("showHighlightColor", false)
                .put("timerInfo", timerInfo(timerAt, nowMillis)))
        }
        val island = JSONObject()
            .put("islandProperty", 1)
            .put("islandTimeout", payload.expiresAtMillis.takeIf { it > nowMillis }
                ?.let { ((it - nowMillis + 999L) / 1000L).coerceIn(60L, 43_200L).toInt() }
                ?: 3600)
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
                timerAt != null -> if (beforeClass) "即将上课" else "距离$timerTarget"
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
                .put("actionTitle", actionTitle)
                .put("action", DndActionKey))
            .put("timerInfo", timerInfo(timerAt, nowMillis))
        val parameters = JSONObject()
            .put("protocol", 1)
            .put("business", "course_reminder")
            .put("enableFloat", beforeClass)
            .put("updatable", true)
            .put("outEffectSrc", if (options.expandGlow) "outer_glow" else "")
            .put("aodTitle", aodText)
            .put("reopen", "reopen")
            .put("sequence", sequenceValue)
            .put("baseInfo", card)
            .put("picInfo", JSONObject().put("type", 1).put("pic", ""))
            .put("hintInfo", hint)
            .put("param_island", island)
        if (!beforeClass) parameters.put("islandFirstFloat", true)
        return JSONObject().put("param_v2", parameters).toString()
    }

    private fun timerInfo(timerAt: Long?, nowMillis: Long): JSONObject = JSONObject().apply {
        put("timerType", if (timerAt != null) -1 else 0)
        put("timerWhen", timerAt ?: 0L)
        put("timerTotal", 0L)
        put("timerSystemCurrent", if (timerAt != null) nowMillis else 0L)
    }
}

/** Binder getters return an effective ALLOW/DENY rule; DEFAULT is retained for OEM variants. */
internal fun isKnownIslandFirewallRule(rule: Int?): Boolean = rule != null && rule in 0..2
