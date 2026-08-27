package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EduImportMapperTest {
    @Test
    fun `generic self check maps canonical and compatible fields into preview`() {
        val draft = ShiguangImportMapper.toDraft(
            adapter = testAdapter("GLOBAL_TOOLS"),
            baseConfig = defaultConfig(),
            basePeriods = defaultPeriods(),
            configJson = """{"semesterTotalWeeks":18}""",
            coursesJson = """
                [
                  {
                    "name":"连续周课程",
                    "teacher":"教师 A",
                    "position":"教室 101",
                    "day":1,
                    "startSection":1,
                    "endSection":2,
                    "weeks":[1,2,3]
                  },
                  {
                    "courseName":"兼容字段课程",
                    "teachers":["教师 B","教师 C"],
                    "classroom":"教室 303",
                    "dayOfWeek":7,
                    "sections":[11,12],
                    "weekList":[2,6,10,14,18]
                  }
                ]
            """.trimIndent(),
            timeSlotsJson = defaultTimeSlotsJson()
        )

        assertEquals(18, draft.config.totalWeeks)
        assertEquals(2, draft.courses.size)
        assertEquals(listOf(1, 2), draft.courses[0].periods)
        assertEquals("教师 B、教师 C", draft.courses[1].teacher)
        assertEquals("教室 303", draft.courses[1].location)
        assertEquals(7, draft.courses[1].weekday)
        assertEquals(listOf(11, 12), draft.courses[1].periods)
        assertEquals(listOf(2, 6, 10, 14, 18), draft.courses[1].weeks)
        assertNull(draft.config.termStartDate)
        assertEquals("21:45", draft.periods.single { it.periodIndex == 12 }.startTime)
    }

    @Test
    fun `wust payload preserves weeks periods and campus time slots`() {
        val draft = ShiguangImportMapper.toDraft(
            adapter = testAdapter("WUST"),
            baseConfig = defaultConfig(),
            basePeriods = defaultPeriods(),
            configJson = """{"semesterTotalWeeks":17}""",
            coursesJson = """
                [{
                  "name":"高等数学",
                  "teacher":"张老师",
                  "position":"黄家湖校区教一楼",
                  "day":3,
                  "startSection":1,
                  "endSection":4,
                  "weeks":[1,3,4,6,7,8,9,10,11,13,15,17]
                }]
            """.trimIndent(),
            timeSlotsJson = """
                [
                  {"number":1,"startTime":"08:20","endTime":"09:05"},
                  {"number":2,"startTime":"09:15","endTime":"10:00"},
                  {"number":3,"startTime":"10:20","endTime":"11:05"},
                  {"number":4,"startTime":"11:15","endTime":"12:00"},
                  {"number":5,"startTime":"14:00","endTime":"14:45"},
                  {"number":6,"startTime":"14:55","endTime":"15:40"},
                  {"number":7,"startTime":"16:00","endTime":"16:45"},
                  {"number":8,"startTime":"16:55","endTime":"17:40"},
                  {"number":9,"startTime":"18:40","endTime":"19:25"},
                  {"number":10,"startTime":"19:35","endTime":"20:20"},
                  {"number":11,"startTime":"20:40","endTime":"21:25"},
                  {"number":12,"startTime":"21:35","endTime":"22:20"}
                ]
            """.trimIndent()
        )

        assertEquals(17, draft.config.totalWeeks)
        assertEquals(listOf(1, 2, 3, 4), draft.courses.single().periods)
        assertEquals(listOf(1, 3, 4, 6, 7, 8, 9, 10, 11, 13, 15, 17), draft.courses.single().weeks)
        assertEquals("08:20", draft.periods.single { it.periodIndex == 1 }.startTime)
        assertEquals("22:20", draft.periods.single { it.periodIndex == 12 }.endTime)
    }

    private fun testAdapter(schoolId: String) = EduAdapter(
        school = EduSchool(schoolId, schoolId, schoolId),
        adapterId = "TEST",
        adapterName = "测试",
        category = "TEST",
        assetJsPath = "test.js",
        importUrl = "https://example.edu.cn",
        maintainer = "test",
        description = "test"
    )

    private fun defaultTimeSlotsJson(): String = """
        [
          {"number":1,"startTime":"08:00","endTime":"08:45"},
          {"number":2,"startTime":"08:55","endTime":"09:40"},
          {"number":3,"startTime":"10:00","endTime":"10:45"},
          {"number":4,"startTime":"10:55","endTime":"11:40"},
          {"number":5,"startTime":"14:00","endTime":"14:45"},
          {"number":6,"startTime":"14:55","endTime":"15:40"},
          {"number":7,"startTime":"16:00","endTime":"16:45"},
          {"number":8,"startTime":"16:55","endTime":"17:40"},
          {"number":9,"startTime":"19:00","endTime":"19:45"},
          {"number":10,"startTime":"19:55","endTime":"20:40"},
          {"number":11,"startTime":"20:50","endTime":"21:35"},
          {"number":12,"startTime":"21:45","endTime":"22:30"}
        ]
    """.trimIndent()
}
