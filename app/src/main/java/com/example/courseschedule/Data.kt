package com.example.courseschedule

import android.app.Application
import android.database.sqlite.SQLiteDatabase
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

@Entity(tableName = "courses")
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
data class ScheduleProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val isActive: Boolean = false
)

@Entity(tableName = "schedule_config")
data class ScheduleConfigEntity(
    @PrimaryKey val id: Int = 1,
    val totalWeeks: Int,
    val currentWeek: Int,
    val notificationLeadMinutes: Int,
    val termStartDate: String? = null,
    val autoCurrentWeek: Boolean = false,
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
    val weekCardHeightDp: Float? = null,
    val homeTextLight: Boolean = false,
    val followSystemDarkMode: Boolean = true,
    val darkMode: Boolean = false,
    val defaultWallpaperStyle: DefaultWallpaperStyle = DefaultWallpaperStyle.KANBAN,
    val hideEmptyWeekends: Boolean = false,
    val dockAlignment: DockAlignment = DockAlignment.LEFT,
    val defaultHomeMode: HomeStartMode = HomeStartMode.WEEK,
    val liveUpdateActionsEnabled: Boolean = true,
    val liveUpdateChipTextMode: LiveUpdateChipTextMode = LiveUpdateChipTextMode.LOCATION,
    val classDurationMinutes: Int = 45,
    val breakDurationMinutes: Int = 10,
    val hideFromRecents: Boolean = false
)

@Entity(tableName = "periods", primaryKeys = ["scheduleId", "periodIndex"])
data class PeriodEntity(
    val periodIndex: Int,
    val startTime: String,
    val endTime: String,
    val scheduleId: Int = 1
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
    fun stringToParity(value: String): WeekParity = WeekParity.valueOf(value)

    @TypeConverter
    fun notificationModeToString(value: NotificationMode): String = value.name

    @TypeConverter
    fun stringToNotificationMode(value: String): NotificationMode = NotificationMode.valueOf(value)

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

    @Query("SELECT * FROM courses")
    fun observeAllCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE scheduleId = (SELECT id FROM schedule_profiles WHERE isActive = 1 LIMIT 1)")
    suspend fun getCourses(): List<CourseEntity>

    @Query("SELECT * FROM courses")
    suspend fun getAllCourses(): List<CourseEntity>

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

    @Query("SELECT * FROM schedule_config")
    fun observeAllConfigs(): Flow<List<ScheduleConfigEntity>>

    @Query("SELECT * FROM schedule_config")
    suspend fun getAllConfigs(): List<ScheduleConfigEntity>

    @Query("SELECT * FROM periods WHERE scheduleId = (SELECT id FROM schedule_profiles WHERE isActive = 1 LIMIT 1) ORDER BY periodIndex")
    fun observePeriods(): Flow<List<PeriodEntity>>

    @Query("SELECT * FROM periods ORDER BY scheduleId, periodIndex")
    fun observeAllPeriods(): Flow<List<PeriodEntity>>

    @Query("SELECT * FROM schedule_config WHERE id = (SELECT id FROM schedule_profiles WHERE isActive = 1 LIMIT 1)")
    suspend fun getConfig(): ScheduleConfigEntity?

    @Query("SELECT * FROM schedule_config WHERE id = :scheduleId")
    suspend fun getConfig(scheduleId: Int): ScheduleConfigEntity?

    @Query("SELECT * FROM periods WHERE scheduleId = (SELECT id FROM schedule_profiles WHERE isActive = 1 LIMIT 1) ORDER BY periodIndex")
    suspend fun getPeriods(): List<PeriodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(config: ScheduleConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeriods(periods: List<PeriodEntity>)

    @Query("DELETE FROM periods WHERE scheduleId = (SELECT id FROM schedule_profiles WHERE isActive = 1 LIMIT 1)")
    suspend fun deletePeriods()

    @Query("DELETE FROM periods WHERE scheduleId = :scheduleId")
    suspend fun deletePeriods(scheduleId: Int)

    @Query("DELETE FROM schedule_config WHERE id = :scheduleId")
    suspend fun deleteConfig(scheduleId: Int)
}

@Dao
interface ScheduleProfileDao {
    @Query("SELECT * FROM schedule_profiles ORDER BY id")
    fun observeProfiles(): Flow<List<ScheduleProfileEntity>>

    @Query("SELECT * FROM schedule_profiles ORDER BY id")
    suspend fun getProfiles(): List<ScheduleProfileEntity>

    @Query("SELECT * FROM schedule_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfile(): ScheduleProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: ScheduleProfileEntity): Long

    @Query("UPDATE schedule_profiles SET name = :name WHERE id = :profileId")
    suspend fun renameProfile(profileId: Int, name: String)

    @Query("UPDATE schedule_profiles SET isActive = CASE WHEN id = :profileId THEN 1 ELSE 0 END")
    suspend fun activateProfile(profileId: Int)

    @Query("DELETE FROM schedule_profiles WHERE id = :profileId")
    suspend fun deleteProfile(profileId: Int)
}

@Database(entities = [CourseEntity::class, ScheduleProfileEntity::class, ScheduleConfigEntity::class, PeriodEntity::class], version = 24, exportSchema = false)
@TypeConverters(ScheduleConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun configDao(): ConfigDao
    abstract fun scheduleProfileDao(): ScheduleProfileDao
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
    db.execSQL("INSERT OR IGNORE INTO schedule_profiles (id, name, isActive) VALUES (1, '\u9ED8\u8BA4\u8BFE\u8868', 1)")
    if (!db.hasColumn("courses", "scheduleId")) {
        db.execSQL("ALTER TABLE courses ADD COLUMN scheduleId INTEGER NOT NULL DEFAULT 1")
    }
    repairPeriodsForMultiSchedule(db)
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

class CourseScheduleApp : Application() {
    val database: AppDatabase by lazy {
        repairDatabaseFileBeforeRoomOpen(getDatabasePath("course_schedule.db"))
        Room.databaseBuilder(this, AppDatabase::class.java, "course_schedule.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24)
            .build()
    }
    val repository: ScheduleRepository by lazy { ScheduleRepository(database) }
}

private fun repairDatabaseFileBeforeRoomOpen(path: File) {
    if (!path.exists()) return
    runCatching {
        SQLiteDatabase.openDatabase(path.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            repairSQLiteDatabase(db)
        }
    }
}

private fun repairSQLiteDatabase(db: SQLiteDatabase) {
    db.beginTransaction()
    try {
        db.execSQL("CREATE TABLE IF NOT EXISTS schedule_profiles (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, isActive INTEGER NOT NULL)")
        db.execSQL("INSERT OR IGNORE INTO schedule_profiles (id, name, isActive) VALUES (1, '\u9ED8\u8BA4\u8BFE\u8868', 1)")
        if (!sqliteColumnExists(db, "courses", "scheduleId")) {
            db.execSQL("ALTER TABLE courses ADD COLUMN scheduleId INTEGER NOT NULL DEFAULT 1")
        }
        repairScheduleConfigTable(db)
        repairPeriodsTable(db)
        db.execSQL("DROP TABLE IF EXISTS room_master_table")
        db.setVersion(24)
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
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
    ensureSqliteColumn(db, "schedule_config", "weekCardHeightDp", "REAL")
    ensureSqliteColumn(db, "schedule_config", "homeTextLight", "INTEGER NOT NULL DEFAULT 0")
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
    db.execSQL(scheduleConfigCreateSql("schedule_config_room_fix"))
    db.execSQL(
        """
        INSERT OR REPLACE INTO schedule_config_room_fix (
            id, totalWeeks, currentWeek, notificationLeadMinutes, termStartDate, autoCurrentWeek,
            notificationsEnabled, notificationMode, wallpaperUri, wallpaperBlur, wallpaperBrightness,
            wallpaperPortraitCenterX, wallpaperPortraitCenterY, wallpaperPortraitScale,
            wallpaperLandscapeCenterX, wallpaperLandscapeCenterY, wallpaperLandscapeScale,
            wallpaperSourceWidth, wallpaperSourceHeight,
            cardColorArgb, cardAlpha, courseCardBlur, courseCardGlassEnabled, courseCardFontScale, weekCardHeightDp,
            homeTextLight, followSystemDarkMode, darkMode, defaultWallpaperStyle, hideEmptyWeekends,
            dockAlignment, defaultHomeMode, liveUpdateActionsEnabled, liveUpdateChipTextMode,
            classDurationMinutes, breakDurationMinutes, hideFromRecents
        )
        SELECT
            id, totalWeeks, currentWeek, notificationLeadMinutes, termStartDate, autoCurrentWeek,
            notificationsEnabled, notificationMode, wallpaperUri, wallpaperBlur, wallpaperBrightness,
            wallpaperPortraitCenterX, wallpaperPortraitCenterY, wallpaperPortraitScale,
            wallpaperLandscapeCenterX, wallpaperLandscapeCenterY, wallpaperLandscapeScale,
            wallpaperSourceWidth, wallpaperSourceHeight,
            cardColorArgb, cardAlpha, courseCardBlur, courseCardGlassEnabled, courseCardFontScale, weekCardHeightDp,
            homeTextLight, followSystemDarkMode, darkMode, defaultWallpaperStyle, hideEmptyWeekends,
            dockAlignment, defaultHomeMode, liveUpdateActionsEnabled, liveUpdateChipTextMode,
            classDurationMinutes, breakDurationMinutes, hideFromRecents
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
        weekCardHeightDp REAL,
        homeTextLight INTEGER NOT NULL,
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
        hideFromRecents INTEGER NOT NULL
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

data class AppState(
    val courses: List<CourseEntity> = emptyList(),
    val allCourses: List<CourseEntity> = emptyList(),
    val schedules: List<ScheduleProfileEntity> = listOf(ScheduleProfileEntity(id = 1, name = "\u9ED8\u8BA4\u8BFE\u8868", isActive = true)),
    val allConfigs: List<ScheduleConfigEntity> = emptyList(),
    val allPeriods: List<PeriodEntity> = emptyList(),
    val config: ScheduleConfigEntity = defaultConfig(),
    val periods: List<PeriodEntity> = defaultPeriods(),
    val loaded: Boolean = false
)

data class ImportDraft(
    val config: ScheduleConfigEntity,
    val periods: List<PeriodEntity>,
    val courses: List<CourseEntity>
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

    private val multiScheduleState = combine(
        courseDao.observeAllCourses(),
        profileDao.observeProfiles(),
        configDao.observeAllConfigs(),
        configDao.observeAllPeriods()
    ) { allCourses, schedules, allConfigs, allPeriods ->
        val profiles = schedules.ifEmpty {
            listOf(ScheduleProfileEntity(id = 1, name = "\u9ED8\u8BA4\u8BFE\u8868", isActive = true))
        }
        val activeId = profiles.firstOrNull { it.isActive }?.id ?: profiles.first().id
        MultiScheduleSnapshot(
            courses = allCourses.filter { it.scheduleId == activeId },
            allCourses = allCourses,
            schedules = profiles,
            allConfigs = allConfigs,
            allPeriods = allPeriods
        )
    }

    val state = multiScheduleState.map { snapshot ->
        val activeId = snapshot.schedules.firstOrNull { it.isActive }?.id ?: 1
        val config = snapshot.allConfigs.firstOrNull { it.id == activeId } ?: defaultConfig(activeId)
        val periods = snapshot.allPeriods.filter { it.scheduleId == activeId }.ifEmpty { defaultPeriods(activeId) }
        AppState(
            courses = snapshot.courses,
            allCourses = snapshot.allCourses,
            schedules = snapshot.schedules,
            allConfigs = snapshot.allConfigs,
            allPeriods = snapshot.allPeriods,
            config = config,
            periods = periods,
            loaded = true
        )
    }

    suspend fun ensureDefaults() {
        if (profileDao.getProfiles().isEmpty()) {
            profileDao.upsertProfile(ScheduleProfileEntity(id = 1, name = "\u9ED8\u8BA4\u8BFE\u8868", isActive = true))
        }
        if (profileDao.getActiveProfile() == null) {
            profileDao.getProfiles().firstOrNull()?.let { profileDao.activateProfile(it.id) }
        }
        val scheduleId = activeScheduleId()
        if (configDao.getConfig() == null) configDao.upsertConfig(defaultConfig(scheduleId))
        if (configDao.getPeriods().isEmpty()) configDao.upsertPeriods(defaultPeriods(scheduleId))
    }

    suspend fun addCourse(course: CourseEntity) {
        val scheduleId = activeScheduleId()
        courseDao.insertCourse(normalizeCoursesForSchedule(listOf(course.copy(id = 0)), scheduleId).single())
    }

    suspend fun updateCourse(course: CourseEntity) {
        val scheduleId = activeScheduleId()
        courseDao.updateCourse(normalizeCoursesForSchedule(listOf(course), scheduleId).single())
    }

    suspend fun updateCourseSingleWeek(original: CourseEntity, edited: CourseEntity, targetWeek: Int) {
        database.withTransaction {
            val scheduleId = activeScheduleId()
            val remainingWeeks = original.weeks.filter { it != targetWeek }
            if (remainingWeeks.isEmpty()) {
                courseDao.deleteCourse(original.id)
            } else {
                courseDao.updateCourse(original.copy(weeks = remainingWeeks, scheduleId = scheduleId))
            }
            val singleWeekCourse = normalizeCoursesForSchedule(listOf(edited.copy(id = 0, weeks = listOf(targetWeek))), scheduleId).single()
            courseDao.getCourses()
                .filter { it.id != original.id && it.weeks.distinct() == listOf(targetWeek) && it.hasSameOccurrenceSlot(singleWeekCourse) }
                .forEach { courseDao.deleteCourse(it.id) }
            courseDao.insertCourse(singleWeekCourse)
        }
    }

    suspend fun deleteCourseSingleWeek(course: CourseEntity, targetWeek: Int) {
        val remainingWeeks = course.weeks.filter { it != targetWeek }
        if (remainingWeeks.isEmpty()) {
            courseDao.deleteCourse(course.id)
        } else {
            courseDao.updateCourse(course.copy(weeks = remainingWeeks))
        }
    }

    suspend fun updateRelatedCourses(original: CourseEntity, edited: CourseEntity) {
        val originalName = original.name.trim()
        val related = courseDao.getCourses().filter {
            it.id == original.id || it.name.trim() == originalName
        }.map {
            it.copy(
                name = edited.name,
                teacher = edited.teacher,
                location = edited.location,
                note = edited.note
            )
        }
        if (related.isNotEmpty()) courseDao.insertCourses(normalizeCoursesForSchedule(related, activeScheduleId()))
    }

    suspend fun deleteCourse(course: CourseEntity) {
        courseDao.deleteCourse(course.id)
    }

    suspend fun importDraft(draft: ImportDraft, createNewSchedule: Boolean = false) {
        database.withTransaction {
            val oldActiveId = activeScheduleId()
            val globalConfig = configDao.getConfig(oldActiveId) ?: defaultConfig(oldActiveId)
            val scheduleId = if (createNewSchedule) {
                profileDao.upsertProfile(ScheduleProfileEntity(name = "\u5BFC\u5165\u8BFE\u8868", isActive = false)).toInt().also {
                    profileDao.activateProfile(it)
                }
            } else {
                oldActiveId
            }
            configDao.upsertConfig(normalizeConfigForSchedule(draft.config.withGlobalSettingsFrom(globalConfig), scheduleId))
            configDao.deletePeriods(scheduleId)
            configDao.upsertPeriods(normalizePeriodsForSchedule(draft.periods, scheduleId))
            courseDao.deleteBySchedule(scheduleId)
            courseDao.insertCourses(normalizeImportedCoursesForSchedule(draft.courses, scheduleId))
        }
    }

    suspend fun saveConfig(config: ScheduleConfigEntity, periods: List<PeriodEntity>) {
        val scheduleId = activeScheduleId()
        database.withTransaction {
            configDao.upsertConfig(normalizeConfigForSchedule(config, scheduleId))
            configDao.deletePeriods(scheduleId)
            configDao.upsertPeriods(normalizePeriodsForSchedule(periods, scheduleId))
        }
    }

    suspend fun saveConfigForSchedule(scheduleId: Int, config: ScheduleConfigEntity, periods: List<PeriodEntity>) {
        database.withTransaction {
            configDao.upsertConfig(normalizeConfigForSchedule(config, scheduleId))
            configDao.deletePeriods(scheduleId)
            configDao.upsertPeriods(normalizePeriodsForSchedule(periods, scheduleId))
        }
    }

    suspend fun saveConfigOnly(config: ScheduleConfigEntity) {
        configDao.upsertConfig(config.copy(id = activeScheduleId()))
    }

    suspend fun saveGlobalSettings(config: ScheduleConfigEntity) {
        val activeId = activeScheduleId()
        database.withTransaction {
            val existing = configDao.getAllConfigs()
            val targetIds = (profileDao.getProfiles().map { it.id } + existing.map { it.id } + activeId).distinct()
            targetIds.forEach { id ->
                val base = existing.firstOrNull { it.id == id } ?: defaultConfig(id)
                configDao.upsertConfig(
                    base.copy(
                        id = id,
                        followSystemDarkMode = config.followSystemDarkMode,
                        darkMode = config.darkMode,
                        dockAlignment = config.dockAlignment,
                        defaultWallpaperStyle = config.defaultWallpaperStyle,
                        defaultHomeMode = config.defaultHomeMode,
                        liveUpdateActionsEnabled = config.liveUpdateActionsEnabled,
                        hideFromRecents = config.hideFromRecents,
                        notificationLeadMinutes = config.notificationLeadMinutes,
                        notificationsEnabled = config.notificationsEnabled,
                        notificationMode = config.notificationMode,
                        liveUpdateChipTextMode = config.liveUpdateChipTextMode
                    )
                )
            }
        }
    }

    suspend fun createSchedule(name: String): Int {
        return database.withTransaction {
            val globalConfig = configDao.getConfig() ?: defaultConfig(activeScheduleId())
            val id = profileDao.upsertProfile(ScheduleProfileEntity(name = name, isActive = false)).toInt()
            configDao.upsertConfig(defaultConfig(id).withGlobalSettingsFrom(globalConfig))
            configDao.upsertPeriods(defaultPeriods(id))
            id
        }
    }

    suspend fun activateSchedule(scheduleId: Int) {
        database.withTransaction {
            val oldActiveId = activeScheduleId()
            val globalConfig = configDao.getConfig(oldActiveId) ?: defaultConfig(oldActiveId)
            val targetConfig = configDao.getConfig(scheduleId) ?: defaultConfig(scheduleId)
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
            configDao.deletePeriods(scheduleId)
            configDao.deleteConfig(scheduleId)
            val remaining = profiles.filterNot { it.id == scheduleId }
            if (profiles.any { it.id == scheduleId && it.isActive }) {
                remaining.firstOrNull()?.let { profileDao.activateProfile(it.id) }
            }
        }
    }

    suspend fun snapshot(): AppState {
        return AppState(
            courses = courseDao.getCourses(),
            allCourses = courseDao.getAllCourses(),
            schedules = profileDao.getProfiles().ifEmpty { listOf(ScheduleProfileEntity(id = 1, name = "\u9ED8\u8BA4\u8BFE\u8868", isActive = true)) },
            allConfigs = emptyList(),
            allPeriods = emptyList(),
            config = configDao.getConfig() ?: defaultConfig(),
            periods = configDao.getPeriods().ifEmpty { defaultPeriods() },
            loaded = true
        )
    }

    private suspend fun activeScheduleId(): Int {
        return profileDao.getActiveProfile()?.id ?: 1
    }

    private fun normalizeConfigForSchedule(config: ScheduleConfigEntity, scheduleId: Int): ScheduleConfigEntity {
        return config.copy(id = scheduleId)
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

    private fun normalizeImportedCoursesForSchedule(courses: List<CourseEntity>, scheduleId: Int): List<CourseEntity> {
        return normalizeCoursesForSchedule(courses, scheduleId).map { it.copy(id = 0) }
    }
}

private fun CourseEntity.hasSameOccurrenceSlot(other: CourseEntity): Boolean {
    return weekday == other.weekday &&
        periods.distinct().sorted() == other.periods.distinct().sorted() &&
        name.trim() == other.name.trim()
}

private fun ScheduleConfigEntity.withGlobalSettingsFrom(global: ScheduleConfigEntity): ScheduleConfigEntity {
    return copy(
        followSystemDarkMode = global.followSystemDarkMode,
        darkMode = global.darkMode,
        dockAlignment = global.dockAlignment,
        defaultWallpaperStyle = global.defaultWallpaperStyle,
        defaultHomeMode = global.defaultHomeMode,
        liveUpdateActionsEnabled = global.liveUpdateActionsEnabled,
        hideFromRecents = global.hideFromRecents,
        notificationLeadMinutes = global.notificationLeadMinutes,
        notificationsEnabled = global.notificationsEnabled,
        notificationMode = global.notificationMode,
        liveUpdateChipTextMode = global.liveUpdateChipTextMode
    )
}

fun defaultConfig(id: Int = 1) = ScheduleConfigEntity(id = id, totalWeeks = 20, currentWeek = 1, notificationLeadMinutes = 10, termStartDate = null, autoCurrentWeek = false, notificationsEnabled = true, notificationMode = NotificationMode.STANDARD, wallpaperUri = null, wallpaperBlur = 0f, wallpaperBrightness = 1f, wallpaperPortraitCenterX = 0.5f, wallpaperPortraitCenterY = 0.5f, wallpaperPortraitScale = 1f, wallpaperLandscapeCenterX = 0.5f, wallpaperLandscapeCenterY = 0.5f, wallpaperLandscapeScale = 1f, wallpaperSourceWidth = null, wallpaperSourceHeight = null, cardColorArgb = 0xFFD6E9FF, cardAlpha = 1f, courseCardBlur = 18f, courseCardGlassEnabled = true, courseCardFontScale = 1f, weekCardHeightDp = null, homeTextLight = false, followSystemDarkMode = true, darkMode = false, defaultWallpaperStyle = DefaultWallpaperStyle.KANBAN, hideEmptyWeekends = false, dockAlignment = DockAlignment.LEFT, defaultHomeMode = HomeStartMode.WEEK, liveUpdateActionsEnabled = true, liveUpdateChipTextMode = LiveUpdateChipTextMode.LOCATION, classDurationMinutes = 45, breakDurationMinutes = 10, hideFromRecents = false)

fun defaultPeriods(scheduleId: Int = 1) = listOf(
    PeriodEntity(1, "08:00", "08:45", scheduleId), PeriodEntity(2, "08:55", "09:40", scheduleId),
    PeriodEntity(3, "10:00", "10:45", scheduleId), PeriodEntity(4, "10:55", "11:40", scheduleId),
    PeriodEntity(5, "14:00", "14:45", scheduleId), PeriodEntity(6, "14:55", "15:40", scheduleId),
    PeriodEntity(7, "16:00", "16:45", scheduleId), PeriodEntity(8, "16:55", "17:40", scheduleId),
    PeriodEntity(9, "19:00", "19:45", scheduleId), PeriodEntity(10, "19:55", "20:40", scheduleId),
    PeriodEntity(11, "20:50", "21:35", scheduleId), PeriodEntity(12, "21:45", "22:30", scheduleId)
)
