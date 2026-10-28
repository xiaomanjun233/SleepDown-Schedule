package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.ImportDraft
import com.xiaomanjun.sleepdownschedule.model.PeriodSchemeTimeEntity

/** The grid is a reference, never a normalization that changes source period identities. */
fun ImportDraft.withCompatiblePeriodGrid(): ImportDraft {
    val sorted = periods.sortedBy { it.periodIndex }
    require(sorted.isNotEmpty()) { "没有可用于显示课程的作息" }
    validateResolvedPeriodTimes(sorted.map {
        PeriodSchemeTimeEntity(0, it.periodIndex, it.startTime, it.endTime, it.alias)
    })?.let { throw IllegalArgumentException(it) }
    return copy(periods = sorted)
}
