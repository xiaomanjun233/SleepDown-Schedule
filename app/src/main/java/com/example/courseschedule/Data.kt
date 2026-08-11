package com.example.courseschedule

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.runtime.Immutable
import androidx.core.database.sqlite.transaction
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

enum class WeekParity { ALL, ODD, EVEN }
enum class NotificationMode { STANDARD, LIVE_UPDATE }
enum class DefaultWallpaperStyle { KANBAN, NONE }
enum class DockAlignment { LEFT, CENTER, RIGHT }
enum class HomeStartMode { DAY, WEEK }
enum class LiveUpdateChipTextMode { LOCATION, COUNTDOWN, SHORT, NORMAL }
enum class PeriodSchemeMode { MANUAL, AUTO_MATCH }
enum class ScheduleTermState { MANUAL, UPCOMING, ACTIVE, ENDED, INVALID }

internal const val DefaultHomeChromeBlurScale = 1f
internal const val MinHomeChromeBlurScale = 0f
internal const val MaxHomeChromeBlurScale = 8f
// Kept only for database/backup compatibility with schema 35. Sampling is always full quality.
internal const val DefaultHomeChromeSamplingScale = 1f
internal const val LiquidCourseCardBlurMax = 10f
internal const val SimpleCourseCardBlurMax = 24f

internal fun courseCardBlurMaximum(glassEnabled: Boolean): Float =
    if (glassEnabled) LiquidCourseCardBlurMax else SimpleCourseCardBlurMax

internal fun normalizedHomeChromeBlurScale(value: Float): Float =
    value.takeIf { it.isFinite() }
        ?.coerceIn(MinHomeChromeBlurScale, MaxHomeChromeBlurScale)
        ?: DefaultHomeChromeBlurScale

@Entity(tableName = "courses")
@Immutable
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val teacher: String?,
    val location: String?,
    val weekday: Int,
    val periods: List<Int>,
    val weeks: List<Int>,
    val weekParity: WeekParity,
    val note: String?,
    @ColumnInfo(defaultValue = "1")
    val scheduleId: Int = 1
)

@Entity(tableName = "schedule_profiles")
@Immutable
data class ScheduleProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val isActive: Boolean = false
)

@Entity(tableName = "schedule_config")
@Immutable
data class ScheduleConfigEntity(
    @PrimaryKey val id: Int = 1,
    val totalWeeks: Int,
    val currentWeek: Int,
    val notificationLeadMinutes: Int,
    val termStartDate: String? = null,
    val autoCurrentWeek: Boolean = false,
    @ColumnInfo(defaultValue = "'MANUAL'") val termState: ScheduleTermState = ScheduleTermState.MANUAL,
    val notificationsEnabled: Boolean = true,
    val notificationMode: NotificationMode = NotificationMode.STANDARD,
    val wallpaperUri: String? = null,
    val wallpaperBlur: Float = 0f,
    val wallpaperBrightness: Float = 1f,
    val wallpaperPortraitCenterX: Float? = 0.5f,
    val wallpaperPortraitCenterY: Float? = 0.5f,
    val wallpaperPortraitScale: Float? = 1f,
    val wallpaperLandscapeCenterX: Float? = 0.5f,
    val wallpaperLandscapeCenterY: Float? = 0.5f,
    val wallpaperLandscapeScale: Float? = 1f,
    val wallpaperSourceWidth: Int? = null,
    val wallpaperSourceHeight: Int? = null,
    val cardColorArgb: Long = 0xFFD6E9FF,
    val cardAlpha: Float = 1f,
    val courseCardBlur: Float = 18f,
    val courseCardGlassEnabled: Boolean = true,
    val courseCardFontScale: Float = 1f,
    @ColumnInfo(defaultValue = "4293516543") val alternateCardColorArgb: Long = 0xFFD6E9FF,
    @ColumnInfo(defaultValue = "1") val alternateCardAlpha: Float = 1f,
    @ColumnInfo(defaultValue = "18") val alternateCourseCardBlur: Float = 18f,
    @ColumnInfo(defaultValue = "1") val alternateCourseCardFontScale: Float = 1f,
    val weekCardHeightDp: Float? = null,
    val homeTextLight: Boolean = false,
    @ColumnInfo(defaultValue = "1") val homeChromeBlurScale: Float = DefaultHomeChromeBlurScale,
    @ColumnInfo(defaultValue = "1") val homeChromeSamplingScale: Float = DefaultHomeChromeSamplingScale,
    val followSystemDarkMode: Boolean = true,
    val darkMode: Boolean = false,
    val defaultWallpaperStyle: DefaultWallpaperStyle = DefaultWallpaperStyle.NONE,
    val hideEmptyWeekends: Boolean = false,
    val dockAlignment: DockAlignment = DockAlignment.LEFT,
    val defaultHomeMode: HomeStartMode = HomeStartMode.WEEK,
    val liveUpdateActionsEnabled: Boolean = true,
    val liveUpdateChipTextMode: LiveUpdateChipTextMode = LiveUpdateChipTextMode.LOCATION,
    val classDurationMinutes: Int = 45,
    val breakDurationMinutes: Int = 10,
    @ColumnInfo(defaultValue = "0") val morningPeriodCount: Int = 4,
    @ColumnInfo(defaultValue = "0") val noonPeriodCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val afternoonPeriodCount: Int = 4,
    @ColumnInfo(defaultValue = "0") val eveningPeriodCount: Int = 4,
    val hideFromRecents: Boolean = false,
    val autoCheckUpdates: Boolean = true
)

@Entity(tableName = "periods", primaryKeys = ["scheduleId", "periodIndex"])
@Immutable
data class PeriodEntity(
    val periodIndex: Int,
    val startTime: String,
    val endTime: String,
    val scheduleId: Int = 1
)

@Entity(tableName = "period_schemes")
@Immutable
data class PeriodSchemeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Int,
    val name: String,
    val mode: PeriodSchemeMode = PeriodSchemeMode.MANUAL,
    val isActive: Boolean = false,
    val classDurationMinutes: Int = 45,
    val breakDurationMinutes: Int = 10,
    val morningStartTime: String = "08:00",
    val noonStartTime: String = "12:00",
    val afternoonStartTime: String = "14:00",
    val eveningStartTime: String = "19:00",
    val specialBreaksJson: String = "{}",
    val overridesJson: String = "{}"
)

@Entity(tableName = "period_scheme_times", primaryKeys = ["schemeId", "periodIndex"])
@Immutable
data class PeriodSchemeTimeEntity(
    val schemeId: Long,
    val periodIndex: Int,
    val startTime: String,
    val endTime: String
)

@Entity(tableName = "agent_daily_sessions", primaryKeys = ["scheduleId", "date"])
@Immutable
data class AgentDailySessionEntity(
    val scheduleId: Int,
    val date: String,
    val dailyPackJson: String,
    val providerId: String,
    val model: String,
    val createdAt: Long,
    val updatedAt: Long,
    val generationStatus: String,
    val lastError: String? = null
)

@Entity(tableName = "agent_messages")
@Immutable
data class AgentMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Int,
    val sessionDate: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val status: String
)

class ScheduleConverters {
    private val json = Json

    @TypeConverter
    fun intListToString(value: List<Int>): String = json.encodeToString(ListSerializer(Int.serializer()), value)

    @TypeConverter
    fun stringToIntList(value: String): List<Int> = json.decodeFromString(ListSerializer(Int.serializer()), value)

    @TypeConverter
    fun parityToString(value: WeekParity): String = value.name

    @TypeConverter
    fun stringToParity(value: String): WeekParity =
        runCatching { WeekParity.valueOf(value) }.getOrDefault(WeekParity.ALL)

    @TypeConverter
    fun scheduleTermStateToString(value: ScheduleTermState): String = value.name

    @TypeConverter
    fun stringToScheduleTermState(value: String): ScheduleTermState =
        runCatching { ScheduleTermState.valueOf(value) }.getOrDefault(ScheduleTermState.MANUAL)

    @TypeConverter
    fun notificationModeToString(value: NotificationMode): String = value.name

    @TypeConverter
    fun stringToNotificationMode(value: String): NotificationMode =
        runCatching { NotificationMode.valueOf(value) }.getOrDefault(NotificationMode.STANDARD)

    @TypeConverter
    fun liveUpdateChipTextModeToString(value: LiveUpdateChipTextMode): String = value.name

    @TypeConverter
    fun stringToLiveUpdateChipTextMode(value: String): LiveUpdateChipTextMode =
        when (value) {
            "AUTO", "NORMAL" -> LiveUpdateChipTextMode.LOCATION
            else -> runCatching { LiveUpdateChipTextMode.valueOf(value) }.getOrDefault(LiveUpdateChipTextMode.LOCATION)
        }

    @TypeConverter
    fun defaultWallpaperStyleToString(value: DefaultWallpaperStyle): String = value.name

    @TypeConverter
    fun stringToDefaultWallpaperStyle(value: String): DefaultWallpaperStyle =
        runCatching { DefaultWallpaperStyle.valueOf(value) }.getOrDefault(DefaultWallpaperStyle.KANBAN)

    @TypeConverter
    fun dockAlignmentToString(value: DockAlignment): String = value.name

    @TypeConverter
    fun stringToDockAlignment(value: String): DockAlignment =
        runCatching { DockAlignment.valueOf(value) }.getOrDefault(DockAlignment.LEFT)

    @TypeConverter
    fun homeStartModeToString(value: HomeStartMode): String = value.name

    @TypeConverter
    fun stringToHomeStartMode(value: String): HomeStartMode =
        runCatching { HomeStartMode.valueOf(value) }.getOrDefault(HomeStartMode.WEEK)
}

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

internal const val APP_DATABASE_VERSION = 36

@Database(
    entities = [
        CourseEntity::class,
        ScheduleProfileEntity::class,
        ScheduleConfigEntity::class,
        PeriodEntity::class,
        PeriodSchemeEntity::class,
        PeriodSchemeTimeEntity::class,
        AgentDailySessionEntity::class,
        AgentMessageEntity::class,
        WidgetAppearanceEntity::class
    ],
    version = APP_DATABASE_VERSION,
    exportSchema = true
)
@TypeConverters(ScheduleConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun configDao(): ConfigDao
    abstract fun periodSchemeDao(): PeriodSchemeDao
    abstract fun scheduleProfileDao(): ScheduleProfileDao
    abstract fun widgetAppearanceDao(): WidgetAppearanceDao
    abstract fun agentDao(): AgentDao
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN termStartDate TEXT")
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN autoCurrentWeek INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN notificationsEnabled INTEGER NOT NULL DEFAULT 1")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN notificationMode TEXT NOT NULL DEFAULT 'STANDARD'")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN wallpaperUri TEXT")
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN wallpaperBlur REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN wallpaperBrightness REAL NOT NULL DEFAULT 1")
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN cardColorArgb INTEGER NOT NULL DEFAULT 4293516543")
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN cardAlpha REAL NOT NULL DEFAULT 1")
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN weekCardHeightDp REAL")
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN homeTextLight INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN followSystemDarkMode INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN darkMode INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN defaultWallpaperStyle TEXT NOT NULL DEFAULT 'KANBAN'")
    }
}

private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN courseCardBlur REAL NOT NULL DEFAULT 18")
    }
}

private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN courseCardGlassEnabled INTEGER NOT NULL DEFAULT 1")
    }
}

private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN hideEmptyWeekends INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN dockAlignment TEXT NOT NULL DEFAULT 'LEFT'")
    }
}

private val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN defaultHomeMode TEXT NOT NULL DEFAULT 'WEEK'")
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN liveUpdateActionsEnabled INTEGER NOT NULL DEFAULT 1")
    }
}

private val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN liveUpdateChipTextMode TEXT NOT NULL DEFAULT 'AUTO'")
    }
}

private val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE schedule_config SET liveUpdateChipTextMode = 'LOCATION' WHERE liveUpdateChipTextMode = 'NORMAL'")
    }
}

private val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN classDurationMinutes INTEGER NOT NULL DEFAULT 45")
    }
}

private val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN breakDurationMinutes INTEGER NOT NULL DEFAULT 10")
    }
}

private val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE schedule_config SET liveUpdateChipTextMode = 'LOCATION' WHERE liveUpdateChipTextMode = 'AUTO'")
    }
}

private val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN hideFromRecents INTEGER NOT NULL DEFAULT 0")
    }
}

private fun SupportSQLiteDatabase.hasTable(table: String): Boolean {
    query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { cursor ->
        return cursor.moveToFirst()
    }
}

private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean {
    if (!hasTable(table)) return false
    query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) == column) return true
        }
    }
    return false
}

private fun ensureMultiScheduleSchema(db: SupportSQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS schedule_profiles (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, isActive INTEGER NOT NULL)")
    db.execSQL("INSERT INTO schedule_profiles (id, name, isActive) SELECT 1, '\u9ED8\u8BA4\u8BFE\u8868', 1 WHERE NOT EXISTS (SELECT 1 FROM schedule_profiles)")
    if (!db.hasColumn("courses", "scheduleId")) {
        db.execSQL("ALTER TABLE courses ADD COLUMN scheduleId INTEGER NOT NULL DEFAULT 1")
    }
    repairPeriodsForMultiSchedule(db)
    ensureSingleActiveScheduleProfile(db)
}

private fun ensureSingleActiveScheduleProfile(db: SupportSQLiteDatabase) {
    val activeCount = db.query("SELECT COUNT(*) FROM schedule_profiles WHERE isActive = 1").use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }
    if (activeCount == 1) return
    val selectedId = db.query(
        """
        SELECT p.id
        FROM schedule_profiles p
        LEFT JOIN courses c ON c.scheduleId = p.id
        GROUP BY p.id
        ORDER BY p.isActive DESC, COUNT(c.id) DESC, p.id DESC
        LIMIT 1
        """.trimIndent()
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else null }
    selectedId?.let { id ->
        db.execSQL("UPDATE schedule_profiles SET isActive = CASE WHEN id = ? THEN 1 ELSE 0 END", arrayOf(id))
    }
}

private fun repairPeriodsForMultiSchedule(db: SupportSQLiteDatabase) {
    if (!db.hasTable("periods")) {
        db.execSQL("CREATE TABLE periods (scheduleId INTEGER NOT NULL, periodIndex INTEGER NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL, PRIMARY KEY(scheduleId, periodIndex))")
        return
    }
    val hasScheduleId = db.hasColumn("periods", "scheduleId")
    db.execSQL("CREATE TABLE IF NOT EXISTS periods_room_fix (scheduleId INTEGER NOT NULL, periodIndex INTEGER NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL, PRIMARY KEY(scheduleId, periodIndex))")
    if (hasScheduleId) {
        db.execSQL("INSERT OR REPLACE INTO periods_room_fix (scheduleId, periodIndex, startTime, endTime) SELECT scheduleId, periodIndex, startTime, endTime FROM periods")
    } else {
        db.execSQL("INSERT OR REPLACE INTO periods_room_fix (scheduleId, periodIndex, startTime, endTime) SELECT 1, periodIndex, startTime, endTime FROM periods")
    }
    db.execSQL("DROP TABLE periods")
    db.execSQL("ALTER TABLE periods_room_fix RENAME TO periods")
}

private val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        ensureMultiScheduleSchema(db)
    }
}

private val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        ensureMultiScheduleSchema(db)
    }
}

private val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        ensureMultiScheduleSchema(db)
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN courseCardFontScale REAL NOT NULL DEFAULT 1")
    }
}

private val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        ensureMultiScheduleSchema(db)
        addWallpaperCropColumns(db)
    }
}

private fun createAgentTables(db: SupportSQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS agent_daily_sessions (scheduleId INTEGER NOT NULL, date TEXT NOT NULL, dailyPackJson TEXT NOT NULL, providerId TEXT NOT NULL, model TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, generationStatus TEXT NOT NULL, lastError TEXT, PRIMARY KEY(scheduleId, date))")
    db.execSQL("CREATE TABLE IF NOT EXISTS agent_messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, scheduleId INTEGER NOT NULL, sessionDate TEXT NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL, createdAt INTEGER NOT NULL, status TEXT NOT NULL)")
}

private val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        createAgentTables(db)
    }
}

private val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN autoCheckUpdates INTEGER NOT NULL DEFAULT 1")
    }
}

private val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val addedCountAssignments = mutableListOf<String>()
        if (!db.hasColumn("schedule_config", "morningPeriodCount")) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN morningPeriodCount INTEGER NOT NULL DEFAULT 0")
            addedCountAssignments += "morningPeriodCount = (SELECT COUNT(*) FROM periods p WHERE p.scheduleId = schedule_config.id AND CAST(substr(p.startTime, 1, 2) AS INTEGER) < 12)"
        }
        if (!db.hasColumn("schedule_config", "afternoonPeriodCount")) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN afternoonPeriodCount INTEGER NOT NULL DEFAULT 0")
            addedCountAssignments += "afternoonPeriodCount = (SELECT COUNT(*) FROM periods p WHERE p.scheduleId = schedule_config.id AND CAST(substr(p.startTime, 1, 2) AS INTEGER) >= 12 AND CAST(substr(p.startTime, 1, 2) AS INTEGER) < 18)"
        }
        if (!db.hasColumn("schedule_config", "eveningPeriodCount")) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN eveningPeriodCount INTEGER NOT NULL DEFAULT 0")
            addedCountAssignments += "eveningPeriodCount = (SELECT COUNT(*) FROM periods p WHERE p.scheduleId = schedule_config.id AND CAST(substr(p.startTime, 1, 2) AS INTEGER) >= 18)"
        }
        if (addedCountAssignments.isNotEmpty()) {
            db.execSQL("UPDATE schedule_config SET ${addedCountAssignments.joinToString()}")
        }
        createPeriodSchemeTables(db)
        db.execSQL("""
            INSERT INTO period_schemes (
              scheduleId, name, mode, isActive, classDurationMinutes, breakDurationMinutes,
              morningStartTime, afternoonStartTime, eveningStartTime, specialBreaksJson, overridesJson
            )
            SELECT c.id, '默认作息', 'MANUAL', 1, c.classDurationMinutes, c.breakDurationMinutes,
              COALESCE((SELECT MIN(startTime) FROM periods p WHERE p.scheduleId=c.id AND CAST(substr(p.startTime,1,2) AS INTEGER)<12), '08:00'),
              COALESCE((SELECT MIN(startTime) FROM periods p WHERE p.scheduleId=c.id AND CAST(substr(p.startTime,1,2) AS INTEGER)>=12 AND CAST(substr(p.startTime,1,2) AS INTEGER)<18), '14:00'),
              COALESCE((SELECT MIN(startTime) FROM periods p WHERE p.scheduleId=c.id AND CAST(substr(p.startTime,1,2) AS INTEGER)>=18), '19:00'),
              '{}', '{}'
            FROM schedule_config c
            WHERE NOT EXISTS (SELECT 1 FROM period_schemes existing WHERE existing.scheduleId = c.id)
        """.trimIndent())
        db.execSQL("""
            INSERT OR IGNORE INTO period_scheme_times (schemeId, periodIndex, startTime, endTime)
            SELECT s.id, p.periodIndex, p.startTime, p.endTime
            FROM period_schemes s JOIN periods p ON p.scheduleId=s.scheduleId
        """.trimIndent())
    }
}
private val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!db.hasColumn("schedule_config", "noonPeriodCount")) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN noonPeriodCount INTEGER NOT NULL DEFAULT 0")
        }
        rebuildPeriodSchemesWithNoonColumn(db)
        // v27 had no independent noon segment. Keep its original afternoon structure intact;
        // interpreting periods by clock hour here used to silently reset existing timetables.
    }
}

private fun rebuildPeriodSchemesWithNoonColumn(db: SupportSQLiteDatabase) {
    createPeriodSchemeTables(db)
    val noonStartTime = if (db.hasColumn("period_schemes", "noonStartTime")) {
        "COALESCE(noonStartTime, '12:00')"
    } else {
        "'12:00'"
    }
    db.execSQL("DROP TABLE IF EXISTS period_schemes_v28")
    db.execSQL(
        """
        CREATE TABLE period_schemes_v28 (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            scheduleId INTEGER NOT NULL,
            name TEXT NOT NULL,
            mode TEXT NOT NULL,
            isActive INTEGER NOT NULL,
            classDurationMinutes INTEGER NOT NULL,
            breakDurationMinutes INTEGER NOT NULL,
            morningStartTime TEXT NOT NULL,
            noonStartTime TEXT NOT NULL,
            afternoonStartTime TEXT NOT NULL,
            eveningStartTime TEXT NOT NULL,
            specialBreaksJson TEXT NOT NULL,
            overridesJson TEXT NOT NULL
        )
        """.trimIndent()
    )
    db.execSQL(
        """
        INSERT INTO period_schemes_v28 (
            id, scheduleId, name, mode, isActive, classDurationMinutes, breakDurationMinutes,
            morningStartTime, noonStartTime, afternoonStartTime, eveningStartTime,
            specialBreaksJson, overridesJson
        )
        SELECT
            id, scheduleId, name, mode, isActive, classDurationMinutes, breakDurationMinutes,
            morningStartTime, $noonStartTime, afternoonStartTime, eveningStartTime,
            specialBreaksJson, overridesJson
        FROM period_schemes
        """.trimIndent()
    )
    db.execSQL("DROP TABLE period_schemes")
    db.execSQL("ALTER TABLE period_schemes_v28 RENAME TO period_schemes")
}

private val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val addedTermState = !db.hasColumn("schedule_config", "termState")
        if (addedTermState) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN termState TEXT NOT NULL DEFAULT 'MANUAL'")
        }
        if (!addedTermState) return
        val start = "replace(replace(termStartDate, '.', '-'), '/', '-')"
        db.execSQL(
            """
            UPDATE schedule_config
            SET termState = CASE
                WHEN autoCurrentWeek = 0 THEN 'MANUAL'
                WHEN termStartDate IS NULL OR trim(termStartDate) = '' THEN 'INVALID'
                WHEN date($start) IS NULL THEN 'INVALID'
                WHEN date('now', 'localtime') < date($start) THEN 'UPCOMING'
                WHEN date('now', 'localtime') > date(
                    $start,
                    '-' || ((CAST(strftime('%w', $start) AS INTEGER) + 6) % 7) || ' days',
                    '+' || ((CASE WHEN totalWeeks < 1 THEN 1 ELSE totalWeeks END) * 7 - 1) || ' days'
                ) THEN 'ENDED'
                ELSE 'ACTIVE'
            END
            """.trimIndent()
        )
    }
}

private val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `widget_appearances` (
                `variant` TEXT NOT NULL,
                `appWidgetId` INTEGER NOT NULL,
                `enabled` INTEGER NOT NULL DEFAULT 0,
                `wallpaperUri` TEXT,
                `centerX` REAL NOT NULL DEFAULT 0.5,
                `centerY` REAL NOT NULL DEFAULT 0.5,
                `scale` REAL NOT NULL DEFAULT 1,
                `sourceWidth` INTEGER,
                `sourceHeight` INTEGER,
                `blurDp` REAL NOT NULL DEFAULT 6,
                `brightness` REAL NOT NULL DEFAULT 1,
                `updatedAt` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`variant`, `appWidgetId`)
            )
            """.trimIndent()
        )
    }
}
private fun createPeriodSchemeTables(db: SupportSQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS period_schemes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, scheduleId INTEGER NOT NULL, name TEXT NOT NULL, mode TEXT NOT NULL, isActive INTEGER NOT NULL, classDurationMinutes INTEGER NOT NULL, breakDurationMinutes INTEGER NOT NULL, morningStartTime TEXT NOT NULL, afternoonStartTime TEXT NOT NULL, eveningStartTime TEXT NOT NULL, specialBreaksJson TEXT NOT NULL, overridesJson TEXT NOT NULL)")
    db.execSQL("CREATE TABLE IF NOT EXISTS period_scheme_times (schemeId INTEGER NOT NULL, periodIndex INTEGER NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL, PRIMARY KEY(schemeId, periodIndex))")
}

private val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `widget_appearances_v31` (
                `variant` TEXT NOT NULL,
                `appWidgetId` INTEGER NOT NULL,
                `enabled` INTEGER NOT NULL DEFAULT 0,
                `wallpaperUri` TEXT,
                `centerX` REAL NOT NULL DEFAULT 0.5,
                `centerY` REAL NOT NULL DEFAULT 0.5,
                `scale` REAL NOT NULL DEFAULT 1,
                `sourceWidth` INTEGER,
                `sourceHeight` INTEGER,
                `blurDp` REAL NOT NULL DEFAULT 6,
                `brightness` REAL NOT NULL DEFAULT 1,
                `updatedAt` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`variant`, `appWidgetId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR REPLACE INTO `widget_appearances_v31`
            SELECT `variant`, `appWidgetId`, `enabled`, `wallpaperUri`, `centerX`, `centerY`, `scale`,
                   `sourceWidth`, `sourceHeight`, `blurDp`,
                   CASE
                       WHEN `appWidgetId` = 0 AND `updatedAt` = 0 AND `wallpaperUri` IS NULL
                            AND `enabled` = 0 AND ABS(`brightness` - 0.85) < 0.0001 THEN 1
                       ELSE `brightness`
                   END,
                   `updatedAt`
            FROM `widget_appearances`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `widget_appearances`")
        db.execSQL("ALTER TABLE `widget_appearances_v31` RENAME TO `widget_appearances`")
    }
}

private val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `widget_appearances_v32` (
                `variant` TEXT NOT NULL,
                `appWidgetId` INTEGER NOT NULL,
                `enabled` INTEGER NOT NULL DEFAULT 0,
                `wallpaperUri` TEXT,
                `centerX` REAL NOT NULL DEFAULT 0.5,
                `centerY` REAL NOT NULL DEFAULT 0.5,
                `scale` REAL NOT NULL DEFAULT 1,
                `sourceWidth` INTEGER,
                `sourceHeight` INTEGER,
                `blurDp` REAL NOT NULL DEFAULT 0,
                `brightness` REAL NOT NULL DEFAULT 1,
                `updatedAt` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`variant`, `appWidgetId`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR REPLACE INTO `widget_appearances_v32`
            SELECT `variant`, `appWidgetId`, `enabled`, `wallpaperUri`, `centerX`, `centerY`, `scale`,
                   `sourceWidth`, `sourceHeight`,
                   CASE
                       WHEN `appWidgetId` = 0 AND `updatedAt` = 0 AND `wallpaperUri` IS NULL
                            AND `enabled` = 0 AND ABS(`blurDp` - 6) < 0.0001 THEN 0
                       ELSE `blurDp`
                   END,
                   `brightness`, `updatedAt`
            FROM `widget_appearances`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `widget_appearances`")
        db.execSQL("ALTER TABLE `widget_appearances_v32` RENAME TO `widget_appearances`")
    }
}

private val MIGRATION_32_34 = object : Migration(32, 34) {
    override fun migrate(db: SupportSQLiteDatabase) = Unit
}

private val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `courses_v34` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `teacher` TEXT,
                `location` TEXT,
                `weekday` INTEGER NOT NULL,
                `periods` TEXT NOT NULL,
                `weeks` TEXT NOT NULL,
                `weekParity` TEXT NOT NULL,
                `note` TEXT,
                `scheduleId` INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `courses_v34`
                (`id`, `name`, `teacher`, `location`, `weekday`, `periods`, `weeks`, `weekParity`, `note`, `scheduleId`)
            SELECT `id`, `name`, `teacher`, `location`, `weekday`, `periods`, `weeks`, `weekParity`, `note`, `scheduleId`
            FROM `courses`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `courses`")
        db.execSQL("ALTER TABLE `courses_v34` RENAME TO `courses`")
    }
}

private val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN homeChromeBlurScale REAL NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE schedule_config ADD COLUMN homeChromeSamplingScale REAL NOT NULL DEFAULT 1")
    }
}

private val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!db.hasColumn("schedule_config", "alternateCardColorArgb")) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN alternateCardColorArgb INTEGER NOT NULL DEFAULT 4293516543")
        }
        if (!db.hasColumn("schedule_config", "alternateCardAlpha")) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN alternateCardAlpha REAL NOT NULL DEFAULT 1")
        }
        if (!db.hasColumn("schedule_config", "alternateCourseCardBlur")) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN alternateCourseCardBlur REAL NOT NULL DEFAULT 18")
        }
        if (!db.hasColumn("schedule_config", "alternateCourseCardFontScale")) {
            db.execSQL("ALTER TABLE schedule_config ADD COLUMN alternateCourseCardFontScale REAL NOT NULL DEFAULT 1")
        }
        db.execSQL(
            """
            UPDATE schedule_config
            SET alternateCardColorArgb = cardColorArgb,
                alternateCardAlpha = cardAlpha,
                alternateCourseCardBlur = CASE
                    WHEN courseCardGlassEnabled = 1 THEN 18
                    WHEN courseCardBlur < 10 THEN courseCardBlur
                    ELSE 10
                END,
                alternateCourseCardFontScale = courseCardFontScale
            """.trimIndent()
        )
    }
}

internal val APP_DATABASE_MIGRATIONS: List<Migration> = listOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
    MIGRATION_12_13,
    MIGRATION_13_14,
    MIGRATION_14_15,
    MIGRATION_15_16,
    MIGRATION_16_17,
    MIGRATION_17_18,
    MIGRATION_18_19,
    MIGRATION_19_20,
    MIGRATION_20_21,
    MIGRATION_21_22,
    MIGRATION_22_23,
    MIGRATION_23_24,
    MIGRATION_24_25,
    MIGRATION_25_26,
    MIGRATION_26_27,
    MIGRATION_27_28,
    MIGRATION_28_29,
    MIGRATION_29_30,
    MIGRATION_30_31,
    MIGRATION_31_32,
    MIGRATION_32_34,
    MIGRATION_33_34,
    MIGRATION_34_35,
    MIGRATION_35_36
)

private fun addWallpaperCropColumns(db: SupportSQLiteDatabase) {
    if (!db.hasColumn("schedule_config", "wallpaperPortraitCenterX")) db.execSQL("ALTER TABLE schedule_config ADD COLUMN wallpaperPortraitCenterX REAL DEFAULT 0.5")
    if (!db.hasColumn("schedule_config", "wallpaperPortraitCenterY")) db.execSQL("ALTER TABLE schedule_config ADD COLUMN wallpaperPortraitCenterY REAL DEFAULT 0.5")
    if (!db.hasColumn("schedule_config", "wallpaperPortraitScale")) db.execSQL("ALTER TABLE schedule_config ADD COLUMN wallpaperPortraitScale REAL DEFAULT 1")
    if (!db.hasColumn("schedule_config", "wallpaperLandscapeCenterX")) db.execSQL("ALTER TABLE schedule_config ADD COLUMN wallpaperLandscapeCenterX REAL DEFAULT 0.5")
    if (!db.hasColumn("schedule_config", "wallpaperLandscapeCenterY")) db.execSQL("ALTER TABLE schedule_config ADD COLUMN wallpaperLandscapeCenterY REAL DEFAULT 0.5")
    if (!db.hasColumn("schedule_config", "wallpaperLandscapeScale")) db.execSQL("ALTER TABLE schedule_config ADD COLUMN wallpaperLandscapeScale REAL DEFAULT 1")
    if (!db.hasColumn("schedule_config", "wallpaperSourceWidth")) db.execSQL("ALTER TABLE schedule_config ADD COLUMN wallpaperSourceWidth INTEGER")
    if (!db.hasColumn("schedule_config", "wallpaperSourceHeight")) db.execSQL("ALTER TABLE schedule_config ADD COLUMN wallpaperSourceHeight INTEGER")
}

internal fun createAppDatabase(
    context: Context,
    databaseName: String = "course_schedule.db"
): AppDatabase {
    repairDatabaseFileBeforeRoomOpen(context.getDatabasePath(databaseName))
    return Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
        .addMigrations(*APP_DATABASE_MIGRATIONS.toTypedArray())
        .build()
}

private fun repairDatabaseFileBeforeRoomOpen(path: File) {
    if (!path.exists()) return
    runCatching {
        SQLiteDatabase.openDatabase(path.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            val needsStructuralRepair = db.version < 26 ||
                !sqliteTableExists(db, "schedule_profiles") ||
                !sqliteTableExists(db, "schedule_config") ||
                !sqliteTableExists(db, "periods") ||
                !sqliteTableExists(db, "courses") ||
                !sqliteTableExists(db, "agent_daily_sessions") ||
                !sqliteTableExists(db, "agent_messages") ||
                !sqliteColumnExists(db, "courses", "scheduleId") ||
                (db.version >= 27 && !sqliteTableExists(db, "period_schemes")) ||
                (db.version >= 27 && !sqliteTableExists(db, "period_scheme_times")) ||
                (db.version >= 27 && !sqliteColumnExists(db, "schedule_config", "morningPeriodCount")) ||
                (db.version >= 27 && !sqliteColumnExists(db, "schedule_config", "afternoonPeriodCount")) ||
                (db.version >= 27 && !sqliteColumnExists(db, "schedule_config", "eveningPeriodCount")) ||
                (db.version >= 28 && !sqliteColumnExists(db, "schedule_config", "noonPeriodCount")) ||
                (db.version >= 28 && !sqliteColumnExists(db, "period_schemes", "noonStartTime")) ||
                (db.version >= 29 && !sqliteColumnExists(db, "schedule_config", "termState")) ||
                (db.version >= 30 && !sqliteTableExists(db, "widget_appearances")) ||
                !sqliteColumnExists(db, "periods", "scheduleId") ||
                !sqliteColumnExists(db, "schedule_config", "dockAlignment") ||
                !sqliteColumnExists(db, "schedule_config", "notificationMode") ||
                !sqliteColumnExists(db, "schedule_config", "autoCheckUpdates")
            if (needsStructuralRepair) {
                repairSQLiteDatabase(db)
            } else {
                repairActiveScheduleProfiles(db)
            }
        }
    }
}

private fun repairSQLiteDatabase(db: SQLiteDatabase) {
    db.transaction {
        execSQL("CREATE TABLE IF NOT EXISTS schedule_profiles (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, isActive INTEGER NOT NULL)")
        execSQL("INSERT INTO schedule_profiles (id, name, isActive) SELECT 1, '\u9ED8\u8BA4\u8BFE\u8868', 1 WHERE NOT EXISTS (SELECT 1 FROM schedule_profiles)")
        repairCoursesTable(this)
        repairScheduleConfigTable(this)
        repairPeriodsTable(this)
        repairAgentTables(this)
        repairPeriodSchemeTables(this)
        repairActiveScheduleProfiles(this)
        execSQL("DROP TABLE IF EXISTS room_master_table")
        setVersion(26)
    }
}

private fun repairCoursesTable(db: SQLiteDatabase) {
    if (!sqliteTableExists(db, "courses")) {
        db.execSQL("CREATE TABLE courses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, teacher TEXT, location TEXT, weekday INTEGER NOT NULL, periods TEXT NOT NULL, weeks TEXT NOT NULL, weekParity TEXT NOT NULL, note TEXT, scheduleId INTEGER NOT NULL DEFAULT 1)")
        return
    }
    if (!sqliteColumnExists(db, "courses", "scheduleId")) {
        db.execSQL("ALTER TABLE courses ADD COLUMN scheduleId INTEGER NOT NULL DEFAULT 1")
    }
}

private fun repairPeriodSchemeTables(db: SQLiteDatabase) {
    if (!sqliteTableExists(db, "period_schemes")) {
        db.execSQL(
            "CREATE TABLE period_schemes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, scheduleId INTEGER NOT NULL, name TEXT NOT NULL, mode TEXT NOT NULL, isActive INTEGER NOT NULL, classDurationMinutes INTEGER NOT NULL, breakDurationMinutes INTEGER NOT NULL, morningStartTime TEXT NOT NULL, noonStartTime TEXT NOT NULL, afternoonStartTime TEXT NOT NULL, eveningStartTime TEXT NOT NULL, specialBreaksJson TEXT NOT NULL, overridesJson TEXT NOT NULL)"
        )
    } else if (!sqliteColumnExists(db, "period_schemes", "noonStartTime")) {
        db.execSQL("DROP TABLE IF EXISTS period_schemes_room_fix")
        db.execSQL(
            "CREATE TABLE period_schemes_room_fix (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, scheduleId INTEGER NOT NULL, name TEXT NOT NULL, mode TEXT NOT NULL, isActive INTEGER NOT NULL, classDurationMinutes INTEGER NOT NULL, breakDurationMinutes INTEGER NOT NULL, morningStartTime TEXT NOT NULL, noonStartTime TEXT NOT NULL, afternoonStartTime TEXT NOT NULL, eveningStartTime TEXT NOT NULL, specialBreaksJson TEXT NOT NULL, overridesJson TEXT NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO period_schemes_room_fix (id, scheduleId, name, mode, isActive, classDurationMinutes, breakDurationMinutes, morningStartTime, noonStartTime, afternoonStartTime, eveningStartTime, specialBreaksJson, overridesJson) SELECT id, scheduleId, name, mode, isActive, classDurationMinutes, breakDurationMinutes, morningStartTime, '12:00', afternoonStartTime, eveningStartTime, specialBreaksJson, overridesJson FROM period_schemes"
        )
        db.execSQL("DROP TABLE period_schemes")
        db.execSQL("ALTER TABLE period_schemes_room_fix RENAME TO period_schemes")
    }
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS period_scheme_times (schemeId INTEGER NOT NULL, periodIndex INTEGER NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL, PRIMARY KEY(schemeId, periodIndex))"
    )
}

private fun sqliteTableExists(db: SQLiteDatabase, table: String): Boolean {
    val cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table))
    try {
        return cursor.moveToFirst()
    } finally {
        cursor.close()
    }
}

private fun sqliteColumnExists(db: SQLiteDatabase, table: String, column: String): Boolean {
    if (!sqliteTableExists(db, table)) return false
    val cursor = db.rawQuery("PRAGMA table_info(`$table`)", null)
    try {
        val nameIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) == column) return true
        }
    } finally {
        cursor.close()
    }
    return false
}

private fun ensureSqliteColumn(db: SQLiteDatabase, table: String, column: String, definition: String) {
    if (!sqliteColumnExists(db, table, column)) {
        db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
    }
}

private fun repairScheduleConfigTable(db: SQLiteDatabase) {
    if (!sqliteTableExists(db, "schedule_config")) {
        db.execSQL(scheduleConfigCreateSql("schedule_config"))
        return
    }
    ensureSqliteColumn(db, "schedule_config", "termStartDate", "TEXT")
    ensureSqliteColumn(db, "schedule_config", "autoCurrentWeek", "INTEGER NOT NULL DEFAULT 0")
    ensureSqliteColumn(db, "schedule_config", "termState", "TEXT NOT NULL DEFAULT 'MANUAL'")
    ensureSqliteColumn(db, "schedule_config", "notificationsEnabled", "INTEGER NOT NULL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "notificationMode", "TEXT NOT NULL DEFAULT 'STANDARD'")
    ensureSqliteColumn(db, "schedule_config", "wallpaperUri", "TEXT")
    ensureSqliteColumn(db, "schedule_config", "wallpaperBlur", "REAL NOT NULL DEFAULT 0")
    ensureSqliteColumn(db, "schedule_config", "wallpaperBrightness", "REAL NOT NULL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "wallpaperPortraitCenterX", "REAL DEFAULT 0.5")
    ensureSqliteColumn(db, "schedule_config", "wallpaperPortraitCenterY", "REAL DEFAULT 0.5")
    ensureSqliteColumn(db, "schedule_config", "wallpaperPortraitScale", "REAL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "wallpaperLandscapeCenterX", "REAL DEFAULT 0.5")
    ensureSqliteColumn(db, "schedule_config", "wallpaperLandscapeCenterY", "REAL DEFAULT 0.5")
    ensureSqliteColumn(db, "schedule_config", "wallpaperLandscapeScale", "REAL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "wallpaperSourceWidth", "INTEGER")
    ensureSqliteColumn(db, "schedule_config", "wallpaperSourceHeight", "INTEGER")
    ensureSqliteColumn(db, "schedule_config", "cardColorArgb", "INTEGER NOT NULL DEFAULT 4293516543")
    ensureSqliteColumn(db, "schedule_config", "cardAlpha", "REAL NOT NULL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "courseCardBlur", "REAL NOT NULL DEFAULT 18")
    ensureSqliteColumn(db, "schedule_config", "courseCardGlassEnabled", "INTEGER NOT NULL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "courseCardFontScale", "REAL NOT NULL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "alternateCardColorArgb", "INTEGER NOT NULL DEFAULT 4293516543")
    ensureSqliteColumn(db, "schedule_config", "alternateCardAlpha", "REAL NOT NULL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "alternateCourseCardBlur", "REAL NOT NULL DEFAULT 18")
    ensureSqliteColumn(db, "schedule_config", "alternateCourseCardFontScale", "REAL NOT NULL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "weekCardHeightDp", "REAL")
    ensureSqliteColumn(db, "schedule_config", "homeTextLight", "INTEGER NOT NULL DEFAULT 0")
    ensureSqliteColumn(db, "schedule_config", "homeChromeBlurScale", "REAL NOT NULL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "homeChromeSamplingScale", "REAL NOT NULL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "followSystemDarkMode", "INTEGER NOT NULL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "darkMode", "INTEGER NOT NULL DEFAULT 0")
    ensureSqliteColumn(db, "schedule_config", "defaultWallpaperStyle", "TEXT NOT NULL DEFAULT 'KANBAN'")
    ensureSqliteColumn(db, "schedule_config", "hideEmptyWeekends", "INTEGER NOT NULL DEFAULT 0")
    ensureSqliteColumn(db, "schedule_config", "dockAlignment", "TEXT NOT NULL DEFAULT 'LEFT'")
    ensureSqliteColumn(db, "schedule_config", "defaultHomeMode", "TEXT NOT NULL DEFAULT 'WEEK'")
    ensureSqliteColumn(db, "schedule_config", "liveUpdateActionsEnabled", "INTEGER NOT NULL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "liveUpdateChipTextMode", "TEXT NOT NULL DEFAULT 'LOCATION'")
    ensureSqliteColumn(db, "schedule_config", "classDurationMinutes", "INTEGER NOT NULL DEFAULT 45")
    ensureSqliteColumn(db, "schedule_config", "breakDurationMinutes", "INTEGER NOT NULL DEFAULT 10")
    ensureSqliteColumn(db, "schedule_config", "hideFromRecents", "INTEGER NOT NULL DEFAULT 0")
    ensureSqliteColumn(db, "schedule_config", "autoCheckUpdates", "INTEGER NOT NULL DEFAULT 1")
    ensureSqliteColumn(db, "schedule_config", "morningPeriodCount", "INTEGER NOT NULL DEFAULT 4")
    ensureSqliteColumn(db, "schedule_config", "noonPeriodCount", "INTEGER NOT NULL DEFAULT 0")
    ensureSqliteColumn(db, "schedule_config", "afternoonPeriodCount", "INTEGER NOT NULL DEFAULT 4")
    ensureSqliteColumn(db, "schedule_config", "eveningPeriodCount", "INTEGER NOT NULL DEFAULT 4")
    db.execSQL(scheduleConfigCreateSql("schedule_config_room_fix"))
    db.execSQL(
        """
        INSERT OR REPLACE INTO schedule_config_room_fix (
            id, totalWeeks, currentWeek, notificationLeadMinutes, termStartDate, autoCurrentWeek, termState,
            notificationsEnabled, notificationMode, wallpaperUri, wallpaperBlur, wallpaperBrightness,
            wallpaperPortraitCenterX, wallpaperPortraitCenterY, wallpaperPortraitScale,
            wallpaperLandscapeCenterX, wallpaperLandscapeCenterY, wallpaperLandscapeScale,
            wallpaperSourceWidth, wallpaperSourceHeight,
            cardColorArgb, cardAlpha, courseCardBlur, courseCardGlassEnabled, courseCardFontScale,
            alternateCardColorArgb, alternateCardAlpha, alternateCourseCardBlur, alternateCourseCardFontScale,
            weekCardHeightDp,
            homeTextLight, homeChromeBlurScale, homeChromeSamplingScale,
            followSystemDarkMode, darkMode, defaultWallpaperStyle, hideEmptyWeekends,
            dockAlignment, defaultHomeMode, liveUpdateActionsEnabled, liveUpdateChipTextMode,
            classDurationMinutes, breakDurationMinutes, hideFromRecents, autoCheckUpdates,
            morningPeriodCount, noonPeriodCount, afternoonPeriodCount, eveningPeriodCount
        )
        SELECT
            id, totalWeeks, currentWeek, notificationLeadMinutes, termStartDate, autoCurrentWeek, termState,
            notificationsEnabled, notificationMode, wallpaperUri, wallpaperBlur, wallpaperBrightness,
            wallpaperPortraitCenterX, wallpaperPortraitCenterY, wallpaperPortraitScale,
            wallpaperLandscapeCenterX, wallpaperLandscapeCenterY, wallpaperLandscapeScale,
            wallpaperSourceWidth, wallpaperSourceHeight,
            cardColorArgb, cardAlpha, courseCardBlur, courseCardGlassEnabled, courseCardFontScale,
            alternateCardColorArgb, alternateCardAlpha, alternateCourseCardBlur, alternateCourseCardFontScale,
            weekCardHeightDp,
            homeTextLight, homeChromeBlurScale, homeChromeSamplingScale,
            followSystemDarkMode, darkMode, defaultWallpaperStyle, hideEmptyWeekends,
            dockAlignment, defaultHomeMode, liveUpdateActionsEnabled, liveUpdateChipTextMode,
            classDurationMinutes, breakDurationMinutes, hideFromRecents, autoCheckUpdates,
            morningPeriodCount, noonPeriodCount, afternoonPeriodCount, eveningPeriodCount
        FROM schedule_config
        """.trimIndent()
    )
    db.execSQL("DROP TABLE schedule_config")
    db.execSQL("ALTER TABLE schedule_config_room_fix RENAME TO schedule_config")
}

private fun scheduleConfigCreateSql(table: String): String =
    """
    CREATE TABLE IF NOT EXISTS $table (
        id INTEGER NOT NULL PRIMARY KEY,
        totalWeeks INTEGER NOT NULL,
        currentWeek INTEGER NOT NULL,
        notificationLeadMinutes INTEGER NOT NULL,
        termStartDate TEXT,
        autoCurrentWeek INTEGER NOT NULL,
        termState TEXT NOT NULL DEFAULT 'MANUAL',
        notificationsEnabled INTEGER NOT NULL,
        notificationMode TEXT NOT NULL,
        wallpaperUri TEXT,
        wallpaperBlur REAL NOT NULL,
        wallpaperBrightness REAL NOT NULL,
        wallpaperPortraitCenterX REAL DEFAULT 0.5,
        wallpaperPortraitCenterY REAL DEFAULT 0.5,
        wallpaperPortraitScale REAL DEFAULT 1,
        wallpaperLandscapeCenterX REAL DEFAULT 0.5,
        wallpaperLandscapeCenterY REAL DEFAULT 0.5,
        wallpaperLandscapeScale REAL DEFAULT 1,
        wallpaperSourceWidth INTEGER,
        wallpaperSourceHeight INTEGER,
        cardColorArgb INTEGER NOT NULL,
        cardAlpha REAL NOT NULL,
        courseCardBlur REAL NOT NULL,
        courseCardGlassEnabled INTEGER NOT NULL,
        courseCardFontScale REAL NOT NULL,
        alternateCardColorArgb INTEGER NOT NULL DEFAULT 4293516543,
        alternateCardAlpha REAL NOT NULL DEFAULT 1,
        alternateCourseCardBlur REAL NOT NULL DEFAULT 18,
        alternateCourseCardFontScale REAL NOT NULL DEFAULT 1,
        weekCardHeightDp REAL,
        homeTextLight INTEGER NOT NULL,
        homeChromeBlurScale REAL NOT NULL DEFAULT 1,
        homeChromeSamplingScale REAL NOT NULL DEFAULT 1,
        followSystemDarkMode INTEGER NOT NULL,
        darkMode INTEGER NOT NULL,
        defaultWallpaperStyle TEXT NOT NULL,
        hideEmptyWeekends INTEGER NOT NULL,
        dockAlignment TEXT NOT NULL,
        defaultHomeMode TEXT NOT NULL,
        liveUpdateActionsEnabled INTEGER NOT NULL,
        liveUpdateChipTextMode TEXT NOT NULL,
        classDurationMinutes INTEGER NOT NULL,
        breakDurationMinutes INTEGER NOT NULL,
        hideFromRecents INTEGER NOT NULL,
        autoCheckUpdates INTEGER NOT NULL DEFAULT 1,
        morningPeriodCount INTEGER NOT NULL DEFAULT 4,
        noonPeriodCount INTEGER NOT NULL DEFAULT 0,
        afternoonPeriodCount INTEGER NOT NULL DEFAULT 4,
        eveningPeriodCount INTEGER NOT NULL DEFAULT 4
    )
    """.trimIndent()

private fun repairPeriodsTable(db: SQLiteDatabase) {
    if (!sqliteTableExists(db, "periods")) {
        db.execSQL("CREATE TABLE periods (scheduleId INTEGER NOT NULL, periodIndex INTEGER NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL, PRIMARY KEY(scheduleId, periodIndex))")
        return
    }
    val hasScheduleId = sqliteColumnExists(db, "periods", "scheduleId")
    db.execSQL("CREATE TABLE IF NOT EXISTS periods_room_fix (scheduleId INTEGER NOT NULL, periodIndex INTEGER NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL, PRIMARY KEY(scheduleId, periodIndex))")
    if (hasScheduleId) {
        db.execSQL("INSERT OR REPLACE INTO periods_room_fix (scheduleId, periodIndex, startTime, endTime) SELECT scheduleId, periodIndex, startTime, endTime FROM periods")
    } else {
        db.execSQL("INSERT OR REPLACE INTO periods_room_fix (scheduleId, periodIndex, startTime, endTime) SELECT 1, periodIndex, startTime, endTime FROM periods")
    }
    db.execSQL("DROP TABLE periods")
    db.execSQL("ALTER TABLE periods_room_fix RENAME TO periods")
}

@Immutable
data class AppState(
    val courses: List<CourseEntity> = emptyList(),
    val allCourses: List<CourseEntity> = emptyList(),
    val schedules: List<ScheduleProfileEntity> = emptyList(),
    val allConfigs: List<ScheduleConfigEntity> = emptyList(),
    val allPeriods: List<PeriodEntity> = emptyList(),
    val config: ScheduleConfigEntity = defaultConfig(),
    val periods: List<PeriodEntity> = defaultPeriods(),
    val loaded: Boolean = false
)

@Immutable
enum class ImportDraftSource {
    STANDARD,
    AI_EDU
}

@Immutable
data class ImportDraft(
    val config: ScheduleConfigEntity,
    val periods: List<PeriodEntity>,
    val courses: List<CourseEntity>,
    val source: ImportDraftSource = ImportDraftSource.STANDARD
)

private data class MultiScheduleSnapshot(
    val courses: List<CourseEntity>,
    val allCourses: List<CourseEntity>,
    val schedules: List<ScheduleProfileEntity>,
    val allConfigs: List<ScheduleConfigEntity>,
    val allPeriods: List<PeriodEntity>
)

class ScheduleRepository(private val database: AppDatabase) {
    private val courseDao = database.courseDao()
    private val configDao = database.configDao()
    private val profileDao = database.scheduleProfileDao()
    private val periodSchemeDao = database.periodSchemeDao()

    suspend fun switchPeriodScheme(scheduleId: Int, schemeId: Long) = database.withTransaction {
        val schemes = periodSchemeDao.getSchemes(scheduleId)
        val target = schemes.firstOrNull { it.id == schemeId } ?: error("作息方案不存在")
        val times = periodSchemeDao.getTimes(target.id)
        require(times.isNotEmpty()) { "作息方案没有节次时间" }
        periodSchemeDao.upsertSchemes(schemes.map { it.copy(isActive = it.id == target.id) })
        configDao.deletePeriods(scheduleId)
        configDao.upsertPeriods(times.map { PeriodEntity(it.periodIndex, it.startTime, it.endTime, scheduleId) })
    }

    suspend fun renamePeriodScheme(scheduleId: Int, schemeId: Long, name: String) = database.withTransaction {
        val target = periodSchemeDao.getSchemes(scheduleId).firstOrNull { it.id == schemeId }
            ?: error("作息方案不存在")
        periodSchemeDao.upsertScheme(target.copy(name = name.trim().ifBlank { "未命名作息" }))
    }

    suspend fun duplicatePeriodScheme(scheduleId: Int, schemeId: Long, name: String? = null): Long =
        database.withTransaction {
            val source = periodSchemeDao.getSchemes(scheduleId).firstOrNull { it.id == schemeId }
                ?: error("作息方案不存在")
            val newId = periodSchemeDao.upsertScheme(
                source.copy(id = 0, name = name?.trim().orEmpty().ifBlank { "${source.name} 副本" }, isActive = false)
            )
            periodSchemeDao.upsertTimes(periodSchemeDao.getTimes(source.id).map { it.copy(schemeId = newId) })
            newId
        }

    suspend fun deletePeriodScheme(scheduleId: Int, schemeId: Long) = database.withTransaction {
        val schemes = periodSchemeDao.getSchemes(scheduleId)
        require(schemes.size > 1) { "至少需要保留一套作息方案" }
        val removedIndex = schemes.indexOfFirst { it.id == schemeId }
        require(removedIndex >= 0) { "作息方案不存在" }
        val wasActive = schemes[removedIndex].isActive
        periodSchemeDao.deleteTimes(schemeId)
        periodSchemeDao.deleteScheme(schemeId)
        if (wasActive) {
            val remaining = schemes.filterNot { it.id == schemeId }
            val adjacent = remaining[removedIndex.coerceAtMost(remaining.lastIndex)]
            switchPeriodScheme(scheduleId, adjacent.id)
        }
    }

    suspend fun loadPeriodSchemes(scheduleId: Int): SchedulePeriodSchemesDraft = database.withTransaction {
        ensureScheduleData(scheduleId)
        val config = configDao.getConfig(scheduleId) ?: defaultConfig(scheduleId)
        val activePeriods = configDao.getPeriods(scheduleId)
        var schemes = periodSchemeDao.getSchemes(scheduleId)
        if (schemes.isEmpty()) {
            val first = activePeriods.firstOrNull()?.startTime ?: "08:00"
            val schemeId = periodSchemeDao.upsertScheme(
                PeriodSchemeEntity(
                    scheduleId = scheduleId,
                    name = "默认作息",
                    isActive = true,
                    classDurationMinutes = config.classDurationMinutes,
                    breakDurationMinutes = config.breakDurationMinutes,
                    morningStartTime = first,
                    afternoonStartTime = activePeriods.firstOrNull {
                        runCatching { java.time.LocalTime.parse(it.startTime).hour }.getOrDefault(0) in 12..17
                    }?.startTime ?: "14:00",
                    eveningStartTime = activePeriods.firstOrNull {
                        runCatching { java.time.LocalTime.parse(it.startTime).hour }.getOrDefault(0) >= 18
                    }?.startTime ?: "19:00"
                )
            )
            periodSchemeDao.upsertTimes(activePeriods.map {
                PeriodSchemeTimeEntity(schemeId, it.periodIndex, it.startTime, it.endTime)
            })
            schemes = periodSchemeDao.getSchemes(scheduleId)
        }
        val drafts = schemes.map { scheme ->
            PeriodSchemeDraft(
                scheme = scheme,
                times = periodSchemeDao.getTimes(scheme.id),
                specialBreaks = decodeSpecialBreaks(scheme.specialBreaksJson),
                overriddenPeriods = decodeOverrides(scheme.overridesJson)
            )
        }
        SchedulePeriodSchemesDraft(drafts, schemes.firstOrNull { it.isActive }?.id ?: schemes.first().id)
    }

    suspend fun saveScheduleDetail(
        config: ScheduleConfigEntity,
        draft: SchedulePeriodSchemesDraft
    ) = database.withTransaction {
        require(draft.schemes.isNotEmpty()) { "至少需要保留一套作息方案" }
        val scheduleId = config.id
        val expectedCount = config.totalPeriodCount()
        require(expectedCount > 0) { "至少需要保留一个节次" }

        val storedConfig = configDao.getConfig(scheduleId)
        val originalPeriods = configDao.getPeriods(scheduleId)
        var courses = courseDao.getCourses(scheduleId)

        val existing = periodSchemeDao.getSchemes(scheduleId)
        val existingDrafts = existing.associate { scheme ->
            scheme.id to PeriodSchemeDraft(
                scheme = scheme,
                times = periodSchemeDao.getTimes(scheme.id),
                specialBreaks = decodeSpecialBreaks(scheme.specialBreaksJson),
                overriddenPeriods = decodeOverrides(scheme.overridesJson)
            )
        }
        val incomingIds = draft.schemes.map { it.scheme.id }.filter { it > 0 }.toSet()
        existing.filter { it.id !in incomingIds }.forEach {
            periodSchemeDao.deleteTimes(it.id)
            periodSchemeDao.deleteScheme(it.id)
        }

        val idMap = mutableMapOf<Long, Long>()
        val saved = draft.schemes.map { item ->
            val sourceId = item.scheme.id
            val entity = item.scheme.copy(
                id = if (sourceId > 0) sourceId else 0,
                scheduleId = scheduleId,
                isActive = sourceId == draft.activeSchemeId,
                specialBreaksJson = encodeSpecialBreaks(item.specialBreaks),
                overridesJson = encodeOverrides(item.overriddenPeriods)
            )
            val storedId = periodSchemeDao.upsertScheme(entity).let { if (entity.id > 0) entity.id else it }
            idMap[sourceId] = storedId
            val incoming = item.copy(scheme = entity.copy(id = storedId))
            val resolved = resolveSchemeTimesForSave(
                config = config,
                draft = incoming,
                storedConfig = storedConfig,
                storedDraft = existingDrafts[sourceId]
            )
            require(resolved.size == expectedCount) { "${entity.name} 的节次数与课表结构不一致" }
            validateResolvedPeriodTimes(resolved)?.let { throw IllegalArgumentException("${entity.name}：$it") }
            periodSchemeDao.deleteTimes(storedId)
            periodSchemeDao.upsertTimes(resolved.map { it.copy(schemeId = storedId) })
            entity.copy(id = storedId) to resolved
        }
        val activeId = idMap[draft.activeSchemeId] ?: draft.activeSchemeId
        periodSchemeDao.upsertSchemes(saved.map { (scheme, _) -> scheme.copy(isActive = scheme.id == activeId) })
        val activeTimes = saved.firstOrNull { it.first.id == activeId }?.second ?: saved.first().second
        if (draft.topologyOperations.isNotEmpty()) {
            courses = courses.map { course ->
                course.copy(periods = remapCoursePeriodsByClockTime(course.periods, originalPeriods, activeTimes))
            }
        }
        configDao.upsertConfig(normalizeConfigForSchedule(config, scheduleId))
        configDao.deletePeriods(scheduleId)
        configDao.upsertPeriods(activeTimes.map { PeriodEntity(it.periodIndex, it.startTime, it.endTime, scheduleId) })
        if (courses.isNotEmpty()) courseDao.insertCourses(courses)
    }

    private val multiScheduleState = combine(
        courseDao.observeAllCourses(),
        profileDao.observeProfiles(),
        configDao.observeAllConfigs(),
        configDao.observeAllPeriods()
    ) { allCourses, schedules, allConfigs, allPeriods ->
        val profiles = schedules
        val activeId = profiles.firstOrNull { it.isActive }?.id ?: profiles.firstOrNull()?.id
        MultiScheduleSnapshot(
            courses = if (activeId == null) emptyList() else allCourses.filter { it.scheduleId == activeId },
            allCourses = allCourses,
            schedules = profiles,
            allConfigs = allConfigs,
            allPeriods = allPeriods
        )
    }

    val allSchedulesState = multiScheduleState.map { snapshot ->
        val activeId = snapshot.schedules.firstOrNull { it.isActive }?.id
            ?: snapshot.schedules.firstOrNull()?.id
        val storedConfig = activeId?.let { id -> snapshot.allConfigs.firstOrNull { it.id == id } }
        val config = storedConfig ?: defaultConfig(activeId ?: 1)
        val storedPeriods = activeId?.let { id -> snapshot.allPeriods.filter { it.scheduleId == id } }.orEmpty()
        val periods = storedPeriods.ifEmpty { defaultPeriods(activeId ?: 1) }
        AppState(
            courses = snapshot.courses,
            allCourses = snapshot.allCourses,
            schedules = snapshot.schedules,
            allConfigs = snapshot.allConfigs,
            allPeriods = snapshot.allPeriods,
            config = config,
            periods = periods,
            loaded = activeId != null && storedConfig != null && storedPeriods.isNotEmpty()
        )
    }.distinctUntilChanged()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state = profileDao.observeActiveProfileId()
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { activeId ->
            combine(
                courseDao.observeCourses(activeId).distinctUntilChanged(),
                configDao.observeConfig(activeId).distinctUntilChanged(),
                configDao.observePeriods(activeId).distinctUntilChanged()
            ) { courses, config, periods ->
                val storedConfig = config?.copy(id = activeId)
                AppState(
                    courses = courses,
                    schedules = emptyList(),
                    config = storedConfig ?: defaultConfig(activeId),
                    periods = periods.ifEmpty { defaultPeriods(activeId) },
                    loaded = storedConfig != null && periods.isNotEmpty()
                )
            }
        }
        .distinctUntilChanged()

    suspend fun ensureDefaults() {
        database.withTransaction {
            if (profileDao.getProfiles().isEmpty()) {
                profileDao.upsertProfile(ScheduleProfileEntity(id = 1, name = "\u9ED8\u8BA4\u8BFE\u8868", isActive = true))
            }
            if (profileDao.getActiveProfile() == null) {
                profileDao.getProfiles().firstOrNull()?.let { profileDao.activateProfile(it.id) }
            }
            profileDao.getProfiles().forEach { profile ->
                ensureScheduleData(profile.id)
            }
        }
    }

    suspend fun addCourse(course: CourseEntity) {
        val scheduleId = activeScheduleId()
        courseDao.insertCourse(normalizeCoursesForSchedule(listOf(course.copy(id = 0)), scheduleId).single())
    }

    suspend fun addCourses(courses: List<CourseEntity>) {
        if (courses.isEmpty()) return
        database.withTransaction {
            val scheduleId = activeScheduleId()
            courseDao.insertCourses(
                normalizeCoursesForSchedule(courses.map { it.copy(id = 0) }, scheduleId)
            )
            mergeCompatibleCourseFragments(scheduleId)
        }
    }

    suspend fun updateCourse(course: CourseEntity) {
        database.withTransaction {
            val scheduleId = activeScheduleId()
            requireCurrentCourse(scheduleId, course.id)
            courseDao.updateCourse(normalizeCoursesForSchedule(listOf(course), scheduleId).single())
        }
    }

    suspend fun replaceCourseGroup(originals: List<CourseEntity>, replacements: List<CourseEntity>) {
        require(originals.isNotEmpty()) { "没有可更新的课程" }
        require(replacements.isNotEmpty()) { "请至少选择一个上课星期和周次" }
        database.withTransaction {
            val scheduleId = activeScheduleId()
            val originalIds = originals.map(CourseEntity::id).filter { it > 0 }.distinct().toSet()
            require(originalIds.isNotEmpty()) { "课程记录已失效，请重新打开" }
            val currentIds = courseDao.getCourses(scheduleId).map(CourseEntity::id).toSet()
            require(originalIds.all(currentIds::contains)) { "课程已在其他操作中变更，请重新打开" }
            originalIds.forEach { courseDao.deleteCourse(it) }
            val normalized = normalizeCoursesForSchedule(
                replacements.map { replacement ->
                    replacement.copy(
                        id = replacement.id.takeIf(originalIds::contains) ?: 0,
                        scheduleId = scheduleId
                    )
                },
                scheduleId
            )
            courseDao.insertCourses(normalized)
            mergeCompatibleCourseFragments(scheduleId)
        }
    }

    suspend fun updateCourseSingleWeek(original: CourseEntity, edited: CourseEntity, targetWeek: Int) {
        database.withTransaction {
            val scheduleId = activeScheduleId()
            val current = requireCurrentCourse(scheduleId, original.id)
            val remainingWeeks = current.weeks.filter { it != targetWeek }
            if (remainingWeeks.isEmpty()) {
                courseDao.deleteCourse(current.id)
            } else {
                courseDao.updateCourse(current.copy(weeks = remainingWeeks))
            }
            val singleWeekCourse = normalizeCoursesForSchedule(listOf(edited.copy(id = 0, weeks = listOf(targetWeek))), scheduleId).single()
            courseDao.getCourses(scheduleId)
                .filter { it.id != current.id && it.weeks.distinct() == listOf(targetWeek) && it.hasSameOccurrenceSlot(singleWeekCourse) }
                .forEach { courseDao.deleteCourse(it.id) }
            courseDao.insertCourse(singleWeekCourse)
            mergeCompatibleCourseFragments(scheduleId)
        }
    }

    suspend fun deleteCourseSingleWeek(course: CourseEntity, targetWeek: Int) {
        database.withTransaction {
            val scheduleId = activeScheduleId()
            val current = requireCurrentCourse(scheduleId, course.id)
            val remainingWeeks = current.weeks.filter { it != targetWeek }
            if (remainingWeeks.isEmpty()) {
                courseDao.deleteCourse(current.id)
            } else {
                courseDao.updateCourse(current.copy(weeks = remainingWeeks))
            }
            mergeCompatibleCourseFragments(scheduleId)
        }
    }

    suspend fun updateRelatedCourses(original: CourseEntity, edited: CourseEntity) {
        database.withTransaction {
            val scheduleId = activeScheduleId()
            val current = requireCurrentCourse(scheduleId, original.id)
            val originalName = current.name.trim()
            val related = courseDao.getCourses(scheduleId).filter {
                it.id == current.id || it.name.trim() == originalName
            }.map {
                it.copy(
                    name = edited.name,
                    teacher = edited.teacher,
                    location = edited.location,
                    note = edited.note
                )
            }
            if (related.isNotEmpty()) {
                courseDao.insertCourses(normalizeCoursesForSchedule(related, scheduleId))
            }
        }
    }

    suspend fun deleteCourse(course: CourseEntity) {
        database.withTransaction {
            val scheduleId = activeScheduleId()
            requireCurrentCourse(scheduleId, course.id)
            courseDao.deleteCourse(course.id)
        }
    }

    suspend fun deleteCourses(courses: List<CourseEntity>) {
        if (courses.isEmpty()) return
        database.withTransaction {
            val scheduleId = activeScheduleId()
            val currentIds = courseDao.getCourses(scheduleId).map(CourseEntity::id).toSet()
            val ids = courses.map(CourseEntity::id).filter(currentIds::contains).distinct()
            require(ids.isNotEmpty()) { "课程记录已失效，请重新打开" }
            ids.forEach { courseDao.deleteCourse(it) }
        }
    }

    suspend fun deleteCoursesSingleWeek(courses: List<CourseEntity>, targetWeek: Int) {
        if (courses.isEmpty()) return
        database.withTransaction {
            val scheduleId = activeScheduleId()
            val currentById = courseDao.getCourses(scheduleId).associateBy(CourseEntity::id)
            val current = courses.mapNotNull { currentById[it.id] }.distinctBy(CourseEntity::id)
            require(current.isNotEmpty()) { "课程记录已失效，请重新打开" }
            current.forEach { course ->
                val remainingWeeks = course.weeks.filterNot { it == targetWeek }
                if (remainingWeeks.isEmpty()) courseDao.deleteCourse(course.id)
                else courseDao.updateCourse(course.copy(weeks = remainingWeeks))
            }
            mergeCompatibleCourseFragments(scheduleId)
        }
    }

    suspend fun executeAgentPlan(plan: AgentPlan): AgentPlanExecutionResult {
        return runCatching {
            database.withTransaction {
                val scheduleId = activeScheduleId()
                val before = courseDao.getCourses(scheduleId)
                plan.actions.forEach { action ->
                    if (
                        action.type == AgentValidatedActionType.UPDATE ||
                        action.type == AgentValidatedActionType.DELETE
                    ) {
                        val original = action.original
                        val stored = original?.let { candidate ->
                            before.firstOrNull { it.id == candidate.id }
                        }
                        if (stored == null) {
                            throw AgentPlanRejectedException("操作对象不属于当前课表，已拒绝执行")
                        }
                        if (stored != original) {
                            throw AgentPlanRejectedException("课程在确认前已发生变化，请让 AI 基于最新课表重新生成操作")
                        }
                    }
                }
                val preview = previewAgentPlan(before, plan)

                plan.actions.forEach { action ->
                    when (action.type) {
                        AgentValidatedActionType.ADD -> action.edited?.let { course ->
                            courseDao.insertCourse(
                                normalizeCoursesForSchedule(
                                    listOf(course.copy(id = 0)),
                                    scheduleId
                                ).single()
                            )
                        }

                        AgentValidatedActionType.UPDATE -> {
                            val original = action.original
                            val edited = action.edited
                            if (original != null && edited != null) {
                                if (action.scope == AgentActionScope.CURRENT_WEEK) {
                                    val remainingWeeks =
                                        original.weeks.filterNot { it == action.targetWeek }
                                    if (remainingWeeks.isEmpty()) {
                                        courseDao.deleteCourse(original.id)
                                    } else {
                                        courseDao.updateCourse(
                                            original.copy(
                                                weeks = remainingWeeks,
                                                scheduleId = scheduleId
                                            )
                                        )
                                    }
                                    courseDao.insertCourse(
                                        normalizeCoursesForSchedule(
                                            listOf(
                                                edited.copy(
                                                    id = 0,
                                                    weeks = listOf(action.targetWeek)
                                                )
                                            ),
                                            scheduleId
                                        ).single()
                                    )
                                } else {
                                    courseDao.updateCourse(
                                        normalizeCoursesForSchedule(
                                            listOf(edited.copy(id = original.id)),
                                            scheduleId
                                        ).single()
                                    )
                                }
                            }
                        }

                        AgentValidatedActionType.DELETE -> action.original?.let { original ->
                            if (action.scope == AgentActionScope.CURRENT_WEEK) {
                                val remainingWeeks =
                                    original.weeks.filterNot { it == action.targetWeek }
                                if (remainingWeeks.isEmpty()) courseDao.deleteCourse(original.id)
                                else courseDao.updateCourse(
                                    original.copy(
                                        weeks = remainingWeeks,
                                        scheduleId = scheduleId
                                    )
                                )
                            } else {
                                courseDao.deleteCourse(original.id)
                            }
                        }

                        AgentValidatedActionType.OPEN_SETTINGS,
                        AgentValidatedActionType.SET_SETTING,
                        AgentValidatedActionType.SET_PERIOD_SETTINGS -> Unit
                    }
                }

                mergeCompatibleCourseFragments(scheduleId)
                val after = courseDao.getCourses(scheduleId)
                if (!verifyAgentPlan(after, plan)) {
                    throw AgentPlanRejectedException("数据库写入后的真实状态与操作计划不一致")
                }
                AgentPlanExecutionResult(
                    success = true,
                    preview = preview,
                    verified = true,
                    message = "操作已完成并验证"
                )
            }
        }.getOrElse { error ->
            AgentPlanExecutionResult(
                success = false,
                preview = null,
                verified = false,
                message = error.message ?: "操作失败，所有修改已回滚"
            )
        }
    }

    suspend fun importDraft(draft: ImportDraft, createNewSchedule: Boolean = false): Int {
        return database.withTransaction {
            val oldActiveId = activeScheduleId()
            val globalConfig = configDao.getConfig(oldActiveId) ?: defaultConfig(oldActiveId)
            val scheduleId = if (createNewSchedule) {
                profileDao.upsertProfile(ScheduleProfileEntity(name = "\u5BFC\u5165\u8BFE\u8868", isActive = false)).toInt().also {
                    profileDao.activateProfile(it)
                }
            } else {
                oldActiveId
            }
            val importedPeriods = normalizePeriodsForSchedule(draft.periods, scheduleId)
            val importedConfig = configWithCountsFromPeriods(draft.config.withGlobalSettingsFrom(globalConfig), importedPeriods)
            configDao.upsertConfig(normalizeConfigForSchedule(importedConfig, scheduleId))
            configDao.deletePeriods(scheduleId)
            configDao.upsertPeriods(importedPeriods)
            replaceSchemesWithPeriods(scheduleId, importedConfig, importedPeriods, "导入作息")
            courseDao.deleteBySchedule(scheduleId)
            courseDao.insertCourses(normalizeImportedCoursesForSchedule(draft.courses, scheduleId))
            scheduleId
        }
    }

    suspend fun saveConfig(config: ScheduleConfigEntity, periods: List<PeriodEntity>) {
        val scheduleId = activeScheduleId()
        database.withTransaction {
            val normalizedPeriods = normalizePeriodsForSchedule(periods, scheduleId)
            val normalizedConfig = configWithCountsFromPeriods(config, normalizedPeriods)
            configDao.upsertConfig(normalizeConfigForSchedule(normalizedConfig, scheduleId))
            configDao.deletePeriods(scheduleId)
            configDao.upsertPeriods(normalizedPeriods)
            syncActiveSchemeTimes(scheduleId, normalizedConfig, normalizedPeriods)
        }
    }

    suspend fun saveConfigForSchedule(scheduleId: Int, config: ScheduleConfigEntity, periods: List<PeriodEntity>) {
        database.withTransaction {
            val normalizedPeriods = normalizePeriodsForSchedule(periods, scheduleId)
            val normalizedConfig = configWithCountsFromPeriods(config, normalizedPeriods)
            configDao.upsertConfig(normalizeConfigForSchedule(normalizedConfig, scheduleId))
            configDao.deletePeriods(scheduleId)
            configDao.upsertPeriods(normalizedPeriods)
            syncActiveSchemeTimes(scheduleId, normalizedConfig, normalizedPeriods)
        }
    }

    suspend fun saveConfigChanges(original: ScheduleConfigEntity, updated: ScheduleConfigEntity) {
        val scheduleId = updated.id
        database.withTransaction {
            val current = configDao.getConfig(scheduleId) ?: return@withTransaction
            val merged = current.withChangesFrom(original, updated)
            configDao.upsertConfig(normalizeConfigForSchedule(merged, scheduleId))
        }
    }

    suspend fun savePersonalizationSnapshot(updated: ScheduleConfigEntity): Boolean {
        val scheduleId = updated.id
        return database.withTransaction {
            val current = configDao.getConfig(scheduleId) ?: return@withTransaction false
            val merged = current.withPersonalizationFrom(updated)
            configDao.upsertConfig(normalizeConfigForSchedule(merged, scheduleId))
            current.wallpaperUri != merged.wallpaperUri
        }
    }

    suspend fun referencedWallpaperUris(): Set<String> = database.withTransaction {
        configDao.getAllConfigs().mapNotNullTo(linkedSetOf()) { it.wallpaperUri }
    }

    suspend fun referencedScheduleIds(): Set<Int> = database.withTransaction {
        profileDao.getProfiles().mapTo(linkedSetOf()) { it.id }
    }

    suspend fun saveGlobalSettings(config: ScheduleConfigEntity) {
        saveGlobalSettingsWith { base -> base.withGlobalSettingsFrom(config) }
    }

    suspend fun saveGlobalSettingsPatches(
        generalSettings: ScheduleConfigEntity?,
        notificationSettings: ScheduleConfigEntity?,
        homeChromeBlurScale: Float? = null
    ) {
        require(
            generalSettings != null ||
                notificationSettings != null ||
                homeChromeBlurScale != null
        ) {
            "至少需要一组 global settings patch"
        }
        saveGlobalSettingsWith { base ->
            var merged = base
            generalSettings?.let { merged = merged.withGeneralSettingsFrom(it) }
            notificationSettings?.let { merged = merged.withNotificationSettingsFrom(it) }
            homeChromeBlurScale?.let { merged = merged.withHomeChromeBlurScale(it) }
            merged
        }
    }

    private suspend fun saveGlobalSettingsWith(
        merge: (ScheduleConfigEntity) -> ScheduleConfigEntity
    ) {
        val activeId = activeScheduleId()
        database.withTransaction {
            val existing = configDao.getAllConfigs()
            val targetIds = (profileDao.getProfiles().map { it.id } + existing.map { it.id } + activeId).distinct()
            targetIds.forEach { id ->
                val base = existing.firstOrNull { it.id == id } ?: defaultConfig(id)
                configDao.upsertConfig(merge(base).copy(id = id))
            }
        }
    }

    suspend fun createSchedule(name: String): Int {
        return database.withTransaction {
            val globalConfig = configDao.getConfig() ?: defaultConfig(activeScheduleId())
            val id = profileDao.upsertProfile(ScheduleProfileEntity(name = name, isActive = false)).toInt()
            configDao.upsertConfig(defaultConfig(id).withGlobalSettingsFrom(globalConfig))
            val periods = defaultPeriods(id)
            configDao.upsertPeriods(periods)
            replaceSchemesWithPeriods(id, defaultConfig(id), periods, "默认作息")
            id
        }
    }

    suspend fun activateSchedule(scheduleId: Int) {
        database.withTransaction {
            val oldActiveId = activeScheduleId()
            val globalConfig = configDao.getConfig(oldActiveId) ?: defaultConfig(oldActiveId)
            ensureScheduleData(scheduleId)
            val targetConfig = configDao.getConfig(scheduleId)
                ?: error("课表配置恢复失败：$scheduleId")
            profileDao.activateProfile(scheduleId)
            configDao.upsertConfig(targetConfig.withGlobalSettingsFrom(globalConfig).copy(id = scheduleId))
        }
    }

    suspend fun renameSchedule(scheduleId: Int, name: String) {
        profileDao.renameProfile(scheduleId, name.ifBlank { "\u672A\u547D\u540D\u8BFE\u8868" })
    }

    suspend fun deleteSchedule(scheduleId: Int) {
        database.withTransaction {
            val profiles = profileDao.getProfiles()
            if (profiles.size <= 1) return@withTransaction
            profileDao.deleteProfile(scheduleId)
            courseDao.deleteBySchedule(scheduleId)
            periodSchemeDao.deleteTimesForSchedule(scheduleId)
            periodSchemeDao.deleteSchemesForSchedule(scheduleId)
            configDao.deletePeriods(scheduleId)
            configDao.deleteConfig(scheduleId)
            val remaining = profiles.filterNot { it.id == scheduleId }
            if (profiles.any { it.id == scheduleId && it.isActive }) {
                remaining.firstOrNull()?.let { profileDao.activateProfile(it.id) }
            }
        }
    }

    suspend fun snapshot(): AppState = database.withTransaction {
        val activeId = activeScheduleId()
        AppState(
            courses = courseDao.getCourses(activeId),
            allCourses = courseDao.getAllCourses(),
            schedules = profileDao.getProfiles().ifEmpty {
                listOf(
                    ScheduleProfileEntity(
                        id = activeId,
                        name = "\u9ED8\u8BA4\u8BFE\u8868",
                        isActive = true
                    )
                )
            },
            allConfigs = emptyList(),
            allPeriods = emptyList(),
            config = configDao.getConfig(activeId) ?: defaultConfig(activeId),
            periods = configDao.getPeriods(activeId).ifEmpty { defaultPeriods(activeId) },
            loaded = true
        )
    }

    /**
     * Coherent current-schedule snapshot for notifications, widgets and previews.
     * These callers never need every schedule's courses, so avoid a full-table read
     * while keeping all related rows pinned to one active schedule transaction.
     */
    suspend fun activeSnapshot(): AppState = database.withTransaction {
        val activeId = activeScheduleId()
        AppState(
            courses = courseDao.getCourses(activeId),
            config = configDao.getConfig(activeId) ?: defaultConfig(activeId),
            periods = configDao.getPeriods(activeId).ifEmpty { defaultPeriods(activeId) },
            loaded = true
        )
    }

    private suspend fun activeScheduleId(): Int {
        return profileDao.getActiveProfile()?.id ?: 1
    }

    private suspend fun requireCurrentCourse(scheduleId: Int, courseId: Long): CourseEntity {
        return courseDao.getCourses(scheduleId).firstOrNull { it.id == courseId }
            ?: throw IllegalStateException("课表已切换或课程已被删除，请返回当前课表后重试")
    }

    /**
     * Reconciles the persisted config, materialized periods and period schemes for
     * one schedule without replacing real user data with defaults. Older builds
     * could leave a non-active schedule without its config or materialized periods
     * while the scheme tables still retained the original timetable.
     */
    private suspend fun ensureScheduleData(scheduleId: Int) {
        var periods = configDao.getPeriods(scheduleId)
        val schemes = periodSchemeDao.getSchemes(scheduleId)
        val activeScheme = schemes.firstOrNull { it.isActive } ?: schemes.firstOrNull()
        var activeTimes = activeScheme?.let { periodSchemeDao.getTimes(it.id) }.orEmpty()

        if (periods.isNotEmpty() && activeTimes.isNotEmpty()) {
            val schemePeriods = activeTimes.map {
                PeriodEntity(it.periodIndex, it.startTime, it.endTime, scheduleId)
            }
            if (!samePeriodTimeline(periods, schemePeriods)) {
                val defaults = defaultPeriods(scheduleId)
                val materializedIsDefault = samePeriodTimeline(periods, defaults)
                val schemeIsDefault = samePeriodTimeline(schemePeriods, defaults)
                if (materializedIsDefault && !schemeIsDefault) {
                    // A legacy/fallback write replaced only the materialized layer.
                    // Recover the remaining customized scheme instead of destroying it.
                    configDao.deletePeriods(scheduleId)
                    configDao.upsertPeriods(schemePeriods)
                    periods = schemePeriods
                }
            }
        }

        if (periods.isEmpty() && activeTimes.isNotEmpty()) {
            periods = activeTimes.map {
                PeriodEntity(it.periodIndex, it.startTime, it.endTime, scheduleId)
            }
            configDao.upsertPeriods(periods)
        }

        if (periods.isEmpty()) {
            periods = defaultPeriods(scheduleId)
            configDao.upsertPeriods(periods)
        }

        val storedConfig = configDao.getConfig(scheduleId)
        val repairedConfig = normalizeConfigForSchedule(
            configWithCountsFromPeriods(
                storedConfig ?: defaultConfig(scheduleId),
                periods
            ),
            scheduleId
        )
        if (storedConfig != repairedConfig) {
            configDao.upsertConfig(repairedConfig.copy(id = scheduleId))
        }

        if (schemes.isEmpty()) {
            replaceSchemesWithPeriods(scheduleId, repairedConfig, periods, "默认作息")
        } else if (activeScheme != null) {
            // Exactly one active scheme is part of the database invariant. Normalize
            // old/corrupt rows here so LIMIT 1 can never select a stale scheme.
            if (schemes.count { it.isActive } != 1 || !activeScheme.isActive) {
                periodSchemeDao.upsertSchemes(schemes.map { it.copy(isActive = it.id == activeScheme.id) })
            }

            val materializedTimes = periods.map {
                PeriodSchemeTimeEntity(activeScheme.id, it.periodIndex, it.startTime, it.endTime)
            }
            if (activeTimes != materializedTimes) {
                // The materialized table is what the home screen, notifications and
                // widgets were actually using before the multi-scheme upgrade. Keep
                // that visible user state authoritative and repair the active scheme.
                periodSchemeDao.deleteTimes(activeScheme.id)
                periodSchemeDao.upsertTimes(materializedTimes)
                activeTimes = materializedTimes
            }

            // A partially written inactive scheme must not later activate as an empty
            // timetable. Preserve its metadata but seed its missing timeline from the
            // currently materialized schedule instead of generating defaults.
            schemes.filter { it.id != activeScheme.id }.forEach { scheme ->
                if (periodSchemeDao.getTimes(scheme.id).isEmpty()) {
                    periodSchemeDao.upsertTimes(activeTimes.map { it.copy(schemeId = scheme.id) })
                }
            }
        }
    }

    private fun samePeriodTimeline(left: List<PeriodEntity>, right: List<PeriodEntity>): Boolean {
        if (left.size != right.size) return false
        return left.sortedBy { it.periodIndex }.zip(right.sortedBy { it.periodIndex }).all { (a, b) ->
            a.periodIndex == b.periodIndex && a.startTime == b.startTime && a.endTime == b.endTime
        }
    }

    private fun normalizeConfigForSchedule(config: ScheduleConfigEntity, scheduleId: Int): ScheduleConfigEntity {
        return config.copy(
            id = scheduleId,
            weekCardHeightDp = config.weekCardHeightDp?.coerceIn(38f, 80f)
        ).withDerivedScheduleTermState()
    }

    private fun configWithCountsFromPeriods(config: ScheduleConfigEntity, periods: List<PeriodEntity>): ScheduleConfigEntity {
        if (config.totalPeriodCount() == periods.size && periods.isNotEmpty()) return config
        val inferred = inferPeriodCounts(periods)
        return config.copy(
            morningPeriodCount = inferred.morning,
            noonPeriodCount = inferred.noon,
            afternoonPeriodCount = inferred.afternoon,
            eveningPeriodCount = inferred.evening
        )
    }

    private suspend fun replaceSchemesWithPeriods(
        scheduleId: Int,
        config: ScheduleConfigEntity,
        periods: List<PeriodEntity>,
        name: String
    ) {
        periodSchemeDao.deleteTimesForSchedule(scheduleId)
        periodSchemeDao.deleteSchemesForSchedule(scheduleId)
        val schemeId = periodSchemeDao.upsertScheme(
            PeriodSchemeEntity(
                scheduleId = scheduleId,
                name = name,
                isActive = true,
                classDurationMinutes = config.classDurationMinutes,
                breakDurationMinutes = config.breakDurationMinutes,
                morningStartTime = periods.firstOrNull()?.startTime ?: "08:00",
                noonStartTime = periods.firstOrNull { runCatching { java.time.LocalTime.parse(it.startTime).hour }.getOrDefault(0) in 12..13 }?.startTime ?: "12:00",
                afternoonStartTime = periods.firstOrNull { runCatching { java.time.LocalTime.parse(it.startTime).hour }.getOrDefault(0) in 14..17 }?.startTime ?: "14:00",
                eveningStartTime = periods.firstOrNull { runCatching { java.time.LocalTime.parse(it.startTime).hour }.getOrDefault(0) >= 18 }?.startTime ?: "19:00"
            )
        )
        periodSchemeDao.upsertTimes(periods.map { PeriodSchemeTimeEntity(schemeId, it.periodIndex, it.startTime, it.endTime) })
    }

    private suspend fun syncActiveSchemeTimes(scheduleId: Int, config: ScheduleConfigEntity, periods: List<PeriodEntity>) {
        val active = periodSchemeDao.getActiveScheme(scheduleId)
        if (active == null) {
            replaceSchemesWithPeriods(scheduleId, config, periods, "默认作息")
            return
        }
        periodSchemeDao.deleteTimes(active.id)
        periodSchemeDao.upsertTimes(periods.map { PeriodSchemeTimeEntity(active.id, it.periodIndex, it.startTime, it.endTime) })
    }

    private fun normalizePeriodsForSchedule(periods: List<PeriodEntity>, scheduleId: Int): List<PeriodEntity> {
        return periods
            .filter { it.periodIndex > 0 }
            .distinctBy { it.periodIndex }
            .sortedBy { it.periodIndex }
            .map { it.copy(scheduleId = scheduleId) }
    }

    private fun normalizeCoursesForSchedule(courses: List<CourseEntity>, scheduleId: Int): List<CourseEntity> {
        return courses.map {
            it.copy(
                weekday = it.weekday.coerceIn(1, 7),
                periods = it.periods.filter { period -> period > 0 }.distinct().sorted().ifEmpty { listOf(1) },
                weeks = it.weeks.filter { week -> week > 0 }.distinct().sorted().ifEmpty { listOf(1) },
                scheduleId = scheduleId
            )
        }
    }

    private fun remapCoursePeriodsByClockTime(
        sourceIndices: List<Int>,
        oldTimes: List<PeriodEntity>,
        newTimes: List<PeriodSchemeTimeEntity>
    ): List<Int> {
        if (newTimes.isEmpty()) return sourceIndices
        val oldByIndex = oldTimes.associateBy { it.periodIndex }
        val parsedNew = newTimes.mapNotNull { item ->
            val start = runCatching { java.time.LocalTime.parse(item.startTime) }.getOrNull() ?: return@mapNotNull null
            val end = runCatching { java.time.LocalTime.parse(item.endTime) }.getOrNull() ?: return@mapNotNull null
            Triple(item.periodIndex, start, end)
        }
        if (parsedNew.isEmpty()) return sourceIndices.map { it.coerceIn(1, newTimes.size) }.distinct().sorted()
        val mapped = sourceIndices.flatMap { sourceIndex ->
            val old = oldByIndex[sourceIndex]
            val oldStart = old?.startTime?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }
            val oldEnd = old?.endTime?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }
            if (oldStart == null || oldEnd == null) {
                listOf(parsedNew.minBy { kotlin.math.abs(it.first - sourceIndex) }.first)
            } else {
                val overlaps = parsedNew.filter { (_, start, end) -> start < oldEnd && end > oldStart }
                if (overlaps.isNotEmpty()) overlaps.map { it.first } else {
                    val oldMinute = oldStart.hour * 60 + oldStart.minute
                    listOf(parsedNew.minBy { (_, start, _) ->
                        kotlin.math.abs(start.hour * 60 + start.minute - oldMinute)
                    }.first)
                }
            }
        }
        return mapped.distinct().sorted().ifEmpty { listOf(parsedNew.first().first) }
    }

    private fun normalizeImportedCoursesForSchedule(courses: List<CourseEntity>, scheduleId: Int): List<CourseEntity> {
        return normalizeCoursesForSchedule(courses, scheduleId).map { it.copy(id = 0) }
    }

    private suspend fun mergeCompatibleCourseFragments(scheduleId: Int) {
        val courses = courseDao.getCourses(scheduleId)
            .map { normalizeCoursesForSchedule(listOf(it), scheduleId).single() }
        courses
            .groupBy { it.mergeKey() }
            .values
            .filter { it.size > 1 }
            .forEach { fragments ->
                val ordered = fragments.sortedBy { it.id }
                val keep = ordered.first()
                val mergedWeeks = ordered
                    .flatMap { it.weeks }
                    .filter { it > 0 }
                    .distinct()
                    .sorted()
                if (mergedWeeks.isNotEmpty() && keep.weeks != mergedWeeks) {
                    courseDao.updateCourse(keep.copy(weeks = mergedWeeks, scheduleId = scheduleId))
                }
                ordered.drop(1).forEach { courseDao.deleteCourse(it.id) }
            }
    }
}

private fun repairAgentTables(db: SQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS agent_daily_sessions (scheduleId INTEGER NOT NULL, date TEXT NOT NULL, dailyPackJson TEXT NOT NULL, providerId TEXT NOT NULL, model TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, generationStatus TEXT NOT NULL, lastError TEXT, PRIMARY KEY(scheduleId, date))")
    db.execSQL("CREATE TABLE IF NOT EXISTS agent_messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, scheduleId INTEGER NOT NULL, sessionDate TEXT NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL, createdAt INTEGER NOT NULL, status TEXT NOT NULL)")
}

private fun repairActiveScheduleProfiles(db: SQLiteDatabase) {
    if (!sqliteTableExists(db, "schedule_profiles")) return
    val profileCount = db.rawQuery("SELECT COUNT(*) FROM schedule_profiles", null).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }
    if (profileCount == 0) {
        db.execSQL("INSERT INTO schedule_profiles (id, name, isActive) VALUES (1, '\u9ED8\u8BA4\u8BFE\u8868', 1)")
        return
    }
    val activeCount = db.rawQuery("SELECT COUNT(*) FROM schedule_profiles WHERE isActive = 1", null).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }
    if (activeCount == 1) return
    val canRankByCourses = sqliteTableExists(db, "courses") && sqliteColumnExists(db, "courses", "scheduleId")
    val selectionSql = if (canRankByCourses) {
        """
        SELECT p.id
        FROM schedule_profiles p
        LEFT JOIN courses c ON c.scheduleId = p.id
        GROUP BY p.id
        ORDER BY p.isActive DESC, COUNT(c.id) DESC, p.id DESC
        LIMIT 1
        """.trimIndent()
    } else {
        "SELECT id FROM schedule_profiles ORDER BY isActive DESC, id DESC LIMIT 1"
    }
    val selectedId = db.rawQuery(selectionSql, null).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else null
    }
    selectedId?.let { id ->
        db.execSQL("UPDATE schedule_profiles SET isActive = CASE WHEN id = ? THEN 1 ELSE 0 END", arrayOf(id))
    }
}

private fun CourseEntity.hasSameOccurrenceSlot(other: CourseEntity): Boolean {
    return weekday == other.weekday &&
        periods.distinct().sorted() == other.periods.distinct().sorted() &&
        name.trim() == other.name.trim() &&
        teacher.orEmpty().trim() == other.teacher.orEmpty().trim() &&
        location.orEmpty().trim() == other.location.orEmpty().trim() &&
        note.orEmpty().trim() == other.note.orEmpty().trim() &&
        weekParity == other.weekParity &&
        scheduleId == other.scheduleId
}

private data class CourseMergeKey(
    val scheduleId: Int,
    val name: String,
    val teacher: String,
    val location: String,
    val note: String,
    val weekday: Int,
    val periods: List<Int>,
    val weekParity: WeekParity
)

private fun CourseEntity.mergeKey(): CourseMergeKey {
    return CourseMergeKey(
        scheduleId = scheduleId,
        name = name.trim(),
        teacher = teacher.orEmpty().trim(),
        location = location.orEmpty().trim(),
        note = note.orEmpty().trim(),
        weekday = weekday,
        periods = periods.distinct().sorted(),
        weekParity = weekParity
    )
}

private fun ScheduleConfigEntity.withGlobalSettingsFrom(global: ScheduleConfigEntity): ScheduleConfigEntity {
    return copy(
        followSystemDarkMode = global.followSystemDarkMode,
        darkMode = global.darkMode,
        dockAlignment = global.dockAlignment,
        defaultWallpaperStyle = global.defaultWallpaperStyle,
        defaultHomeMode = global.defaultHomeMode,
        liveUpdateActionsEnabled = global.liveUpdateActionsEnabled,
        homeChromeBlurScale = global.homeChromeBlurScale,
        homeChromeSamplingScale = global.homeChromeSamplingScale,
        hideFromRecents = global.hideFromRecents,
        autoCheckUpdates = global.autoCheckUpdates,
        notificationLeadMinutes = global.notificationLeadMinutes,
        notificationsEnabled = global.notificationsEnabled,
        notificationMode = global.notificationMode,
        liveUpdateChipTextMode = global.liveUpdateChipTextMode
    )
}

/**
 * Reapplies only the values edited by the General settings page onto the latest database row.
 * The page saves eagerly, so an older database emission must not roll a newer local tap back,
 * while notification values changed elsewhere must not be overwritten by the General page.
 */
internal fun ScheduleConfigEntity.withGeneralSettingsFrom(draft: ScheduleConfigEntity): ScheduleConfigEntity {
    return copy(
        followSystemDarkMode = draft.followSystemDarkMode,
        darkMode = draft.darkMode,
        dockAlignment = draft.dockAlignment,
        defaultWallpaperStyle = draft.defaultWallpaperStyle,
        defaultHomeMode = draft.defaultHomeMode,
        liveUpdateActionsEnabled = draft.liveUpdateActionsEnabled,
        hideFromRecents = draft.hideFromRecents,
        autoCheckUpdates = draft.autoCheckUpdates
    )
}

/** Applies only the value owned by the Liquid Glass detail page. */
internal fun ScheduleConfigEntity.withHomeChromeBlurScale(value: Float): ScheduleConfigEntity {
    return copy(homeChromeBlurScale = normalizedHomeChromeBlurScale(value))
}

internal fun ScheduleConfigEntity.withNotificationSettingsFrom(draft: ScheduleConfigEntity): ScheduleConfigEntity {
    return copy(
        notificationLeadMinutes = draft.notificationLeadMinutes,
        notificationsEnabled = draft.notificationsEnabled,
        notificationMode = draft.notificationMode,
        liveUpdateChipTextMode = draft.liveUpdateChipTextMode
    )
}

/**
 * Applies only fields that the caller actually changed. Settings are saved asynchronously, so
 * replacing the complete row with an older UI snapshot could otherwise roll back unrelated edits.
 * The receiver is always the latest database row and keeps its schedule id.
 */
internal fun ScheduleConfigEntity.withChangesFrom(
    original: ScheduleConfigEntity,
    updated: ScheduleConfigEntity
): ScheduleConfigEntity {
    fun <T> changed(oldValue: T, newValue: T, currentValue: T): T =
        if (oldValue != newValue) newValue else currentValue

    return copy(
        totalWeeks = changed(original.totalWeeks, updated.totalWeeks, totalWeeks),
        currentWeek = changed(original.currentWeek, updated.currentWeek, currentWeek),
        notificationLeadMinutes = changed(
            original.notificationLeadMinutes,
            updated.notificationLeadMinutes,
            notificationLeadMinutes
        ),
        termStartDate = changed(original.termStartDate, updated.termStartDate, termStartDate),
        autoCurrentWeek = changed(original.autoCurrentWeek, updated.autoCurrentWeek, autoCurrentWeek),
        termState = changed(original.termState, updated.termState, termState),
        notificationsEnabled = changed(
            original.notificationsEnabled,
            updated.notificationsEnabled,
            notificationsEnabled
        ),
        notificationMode = changed(original.notificationMode, updated.notificationMode, notificationMode),
        wallpaperUri = changed(original.wallpaperUri, updated.wallpaperUri, wallpaperUri),
        wallpaperBlur = changed(original.wallpaperBlur, updated.wallpaperBlur, wallpaperBlur),
        wallpaperBrightness = changed(
            original.wallpaperBrightness,
            updated.wallpaperBrightness,
            wallpaperBrightness
        ),
        wallpaperPortraitCenterX = changed(
            original.wallpaperPortraitCenterX,
            updated.wallpaperPortraitCenterX,
            wallpaperPortraitCenterX
        ),
        wallpaperPortraitCenterY = changed(
            original.wallpaperPortraitCenterY,
            updated.wallpaperPortraitCenterY,
            wallpaperPortraitCenterY
        ),
        wallpaperPortraitScale = changed(
            original.wallpaperPortraitScale,
            updated.wallpaperPortraitScale,
            wallpaperPortraitScale
        ),
        wallpaperLandscapeCenterX = changed(
            original.wallpaperLandscapeCenterX,
            updated.wallpaperLandscapeCenterX,
            wallpaperLandscapeCenterX
        ),
        wallpaperLandscapeCenterY = changed(
            original.wallpaperLandscapeCenterY,
            updated.wallpaperLandscapeCenterY,
            wallpaperLandscapeCenterY
        ),
        wallpaperLandscapeScale = changed(
            original.wallpaperLandscapeScale,
            updated.wallpaperLandscapeScale,
            wallpaperLandscapeScale
        ),
        wallpaperSourceWidth = changed(
            original.wallpaperSourceWidth,
            updated.wallpaperSourceWidth,
            wallpaperSourceWidth
        ),
        wallpaperSourceHeight = changed(
            original.wallpaperSourceHeight,
            updated.wallpaperSourceHeight,
            wallpaperSourceHeight
        ),
        cardColorArgb = changed(original.cardColorArgb, updated.cardColorArgb, cardColorArgb),
        cardAlpha = changed(original.cardAlpha, updated.cardAlpha, cardAlpha),
        courseCardBlur = changed(original.courseCardBlur, updated.courseCardBlur, courseCardBlur),
        courseCardGlassEnabled = changed(
            original.courseCardGlassEnabled,
            updated.courseCardGlassEnabled,
            courseCardGlassEnabled
        ),
        courseCardFontScale = changed(
            original.courseCardFontScale,
            updated.courseCardFontScale,
            courseCardFontScale
        ),
        alternateCardColorArgb = changed(
            original.alternateCardColorArgb,
            updated.alternateCardColorArgb,
            alternateCardColorArgb
        ),
        alternateCardAlpha = changed(
            original.alternateCardAlpha,
            updated.alternateCardAlpha,
            alternateCardAlpha
        ),
        alternateCourseCardBlur = changed(
            original.alternateCourseCardBlur,
            updated.alternateCourseCardBlur,
            alternateCourseCardBlur
        ),
        alternateCourseCardFontScale = changed(
            original.alternateCourseCardFontScale,
            updated.alternateCourseCardFontScale,
            alternateCourseCardFontScale
        ),
        weekCardHeightDp = changed(original.weekCardHeightDp, updated.weekCardHeightDp, weekCardHeightDp),
        homeTextLight = changed(original.homeTextLight, updated.homeTextLight, homeTextLight),
        homeChromeBlurScale = changed(
            original.homeChromeBlurScale,
            updated.homeChromeBlurScale,
            homeChromeBlurScale
        ),
        homeChromeSamplingScale = changed(
            original.homeChromeSamplingScale,
            updated.homeChromeSamplingScale,
            homeChromeSamplingScale
        ),
        followSystemDarkMode = changed(
            original.followSystemDarkMode,
            updated.followSystemDarkMode,
            followSystemDarkMode
        ),
        darkMode = changed(original.darkMode, updated.darkMode, darkMode),
        defaultWallpaperStyle = changed(
            original.defaultWallpaperStyle,
            updated.defaultWallpaperStyle,
            defaultWallpaperStyle
        ),
        hideEmptyWeekends = changed(
            original.hideEmptyWeekends,
            updated.hideEmptyWeekends,
            hideEmptyWeekends
        ),
        dockAlignment = changed(original.dockAlignment, updated.dockAlignment, dockAlignment),
        defaultHomeMode = changed(original.defaultHomeMode, updated.defaultHomeMode, defaultHomeMode),
        liveUpdateActionsEnabled = changed(
            original.liveUpdateActionsEnabled,
            updated.liveUpdateActionsEnabled,
            liveUpdateActionsEnabled
        ),
        liveUpdateChipTextMode = changed(
            original.liveUpdateChipTextMode,
            updated.liveUpdateChipTextMode,
            liveUpdateChipTextMode
        ),
        classDurationMinutes = changed(
            original.classDurationMinutes,
            updated.classDurationMinutes,
            classDurationMinutes
        ),
        breakDurationMinutes = changed(
            original.breakDurationMinutes,
            updated.breakDurationMinutes,
            breakDurationMinutes
        ),
        morningPeriodCount = changed(
            original.morningPeriodCount,
            updated.morningPeriodCount,
            morningPeriodCount
        ),
        noonPeriodCount = changed(original.noonPeriodCount, updated.noonPeriodCount, noonPeriodCount),
        afternoonPeriodCount = changed(
            original.afternoonPeriodCount,
            updated.afternoonPeriodCount,
            afternoonPeriodCount
        ),
        eveningPeriodCount = changed(
            original.eveningPeriodCount,
            updated.eveningPeriodCount,
            eveningPeriodCount
        ),
        hideFromRecents = changed(original.hideFromRecents, updated.hideFromRecents, hideFromRecents),
        autoCheckUpdates = changed(original.autoCheckUpdates, updated.autoCheckUpdates, autoCheckUpdates)
    )
}

/**
 * Replaces the complete set of fields owned by the personalization UI. Each queued save carries
 * a self-contained snapshot, so conflating rapid edits cannot lose a toggle or mistake a value
 * returning to its original state for "unchanged". Non-personalization settings stay database-led.
 */
internal fun ScheduleConfigEntity.withPersonalizationFrom(
    updated: ScheduleConfigEntity
): ScheduleConfigEntity = copy(
    wallpaperUri = updated.wallpaperUri,
    wallpaperBlur = updated.wallpaperBlur,
    wallpaperBrightness = updated.wallpaperBrightness,
    wallpaperPortraitCenterX = updated.wallpaperPortraitCenterX,
    wallpaperPortraitCenterY = updated.wallpaperPortraitCenterY,
    wallpaperPortraitScale = updated.wallpaperPortraitScale,
    wallpaperLandscapeCenterX = updated.wallpaperLandscapeCenterX,
    wallpaperLandscapeCenterY = updated.wallpaperLandscapeCenterY,
    wallpaperLandscapeScale = updated.wallpaperLandscapeScale,
    wallpaperSourceWidth = updated.wallpaperSourceWidth,
    wallpaperSourceHeight = updated.wallpaperSourceHeight,
    cardColorArgb = updated.cardColorArgb,
    cardAlpha = updated.cardAlpha,
    courseCardBlur = updated.courseCardBlur,
    courseCardGlassEnabled = updated.courseCardGlassEnabled,
    courseCardFontScale = updated.courseCardFontScale,
    alternateCardColorArgb = updated.alternateCardColorArgb,
    alternateCardAlpha = updated.alternateCardAlpha,
    alternateCourseCardBlur = updated.alternateCourseCardBlur,
    alternateCourseCardFontScale = updated.alternateCourseCardFontScale,
    weekCardHeightDp = updated.weekCardHeightDp,
    homeTextLight = updated.homeTextLight
)

internal fun ScheduleConfigEntity.switchCourseCardGlassMode(enabled: Boolean): ScheduleConfigEntity {
    if (enabled == courseCardGlassEnabled) return this
    return copy(
        cardColorArgb = alternateCardColorArgb,
        cardAlpha = alternateCardAlpha,
        courseCardBlur = alternateCourseCardBlur.coerceIn(0f, courseCardBlurMaximum(enabled)),
        courseCardGlassEnabled = enabled,
        courseCardFontScale = alternateCourseCardFontScale,
        alternateCardColorArgb = cardColorArgb,
        alternateCardAlpha = cardAlpha,
        alternateCourseCardBlur = courseCardBlur,
        alternateCourseCardFontScale = courseCardFontScale
    )
}

fun defaultConfig(id: Int = 1) = ScheduleConfigEntity(id = id, totalWeeks = 20, currentWeek = 1, notificationLeadMinutes = 10, termStartDate = null, autoCurrentWeek = false, notificationsEnabled = true, notificationMode = NotificationMode.STANDARD, wallpaperUri = null, wallpaperBlur = 0f, wallpaperBrightness = 1f, wallpaperPortraitCenterX = 0.5f, wallpaperPortraitCenterY = 0.5f, wallpaperPortraitScale = 1f, wallpaperLandscapeCenterX = 0.5f, wallpaperLandscapeCenterY = 0.5f, wallpaperLandscapeScale = 1f, wallpaperSourceWidth = null, wallpaperSourceHeight = null, cardColorArgb = 0xFFD6E9FF, cardAlpha = 1f, courseCardBlur = 18f, courseCardGlassEnabled = true, courseCardFontScale = 1f, weekCardHeightDp = null, homeTextLight = false, homeChromeBlurScale = DefaultHomeChromeBlurScale, homeChromeSamplingScale = DefaultHomeChromeSamplingScale, followSystemDarkMode = true, darkMode = false, defaultWallpaperStyle = DefaultWallpaperStyle.NONE, hideEmptyWeekends = false, dockAlignment = DockAlignment.LEFT, defaultHomeMode = HomeStartMode.WEEK, liveUpdateActionsEnabled = true, liveUpdateChipTextMode = LiveUpdateChipTextMode.LOCATION, classDurationMinutes = 45, breakDurationMinutes = 10, hideFromRecents = false, autoCheckUpdates = true)

fun defaultPeriods(scheduleId: Int = 1) = listOf(
    PeriodEntity(1, "08:00", "08:45", scheduleId), PeriodEntity(2, "08:55", "09:40", scheduleId),
    PeriodEntity(3, "10:00", "10:45", scheduleId), PeriodEntity(4, "10:55", "11:40", scheduleId),
    PeriodEntity(5, "14:00", "14:45", scheduleId), PeriodEntity(6, "14:55", "15:40", scheduleId),
    PeriodEntity(7, "16:00", "16:45", scheduleId), PeriodEntity(8, "16:55", "17:40", scheduleId),
    PeriodEntity(9, "19:00", "19:45", scheduleId), PeriodEntity(10, "19:55", "20:40", scheduleId),
    PeriodEntity(11, "20:50", "21:35", scheduleId), PeriodEntity(12, "21:45", "22:30", scheduleId)
)
