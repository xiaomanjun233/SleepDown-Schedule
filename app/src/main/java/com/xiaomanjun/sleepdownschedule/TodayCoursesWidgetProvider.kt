package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.feature.widget.*
import com.xiaomanjun.sleepdownschedule.feature.widget.providers.*

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

class TodayCoursesWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        keepBroadcastAliveUntil(
            MiuixTodayWidgetRenderer.refreshAsync(
                context,
                appWidgetManager,
                appWidgetIds,
                TodayWidgetVariant.LARGE
            )
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (MiuixTodayWidgetRenderer.isRefreshAction(intent.action)) {
            keepBroadcastAliveUntil(refreshAllAsync(context))
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        keepBroadcastAliveUntil(
            MiuixTodayWidgetRenderer.refreshAsync(
                context,
                appWidgetManager,
                intArrayOf(appWidgetId),
                TodayWidgetVariant.LARGE
            )
        )
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val app = context.applicationContext as CourseScheduleApp
        keepBroadcastAliveUntil(
            launchWidgetWork(context) {
                appWidgetIds.forEach {
                    app.widgetAppearanceRepository.deleteInstance(WidgetAppearanceVariant.COURSES_LARGE, it)
                }
            }
        )
    }

    companion object {
        fun refreshAll(context: Context) {
            MiuixTodayWidgetRenderer.refreshAll(context)
        }

        internal fun refreshAllAsync(context: Context) =
            MiuixTodayWidgetRenderer.refreshAllAsync(context)
    }
}
