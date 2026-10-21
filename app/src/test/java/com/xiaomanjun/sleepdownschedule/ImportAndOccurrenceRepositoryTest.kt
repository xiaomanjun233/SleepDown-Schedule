package com.xiaomanjun.sleepdownschedule

import android.app.Application
import androidx.room.Room
import com.xiaomanjun.sleepdownschedule.data.repository.ScheduleRepository
import com.xiaomanjun.sleepdownschedule.feature.importing.IcsScheduleCodec
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ImportAndOccurrenceRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: ScheduleRepository
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = ScheduleRepository(db)
        repository.ensureDefaults()
    }
    @After fun close() { db.close() }
    private fun course() = CourseEntity(name = "数学", teacher = null, location = null, weekday = 1,
        periods = listOf(1), weeks = listOf(3, 4, 5), weekParity = WeekParity.ALL, note = null)
    private fun calendar(): ImportDraft = IcsScheduleCodec.parse("""
        BEGIN:VCALENDAR
        BEGIN:VEVENT
        DTSTART:20260907T080000
        DTEND:20260907T093000
        SUMMARY:周一课程
        END:VEVENT
        BEGIN:VEVENT
        DTSTART:20260908T083000
        DTEND:20260908T100000
        SUMMARY:周二课程
        END:VEVENT
        END:VCALENDAR
    """.trimIndent().toByteArray(), defaultConfig()).getOrThrow()

    @Test fun calendarCanCreateAndOverwriteWithoutRejectingOverlappingClockRanges() = runBlocking {
        repository.addCourse(course())
        val created = repository.importDraft(calendar(), true)
        assertTrue(created != 1)
        assertEquals(1, db.courseDao().getCourses(1).size)
        assertEquals(2, db.courseDao().getCourses(created).size)
        val overwritten = repository.importDraft(calendar(), false)
        assertEquals(created, overwritten)
        assertEquals(2, db.courseDao().getCourses(created).size)
        assertEquals(3, db.configDao().getPeriods(created).size)
    }

    @Test fun failedImportLeavesOriginalDataAndAllowsRetry() = runBlocking {
        repository.addCourse(course())
        val before = db.courseDao().getCourses(1)
        val invalid = calendar().copy(courses = listOf(course().copy(customStartTime = "10:00", customEndTime = "08:00")))
        assertTrue(runCatching { repository.importDraft(invalid, false) }.isFailure)
        assertEquals(before, db.courseDao().getCourses(1))
        assertTrue(runCatching { repository.importDraft(invalid, true) }.isFailure)
        assertEquals(1, db.scheduleProfileDao().getProfiles().size)
        repository.importDraft(calendar(), false)
        assertEquals(2, db.courseDao().getCourses(1).size)
    }

    @Test fun moveOneOccurrenceAcrossWeeksPreservesAllOtherOccurrences() = runBlocking {
        repository.addCourse(course())
        val original = db.courseDao().getCourses(1).single()
        repository.updateCourseSingleWeek(original, original.copy(weekday = 5, weeks = listOf(4)), 3)
        val result = db.courseDao().getCourses(1)
        assertEquals(listOf(4, 5), result.single { it.weekday == 1 }.weeks)
        assertEquals(listOf(4), result.single { it.weekday == 5 }.weeks)
    }
}
