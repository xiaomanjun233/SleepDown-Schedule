package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.*

/** Retire a per-course timetable reference without changing any actual lesson clocks. */
fun CourseEntity.withCustomTimetableTimes(reference: List<PeriodEntity>): CourseEntity {
    val source = arrangementForWrite()
    if (source.independentPeriodSchemeId == null) return source
    val actual = courseTimeSegments(source, reference)
    val wholeClock = source.customStartTime != null && source.customEndTime != null && source.customPeriodTimes.isNullOrBlank()
    require(actual.isNotEmpty() && source.periods.all { number ->
        wholeClock || actual.any { it.index == number }
    }) { "课程引用的旧作息不完整，请先补充课程时间" }
    return source.copy(independentPeriodSchemeId = null,
        customStartTime = if (wholeClock) source.customStartTime else actual.minOf { it.start }.toString(),
        customEndTime = if (wholeClock) source.customEndTime else actual.maxOf { it.end }.toString(),
        customPeriodTimes = if (wholeClock) null else encodeCoursePeriodTimes(actual))
}

/** Legacy wire formats remain readable; newly imported courses own their exceptional clocks. */
fun ImportDraft.withCustomCourseTimetables(catalog: TimetableCatalog = TimetableCatalog()): ImportDraft = copy(
    courses = courses.map { course ->
        val id = course.independentPeriodSchemeId ?: return@map course
        val reference = if (id < 0) timetablePlan.independentTimetables.getOrNull((-id - 1).toInt())?.periods
            ?: error("课程引用的旧作息不存在")
        else catalog.times.filter { it.schemeId == id }.map { PeriodEntity(it.periodIndex, it.startTime, it.endTime, alias = it.alias) }
        course.withCustomTimetableTimes(reference)
    },
    timetablePlan = timetablePlan.copy(independentTimetables = emptyList())
)

/** Document-local negative identities never reach Room before confirmation. */
fun ImportDraft.timingPreviewState(): AppState {
    val independent = timetablePlan.independentTimetables.mapIndexed { index, table -> -(index+1).toLong() to table }
    val dates = timetablePlan.dateTimetables.mapIndexed { index, table -> -(independent.size+index+1).toLong() to table }
    val tables = listOf(0L to ImportedTimetable("基准作息",periods)) + independent + dates
    val comboId = (-1L).takeIf { dates.isNotEmpty() }
    val catalog = TimetableCatalog(
        schemes=tables.map { (id,table) -> PeriodSchemeEntity(id=id,scheduleId=0,name=table.name) },
        times=tables.flatMap { (id,table) -> table.periods.map { PeriodSchemeTimeEntity(id,it.periodIndex,it.startTime,it.endTime,it.alias) } },
        combos=comboId?.let { listOf(TimetableComboEntity(it,timetablePlan.comboName ?: "组合作息",0)) }.orEmpty(),
        rules=dates.mapIndexed { index,(id,table) -> TimetableDateRuleEntity(-1,index,id,requireNotNull(table.startDate),requireNotNull(table.endDate)) })
    return AppState(config=config.copy(activePeriodSchemeId=0,activeTimetableComboId=comboId),periods=periods,
        courses=withCustomCourseTimetables(catalog).courses,timetableCatalog=catalog)
}

fun ImportDraft.resolvedCoursesForPreview(): List<CourseEntity> {
    val state = timingPreviewState()
    return state.courses.map { course ->
        val week = course.weeks.firstOrNull { parityMatches(course.weekParity,it) } ?: 1
        val date = scheduleWeekStartDate(config,week).plusDays(scheduleWeekdayOffset(config,course.weekday))
        projectCourseArrangement(course,state.config,periods,state.timetableCatalog,date)
    }
}
