package com.xiaomanjun.sleepdownschedule.data.local

import androidx.room.*
import com.xiaomanjun.sleepdownschedule.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Relations are read atomically; all course writers replace metadata and timing in one transaction. */
@Dao
abstract class CourseDao {
    @Transaction @Query("SELECT * FROM courses WHERE scheduleId = (SELECT id FROM schedule_profiles WHERE isActive = 1 LIMIT 1)")
    protected abstract fun observeActiveRows(): Flow<List<StoredCourseWithTiming>>
    @Transaction @Query("SELECT * FROM courses WHERE scheduleId = :scheduleId")
    protected abstract fun observeRows(scheduleId: Int): Flow<List<StoredCourseWithTiming>>
    @Transaction @Query("SELECT * FROM courses")
    protected abstract fun observeAllRows(): Flow<List<StoredCourseWithTiming>>
    @Transaction @Query("SELECT * FROM courses WHERE scheduleId = (SELECT id FROM schedule_profiles WHERE isActive = 1 LIMIT 1)")
    protected abstract suspend fun getActiveRows(): List<StoredCourseWithTiming>
    @Transaction @Query("SELECT * FROM courses WHERE scheduleId = :scheduleId")
    protected abstract suspend fun getRows(scheduleId: Int): List<StoredCourseWithTiming>
    @Transaction @Query("SELECT * FROM courses")
    protected abstract suspend fun getAllRows(): List<StoredCourseWithTiming>

    fun observeCourses(): Flow<List<CourseEntity>> = observeActiveRows().map { it.map(StoredCourseWithTiming::toCourse) }
    fun observeCourses(scheduleId: Int): Flow<List<CourseEntity>> = observeRows(scheduleId).map { it.map(StoredCourseWithTiming::toCourse) }
    fun observeAllCourses(): Flow<List<CourseEntity>> = observeAllRows().map { it.map(StoredCourseWithTiming::toCourse) }
    suspend fun getCourses(): List<CourseEntity> = getActiveRows().map(StoredCourseWithTiming::toCourse)
    suspend fun getCourses(scheduleId: Int): List<CourseEntity> = getRows(scheduleId).map(StoredCourseWithTiming::toCourse)
    suspend fun getAllCourses(): List<CourseEntity> = getAllRows().map(StoredCourseWithTiming::toCourse)

    @Insert(onConflict = OnConflictStrategy.REPLACE) protected abstract suspend fun putCourse(row: StoredCourseEntity): Long
    @Insert protected abstract suspend fun putSections(rows: List<CourseSectionEntity>)
    @Insert protected abstract suspend fun putWeeks(rows: List<CourseWeekEntity>)
    @Insert protected abstract suspend fun putClocks(rows: List<CourseClockSegmentEntity>)

    @Transaction open suspend fun insertCourse(course: CourseEntity): Long {
        val stored = course.toStoredTiming()
        val id = putCourse(stored.course)
        putSections(stored.sections.map { it.copy(courseId = id) })
        putWeeks(stored.weeks.map { it.copy(courseId = id) })
        putClocks(stored.clocks.map { it.copy(courseId = id) })
        return id
    }
    @Transaction open suspend fun updateCourse(course: CourseEntity): Long = insertCourse(course)
    @Transaction open suspend fun insertCourses(courses: List<CourseEntity>): List<Long> = courses.map { insertCourse(it) }
    @Query("DELETE FROM courses") abstract suspend fun deleteAllRows()
    @Query("DELETE FROM courses WHERE id = :courseId") abstract suspend fun deleteCourse(courseId: Long)
    @Query("DELETE FROM courses WHERE scheduleId = (SELECT id FROM schedule_profiles WHERE isActive = 1 LIMIT 1)") abstract suspend fun deleteAll()
    @Query("DELETE FROM courses WHERE scheduleId = :scheduleId") abstract suspend fun deleteBySchedule(scheduleId: Int)
}
