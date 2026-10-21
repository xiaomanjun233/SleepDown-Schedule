package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import com.xiaomanjun.sleepdownschedule.CourseEntity
import com.xiaomanjun.sleepdownschedule.WeekParity
import com.xiaomanjun.sleepdownschedule.feature.importing.EduAdapter
import com.xiaomanjun.sleepdownschedule.feature.importing.EduSchool
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.describeAdapterRefresh
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshResultSummaryTest {
    private fun course(name: String, start: String? = null) = CourseEntity(
        name = name, teacher = null, location = null, weekday = 1, periods = listOf(1),
        weeks = listOf(1), weekParity = WeekParity.ALL, note = null,
        customStartTime = start, customEndTime = start?.let { "09:00" }
    )

    @Test
    fun manualScheduleResultNamesNewAndUpdatedCourses() {
        val message = describeScheduleRefresh(
            before = listOf(course("高等数学")),
            after = listOf(course("高等数学", "08:10"), course("物理实验"))
        )
        assertTrue(message.contains("新增 1 门：物理实验"))
        assertTrue(message.contains("更新 1 门：高等数学"))
    }

    @Test
    fun manualAdapterResultNamesNewSchoolEntry() {
        fun adapter(id: String) = EduAdapter(
            school = EduSchool(id, "学校$id", id), adapterId = id,
            adapterName = "本科教务", category = "BACHELOR_AND_ASSOCIATE", assetJsPath = "$id.js",
            importUrl = "https://example.edu", maintainer = "维护者", description = ""
        )
        val message = describeAdapterRefresh(listOf(adapter("A")), listOf(adapter("A"), adapter("B")), true)
        assertTrue(message.contains("新增 1 个教务入口：学校B · 本科教务"))
    }
}
