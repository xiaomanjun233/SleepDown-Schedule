package com.xiaomanjun.sleepdownschedule.feature.widget.providers

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import com.xiaomanjun.sleepdownschedule.feature.widget.WidgetAppearanceVariant

open class WeekScheduleWidgetProviderHost : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        keepBroadcastAliveUntil(WeekScheduleWidgetRenderer.refreshAsync(context, manager, ids))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (MiuixTodayWidgetRenderer.isRefreshAction(intent.action)) {
            keepBroadcastAliveUntil(MiuixTodayWidgetRenderer.refreshAllAsync(context))
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        keepBroadcastAliveUntil(
            WeekScheduleWidgetRenderer.refreshAsync(context, manager, intArrayOf(appWidgetId))
        )
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val app = context.applicationContext as CourseScheduleApp
        keepBroadcastAliveUntil(
            launchWidgetWork(context) {
                appWidgetIds.forEach {
                    app.widgetAppearanceRepository.deleteInstance(
                        WidgetAppearanceVariant.WEEK_SCHEDULE,
                        it
                    )
                }
            }
        )
    }
}
