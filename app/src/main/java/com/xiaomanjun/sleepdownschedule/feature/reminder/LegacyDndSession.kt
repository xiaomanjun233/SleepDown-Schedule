package com.xiaomanjun.sleepdownschedule.feature.reminder

import android.app.NotificationManager

/** Ownership of a global DND change on Android 14 and earlier. */
internal data class LegacyDndSession(
    val previousFilter: Int,
    val appliedFilter: Int,
    val bootCount: Int,
    val overridden: Boolean = false
) {
    fun observe(currentFilter: Int, currentBootCount: Int): LegacyDndSession =
        if (currentFilter != appliedFilter || currentBootCount != bootCount) copy(overridden = true) else this

    fun filterToRestore(currentFilter: Int, currentBootCount: Int): Int? =
        previousFilter.takeIf { !overridden && currentFilter == appliedFilter && currentBootCount == bootCount }

    companion object {
        fun start(currentFilter: Int, bootCount: Int): LegacyDndSession {
            require(currentFilter in setOf(
                NotificationManager.INTERRUPTION_FILTER_ALL,
                NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                NotificationManager.INTERRUPTION_FILTER_ALARMS,
                NotificationManager.INTERRUPTION_FILTER_NONE
            )) { "Unable to read the current DND mode" }
            require(bootCount >= 0) { "Unable to read the current boot count" }
            return LegacyDndSession(
                previousFilter = currentFilter,
                appliedFilter = if (currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL)
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY else currentFilter,
                bootCount = bootCount
            )
        }
    }
}
