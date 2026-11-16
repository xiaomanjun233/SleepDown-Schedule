package com.xiaomanjun.sleepdownschedule.domain.schedule

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xiaomanjun.sleepdownschedule.AppDatabase
import com.xiaomanjun.sleepdownschedule.data.repository.ScheduleRepository
import com.xiaomanjun.sleepdownschedule.feature.home.day.weekCourseBuckets
import com.xiaomanjun.sleepdownschedule.feature.importing.CanonicalScheduleJson
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.ShiguangImportSession
import com.xiaomanjun.sleepdownschedule.feature.reminder.LiveUpdatePreferencesSnapshot
import com.xiaomanjun.sleepdownschedule.feature.reminder.NotificationScheduler
import com.xiaomanjun.sleepdownschedule.model.CourseEntity
import com.xiaomanjun.sleepdownschedule.model.ImportDraft
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import com.xiaomanjun.sleepdownschedule.model.WeekParity
import com.xiaomanjun.sleepdownschedule.model.defaultConfig
import com.xiaomanjun.sleepdownschedule.model.defaultPeriods
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MajorityPeriodBaselineTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ScheduleRepository

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ScheduleRepository(database)
        repository.ensureDefaults()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun schoolCourse(
        name: String,
        weekday: Int,
        periods: List<Int>,
        weeks: List<Int>,
        customStart: String? = null,
        customEnd: String? = null,
        customLessons: String? = null,
        sourceLessons: String,
    ) = CourseEntity(
        name = name,
        teacher = null,
        location = null,
        weekday = weekday,
        periods = periods,
        weeks = weeks,
        weekParity = WeekParity.ALL,
        note = null,
        customStartTime = customStart,
        customEndTime = customEnd,
        customPeriodTimes = customLessons,
        originalPeriodTimes = sourceLessons,
    )

    private fun frozenCampusSchedule(): List<CourseEntity> {
        val major12 = "1,08:30-09:10;2,09:15-09:55"
        val major34 = "3,10:10-10:50;4,10:55-11:35"
        val major56 = "5,14:15-14:55;6,15:00-15:40"
        val major78 = "7,15:55-16:35;8,16:35-17:15"
        val major90 = "9,18:05-18:45;10,18:45-19:25"
        return listOf(
            schoolCourse("英语1", 1, listOf(1, 2), listOf(5, 6), "08:30", "09:55", major12, major12),
            schoolCourse("高数1", 2, listOf(1, 2), listOf(5, 6), "08:30", "09:55", major12, major12),
            schoolCourse("电工1", 3, listOf(1, 2), listOf(5, 6), "08:30", "09:55", major12, major12),
            schoolCourse("高数2", 4, listOf(3, 4), listOf(5, 6), "10:10", "11:35", major34, major34),
            schoolCourse("英语2", 5, listOf(3, 4), listOf(12), "10:10", "11:35", major34, major34),
            schoolCourse("体育1", 1, listOf(5, 6), listOf(5, 6), "14:15", "15:40", major56, major56),
            schoolCourse("制图1", 2, listOf(5, 6), listOf(5, 6), "14:15", "15:40", major56, major56),
            schoolCourse("心理1", 1, listOf(7, 8), listOf(6), "15:55", "17:15", major78, major78),
            schoolCourse("制图2", 3, listOf(7, 8), listOf(9), "15:55", "17:15", major78, major78),
            schoolCourse("生涯1", 3, listOf(9, 10), listOf(12), "18:05", "19:25", major90, major90),
            schoolCourse("思政1", 4, listOf(9, 10), listOf(9), "18:05", "19:25", major90, major90),
            // Minority campus schedule on periods 3-4: 10:30-11:55
            schoolCourse("思政-异区", 3, listOf(3, 4), listOf(17), "10:30", "11:55", "3,10:30-10:50;4,10:55-11:55", major34),
            schoolCourse("实践-异区", 5, listOf(3, 4), listOf(5, 15), "10:30", "11:55", "3,10:30-10:50;4,10:55-11:55", major34),
        )
    }

    @Test
    fun importingFrozenDefaultGridSchedulePromotesMajorityBaselineAndPreservesExceptions() = runBlocking {
        val draft = ImportDraft(
            config = defaultConfig().copy(termStartDate = "2026-08-31", totalWeeks = 21),
            periods = defaultPeriods(),
            courses = frozenCampusSchedule(),
        )
        repository.importDraft(draft, createNewSchedule = false)
        val state = repository.snapshot()
        assertEquals((1..10).toList(), state.periods.map { it.periodIndex })
        assertEquals("08:30", state.periods.first().startTime)
        assertEquals("18:45", state.periods.last().startTime)

        val following = state.courses.filterNot { it.name.endsWith("异区") }
        assertEquals(11, following.size)
        assertTrue(following.all { !it.hasCustomTime() && it.customPeriodTimes == null && it.customStartTime == null })

        val exceptions = state.courses.filter { it.name.endsWith("异区") }
        assertEquals(2, exceptions.size)
        assertTrue(exceptions.all { it.hasCustomTime() && it.customStartTime == "10:30" && it.customEndTime == "11:55" })

        // Reminders always resolve real clocks regardless of regular vs custom.
        val zone = ZoneId.of("Asia/Shanghai")
        val fridayWeek15 = LocalDate.of(2026, 12, 11)
        val payloads = NotificationScheduler.coursePayloadsForDate(
            state,
            fridayWeek15,
            LiveUpdatePreferencesSnapshot(
                duringClassEnabled = true,
                breakStatusEnabled = true,
                tomorrowReminderEnabled = true,
                tomorrowReminderTime = LocalTime.of(22, 0),
            ),
            zone,
        )
        assertEquals(2, payloads.size)
        assertEquals(listOf("实践-异区"), payloads.map { it.name }.distinct())
        assertEquals(listOf("10:30 - 10:50", "10:55 - 11:55"), payloads.map { it.timeText })
    }

    @Test
    fun repositoryCanRestoreMajorityBaselineOnExistingFrozenSchedule() = runBlocking {
        val frozen = frozenCampusSchedule()
        val scheduleId = repository.importDraft(
            ImportDraft(defaultConfig(), listOf(PeriodEntity(1, "08:00", "08:45")), emptyList()),
            createNewSchedule = false,
        )
        database.courseDao().insertCourses(frozen.map { it.copy(scheduleId = scheduleId) })
        // Seed default 12-period scheme explicitly so import normalization wasn't run on courses.
        val routinesBefore = repository.loadScheduleRoutines(scheduleId)
        val proposal = proposeMajorityPeriodBaseline(routinesBefore.courses, routinesBefore.gridPeriods(), scheduleId)
        assertNotNull(proposal)
        assertEquals(11, proposal!!.matchedCourseCount)
        assertEquals(2, proposal.customCourseCount)

        val updatedRoutines = repository.restoreMajorityBaseline(routinesBefore)
        assertEquals(10, updatedRoutines.regular.times.size)
        assertEquals("08:30", updatedRoutines.regular.times.first().startTime)
        assertTrue(updatedRoutines.courses.filterNot { it.name.endsWith("异区") }.all { it.customPeriodTimes == null })
        assertTrue(updatedRoutines.courses.filter { it.name.endsWith("异区") }.all { it.customStartTime == "10:30" })
        assertNull(proposeMajorityPeriodBaseline(updatedRoutines.courses, updatedRoutines.gridPeriods(), scheduleId))
    }

    @Test
    fun conflictingAcademicSlotsInferMajorityBaselineInsteadOfDroppingAllToDefault() {
        val session = ShiguangImportSession()
        session.begin(defaultConfig(), defaultPeriods(), allowImportedBellTimes = true)
        session.stageTimeSlots("""[
            {"number":1,"startTime":"08:30","endTime":"09:10"},
            {"number":2,"startTime":"09:15","endTime":"09:55"},
            {"number":3,"startTime":"10:10","endTime":"10:50"},
            {"number":4,"startTime":"10:45","endTime":"11:35"}
        ]""")
        session.stageCourses("""[
            {"name":"主流A","teacher":"","position":"","day":1,"startSection":1,"endSection":2,"weeks":[1]},
            {"name":"主流B","teacher":"","position":"","day":2,"startSection":1,"endSection":2,"weeks":[1]},
            {"name":"非重叠三","teacher":"","position":"","day":3,"startSection":3,"endSection":3,"weeks":[1]},
            {"name":"冲突四","teacher":"","position":"异区","day":4,"startSection":3,"endSection":4,"weeks":[1],
              "isCustomTime":true,"customStartTime":"10:30","customEndTime":"11:55","customPeriodTimes":"3,10:30-10:50;4,10:55-11:55"}
        ]""")
        val draft = session.complete()
        assertEquals(listOf("08:30", "09:15", "10:10", "10:55"), draft.periods.map { it.startTime })
        val ordinary = draft.courses.filter { it.name != "冲突四" }
        assertTrue(ordinary.all { it.customStartTime == null && it.customPeriodTimes == null })
        val custom = draft.courses.single { it.name == "冲突四" }
        assertEquals("10:30", custom.customStartTime)
        assertEquals("11:55", custom.customEndTime)
    }

    @Test
    fun seasonalDateRuleUsesWinterSchemeOnWeekAndDayWithoutMarkingAllCustom() {
        val json = """{
          "courses":[
            {"name":"大学体育","teacher":"","position":"操场","day":1,"startSection":3,"endSection":4,"weeks":[1,2,3,4,12],"isCustomTime":false},
            {"name":"冬令自定义实验","teacher":"","position":"B区","day":5,"weeks":[12],"isCustomTime":true,"customStartTime":"14:10","customEndTime":"15:30",
              "sleepdown":{"groupId":"e1","mode":"CUSTOM","periods":[5,6],"startTime":"14:10","endTime":"15:30","lessonTimes":[{"number":5,"startTime":"14:10","endTime":"14:45"},{"number":6,"startTime":"14:55","endTime":"15:30"}]}}
          ],
          "timeSlots":[
            {"number":1,"startTime":"08:00","endTime":"08:45"},
            {"number":2,"startTime":"08:55","endTime":"09:40"},
            {"number":3,"startTime":"09:55","endTime":"10:40"},
            {"number":4,"startTime":"10:50","endTime":"11:35"},
            {"number":5,"startTime":"14:30","endTime":"15:15"},
            {"number":6,"startTime":"15:25","endTime":"16:10"}
          ],
          "config":{"semesterStartDate":"2026-08-31","semesterTotalWeeks":16,"defaultClassDuration":45,"defaultBreakDuration":10,"firstDayOfWeek":1},
          "comboSchedule":{"name":"日期作息","publicSchedules":[{
            "name":"冬作息","startDate":"2026-10-01","endDate":"2027-05-01",
            "timeSlots":[
              {"number":3,"startTime":"09:50","endTime":"10:35"},
              {"number":4,"startTime":"10:45","endTime":"11:30"},
              {"number":5,"startTime":"14:00","endTime":"14:45"},
              {"number":6,"startTime":"14:55","endTime":"15:40"}
            ]
          }]}
        }"""
        val state = CanonicalScheduleJson.decode(json, defaultConfig()).timingPreviewState()
        val summerDate = LocalDate.of(2026, 9, 7)
        val winterDate = LocalDate.of(2026, 11, 16)

        val summerPeriods = resolveWeekTimetable(state.config, state.periods, state.timetableCatalog, 2, summerDate).periods
        val winterPeriods = resolveWeekTimetable(state.config, state.periods, state.timetableCatalog, 12, winterDate).periods
        assertEquals("09:55", summerPeriods.first { it.periodIndex == 3 }.startTime)
        assertEquals("09:50", winterPeriods.first { it.periodIndex == 3 }.startTime)

        val winterBuckets = weekCourseBuckets(state.courses, 12, state.config, winterDate, state.timetableCatalog, winterPeriods)
        val pe = winterBuckets.visibleCourses.single { it.name == "大学体育" }
        val lab = winterBuckets.visibleCourses.single { it.name == "冬令自定义实验" }
        assertFalse(pe.hasCustomTime())
        assertEquals("09:50 - 11:30", courseTimeLabel(pe, winterPeriods))
        assertTrue(lab.hasCustomTime())
        assertEquals("14:10 - 15:30", courseTimeLabel(lab, winterPeriods))
    }
}
