package com.xiaomanjun.sleepdownschedule

import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class LiveUpdateActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationScheduler.ACTION_CANCEL_LIVE_UPDATE -> {
                NotificationScheduler.cancelCurrentLiveUpdate(
                    context,
                    intent.getStringExtra("muteKey"),
                    intent.getStringExtra("muteUntil")
                )
            }
            NotificationScheduler.ACTION_TOGGLE_DND -> {
                NotificationScheduler.toggleDoNotDisturb(context)
            }
        }
    }
}
