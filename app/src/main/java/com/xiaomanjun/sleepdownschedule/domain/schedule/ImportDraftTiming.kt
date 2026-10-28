package com.xiaomanjun.sleepdownschedule.domain.schedule

import com.xiaomanjun.sleepdownschedule.model.*

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
    return AppState(config=config.copy(activePeriodSchemeId=0,activeTimetableComboId=comboId),periods=periods,courses=courses,timetableCatalog=catalog)
}

fun ImportDraft.resolvedCoursesForPreview(): List<CourseEntity> {
    val state = timingPreviewState()
    return courses.map { course ->
        val week = course.weeks.firstOrNull { parityMatches(course.weekParity,it) } ?: 1
        val date = scheduleWeekStartDate(config,week).plusDays(scheduleWeekdayOffset(config,course.weekday))
        projectCourseArrangement(course,state.config,periods,state.timetableCatalog,date)
    }
}
