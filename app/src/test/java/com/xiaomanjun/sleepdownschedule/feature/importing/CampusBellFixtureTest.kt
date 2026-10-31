package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.defaultConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Acceptance fixture for a course that carries its own campus bells. */
class CampusBellFixtureTest {
    @Test
    fun campusBellsBecomePerLessonClocksWithoutTouchingTheScheme() {
        val draft = ScheduleImportParser.parseStoredDraft(fixture, defaultConfig()).getOrThrow()

        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), draft.periods.map { it.periodIndex })
        assertEquals(16, draft.config.totalWeeks)
        assertEquals(5, draft.courses.size)

        fun course(name: String) = draft.courses.single { it.name == name }

        val ordinary = course("高等数学")
        assertEquals(listOf(1, 2), ordinary.periods)
        assertNull(ordinary.customStartTime)
        assertNull(ordinary.customPeriodTimes)

        val campus = course("大学物理实验")
        assertEquals(listOf(3, 4), campus.periods)
        assertEquals("3,10:10-10:45;4,10:55-11:45", campus.customPeriodTimes)
        assertEquals("10:10", campus.customStartTime)
        assertEquals("11:45", campus.customEndTime)

        val workshop = course("金工实习")
        assertEquals(listOf(5, 6), workshop.periods)
        assertEquals("5,14:10-14:45;6,14:55-15:50", workshop.customPeriodTimes)

        val morning = course("早读")
        assertEquals(emptyList<Int>(), morning.periods)
        assertEquals("07:40", morning.customStartTime)
        assertEquals("08:00", morning.customEndTime)

        // The scheme itself is the submitted preset: no campus-specific rows leak into it.
        assertEquals(listOf("08:00", "08:55", "10:00", "10:55", "14:00", "14:55", "16:00", "16:55"),
            draft.periods.map { it.startTime })
    }

    private val fixture = """
    {
      "courses": [
        {"name":"高等数学","teacher":"李老师","position":"教学楼A-101","day":1,"startSection":1,"endSection":2,
         "weeks":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16]},
        {"name":"大学物理实验","teacher":"王老师","position":"实验楼B-203","day":2,"startSection":3,"endSection":4,
         "weeks":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16],"isCustomTime":true,
         "customStartTime":"10:10","customEndTime":"11:45"},
        {"name":"早读","teacher":"辅导员","position":"本班教室","day":3,"isCustomTime":true,
         "customStartTime":"07:40","customEndTime":"08:00",
         "weeks":[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16]},
        {"name":"金工实习","teacher":"赵老师","position":"实训中心一楼","day":4,"startSection":5,"endSection":6,
         "weeks":[1,2,3,4,5,6,7,8],"isCustomTime":true,
         "customStartTime":"14:10","customEndTime":"15:50"},
        {"name":"大学英语","teacher":"陈老师","position":"教学楼C-305","day":5,"startSection":7,"endSection":8,
         "weeks":[9,10,11,12,13,14,15,16]}
      ],
      "timeSlots": [
        {"number":1,"startTime":"08:00","endTime":"08:45"},
        {"number":2,"startTime":"08:55","endTime":"09:40"},
        {"number":3,"startTime":"10:00","endTime":"10:45"},
        {"number":4,"startTime":"10:55","endTime":"11:40"},
        {"number":5,"startTime":"14:00","endTime":"14:45"},
        {"number":6,"startTime":"14:55","endTime":"15:40"},
        {"number":7,"startTime":"16:00","endTime":"16:45"},
        {"number":8,"startTime":"16:55","endTime":"17:40"}
      ],
      "config": {"semesterStartDate":null,"semesterTotalWeeks":16,
                 "defaultClassDuration":45,"defaultBreakDuration":10,"firstDayOfWeek":1}
    }
    """.trimIndent()
}
