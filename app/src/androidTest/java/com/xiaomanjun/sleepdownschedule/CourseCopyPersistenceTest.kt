package com.xiaomanjun.sleepdownschedule

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xiaomanjun.sleepdownschedule.data.repository.ScheduleRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CourseCopyPersistenceTest {
    private val source = CourseEntity(
        name = "复制验证", teacher = "教师", location = "教室", weekday = 2,
        periods = listOf(1, 2), weeks = listOf(1, 3), weekParity = WeekParity.ODD, note = "保持原课程"
    )

    private fun database() = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
    ).build()

    private inline fun withDatabase(block: (AppDatabase) -> Unit) {
        val database = database()
        try { block(database) } finally { database.close() }
    }

    @Test fun copyPreservesOriginalAndMergesOnlyMatchingWeeks() = runBlocking {
        withDatabase { database ->
            val repository = ScheduleRepository(database)
            repository.ensureDefaults()
            repository.addCourse(source)
            val original = database.courseDao().getCourses().single()
            repository.addCourse(source.copy(weekday = 4, periods = listOf(5, 6), weeks = listOf(5)))
            repository.copyCourses(listOf(source.copy(weekday = 4, periods = listOf(5, 6), weeks = listOf(3))))
            val saved = database.courseDao().getCourses()
            assertEquals(2, saved.size)
            assertEquals(original, saved.first { it.id == original.id })
            assertEquals(listOf(3, 5), saved.first { it.weekday == 4 }.weeks)
        }
    }

    @Test fun conflictAtCommitDoesNotCreatePartialCopies() = runBlocking {
        withDatabase { database ->
            val repository = ScheduleRepository(database)
            repository.ensureDefaults()
            repository.addCourse(source)
            val before = database.courseDao().getCourses()
            val result = runCatching { repository.copyCourses(listOf(source.copy(id = 0))) }
            assertTrue(result.isFailure)
            assertEquals(before, database.courseDao().getCourses())
        }
    }

    @Test fun scheduleSwitchBeforeCommitCannotRedirectTheCopy() = runBlocking {
        withDatabase { database ->
            val repository = ScheduleRepository(database)
            repository.ensureDefaults()
            repository.addCourse(source)
            repository.activateSchedule(repository.createSchedule("另一个课表"))
            val before = database.courseDao().getCourses()
            val result = runCatching {
                repository.copyCourses(listOf(source.copy(weekday = 4, periods = listOf(5, 6))))
            }
            assertTrue(result.isFailure)
            assertEquals(before, database.courseDao().getCourses())
        }
    }
}
