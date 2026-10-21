package com.xiaomanjun.sleepdownschedule.feature.reminder

/** The current phase retains its slot, while a new phase uses the other notification ID. */
internal fun liveUpdateNotificationSlot(
    activeNewestFirst: List<Pair<Int, String?>>,
    identity: String,
    primaryId: Int,
    secondaryId: Int
): Int {
    activeNewestFirst.firstOrNull { it.second == identity }?.let { return it.first }
    return if (activeNewestFirst.firstOrNull()?.first == primaryId) secondaryId else primaryId
}
