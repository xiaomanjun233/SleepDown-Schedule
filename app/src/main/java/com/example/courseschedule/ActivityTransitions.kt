package com.example.courseschedule

import android.app.Activity
import android.content.Intent
import android.os.Build

const val ScheduleCustomizeIdExtra = "schedule_customize_id"
const val ScheduleEntrySnapshotExtra = "schedule_entry_snapshot"

fun Activity.startActivityWithScheduleDepthTransition(intent: Intent) {
    if (Build.VERSION.SDK_INT >= 34) {
        startActivity(intent)
    } else {
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.schedule_depth_enter, R.anim.schedule_depth_exit)
    }
}

fun Activity.installScheduleDepthTransitions() {
    if (Build.VERSION.SDK_INT >= 34) {
        overrideActivityTransition(
            Activity.OVERRIDE_TRANSITION_OPEN,
            R.anim.schedule_depth_enter,
            R.anim.schedule_depth_exit
        )
        overrideActivityTransition(
            Activity.OVERRIDE_TRANSITION_CLOSE,
            R.anim.schedule_depth_pop_enter,
            R.anim.schedule_depth_pop_exit
        )
    }
}

fun Activity.applyLegacyScheduleDepthCloseTransition() {
    if (Build.VERSION.SDK_INT < 34) {
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.schedule_depth_pop_enter, R.anim.schedule_depth_pop_exit)
    }
}

fun android.content.Intent.putScheduleCustomizeId(scheduleId: Int): android.content.Intent =
    putExtra(ScheduleCustomizeIdExtra, scheduleId)
