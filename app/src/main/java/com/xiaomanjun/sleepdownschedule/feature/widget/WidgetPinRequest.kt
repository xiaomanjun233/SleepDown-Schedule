package com.xiaomanjun.sleepdownschedule.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.xiaomanjun.sleepdownschedule.TodayCoursesWidgetProvider

/** The launcher owns the consent dialog; BIND_APPWIDGET is a host-only permission. */
internal fun requestWidgetPin(context: Context, provider: ComponentName, preview: Bundle) {
    val manager = AppWidgetManager.getInstance(context)
    val message = try {
        if (!manager.isRequestPinAppWidgetSupported) {
            "当前桌面不支持直接添加，请长按桌面空白处，选择小组件 → SleepDown"
        } else {
            val callback = PendingIntent.getBroadcast(context, provider.className.hashCode(),
                Intent(context, WidgetPinnedReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            if (manager.requestPinAppWidget(provider, preview, callback)) {
                "已向系统申请添加，请在桌面确认。若未弹窗，请在系统应用权限中允许添加桌面小组件。"
            } else "桌面未接受添加请求，请长按桌面空白处添加 SleepDown 小组件"
        }
    } catch (_: SecurityException) {
        "系统阻止了添加请求，请在系统应用权限中允许添加桌面小组件后重试"
    } catch (_: IllegalStateException) {
        "请保持此页面在前台，然后重试添加小组件"
    }
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

class WidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Toast.makeText(context, "已添加到桌面", Toast.LENGTH_SHORT).show()
        val pending = goAsync()
        TodayCoursesWidgetProvider.refreshAllAsync(context).invokeOnCompletion { pending.finish() }
    }
}
