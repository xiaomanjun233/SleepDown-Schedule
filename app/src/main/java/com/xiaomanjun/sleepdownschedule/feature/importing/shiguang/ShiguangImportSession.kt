package com.xiaomanjun.sleepdownschedule.feature.importing.shiguang

import com.xiaomanjun.sleepdownschedule.ImportDraft
import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.WeekParity
import com.xiaomanjun.sleepdownschedule.domain.schedule.courseAnchorPeriodsForTimeRange
import com.xiaomanjun.sleepdownschedule.domain.schedule.normalizeCourseClock
import com.xiaomanjun.sleepdownschedule.domain.schedule.parseCoursePeriodTimes
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.LocalTime
import com.xiaomanjun.sleepdownschedule.feature.importing.NativeCourseTimingJson
import com.xiaomanjun.sleepdownschedule.feature.importing.CanonicalScheduleJson
import com.xiaomanjun.sleepdownschedule.feature.importing.CanonicalScheduleDocument
import com.xiaomanjun.sleepdownschedule.feature.importing.ComboScheduleJson
import kotlinx.serialization.encodeToString

@Serializable
internal data class ShiguangCoursePayload(
    val id: String? = null,
    val name: String,
    val teacher: String,
    val position: String,
    val day: Int,
    val startSection: Int? = null,
    val endSection: Int? = null,
    val weeks: List<Int>,
    val isCustomTime: Boolean = false,
    val customStartTime: String? = null,
    val customEndTime: String? = null,
    val color: Int? = null,
    val remark: String? = null,
    val customPeriodTimes: String? = null,
    val credit: Float? = null,
    val sleepdown: NativeCourseTimingJson? = null
)

@Serializable
internal data class ShiguangCourseConfigPayload(
    val semesterStartDate: String? = null,
    val semesterTotalWeeks: Int = 20,
    val defaultClassDuration: Int = 45,
    val defaultBreakDuration: Int = 10,
    val firstDayOfWeek: Int = 1
)

@Serializable
internal data class ShiguangTimeSlotPayload(
    val number: Int,
    val startTime: String,
    val endTime: String,
    val alias: String? = null
)

internal class ShiguangImportSession {
    private val lock = Any()
    private var active = false
    private var baseConfig: ScheduleConfigEntity? = null
    private var basePeriods: List<PeriodEntity> = emptyList()
    private var courses: List<ShiguangCoursePayload>? = null
    private var courseConfig: ShiguangCourseConfigPayload? = null
    private var timeSlots: List<ShiguangTimeSlotPayload>? = null
    private var mergeOverlappingTimeSlots = false
    private var allowImportedBellTimes = false
    private var sectionMapping: Map<Int, Int>? = null
    private var originalConflictingSlots: List<ShiguangTimeSlotPayload>? = null
    private var comboSchedule: ComboScheduleJson? = null

    fun begin(
        config: ScheduleConfigEntity,
        periods: List<PeriodEntity>,
        mergeOverlappingTimeSlots: Boolean = false,
        allowImportedBellTimes: Boolean = false
    ) {
        synchronized(lock) {
            active = true
            baseConfig = config
            basePeriods = periods
            courses = null
            courseConfig = null
            timeSlots = null
            this.mergeOverlappingTimeSlots = mergeOverlappingTimeSlots
            this.allowImportedBellTimes = allowImportedBellTimes
            sectionMapping = null
            originalConflictingSlots = null
            comboSchedule = null
        }
    }

    fun stageCourses(json: String) {
        val parsed = ShiguangPayloadJson.decodeFromString(
            ListSerializer(ShiguangCoursePayload.serializer()),
            json
        )
        require(parsed.isNotEmpty()) { "课程数据为空" }
        parsed.forEachIndexed { index, course ->
            require(allowImportedBellTimes || course.customPeriodTimes == null) {
                "逐节铃声仅支持真实教务适配器导入"
            }
            validateCourse(index, course)
        }
        synchronized(lock) {
            require(active) { "当前没有正在执行的拾光导入任务" }
            courses = parsed
        }
    }

    fun stageCourseConfig(json: String) {
        val parsed = ShiguangPayloadJson.decodeFromString(ShiguangCourseConfigPayload.serializer(), json)
        require(parsed.semesterTotalWeeks in 1..60) { "semesterTotalWeeks 必须在 1 到 60 之间" }
        require(parsed.defaultClassDuration in 1..300) { "defaultClassDuration 无效" }
        require(parsed.defaultBreakDuration in 0..300) { "defaultBreakDuration 无效" }
        require(parsed.firstDayOfWeek in 1..7) { "firstDayOfWeek 必须在 1 到 7 之间" }
        parsed.semesterStartDate?.let { java.time.LocalDate.parse(it) }
        synchronized(lock) {
            require(active) { "当前没有正在执行的拾光导入任务" }
            courseConfig = parsed
        }
    }

    fun stageTimeSlots(json: String) {
        val parsed = ShiguangPayloadJson.decodeFromString(
            ListSerializer(ShiguangTimeSlotPayload.serializer()),
            json
        )
        require(parsed.isNotEmpty()) { "时间段数据为空" }
        val sorted = parsed.distinct()
            .sortedBy(ShiguangTimeSlotPayload::number)
        require(sorted.map { it.number }.distinct().size == sorted.size) { "同一节次存在不同时间，无法确定课程的真实作息" }
        sorted.forEachIndexed { index, slot ->
            require(slot.number > 0) { "时间段编号必须大于 0" }
            val start = parseTime(slot.startTime, "第 ${slot.number} 节 startTime")
            val end = parseTime(slot.endTime, "第 ${slot.number} 节 endTime")
            require(start < end) { "第 ${slot.number} 节结束时间必须晚于开始时间" }
        }
        synchronized(lock) {
            require(active) { "当前没有正在执行的拾光导入任务" }
            require(comboSchedule == null) { "组合作息已提交，不能再覆盖基准作息" }
            val overlapping = sorted.zipWithNext().any { (previous, next) ->
                LocalTime.parse(next.startTime) < LocalTime.parse(previous.endTime)
            }
            if (overlapping) {
                // Keep exact source clocks; the existing base is only the display reference.
                // No source period is merged, invented, or renumbered.
                originalConflictingSlots = sorted
                timeSlots = basePeriods.map { ShiguangTimeSlotPayload(it.periodIndex, it.startTime, it.endTime, it.alias) }
            } else {
                originalConflictingSlots = null
                timeSlots = sorted
            }
            sectionMapping = null
        }
    }

    fun stageComboSchedule(json: String) {
        val parsed = ShiguangPayloadJson.decodeFromString<ComboScheduleJson>(json)
        synchronized(lock) {
            require(active) { "当前没有正在执行的拾光导入任务" }
            require(timeSlots != null) { "saveComboSchedule 必须在 savePresetTimeSlots 成功后调用" }
            require(comboSchedule == null) { "组合作息已经提交" }
            comboSchedule = parsed
        }
    }

    fun complete(): ImportDraft {
        val snapshot = synchronized(lock) {
            require(active) { "当前没有正在执行的拾光导入任务" }
            active = false
            SessionSnapshot(
                baseConfig = checkNotNull(baseConfig),
                basePeriods = basePeriods,
                courses = courses ?: error("适配器未提交课程数据"),
                courseConfig = courseConfig,
                timeSlots = timeSlots,
                sectionMapping = sectionMapping,
                originalConflictingSlots = originalConflictingSlots,
                comboSchedule = comboSchedule
            )
        }
        return snapshot.toDraft()
    }

    private fun validateCourse(index: Int, course: ShiguangCoursePayload) {
        val label = "第 ${index + 1} 门课程"
        require(course.name.isNotBlank()) { "$label name 不能为空" }
        require(course.day in 1..7) { "$label day 必须在 1 到 7 之间" }
        require(course.weeks.isNotEmpty()) { "$label weeks 不能为空" }
        require(course.weeks.all { it > 0 }) { "$label weeks 必须为正整数" }
        require(course.credit == null || (course.credit.isFinite() && course.credit >= 0f)) { "$label 学分无效" }
        val startSection = course.startSection
        val endSection = course.endSection
        val hasStartSection = startSection != null
        val hasEndSection = endSection != null
        require(course.isCustomTime || hasStartSection == hasEndSection) { "$label startSection 与 endSection 必须同时提供" }
        if (!course.isCustomTime && startSection != null && endSection != null) {
            require(startSection > 0 && endSection >= startSection) {
                "$label 节次范围无效"
            }
        }
        if (course.hasExactTime()) {
            val periodIndexes = course.sectionRangeOrNull()?.toList()
                ?: parseCoursePeriodTimes(course.customPeriodTimes).map { it.index }
            if (periodIndexes.isNotEmpty()) {
                val clock = normalizeCourseClock(
                    course.customStartTime, course.customEndTime, course.customPeriodTimes, periodIndexes
                )
                require(clock.start != null && clock.end != null) { "$label 缺少真实起止时间" }
            } else {
                val start = parseTime(course.customStartTime, "$label customStartTime")
                val end = parseTime(course.customEndTime, "$label customEndTime")
                require(start < end) { "$label 自定义结束时间必须晚于开始时间" }
            }
        } else {
            require(hasStartSection) { "$label 缺少节次范围" }
        }
    }

    private data class SessionSnapshot(
        val baseConfig: ScheduleConfigEntity,
        val basePeriods: List<PeriodEntity>,
        val courses: List<ShiguangCoursePayload>,
        val courseConfig: ShiguangCourseConfigPayload?,
        val timeSlots: List<ShiguangTimeSlotPayload>?,
        val sectionMapping: Map<Int, Int>?,
        val originalConflictingSlots: List<ShiguangTimeSlotPayload>?,
        val comboSchedule: ComboScheduleJson?
    ) {
        fun toDraft(): ImportDraft {
            if (comboSchedule != null) return CanonicalScheduleJson.decode(ShiguangPayloadJson.encodeToString(
                CanonicalScheduleDocument(courses, timeSlots, courseConfig, comboSchedule)), baseConfig, basePeriods)
            val mappedPeriods = timeSlots?.map { slot ->
                PeriodEntity(slot.number, slot.startTime, slot.endTime, baseConfig.id, slot.alias)
            } ?: basePeriods
            require(mappedPeriods.isNotEmpty()) { "目标课表没有可用于预览的节次" }
            val periodIndexSet = mappedPeriods.mapTo(hashSetOf(), PeriodEntity::periodIndex)
            val sourceSlots = originalConflictingSlots?.associateBy { it.number }
            val mappedCourses = courses.mapIndexed { index, course ->
                val periods = if (course.isCustomTime && course.customPeriodTimes.isNullOrBlank()) emptyList() else course.sectionRangeOrNull()?.map { originalSection ->
                    if (sectionMapping == null || sourceSlots != null) originalSection
                    else requireNotNull(sectionMapping[originalSection]) {
                        "第 ${index + 1} 门课程引用了不存在的节次"
                    }
                }?.distinct()?.sorted()
                    ?: parseCoursePeriodTimes(course.customPeriodTimes).map { it.index }
                        .takeIf { it.isNotEmpty() }
                    ?: emptyList()
                if (!course.hasExactTime()) {
                    require(periods.isNotEmpty()) { "第 ${index + 1} 门课程缺少节次范围" }
                }
                val sourceTimes = sourceSlots?.takeIf { course.sectionRangeOrNull() != null }
                    ?.let { slots -> periods.mapNotNull(slots::get) }
                if (sourceTimes != null && !course.hasExactTime()) {
                    // WakeUp may hide unused intermediate nodes. Keep known endpoint clocks;
                    // no missing lesson or break time is invented inside that range.
                    val knownSlots = requireNotNull(sourceSlots)
                    require(periods.first() in knownSlots && periods.last() in knownSlots) {
                        "第 ${index + 1} 门课程缺少起止节次的真实时间，请补充时间后再导入"
                    }
                } else if (sourceTimes == null) require(periods.all { it in periodIndexSet } || course.hasExactTime()) {
                    "第 ${index + 1} 门课程引用了不存在的节次"
                }
                val originalClock = sourceTimes?.joinToString(";") { "${it.number},${it.startTime}-${it.endTime}" }
                val exactPeriodClock = originalClock?.takeIf {
                    sourceTimes != null && sourceTimes.size == periods.size &&
                        sourceTimes.zipWithNext().all { (a, b) -> LocalTime.parse(b.startTime) >= LocalTime.parse(a.endTime) }
                }
                val clock = if (sourceTimes != null && !course.hasExactTime()) normalizeCourseClock(
                    sourceTimes.minOf { LocalTime.parse(it.startTime) }.toString(),
                    sourceTimes.maxOf { LocalTime.parse(it.endTime) }.toString(), exactPeriodClock, periods
                ) else normalizeCourseClock(course.customStartTime, course.customEndTime, course.customPeriodTimes, periods)
                CourseEntity(
                    name = course.name,
                    teacher = course.teacher.ifBlank { null },
                    location = course.position.ifBlank { null },
                    weekday = course.day,
                    periods = periods,
                    weeks = course.weeks.distinct().sorted(),
                    weekParity = WeekParity.ALL,
                    note = course.remark?.takeIf { it.isNotBlank() },
                    customStartTime = clock.start,
                    customEndTime = clock.end,
                    customPeriodTimes = clock.periodTimes,
                    originalPeriodTimes = originalClock,
                    // Shiguang color is a palette index, not ARGB. SleepDown keeps automatic color assignment.
                    customColorArgb = null,
                    scheduleId = baseConfig.id,
                    credit = course.credit,
                    sourcePaletteIndex = course.color
                )
            }
            val mappedConfig = courseConfig?.let { imported ->
                baseConfig.copy(
                    totalWeeks = imported.semesterTotalWeeks,
                    currentWeek = baseConfig.currentWeek.coerceIn(1, imported.semesterTotalWeeks),
                    termStartDate = imported.semesterStartDate,
                    autoCurrentWeek = imported.semesterStartDate != null,
                    classDurationMinutes = imported.defaultClassDuration,
                    breakDurationMinutes = imported.defaultBreakDuration,
                    weekStartsOnSunday = imported.firstDayOfWeek == 7
                )
            } ?: baseConfig
            require(mappedCourses.all { course -> course.weeks.all { it in 1..mappedConfig.totalWeeks } }) { "课程周次超出学期范围" }
            return ImportDraft(
                config = com.xiaomanjun.sleepdownschedule.domain.schedule.inferPeriodCounts(mappedPeriods).let { counts ->
                    mappedConfig.copy(morningPeriodCount=counts.morning,noonPeriodCount=counts.noon,
                        afternoonPeriodCount=counts.afternoon,eveningPeriodCount=counts.evening)
                },
                periods = mappedPeriods,
                courses = mappedCourses,
                notices = if (originalConflictingSlots != null) listOf("原作息含重叠时段，课程真实时间与原始编号已保留；网格继续使用当前基准作息。") else emptyList()
            )
        }
    }
}

private fun ShiguangCoursePayload.sectionRangeOrNull(): IntRange? {
    val start = startSection ?: return null
    val end = endSection ?: return null
    return start..end
}

private fun ShiguangCoursePayload.hasExactTime(): Boolean =
    isCustomTime || !customStartTime.isNullOrBlank() || !customEndTime.isNullOrBlank() || !customPeriodTimes.isNullOrBlank()

private fun ShiguangCoursePayload.customTimePeriodIndexes(periods: List<PeriodEntity>): List<Int> {
    if (!hasExactTime()) return emptyList()
    val clock = normalizeCourseClock(customStartTime, customEndTime, customPeriodTimes, emptyList())
    val start = parseTime(clock.start, "customStartTime")
    val end = parseTime(clock.end, "customEndTime")
    return courseAnchorPeriodsForTimeRange(start, end, periods)
}

private fun parseTime(value: String?, label: String): LocalTime {
    require(!value.isNullOrBlank()) { "$label 不能为空" }
    return try {
        LocalTime.parse(value)
    } catch (error: Exception) {
        throw IllegalArgumentException("$label 必须是 HH:mm", error)
    }
}
