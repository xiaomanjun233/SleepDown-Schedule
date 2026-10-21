package com.xiaomanjun.sleepdownschedule.feature.home.week

/** Display the existing week beside the requested week, while keeping every pager key unique. */
internal data class AdjacentWeekJump(val sourcePage: Int, val targetPage: Int) {
    init { require(sourcePage != targetPage) }
    val sourceSlot = targetPage - (targetPage - sourcePage).compareTo(0)

    fun logicalPage(slot: Int): Int = when (slot) {
        sourceSlot -> sourcePage
        sourcePage -> sourceSlot
        else -> slot
    }

    fun contains(slot: Int): Boolean = slot == sourceSlot || slot == targetPage
}
