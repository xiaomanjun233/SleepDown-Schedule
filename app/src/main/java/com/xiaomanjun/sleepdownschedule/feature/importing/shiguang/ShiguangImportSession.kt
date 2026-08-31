package com.xiaomanjun.sleepdownschedule.feature.importing.shiguang

import com.xiaomanjun.sleepdownschedule.ImportDraft
import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.WeekParity
import com.xiaomanjun.sleepdownschedule.courseAnchorPeriodsForTimeRange
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.LocalTime

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
    val remark: String? = null
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

    fun begin(config: ScheduleConfigEntity, periods: List<PeriodEntity>) {
        synchronized(lock) {
            active = true
            baseConfig = config
            basePeriods = periods
            courses = null
            courseConfig = null
            timeSlots = null
        }
    }

    fun stageCourses(json: String) {
        val parsed = ShiguangPayloadJson.decodeFromString(
            ListSerializer(ShiguangCoursePayload.serializer()),
            json
        )
        require(parsed.isNotEmpty()) { "课程数据为空" }
        parsed.forEachIndexed { index, course -> validateCourse(index, course) }
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
        val sorted = parsed.sortedBy(ShiguangTimeSlotPayload::number)
        sorted.forEachIndexed { index, slot ->
            require(slot.number == index + 1) { "时间段编号必须从 1 连续递增" }
            val start = parseTime(slot.startTime, "第 ${slot.number} 节 startTime")
            val end = parseTime(slot.endTime, "第 ${slot.number} 节 endTime")
            require(start < end) { "第 ${slot.number} 节结束时间必须晚于开始时间" }
            if (index > 0) {
                val previousEnd = parseTime(sorted[index - 1].endTime, "第 ${slot.number - 1} 节 endTime")
                require(start >= previousEnd) { "时间段配置存在重叠" }
            }
        }
        synchronized(lock) {
            require(active) { "当前没有正在执行的拾光导入任务" }
            timeSlots = parsed
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
                timeSlots = timeSlots
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
        val startSection = course.startSection
        val endSection = course.endSection
        val hasStartSection = startSection != null
        val hasEndSection = endSection != null
        require(hasStartSection == hasEndSection) { "$label startSection 与 endSection 必须同时提供" }
        if (startSection != null && endSection != null) {
            require(startSection > 0 && endSection >= startSection) {
                "$label 节次范围无效"
            }
        }
        if (course.isCustomTime) {
            val start = parseTime(course.customStartTime, "$label customStartTime")
            val end = parseTime(course.customEndTime, "$label customEndTime")
            require(start < end) { "$label 自定义结束时间必须晚于开始时间" }
        } else {
            require(hasStartSection) { "$label 缺少节次范围" }
        }
    }

    private data class SessionSnapshot(
        val baseConfig: ScheduleConfigEntity,
        val basePeriods: List<PeriodEntity>,
        val courses: List<ShiguangCoursePayload>,
        val courseConfig: ShiguangCourseConfigPayload?,
        val timeSlots: List<ShiguangTimeSlotPayload>?
    ) {
        fun toDraft(): ImportDraft {
            val mappedPeriods = timeSlots?.map { slot ->
                PeriodEntity(slot.number, slot.startTime, slot.endTime, baseConfig.id)
            } ?: basePeriods
            require(mappedPeriods.isNotEmpty()) { "目标课表没有可用于预览的节次" }
            val periodIndexSet = mappedPeriods.mapTo(hashSetOf(), PeriodEntity::periodIndex)
            val mappedCourses = courses.mapIndexed { index, course ->
                val periods = if (course.isCustomTime) {
                    course.customTimePeriodIndexes(mappedPeriods)
                } else {
                    course.sectionRangeOrNull()?.toList().orEmpty()
                }
                require(periods.isNotEmpty()) { "第 ${index + 1} 门课程无法映射到 SleepDown 节次" }
                require(periods.all { it in periodIndexSet }) {
                    "第 ${index + 1} 门课程引用了不存在的节次"
                }
                CourseEntity(
                    name = course.name,
                    teacher = course.teacher.ifBlank { null },
                    location = course.position.ifBlank { null },
                    weekday = course.day,
                    periods = periods,
                    weeks = course.weeks.distinct().sorted(),
                    weekParity = WeekParity.ALL,
                    note = course.remark?.takeIf { it.isNotBlank() },
                    customStartTime = course.customStartTime?.takeIf { course.isCustomTime },
                    customEndTime = course.customEndTime?.takeIf { course.isCustomTime },
                    // Shiguang color is a palette index, not ARGB. SleepDown keeps automatic color assignment.
                    customColorArgb = null,
                    scheduleId = baseConfig.id
                )
            }
            val mappedConfig = courseConfig?.let { imported ->
                baseConfig.copy(
                    totalWeeks = imported.semesterTotalWeeks,
                    currentWeek = baseConfig.currentWeek.coerceIn(1, imported.semesterTotalWeeks),
                    termStartDate = imported.semesterStartDate,
                    classDurationMinutes = imported.defaultClassDuration,
                    breakDurationMinutes = imported.defaultBreakDuration
                )
            } ?: baseConfig
            return ImportDraft(
                config = mappedConfig,
                periods = mappedPeriods,
                courses = mappedCourses
            )
        }
    }
}

private fun ShiguangCoursePayload.sectionRangeOrNull(): IntRange? {
    val start = startSection ?: return null
    val end = endSection ?: return null
    return start..end
}

private fun ShiguangCoursePayload.customTimePeriodIndexes(periods: List<PeriodEntity>): List<Int> {
    if (!isCustomTime) return emptyList()
    val start = parseTime(customStartTime, "customStartTime")
    val end = parseTime(customEndTime, "customEndTime")
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
