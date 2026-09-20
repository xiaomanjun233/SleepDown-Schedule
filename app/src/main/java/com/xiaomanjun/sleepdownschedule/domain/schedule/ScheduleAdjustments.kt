package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A null source means no classes. A source uses that original teaching date, without chaining. */
@Serializable
data class ScheduleAdjustment(val date: String, val sourceDate: String? = null, val label: String = "")

private val adjustmentJson = Json { ignoreUnknownKeys = true }

fun decodeScheduleAdjustments(value: String): List<ScheduleAdjustment> {
    if (value.isBlank()) return emptyList()
    return adjustmentJson.decodeFromString<List<ScheduleAdjustment>>(value).also(::validateScheduleAdjustments)
}

fun encodeScheduleAdjustments(values: List<ScheduleAdjustment>): String {
    validateScheduleAdjustments(values)
    return if (values.isEmpty()) "" else adjustmentJson.encodeToString(values.sortedBy { it.date })
}

fun validateScheduleAdjustments(values: List<ScheduleAdjustment>) {
    require(values.size <= 3660) { "调休日期过多" }
    require(values.map { it.date }.distinct().size == values.size) { "同一天只能设置一个调休安排" }
    values.forEach {
        val date = LocalDate.parse(it.date)
        require(it.label.length <= 80) { "调休名称不能超过 80 字" }
        it.sourceDate?.let { source -> require(LocalDate.parse(source) != date) { "补课日期不能与原课程日期相同" } }
    }
}

/** UI dates may contain dots; persisted dates always use ISO, shared with backup and Agent. */
fun scheduleAdjustmentFromInput(date: String, sourceDate: String?, label: String = ""): ScheduleAdjustment {
    val target = requireNotNull(parseScheduleDate(date)) { "请选择有效的调休日期" }
    val source = sourceDate?.let { requireNotNull(parseScheduleDate(it)) { "请选择有效的原课程日期" } }
    return ScheduleAdjustment(target.toString(), source?.toString(), label).also {
        validateScheduleAdjustments(listOf(it))
    }
}

fun replaceScheduleAdjustment(
    entries: List<ScheduleAdjustment>, originalDate: String?, replacement: ScheduleAdjustment
): List<ScheduleAdjustment> {
    require(entries.none { it.date == replacement.date && it.date != originalDate }) { "该日期已有安排，请编辑原安排" }
    return (entries.filterNot { it.date == originalDate } + replacement).sortedBy { it.date }
        .also(::validateScheduleAdjustments)
}

fun scheduleAdjustmentForDate(config: ScheduleConfigEntity, date: LocalDate): ScheduleAdjustment? =
    decodeScheduleAdjustments(config.scheduleAdjustmentsJson).firstOrNull { it.date == date.toString() }

fun teachingDateForSchedule(config: ScheduleConfigEntity, date: LocalDate): LocalDate? {
    val adjustment = scheduleAdjustmentForDate(config, date) ?: return date
    return adjustment.sourceDate?.let(LocalDate::parse)
}

fun adjustedTeachingWeekForDate(config: ScheduleConfigEntity, date: LocalDate, today: LocalDate = LocalDate.now()): Int? {
    if (config.autoCurrentWeek) return scheduleWeekForDateOrNull(config, date)
    val monday = scheduleWeekStartDate(config, config.currentWeek, today)
    val week = config.currentWeek + Math.floorDiv(ChronoUnit.DAYS.between(monday, date), 7L).toInt()
    return week.takeIf { it in 1..config.totalWeeks }
}
