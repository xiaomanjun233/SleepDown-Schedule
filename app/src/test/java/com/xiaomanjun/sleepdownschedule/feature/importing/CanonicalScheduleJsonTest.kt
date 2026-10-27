package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.model.*
import com.xiaomanjun.sleepdownschedule.domain.schedule.*
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.ShiguangImportSession
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import kotlinx.serialization.json.jsonObject

class CanonicalScheduleJsonTest {
    @Test fun aiSubmissionUsesTheSamePublicContractAsFilesAndRepair() {
        val schema=scheduleImportChatTool()["function"]!!.jsonObject["parameters"]!!.jsonObject
        val properties=schema["properties"]!!.jsonObject
        assertTrue(properties.keys.containsAll(listOf("courses","timeSlots","config","comboSchedule")))
        assertFalse("schemaVersion" in properties || "scheduleConfig" in properties)
        val payload="""{"courses":[{"name":"实验","teacher":"","position":"B楼","day":2,"weeks":[1,3],"isCustomTime":true,"customStartTime":"08:20","customEndTime":"10:00","credit":2}],"timeSlots":[{"number":1,"startTime":"08:00","endTime":"08:45"}],"config":{"semesterTotalWeeks":16},"comboSchedule":null,"changeSummary":"保留实验真实时刻"}"""
        val result=ScheduleImportParser.parseAiResponse(payload,config).getOrThrow()
        assertEquals("08:20",result.courses.single().customStartTime)
        assertTrue(result.courses.single().periods.isEmpty())
        assertEquals(2f,result.courses.single().credit)
        val prompt=AiImportRepairManager.buildRepairPrompt(payload,AiImportRepairManager.classifyFailure("{}",IllegalArgumentException("courses")))
        assertTrue(prompt.contains("timeSlots") && prompt.contains("isCustomTime"))
    }
    private val config = defaultConfig().copy(totalWeeks=16,termStartDate="2026-08-31")
    private val base = listOf(PeriodEntity(1,"08:00","08:45",alias="早一"),PeriodEntity(2,"08:55","09:40"),PeriodEntity(3,"10:00","10:45"))
    private val course = CourseEntity(name="实验课",teacher="教师",location="B楼",weekday=1,periods=listOf(1,3),weeks=listOf(1,3,5),weekParity=WeekParity.ALL,note="备注",credit=2.5f,sourcePaletteIndex=3)

    @Test fun discontinuousSourceNumbersAndIndependentBellsSurviveThePublicDocumentRoundTrip() {
        val independent = base.map { it.copy(startTime=when(it.periodIndex) {1->"08:20";2->"09:15";else->it.startTime},endTime=when(it.periodIndex) {1->"09:05";2->"10:00";else->it.endTime}) }
        val draft = ImportDraft(config,base,listOf(course.copy(independentPeriodSchemeId=-1)),
            timetablePlan=ImportedTimetablePlan(independentTimetables=listOf(ImportedTimetable("B楼",independent))))
        val encoded = CanonicalScheduleJson.encode(draft)
        assertTrue(encoded.contains("\"timeSlots\""))
        assertFalse(encoded.contains("scheduleConfig"))
        val restored = CanonicalScheduleJson.decode(encoded,config)
        assertEquals(1,restored.courses.size)
        assertEquals(course.periods,restored.courses.single().periods)
        assertEquals(-1L,restored.courses.single().independentPeriodSchemeId)
        assertNull(restored.courses.single().customStartTime)
        assertEquals(2.5f,restored.courses.single().credit)
        assertEquals(3,restored.courses.single().sourcePaletteIndex)
        assertEquals("早一",restored.periods.first().alias)
        assertEquals("08:20",restored.timetablePlan.independentTimetables.single().periods.first().startTime)
    }

    @Test fun exactLessonsAndPartialOverridesKeepTheirBoundariesInHistory() {
        for (clock in listOf("1,08:20-09:05;3,10:10-10:55","3,10:10-10:55")) {
            val source = course.copy(customPeriodTimes=clock,originalPeriodTimes="1,08:00-08:45;3,10:00-10:45")
            val restored = CanonicalScheduleJson.decode(CanonicalScheduleJson.encode(ImportDraft(config,base,listOf(source))),config).courses.single()
            assertEquals(source.periods,restored.periods)
            assertEquals(source.customPeriodTimes,restored.customPeriodTimes)
            assertEquals(source.originalPeriodTimes,restored.originalPeriodTimes)
            assertEquals(source.note,restored.note)
        }
    }

    @Test fun standardUpstreamJsonSupportsCustomTimesWithoutInventingSourceSections() {
        val draft = CanonicalScheduleJson.decode("""{"courses":[{"name":"晚间实验","teacher":"","position":"B楼","day":3,"weeks":[1,2],"isCustomTime":true,"customStartTime":"19:10","customEndTime":"20:50","credit":1.0}],"timeSlots":[{"number":3,"startTime":"10:00","endTime":"10:45","alias":"三"}],"config":{"semesterTotalWeeks":16,"semesterStartDate":"2026-08-31","firstDayOfWeek":7}}""",config)
        assertEquals(listOf(3),draft.periods.map { it.periodIndex })
        assertTrue(draft.courses.single().periods.isEmpty())
        assertEquals("19:10",draft.courses.single().customStartTime)
        assertTrue(draft.config.weekStartsOnSunday)
    }

    @Test fun bridgeComboOrderingAndPartialDateTemplatesMatchUpstream() {
        val session = ShiguangImportSession()
        session.begin(config,base,allowImportedBellTimes=true)
        val combo = """{"name":"季节作息","publicSchedules":[{"name":"夏季","startDate":"2026-09-01","endDate":"2026-09-30","timeSlots":[{"number":1,"startTime":"08:10","endTime":"08:55"}]}]}"""
        assertThrows(IllegalArgumentException::class.java) { session.stageComboSchedule(combo) }
        session.stageTimeSlots("""[{"number":1,"startTime":"08:00","endTime":"08:45"},{"number":2,"startTime":"08:55","endTime":"09:40"}]""")
        session.stageComboSchedule(combo)
        assertThrows(IllegalArgumentException::class.java) { session.stageTimeSlots("[]") }
        session.stageCourses("""[{"name":"课程","teacher":"","position":"","day":1,"startSection":1,"endSection":2,"weeks":[1]}]""")
        val draft = session.complete()
        assertEquals("08:10",draft.timetablePlan.dateTimetables.single().periods.first().startTime)
        assertEquals("08:55",draft.timetablePlan.dateTimetables.single().periods.last().startTime)
        val restored = CanonicalScheduleJson.decode(CanonicalScheduleJson.encode(draft),config)
        assertEquals(draft.timetablePlan,restored.timetablePlan)
    }

    @Test fun sharingMapsDatabaseReferencesWithoutLeakingLocalIds() {
        val catalog = TimetableCatalog(schemes=listOf(PeriodSchemeEntity(id=98,scheduleId=0,name="B楼")),times=base.map { PeriodSchemeTimeEntity(98,it.periodIndex,it.startTime,it.endTime,it.alias) })
        val document = buildCanonicalScheduleJson(config,base,listOf(course.copy(independentPeriodSchemeId=98)),catalog)
        val restored = ScheduleImportParser.parse(document,config).getOrThrow()
        assertEquals(-1L,restored.courses.single().independentPeriodSchemeId)
        assertEquals("B楼",restored.timetablePlan.independentTimetables.single().name)
    }
}
