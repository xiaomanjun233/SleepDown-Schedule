package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CourseBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_LOCKED_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_DATE_CHANGED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED
            )
        ) {
            return
        }
        val pending = goAsync()
        val app = context.applicationContext as CourseScheduleApp
        app.applicationScope.launch(Dispatchers.IO) {
            try {
                app.repository.ensureDefaults()
                val snapshot = app.repository.activeSnapshot()
                NotificationScheduler.refreshToday(context, snapshot.courses, snapshot.config, snapshot.periods)
            } finally {
                pending.finish()
            }
        }
    }
}
