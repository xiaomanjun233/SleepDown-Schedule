package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 时段课程是课表级的固定时段标记，例如早读、午休、晚自习。
 * 它不参与课程统计、提醒和冲突计算，只按 [afterPeriod] 锚定在周视图的节次边界上。
 * 0 表示第一节之前，N 表示第 N 节之后。
 */
@Serializable
enum class TimeSlotDisplayMode { INDEPENDENT, GRID, SMART }

/** The picker offers two modes; GRID is the retired third one and keeps decoding as 智能显示. */
internal val SelectableTimeSlotDisplayModes = listOf(
    TimeSlotDisplayMode.INDEPENDENT,
    TimeSlotDisplayMode.SMART
)

/** 独立显示 keeps its own row on the boundary; every other mode occupies lesson cells. */
internal fun TimeSlotDisplayMode.occupiesGrid(): Boolean = this != TimeSlotDisplayMode.INDEPENDENT

@Serializable
data class TimeSlotCourse(
    val id: String,
    val name: String,
    val startTime: String,
    val endTime: String,
    val afterPeriod: Int = 0,
    val weekdays: List<Int> = DefaultTimeSlotWeekdays,
    /** Empty means an exact range; selected periods follow the active timetable's bells. */
    val periodIndices: List<Int> = emptyList(),
    /** Same per-lesson clock protocol as ordinary courses; present only for lesson mode. */
    val customPeriodTimes: String? = null,
    val displayMode: TimeSlotDisplayMode = TimeSlotDisplayMode.INDEPENDENT,
    val location: String = "",
    val note: String = "",
    val startWeek: Int = 1,
    val endWeek: Int? = null
)

internal val DefaultTimeSlotWeekdays = listOf(1, 2, 3, 4, 5)
internal const val MaxTimeSlotCourses = 12
internal const val MaxTimeSlotNameLength = 12
internal const val MaxTimeSlotAnchor = 30

private val timeSlotJson = Json { ignoreUnknownKeys = true }
private val timeSlotFormatter = DateTimeFormatter.ofPattern("HH:mm")

private val timeSlotCache = LinkedHashMap<String, List<TimeSlotCourse>>(4, 0.75f, true)

fun decodeTimeSlotCourses(value: String): List<TimeSlotCourse> = synchronized(timeSlotCache) {
    timeSlotCache[value] ?: run {
        val decoded = if (value.isBlank()) emptyList() else
            timeSlotJson.decodeFromString<List<TimeSlotCourse>>(value).also(::validateTimeSlotCourses)
        timeSlotCache[value] = decoded
        if (timeSlotCache.size > 4) timeSlotCache.remove(timeSlotCache.keys.first())
        decoded
    }
}

fun encodeTimeSlotCourses(values: List<TimeSlotCourse>): String {
    validateTimeSlotCourses(values)
    if (values.isEmpty()) return ""
    return timeSlotJson.encodeToString(
        values.sortedWith(compareBy<TimeSlotCourse> { it.afterPeriod }.thenBy { it.startTime }.thenBy { it.id })
            .map { it.copy(name = it.name.trim(), weekdays = it.weekdays.distinct().sorted(), periodIndices = it.periodIndices.distinct().sorted()) }
    )
}

fun validateTimeSlotCourses(values: List<TimeSlotCourse>) {
    require(values.size <= MaxTimeSlotCourses) { "时段课程最多 $MaxTimeSlotCourses 条" }
    require(values.map { it.id }.distinct().size == values.size) { "时段课程标识重复" }
    values.forEach { slot ->
        require(slot.id.isNotBlank()) { "时段课程标识不能为空" }
        require(slot.name.isNotBlank()) { "请填写时段课程名称" }
        require(slot.name.trim().length <= MaxTimeSlotNameLength) { "时段课程名称不能超过 $MaxTimeSlotNameLength 字" }
        val start = parseTimeSlotTime(slot.startTime)
        val end = parseTimeSlotTime(slot.endTime)
        require(start != null && end != null) { "时段课程时间格式必须为 HH:mm" }
        require(start < end) { "时段课程结束时间必须晚于开始时间" }
        require(slot.afterPeriod in 0..MaxTimeSlotAnchor) { "时段课程位置无效" }
        require(slot.startWeek >= 1 && (slot.endWeek == null || slot.endWeek >= slot.startWeek)) { "结束周不能早于开始周" }
        require(slot.location.length <= 200 && slot.note.length <= 2000) { "地点或备注过长" }
        require(slot.weekdays.isNotEmpty()) { "请至少选择一个星期" }
        require(slot.weekdays.all { it in 1..7 }) { "时段课程星期无效" }
        require(slot.periodIndices.size <= 40 && slot.periodIndices.all { it > 0 } &&
            slot.periodIndices.distinct().size == slot.periodIndices.size) { "特殊课程节次无效" }
        if (!slot.customPeriodTimes.isNullOrBlank()) {
            val lessons = parseCoursePeriodTimes(slot.customPeriodTimes)
            require(lessons.map { it.index } == slot.periodIndices.sorted()) { "特殊课程课时与逐节时间不一致" }
            normalizeCourseClock(slot.startTime, slot.endTime, slot.customPeriodTimes, slot.periodIndices)
        }
    }
}

fun parseTimeSlotTime(value: String): LocalTime? = value.takeIf { it.matches(Regex("\\d{2}:\\d{2}")) }
    ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

fun formatTimeSlotTime(value: LocalTime): String = value.format(timeSlotFormatter)

/** Preserve unparseable stored data so saving an unrelated setting cannot silently erase it. */
fun canonicalTimeSlotCourses(value: String): String =
    runCatching { encodeTimeSlotCourses(decodeTimeSlotCourses(value)) }.getOrDefault(value)

/** Apply one editor's change without replacing other slots added while it was open. */
internal fun editTimeSlotCourse(
    current: List<TimeSlotCourse>, original: TimeSlotCourse?, updated: TimeSlotCourse?
): List<TimeSlotCourse> {
    require(original != null || updated != null) { "特殊课程编辑已失效" }
    if (original != null) {
        fun TimeSlotCourse.normalized() = copy(name = name.trim(), weekdays = weekdays.distinct().sorted(), periodIndices = periodIndices.sorted())
        require(current.firstOrNull { it.id == original.id }?.normalized() == original.normalized()) {
            "这条特殊课程已有其他修改，请重新打开后编辑"
        }
        require(updated == null || updated.id == original.id) { "特殊课程标识不能改变" }
    } else {
        require(current.none { it.id == updated!!.id }) { "特殊课程已存在，请重新打开" }
    }
    return (current.filterNot { it.id == original?.id } + listOfNotNull(updated))
        .also(::validateTimeSlotCourses)
}

/** Gaps have one anchor. A range covering lessons may sit before or after that entire span. */
internal fun allowedTimeSlotAnchors(start: String, end: String, periods: List<PeriodEntity>): List<Int> {
    val startClock = requireNotNull(parseTimeSlotTime(start)) { "请选择有效的开始时间" }
    val endClock = requireNotNull(parseTimeSlotTime(end)) { "请选择有效的结束时间" }
    require(startClock < endClock) { "结束时间必须晚于开始时间" }
    val sorted = periods.sortedBy { it.periodIndex }
    val clocks = sorted.map { period ->
        requireNotNull(parseTimeSlotTime(period.startTime)) { "作息开始时间无效，请先检查作息" } to
            requireNotNull(parseTimeSlotTime(period.endTime)) { "作息结束时间无效，请先检查作息" }
    }
    val gaps = (0..sorted.size).filter { boundary ->
        (boundary == 0 || clocks[boundary - 1].second <= startClock) &&
            (boundary == sorted.size || endClock <= clocks[boundary].first)
    }.map { if (it == 0) 0 else sorted[it - 1].periodIndex }
    if (gaps.isNotEmpty()) return gaps
    val covered = clocks.indices.filter { startClock < clocks[it].second && endClock > clocks[it].first }
    if (covered.isEmpty()) return emptyList()
    return listOf(if (covered.first() == 0) 0 else sorted[covered.first() - 1].periodIndex,
        sorted[covered.last()].periodIndex).distinct()
}

/** Keep an orphaned selection's saved clock visible until it is edited, never erase its data. */
internal fun resolvedTimeSlotCourse(slot: TimeSlotCourse, periods: List<PeriodEntity>): TimeSlotCourse {
    if (slot.periodIndices.isEmpty() || !slot.customPeriodTimes.isNullOrBlank()) return slot
    val byIndex = periods.associateBy { it.periodIndex }
    val selected = slot.periodIndices.sorted().map { byIndex[it] ?: return slot }
    val start = selected.first().startTime
    val end = selected.last().endTime
    val anchors = allowedTimeSlotAnchors(start, end, periods)
    return slot.copy(startTime = start, endTime = end,
        afterPeriod = slot.afterPeriod.takeIf { it in anchors } ?: anchors.firstOrNull() ?: slot.afterPeriod)
}

internal fun resolvedTimeSlotCourses(value: String, periods: List<PeriodEntity>): List<TimeSlotCourse> =
    decodeTimeSlotCourses(value).map { resolvedTimeSlotCourse(it, periods) }

/** 周视图按天取用；锚点超出当前作息节次时收敛到最后一天内，避免绘制到网格外。 */
fun timeSlotCoursesForWeekday(
    config: ScheduleConfigEntity,
    weekday: Int,
    periodCount: Int
): List<TimeSlotCourse> = runCatching {
    decodeTimeSlotCourses(config.timeSlotCoursesJson)
        .filter { weekday in it.weekdays }
        .map { it.copy(afterPeriod = it.afterPeriod.coerceIn(0, periodCount.coerceAtLeast(0))) }
        .sortedWith(compareBy<TimeSlotCourse> { it.afterPeriod }.thenBy { it.startTime })
}.getOrDefault(emptyList())

fun timeSlotAnchorLabel(afterPeriod: Int): String =
    if (afterPeriod <= 0) "第一节前" else "第${afterPeriod}节后"

fun timeSlotWeekdaySummary(weekdays: Collection<Int>): String {
    val days = weekdays.filter { it in 1..7 }.distinct().sorted()
    return when (days) {
        emptyList<Int>() -> "未选择星期"
        (1..7).toList() -> "每天"
        (1..5).toList() -> "周一至周五"
        (6..7).toList() -> "周末"
        else -> days.joinToString("、") { "周${ScheduleWeekdayLabels[it - 1]}" }
    }
}

fun timeSlotCourseSummary(slot: TimeSlotCourse): String =
    "${timeSlotAnchorLabel(slot.afterPeriod)} · ${slot.startTime}–${slot.endTime} · ${timeSlotWeekdaySummary(slot.weekdays)}"

private val ScheduleWeekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
