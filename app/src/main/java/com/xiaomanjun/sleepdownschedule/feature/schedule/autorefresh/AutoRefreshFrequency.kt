package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

internal object AutoRefreshFrequency {
    const val DailyMinutes = 24L * 60L
    const val WeeklyMinutes = 7L * DailyMinutes

    // Preserve whether automation was enabled, but retire the old sub-day intervals.
    fun normalize(minutes: Long): Long =
        if (minutes >= WeeklyMinutes) WeeklyMinutes else DailyMinutes

    fun selectedIndex(automatic: Boolean, minutes: Long): Int = when {
        !automatic -> 0
        normalize(minutes) == WeeklyMinutes -> 2
        else -> 1
    }
}
