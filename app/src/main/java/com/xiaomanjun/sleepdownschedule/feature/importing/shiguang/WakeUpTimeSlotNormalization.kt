package com.xiaomanjun.sleepdownschedule.feature.importing.shiguang

import java.time.LocalTime

internal data class NormalizedWakeUpTimeSlots(
    val slots: List<ShiguangTimeSlotPayload>,
    val sectionMapping: Map<Int, Int>
)

/** Merge overlapping clock intervals, keeping the earliest start and every course reference. */
internal fun mergeWakeUpTimeSlots(slots: List<ShiguangTimeSlotPayload>): NormalizedWakeUpTimeSlots {
    val merged = mutableListOf<ShiguangTimeSlotPayload>()
    val sectionMapping = mutableMapOf<Int, Int>()
    slots.sortedWith(compareBy({ LocalTime.parse(it.startTime) }, { it.number })).forEach { slot ->
        val previous = merged.lastOrNull()
        if (previous != null && LocalTime.parse(slot.startTime) < LocalTime.parse(previous.endTime)) {
            // Strict overlap: touching lessons retain their separate period numbers.
            merged[merged.lastIndex] = previous.copy(
                endTime = maxOf(LocalTime.parse(previous.endTime), LocalTime.parse(slot.endTime)).toString()
            )
        } else {
            merged += slot.copy(number = merged.size + 1)
        }
        sectionMapping[slot.number] = merged.size
    }
    return NormalizedWakeUpTimeSlots(merged, sectionMapping)
}
