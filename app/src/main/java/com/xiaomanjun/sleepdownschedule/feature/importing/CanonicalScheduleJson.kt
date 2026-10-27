package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.model.*
import com.xiaomanjun.sleepdownschedule.model.CoursePeriodTime
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.time.LocalDate

/** Map Room identities into document-local references before sharing or editing a snapshot. */
internal fun ImportDraft.withTimetableCatalog(catalog: TimetableCatalog): ImportDraft {
    val ids = courses.mapNotNull { it.independentPeriodSchemeId?.takeIf { id -> id > 0 } }.distinct()
    if (ids.isEmpty() && config.activeTimetableComboId == null) return this
    fun template(id: Long, start: String? = null, end: String? = null): ImportedTimetable {
        val scheme = requireNotNull(catalog.schemes.firstOrNull { it.id == id }) { "课程引用的作息不存在" }
        val times = catalog.times.filter { it.schemeId == id }.sortedBy { it.periodIndex }
        require(times.isNotEmpty()) { "课程引用的作息没有时间" }
        return ImportedTimetable(scheme.name, times.map { PeriodEntity(it.periodIndex,it.startTime,it.endTime,config.id,it.alias) },
            start,end,scheme.classDurationMinutes,scheme.breakDurationMinutes)
    }
    val offset = timetablePlan.independentTimetables.size
    val mapping = ids.mapIndexed { index, id -> id to -(offset + index + 1).toLong() }.toMap()
    val combo = config.activeTimetableComboId?.let { id -> requireNotNull(catalog.combos.firstOrNull { it.id == id }) { "组合作息不存在" } }
    val dateTables = if (combo == null) timetablePlan.dateTimetables else catalog.rules.filter { it.comboId == combo.id }
        .sortedBy { it.position }.map { template(it.schemeId,it.startDate,it.endDate) }
    return copy(courses = courses.map { course -> course.copy(independentPeriodSchemeId = mapping[course.independentPeriodSchemeId] ?: course.independentPeriodSchemeId) },
        timetablePlan = ImportedTimetablePlan(combo?.name ?: timetablePlan.comboName,dateTables,
            timetablePlan.independentTimetables + ids.map { template(it) }))
}

fun buildCanonicalScheduleJson(config: ScheduleConfigEntity, periods: List<PeriodEntity>, courses: List<CourseEntity>,
    catalog: TimetableCatalog): String {
    require(courses.none { it.timingReviewReason != null }) { "请先确认待处理课程的时间，再分享课表" }
    return CanonicalScheduleJson.encode(ImportDraft(config,periods,courses).withTimetableCatalog(catalog))
        .also { CanonicalScheduleJson.decode(it,config) }
}

@Serializable internal data class CanonicalScheduleDocument(
    val courses: List<ShiguangCoursePayload>, val timeSlots: List<ShiguangTimeSlotPayload>? = emptyList(),
    val config: ShiguangCourseConfigPayload? = null, val comboSchedule: ComboScheduleJson? = null,
    val sleepdown: NativeScheduleExtension? = null
)
@Serializable internal data class ComboScheduleJson(val name: String? = null, val publicSchedules: List<PublicScheduleJson> = emptyList())
@Serializable internal data class PublicScheduleJson(val name: String, val startDate: String, val endDate: String,
    val defaultClassDuration: Int = 45, val defaultBreakDuration: Int = 10, val timeSlots: List<ShiguangTimeSlotPayload>)
@Serializable internal data class NativeScheduleExtension(val version: Int = 1, val timetables: List<NativeTimetableJson> = emptyList())
@Serializable internal data class NativeTimetableJson(val id: String, val name: String, val timeSlots: List<ShiguangTimeSlotPayload>)
@Serializable internal data class NativeLessonJson(val number: Int, val startTime: String, val endTime: String)
@Serializable internal data class NativeCourseTimingJson(
    val groupId: String, val mode: String, val periods: List<Int>, val timetableId: String? = null,
    val startTime: String? = null, val endTime: String? = null, val lessonTimes: List<NativeLessonJson> = emptyList(),
    val sourcePeriodTimes: String? = null, val colorArgb: Long? = null
)

/** Shared public wire contract. The legacy SleepDown JSON remains a read adapter only. */
internal object CanonicalScheduleJson {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    fun isCanonical(value: String): Boolean = runCatching {
        val root = json.parseToJsonElement(value).jsonObject
        "courses" in root && "scheduleConfig" !in root && "schemaVersion" !in root
    }.getOrDefault(false)

    fun decode(value: String, baseConfig: ScheduleConfigEntity, basePeriods: List<PeriodEntity> = emptyList()): ImportDraft {
        val document = json.decodeFromString<CanonicalScheduleDocument>(value)
        val extension = document.sleepdown
        require(extension == null || extension.version == 1) { "不支持此 SleepDown JSON 扩展版本" }
        val slots = document.timeSlots.orEmpty()
        require(slots.isNotEmpty() || basePeriods.isNotEmpty() || document.courses.all { it.isCustomTime }) { "请提供课程对应的 timeSlots 作息" }
        val session = ShiguangImportSession().apply {
            begin(baseConfig, basePeriods.ifEmpty { defaultPeriods(baseConfig.id) }, allowImportedBellTimes = true)
            if (slots.isNotEmpty()) stageTimeSlots(json.encodeToString(slots))
            document.config?.let { stageCourseConfig(json.encodeToString(it)) }
            stageCourses(json.encodeToString(document.courses))
        }
        val draft = session.complete()
        val templates = extension?.timetables.orEmpty()
        require(templates.map { it.id }.distinct().size == templates.size) { "独立作息标识重复" }
        val grouped = document.courses.zip(draft.courses).groupBy { (wire, _) -> wire.sleepdown?.groupId }
        val restored = mutableListOf<CourseEntity>()
        document.courses.zip(draft.courses).forEach { (wire, parsed) ->
            val native = wire.sleepdown
            if (native == null) { restored += parsed; return@forEach }
            require(native.groupId.isNotBlank()) { "课程分组标识不能为空" }
            if (grouped.getValue(native.groupId).first().first !== wire) return@forEach
            val members = grouped.getValue(native.groupId)
            require(members.all { (other, _) -> other.sleepdown == native && other.name == wire.name && other.teacher == wire.teacher &&
                other.position == wire.position && other.day == wire.day && other.weeks == wire.weeks &&
                other.credit == wire.credit && other.remark == wire.remark && other.color == wire.color }) { "课程扩展分组与标准字段不一致" }
            require(native.mode in setOf("FOLLOW_TABLE", "FOLLOW_SCHEME", "CUSTOM")) { "课程时间模式无效" }
            require(native.periods.all { it > 0 } && native.periods.distinct().size==native.periods.size &&
                (native.mode=="CUSTOM" || native.periods.isNotEmpty())) { "课程扩展节次无效" }
            require(native.colorArgb == null || native.colorArgb in 0L..0xFFFFFFFFL) { "课程颜色无效" }
            val scheme = native.timetableId?.let { id ->
                val index = templates.indexOfFirst { it.id == id }
                require(index >= 0) { "课程引用的独立作息不存在" }
                -(index + 1).toLong()
            }
            require((native.mode == "FOLLOW_SCHEME") == (scheme != null)) { "课程时间模式与作息引用不一致" }
            val lessonClocks = native.lessonTimes.map { CoursePeriodTime(it.number, parseScheduleTime(it.startTime), parseScheduleTime(it.endTime)) }
            val clock = normalizeCourseClock(native.startTime, native.endTime, encodeCoursePeriodTimes(lessonClocks), native.periods)
            require(native.mode != "CUSTOM" || clock.start != null || lessonClocks.isNotEmpty()) { "自定义课程缺少真实时间" }
            require(native.mode == "CUSTOM" || (native.startTime == null && native.endTime == null)) { "节次课程不能同时指定整体时钟" }
            if (native.mode == "CUSTOM" && members.size == 1 && native.lessonTimes.isEmpty()) {
                require(wire.isCustomTime && wire.customStartTime == clock.start && wire.customEndTime == clock.end) { "课程扩展与标准时间不一致" }
            }
            val source = parsed.copy(periods = native.periods.sorted(), customStartTime = clock.start, customEndTime = clock.end,
                customPeriodTimes = clock.periodTimes, originalPeriodTimes = native.sourcePeriodTimes,
                independentPeriodSchemeId = scheme, customColorArgb = native.colorArgb)
            if(native.mode=="FOLLOW_TABLE" && native.lessonTimes.isEmpty()) {
                require(members.all { !it.first.isCustomTime } && members.flatMap { (standard,_) ->
                    (requireNotNull(standard.startSection)..requireNotNull(standard.endSection)).toList()
                }.sorted()==source.periods) { "标准节次与课程扩展不一致" }
            } else {
                val reference = scheme?.let { templates[(-it-1).toInt()].timeSlots.map { slot -> PeriodEntity(slot.number,slot.startTime,slot.endTime) } } ?: draft.periods
                val groups = mutableListOf<MutableList<Int>>()
                source.periods.forEach { number ->
                    if(groups.isEmpty() || number!=groups.last().last()+1) groups+=mutableListOf<Int>()
                    groups.last()+=number
                }
                val portions = if(clock.periodTimes==null && clock.start!=null || groups.isEmpty()) listOf(source.periods) else groups
                require(members.size==portions.size && members.zip(portions).all { (member,selected) ->
                    val times=courseTimeSegments(source.copy(periods=selected),reference)
                    member.first.isCustomTime && member.first.customStartTime?.let(::parseScheduleTime)==times.minOfOrNull { it.start } &&
                        member.first.customEndTime?.let(::parseScheduleTime)==times.maxOfOrNull { it.end }
                }) { "标准时间与课程扩展不一致" }
            }
            restored += source
        }
        val dateTables = document.comboSchedule?.publicSchedules.orEmpty().map { rule ->
            require(LocalDate.parse(rule.startDate) <= LocalDate.parse(rule.endDate)) { "组合作息日期范围无效" }
            val byNumber = rule.timeSlots.associateBy { it.number }
            require(byNumber.size == rule.timeSlots.size) { "组合作息节次重复" }
            val aligned = draft.periods.map { base -> byNumber[base.periodIndex]?.let { PeriodEntity(it.number,it.startTime,it.endTime,baseConfig.id,it.alias) } ?: base }
            validateResolvedPeriodTimes(aligned.map { PeriodSchemeTimeEntity(0,it.periodIndex,it.startTime,it.endTime,it.alias) })?.let { error(it) }
            ImportedTimetable(rule.name, aligned, rule.startDate, rule.endDate, rule.defaultClassDuration, rule.defaultBreakDuration)
        }
        val independents = templates.map { table ->
            val periods = table.timeSlots.map { PeriodEntity(it.number,it.startTime,it.endTime,baseConfig.id,it.alias) }
            validateResolvedPeriodTimes(periods.map { PeriodSchemeTimeEntity(0,it.periodIndex,it.startTime,it.endTime,it.alias) })?.let { error(it) }
            ImportedTimetable(table.name,periods)
        }
        return draft.copy(courses = restored, timetablePlan = ImportedTimetablePlan(document.comboSchedule?.name,dateTables,independents))
    }

    fun encode(draft: ImportDraft): String {
        fun slots(periods: List<PeriodEntity>) = periods.map { ShiguangTimeSlotPayload(it.periodIndex,it.startTime,it.endTime,it.alias) }
        val templates = draft.timetablePlan.independentTimetables.mapIndexed { index, table -> NativeTimetableJson("t$index",table.name,slots(table.periods)) }
        val courses = draft.courses.flatMapIndexed { index, value ->
            val source = value.originalArrangement()
            val exact = parseCoursePeriodTimes(source.customPeriodTimes)
            val whole = exact.isEmpty() && source.customStartTime != null && source.customEndTime != null
            val mode = if (whole || (exact.isNotEmpty() && exact.map { it.index } == source.periods.sorted())) "CUSTOM"
                else if (source.independentPeriodSchemeId != null) "FOLLOW_SCHEME" else "FOLLOW_TABLE"
            val templateIndex = source.independentPeriodSchemeId?.takeUnless { mode == "CUSTOM" }?.let { id -> require(id < 0) { "导出前需要建立独立作息映射" }; (-id-1).toInt() }
            val native = NativeCourseTimingJson("c$index",mode,source.periods,
                templateIndex?.let { templates.getOrNull(it)?.id ?: error("独立作息缺失") },
                source.customStartTime?.takeIf { mode == "CUSTOM" }, source.customEndTime?.takeIf { mode == "CUSTOM" },
                exact.map { NativeLessonJson(it.index,it.start.toString(),it.end.toString()) },source.originalPeriodTimes,source.customColorArgb)
            val numbers = source.periods.distinct().sorted()
            val groups = mutableListOf<MutableList<Int>>()
            numbers.forEach { number -> if (groups.isEmpty() || number != groups.last().last()+1) groups += mutableListOf<Int>(); groups.last() += number }
            val portions = if (whole || groups.isEmpty()) listOf(numbers) else groups
            portions.map { selected ->
                val normal = mode == "FOLLOW_TABLE" && exact.isEmpty()
                val reference = templateIndex?.let { draft.timetablePlan.independentTimetables[it].periods } ?: draft.periods
                val times = if (normal) emptyList() else courseTimeSegments(source.copy(periods=selected,arrangementProjection=null),reference)
                ShiguangCoursePayload(name=source.name,teacher=source.teacher.orEmpty(),position=source.location.orEmpty(),day=source.weekday,
                    startSection=selected.firstOrNull().takeIf { normal },endSection=selected.lastOrNull().takeIf { normal },
                    weeks=source.weeks.filter { parityMatches(source.weekParity,it) },isCustomTime=!normal,
                    customStartTime=times.minOfOrNull { it.start }?.toString(),customEndTime=times.maxOfOrNull { it.end }?.toString(),
                    remark=source.note,credit=source.credit,color=source.sourcePaletteIndex,sleepdown=native)
            }
        }
        val combo = draft.timetablePlan.dateTimetables.takeIf { it.isNotEmpty() }?.let { tables ->
            ComboScheduleJson(draft.timetablePlan.comboName,tables.map { PublicScheduleJson(it.name,requireNotNull(it.startDate),requireNotNull(it.endDate),it.classDuration,it.breakDuration,slots(it.periods)) })
        }
        return json.encodeToString(CanonicalScheduleDocument(courses,slots(draft.periods),
            ShiguangCourseConfigPayload(draft.config.termStartDate,draft.config.totalWeeks,draft.config.classDurationMinutes,draft.config.breakDurationMinutes,
                if (draft.config.weekStartsOnSunday) 7 else 1),combo,NativeScheduleExtension(timetables=templates)))
    }
}
