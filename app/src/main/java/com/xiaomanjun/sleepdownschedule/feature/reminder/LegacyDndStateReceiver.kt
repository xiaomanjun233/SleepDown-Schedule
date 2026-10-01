package com.xiaomanjun.sleepdownschedule.feature.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class LegacyDndStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) {
            NotificationScheduler.observeLegacyDndState(context)
        }
    }
}
