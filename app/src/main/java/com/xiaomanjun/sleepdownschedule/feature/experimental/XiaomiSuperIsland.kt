package com.xiaomanjun.sleepdownschedule.feature.experimental

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.feature.reminder.LiveUpdateKind
import com.xiaomanjun.sleepdownschedule.feature.reminder.LiveUpdatePayload
import com.xiaomanjun.sleepdownschedule.feature.reminder.LiveUpdateStatus
import org.json.JSONObject

/** Official local notification extension for HyperOS 3; ordinary Android content is retained. */
internal object XiaomiSuperIsland {
    private const val Prefs = "experimental_notification_modes"
    private const val Enabled = "xiaomi_super_island_enabled"
    private const val FocusParameter = "miui.focus.param"
    private const val CoursePicture = "miui.focus.pic_course"

    fun isXiaomiDevice(manufacturer: String, brand: String): Boolean =
        listOf(manufacturer, brand).any { value ->
            value.equals("xiaomi", true) || value.equals("redmi", true) ||
                value.equals("poco", true)
        }

    fun protocolVersion(context: Context): Int = runCatching {
        Settings.System.getInt(context.contentResolver, "notification_focus_protocol", 0)
    }.getOrDefault(0)

    fun isEnabled(context: Context): Boolean = BuildConfig.SLEEPDOWN_EXP_BUILD &&
        isXiaomiDevice(Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty()) &&
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).getBoolean(Enabled, false)

    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        val accepted = enabled && BuildConfig.SLEEPDOWN_EXP_BUILD &&
            isXiaomiDevice(Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty())
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit()
            .putBoolean(Enabled, accepted).apply()
        return accepted
    }

    fun hasFocusPermission(context: Context): Boolean = runCatching {
        val args = Bundle().apply { putString("package", context.packageName) }
        context.contentResolver.call(
            Uri.parse("content://miui.statusbar.notification.public"),
            "canShowFocus", null, args
        )?.getBoolean("canShowFocus", false) == true
    }.getOrDefault(false)

    fun decorate(
        context: Context,
        notification: Notification,
        payload: LiveUpdatePayload,
        status: LiveUpdateStatus,
        shortText: String
    ) {
        if (!isEnabled(context) || BuildConfig.SLEEPDOWN_XIAOMI_APP_ID.isBlank() ||
            protocolVersion(context) < 3) return
        notification.extras.putString(FocusParameter, parameters(payload, status, shortText))
        notification.extras.putBundle("miui.focus.pics", Bundle().apply {
            putParcelable(CoursePicture, Icon.createWithResource(context, R.drawable.ic_school_import))
        })
    }

    internal fun parameters(
        payload: LiveUpdatePayload,
        status: LiveUpdateStatus,
        shortText: String
    ): String {
        val detail = if (payload.kind == LiveUpdateKind.TOMORROW) {
            payload.location.ifBlank { payload.timeText }
        } else {
            listOf(payload.timeText, payload.location).filter(String::isNotBlank).joinToString(" · ")
        }
        val icon = JSONObject().put("type", 1).put("pic", CoursePicture)
        val bigText = JSONObject()
            .put("frontTitle", status.statusText)
            .put("title", payload.name)
            .put("content", detail)
            .put("useHighLight", false)
        val left = JSONObject()
            .put("type", 1)
            .put("picInfo", icon)
            .put("miui.focus.paramtextInfo", bigText)
        val island = JSONObject()
            .put("islandProperty", 1)
            .put("bigIslandArea", JSONObject().put("imageTextInfoLeft", left))
            .put("smallIslandArea", JSONObject().put("picInfo", icon))
        val card = JSONObject()
            .put("title", payload.name)
            .put("content", detail)
            .put("type", 2)
        return JSONObject().put("param_v2", JSONObject()
            .put("protocol", 1)
            .put("business", "education")
            .put("enableFloat", false)
            .put("updatable", true)
            .put("ticker", shortText)
            .put("aodTitle", shortText)
            .put("param_island", island)
            .put("baseInfo", card)
        ).toString()
    }
}
