package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CourseBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_LOCKED_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_DATE_CHANGED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
            )
        ) {
            return
        }
        val pending = goAsync()
        // Rebuild absolute timestamps after clock/zone changes and restore cleared alarms at boot.
        NotificationScheduler.requestRefresh(context, forceReschedule = true) {
            pending.finish()
        }
    }
}
