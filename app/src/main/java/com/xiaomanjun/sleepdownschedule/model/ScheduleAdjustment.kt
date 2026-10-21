package com.xiaomanjun.sleepdownschedule.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A null source means no classes. A source uses that original teaching date, without chaining. */
@Serializable
@SerialName("com.xiaomanjun.sleepdownschedule.domain.schedule.ScheduleAdjustment")
data class ScheduleAdjustment(
    val date: String, val sourceDate: String? = null, val label: String = "",
    /** Presentation only. Calendar queries deliberately ignore this flag. */
    val allDayPlaceholder: Boolean = false
)
