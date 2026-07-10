package com.example.courseschedule

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScheduleRepositoryDataBoundaryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ScheduleRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ScheduleRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun defaultScheduleImportWritesConfigPeriodsAndCoursesToScheduleOne() = runBlocking {
        repository.ensureDefaults()

        repository.importDraft(sampleDraft(totalWeeks = 18, periodCount = 14))

        val snapshot = repository.snapshot()
        assertEquals(1, snapshot.config.id)
        assertEquals(18, snapshot.config.totalWeeks)
        assertEquals(14, snapshot.periods.size)
        assertTrue(snapshot.periods.all { it.scheduleId == 1 })
        assertTrue(snapshot.courses.isNotEmpty())
        assertTrue(snapshot.courses.all { it.scheduleId == 1 })
    }

    @Test
    fun newScheduleImportNormalizesTargetScheduleAndDoesNotPolluteScheduleOne() = runBlocking {
        repository.ensureDefaults()

        repository.importDraft(sampleDraft(totalWeeks = 18, periodCount = 14), createNewSchedule = true)

        val activeId = database.scheduleProfileDao().getActiveProfile()!!.id
        val allCourses = database.courseDao().getAllCourses()
        val allPeriods = database.configDao().observeAllPeriods().first()
        assertNotEquals(1, activeId)
        assertEquals(18, database.configDao().getConfig(activeId)!!.totalWeeks)
        assertEquals(20, database.configDao().getConfig(1)!!.totalWeeks)
        assertTrue(allCourses.isNotEmpty())
        assertTrue(allCourses.all { it.scheduleId == activeId })
        assertEquals(12, allPeriods.count { it.scheduleId == 1 })
        assertEquals(14, allPeriods.count { it.scheduleId == activeId })
    }

    @Test
    fun savingSettingsAfterFourteenPeriodImportKeepsImportedPeriods() = runBlocking {
        repository.ensureDefaults()
        repository.importDraft(sampleDraft(totalWeeks = 18, periodCount = 14))
        val imported = repository.snapshot()

        repository.saveConfig(imported.config, imported.periods)

        assertEquals(14, repository.snapshot().periods.size)
    }

    @Test
    fun editingImportedCourseKeepsItInActiveSchedule() = runBlocking {
        repository.ensureDefaults()
        repository.importDraft(sampleDraft(totalWeeks = 18, periodCount = 14))
        val importedCourse = repository.snapshot().courses.first()

        repository.updateCourse(importedCourse.copy(name = "Edited", scheduleId = 999))

        val editedCourse = repository.snapshot().courses.single()
        assertEquals(importedCourse.id, editedCourse.id)
        assertEquals("Edited", editedCourse.name)
        assertEquals(1, editedCourse.scheduleId)
    }

    @Test
    fun switchingSchedulesShowsEachScheduleConfigAndPeriods() = runBlocking {
        repository.ensureDefaults()
        repository.importDraft(sampleDraft(totalWeeks = 18, periodCount = 14))
        val secondId = repository.createSchedule("Second")
        repository.activateSchedule(secondId)
        repository.saveConfig(defaultConfig(secondId).copy(totalWeeks = 16), defaultPeriods(secondId).take(10))

        var snapshot = repository.snapshot()
        assertEquals(secondId, snapshot.config.id)
        assertEquals(16, snapshot.config.totalWeeks)
        assertEquals(10, snapshot.periods.size)

        repository.activateSchedule(1)

        snapshot = repository.snapshot()
        assertEquals(1, snapshot.config.id)
        assertEquals(18, snapshot.config.totalWeeks)
        assertEquals(14, snapshot.periods.size)
    }

    @Test
    fun activeStateExcludesOtherSchedulePayloadsWhileManagerStateKeepsThem() = runBlocking {
        repository.ensureDefaults()
        repository.importDraft(sampleDraft(totalWeeks = 18, periodCount = 14))
        val secondId = repository.createSchedule("Second")
        repository.activateSchedule(secondId)
        repository.addCourse(
            CourseEntity(
                name = "Second course",
                teacher = "Teacher",
                location = "Room",
                weekday = 2,
                periods = listOf(3, 4),
                weeks = listOf(1, 2),
                weekParity = WeekParity.ALL,
                note = null,
                scheduleId = 1
            )
        )

        val active = repository.state.first { it.loaded && it.config.id == secondId }
        assertEquals(secondId, active.config.id)
        assertTrue(active.courses.all { it.scheduleId == secondId })
        assertTrue(active.allCourses.isEmpty())
        assertTrue(active.allConfigs.isEmpty())
        assertTrue(active.allPeriods.isEmpty())

        val manager = repository.allSchedulesState.first {
            it.loaded && it.schedules.size == 2 && it.allCourses.any { course -> course.scheduleId == secondId }
        }
        assertEquals(2, manager.schedules.size)
        assertTrue(manager.allCourses.any { it.scheduleId == 1 })
        assertTrue(manager.allCourses.any { it.scheduleId == secondId })
        assertTrue(manager.allConfigs.any { it.id == 1 })
        assertTrue(manager.allConfigs.any { it.id == secondId })
    }

    private fun sampleDraft(totalWeeks: Int, periodCount: Int): ImportDraft {
        return ImportDraft(
            config = defaultConfig().copy(totalWeeks = totalWeeks),
            periods = (1..periodCount).map { PeriodEntity(it, "08:00", "08:45") },
            courses = listOf(
                CourseEntity(
                    name = "Imported",
                    teacher = "Teacher",
                    location = "Room",
                    weekday = 1,
                    periods = listOf(1, minOf(2, periodCount)),
                    weeks = (1..totalWeeks).toList(),
                    weekParity = WeekParity.ALL,
                    note = null,
                    scheduleId = 1
                )
            )
        )
    }
}
