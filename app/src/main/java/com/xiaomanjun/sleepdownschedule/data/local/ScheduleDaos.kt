package com.xiaomanjun.sleepdownschedule.data.local

import com.xiaomanjun.sleepdownschedule.model.*

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses WHERE scheduleId = (SELECT id FROM schedule_profiles WHERE isActive = 1 LIMIT 1)")
    fun observeCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE scheduleId = :scheduleId")
    fun observeCourses(scheduleId: Int): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses")
    fun observeAllCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE scheduleId = (SELECT id FROM schedule_profiles WHERE isActive = 1 LIMIT 1)")
    suspend fun getCourses(): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE scheduleId = :scheduleId")
    suspend fun getCourses(scheduleId: Int): List<CourseEntity>

    @Query("SELECT * FROM courses")
    suspend fun getAllCourses(): List<CourseEntity>

    @Query("DELETE FROM courses")
    suspend fun deleteAllRows()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: CourseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateCourse(course: CourseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<CourseEntity>): List<Long>

    @Query("DELETE FROM courses WHERE id = :courseId")
    suspend fun deleteCourse(courseId: Long)

    @Query("DELETE FROM courses WHERE scheduleId = (SELECT id FROM schedule_profiles WHERE isActive = 1 LIMIT 1)")
    suspend fun deleteAll()

    @Query("DELETE FROM courses WHERE scheduleId = :scheduleId")
    suspend fun deleteBySchedule(scheduleId: Int)
}

@Dao
interface ConfigDao {
    @Query("SELECT * FROM schedule_config WHERE id = (SELECT id FROM schedule_profiles WHERE isActive = 1 LIMIT 1)")
    fun observeConfig(): Flow<ScheduleConfigEntity?>

    @Query("SELECT * FROM schedule_config WHERE id = :scheduleId")
    fun observeConfig(scheduleId: Int): Flow<ScheduleConfigEntity?>

    @Query("SELECT * FROM schedule_config")
    fun observeAllConfigs(): Flow<List<ScheduleConfigEntity>>

    @Query("SELECT * FROM schedule_config")
    suspend fun getAllConfigs(): List<ScheduleConfigEntity>

    @Query("SELECT * FROM periods WHERE scheduleId = (SELECT id FROM schedule_profiles WHERE isActive = 1 LIMIT 1) ORDER BY periodIndex")
    fun observePeriods(): Flow<List<PeriodEntity>>

    @Query("SELECT * FROM periods WHERE scheduleId = :scheduleId ORDER BY periodIndex")
    fun observePeriods(scheduleId: Int): Flow<List<PeriodEntity>>

    @Query("SELECT * FROM periods ORDER BY scheduleId, periodIndex")
    fun observeAllPeriods(): Flow<List<PeriodEntity>>

    @Query("SELECT * FROM periods ORDER BY scheduleId, periodIndex")
    suspend fun getAllPeriods(): List<PeriodEntity>

    @Query("SELECT * FROM schedule_config WHERE id = (SELECT id FROM schedule_profiles WHERE isActive = 1 LIMIT 1)")
    suspend fun getConfig(): ScheduleConfigEntity?

    @Query("SELECT * FROM schedule_config WHERE id = :scheduleId")
    suspend fun getConfig(scheduleId: Int): ScheduleConfigEntity?

    @Query("SELECT * FROM periods WHERE scheduleId = (SELECT id FROM schedule_profiles WHERE isActive = 1 LIMIT 1) ORDER BY periodIndex")
    suspend fun getPeriods(): List<PeriodEntity>

    @Query("SELECT * FROM periods WHERE scheduleId = :scheduleId ORDER BY periodIndex")
    suspend fun getPeriods(scheduleId: Int): List<PeriodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(config: ScheduleConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfigs(configs: List<ScheduleConfigEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeriods(periods: List<PeriodEntity>)

    @Query("DELETE FROM periods WHERE scheduleId = (SELECT id FROM schedule_profiles WHERE isActive = 1 LIMIT 1)")
    suspend fun deletePeriods()

    @Query("DELETE FROM periods WHERE scheduleId = :scheduleId")
    suspend fun deletePeriods(scheduleId: Int)

    @Query("DELETE FROM schedule_config WHERE id = :scheduleId")
    suspend fun deleteConfig(scheduleId: Int)

    @Query("DELETE FROM schedule_config")
    suspend fun deleteAllConfigs()

    @Query("DELETE FROM periods")
    suspend fun deleteAllPeriods()
}

@Dao
interface PeriodSchemeDao {
    @Query("SELECT * FROM period_schemes WHERE scheduleId = :scheduleId ORDER BY id")
    fun observeSchemes(scheduleId: Int): Flow<List<PeriodSchemeEntity>>

    @Query("SELECT * FROM period_schemes WHERE scheduleId = :scheduleId ORDER BY id")
    suspend fun getSchemes(scheduleId: Int): List<PeriodSchemeEntity>

    @Query("SELECT * FROM period_schemes ORDER BY scheduleId, id")
    suspend fun getAllSchemes(): List<PeriodSchemeEntity>

    @Query("SELECT * FROM period_schemes WHERE scheduleId = :scheduleId AND isActive = 1 LIMIT 1")
    suspend fun getActiveScheme(scheduleId: Int): PeriodSchemeEntity?

    @Query("SELECT * FROM period_scheme_times WHERE schemeId = :schemeId ORDER BY periodIndex")
    suspend fun getTimes(schemeId: Long): List<PeriodSchemeTimeEntity>

    @Query("SELECT * FROM period_scheme_times ORDER BY schemeId, periodIndex")
    suspend fun getAllTimes(): List<PeriodSchemeTimeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScheme(scheme: PeriodSchemeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchemes(schemes: List<PeriodSchemeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTimes(times: List<PeriodSchemeTimeEntity>)

    @Query("DELETE FROM period_scheme_times WHERE schemeId = :schemeId")
    suspend fun deleteTimes(schemeId: Long)

    @Query("DELETE FROM period_schemes WHERE id = :schemeId")
    suspend fun deleteScheme(schemeId: Long)

    @Query("DELETE FROM period_scheme_times WHERE schemeId IN (SELECT id FROM period_schemes WHERE scheduleId = :scheduleId)")
    suspend fun deleteTimesForSchedule(scheduleId: Int)

    @Query("DELETE FROM period_schemes WHERE scheduleId = :scheduleId")
    suspend fun deleteSchemesForSchedule(scheduleId: Int)

    @Query("DELETE FROM period_scheme_times")
    suspend fun deleteAllTimes()

    @Query("DELETE FROM period_schemes")
    suspend fun deleteAllSchemes()
}

@Dao
interface ScheduleProfileDao {
    @Query("SELECT * FROM schedule_profiles ORDER BY id")
    fun observeProfiles(): Flow<List<ScheduleProfileEntity>>

    @Query("SELECT id FROM schedule_profiles WHERE isActive = 1 LIMIT 1")
    fun observeActiveProfileId(): Flow<Int?>

    @Query("SELECT * FROM schedule_profiles ORDER BY id")
    suspend fun getProfiles(): List<ScheduleProfileEntity>

    @Query("SELECT * FROM schedule_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfile(): ScheduleProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: ScheduleProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfiles(profiles: List<ScheduleProfileEntity>)

    @Query("UPDATE schedule_profiles SET name = :name WHERE id = :profileId")
    suspend fun renameProfile(profileId: Int, name: String)

    @Query("UPDATE schedule_profiles SET isActive = CASE WHEN id = :profileId THEN 1 ELSE 0 END")
    suspend fun activateProfile(profileId: Int)

    @Query("DELETE FROM schedule_profiles WHERE id = :profileId")
    suspend fun deleteProfile(profileId: Int)

    @Query("DELETE FROM schedule_profiles")
    suspend fun deleteAllProfiles()
}

@Dao
interface AgentDao {
    @Query("SELECT * FROM agent_daily_sessions ORDER BY scheduleId, date")
    suspend fun getAllDailySessions(): List<AgentDailySessionEntity>

    @Query("SELECT * FROM agent_daily_sessions WHERE scheduleId = :scheduleId ORDER BY date")
    suspend fun getDailySessions(scheduleId: Int): List<AgentDailySessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailySession(session: AgentDailySessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailySessions(sessions: List<AgentDailySessionEntity>)

    @Query("SELECT * FROM agent_messages WHERE scheduleId = :scheduleId AND sessionDate = :date ORDER BY createdAt, id")
    fun observeMessages(scheduleId: Int, date: String): Flow<List<AgentMessageEntity>>

    @Query("SELECT * FROM agent_messages ORDER BY scheduleId, sessionDate, createdAt, id")
    suspend fun getAllMessages(): List<AgentMessageEntity>

    @Query("SELECT * FROM agent_messages WHERE scheduleId = :scheduleId ORDER BY sessionDate, createdAt, id")
    suspend fun getMessages(scheduleId: Int): List<AgentMessageEntity>

    @Query("SELECT * FROM agent_messages WHERE scheduleId = :scheduleId AND sessionDate = :date AND status = 'READY' ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun getRecentMessages(scheduleId: Int, date: String, limit: Int): List<AgentMessageEntity>

    @Query("UPDATE agent_messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: Long, status: String)

    @Query("UPDATE agent_messages SET status = 'FAILED' WHERE status = 'PENDING' AND createdAt < :cutoff")
    suspend fun failPendingMessagesBefore(cutoff: Long)

    @Query("SELECT content FROM agent_messages")
    suspend fun getAllMessageContents(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AgentMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<AgentMessageEntity>)

    @Query("DELETE FROM agent_daily_sessions")
    suspend fun deleteAllDailySessions()

    @Query("DELETE FROM agent_messages")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM agent_daily_sessions WHERE date < :oldestDate")
    suspend fun deleteSessionsBefore(oldestDate: String)

    @Query("DELETE FROM agent_messages WHERE sessionDate < :oldestDate")
    suspend fun deleteMessagesBefore(oldestDate: String)
}
