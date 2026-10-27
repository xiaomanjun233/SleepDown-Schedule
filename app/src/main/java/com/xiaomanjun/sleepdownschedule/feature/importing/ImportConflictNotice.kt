package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.model.ImportDraft
import com.xiaomanjun.sleepdownschedule.domain.course.conflictsWith
import com.xiaomanjun.sleepdownschedule.domain.schedule.*

internal fun importConflictNotice(draft: ImportDraft): String? {
    val preview = draft.timingPreviewState()
    val courses = draft.courses.mapIndexed { index, course ->
        course.copy(id = index + 1L, scheduleId = draft.config.id)
    }
    val conflicts = courses.indices.any { index ->
        val course = courses[index]
        courses.drop(index + 1).any { other ->
            course.weekday == other.weekday && course.weeks.intersect(other.weeks.toSet()).any { week ->
                val date = scheduleWeekStartDate(draft.config,week).plusDays(scheduleWeekdayOffset(draft.config,course.weekday))
                projectCourseArrangement(course,preview.config,draft.periods,preview.timetableCatalog,date)
                    .conflictsWith(projectCourseArrangement(other,preview.config,draft.periods,preview.timetableCatalog,date),week,draft.periods)
            }
        }
    }
    return if (conflicts) "存在时间冲突：全部课程和原始上课时间都会保留。重叠位置优先展示较早开始的课程，其余课程按顺序切换查看，不会推迟上课时间。" else null
}
