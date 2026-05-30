package com.example.courseschedule

import android.app.Application
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    val note: String?
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
    val cardColorArgb: Long = 0xFFD6E9FF,
    val cardAlpha: Float = 1f,
    val courseCardBlur: Float = 18f,
    val courseCardGlassEnabled: Boolean = true,
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

@Entity(tableName = "periods")
data class PeriodEntity(
    @PrimaryKey val periodIndex: Int,
    val startTime: String,
    val endTime: String
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
    @Query("SELECT * FROM courses")
    fun observeCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses")
    suspend fun getCourses(): List<CourseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: CourseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateCourse(course: CourseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<CourseEntity>): List<Long>

    @Query("DELETE FROM courses WHERE id = :courseId")
    suspend fun deleteCourse(courseId: Long)

    @Query("DELETE FROM courses")
    suspend fun deleteAll()
}

@Dao
interface ConfigDao {
    @Query("SELECT * FROM schedule_config WHERE id = 1")
    fun observeConfig(): Flow<ScheduleConfigEntity?>

    @Query("SELECT * FROM periods ORDER BY periodIndex")
    fun observePeriods(): Flow<List<PeriodEntity>>

    @Query("SELECT * FROM schedule_config WHERE id = 1")
    suspend fun getConfig(): ScheduleConfigEntity?

    @Query("SELECT * FROM periods ORDER BY periodIndex")
    suspend fun getPeriods(): List<PeriodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(config: ScheduleConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeriods(periods: List<PeriodEntity>)

    @Query("DELETE FROM periods")
    suspend fun deletePeriods()
}

@Database(entities = [CourseEntity::class, ScheduleConfigEntity::class, PeriodEntity::class], version = 20, exportSchema = false)
@TypeConverters(ScheduleConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun configDao(): ConfigDao
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

class CourseScheduleApp : Application() {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "course_schedule.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20)
            .build()
    }
    val repository: ScheduleRepository by lazy { ScheduleRepository(database) }
}

data class AppState(
    val courses: List<CourseEntity> = emptyList(),
    val config: ScheduleConfigEntity = defaultConfig(),
    val periods: List<PeriodEntity> = defaultPeriods(),
    val loaded: Boolean = false
)

data class ImportDraft(
    val config: ScheduleConfigEntity,
    val periods: List<PeriodEntity>,
    val courses: List<CourseEntity>
)

class ScheduleRepository(private val database: AppDatabase) {
    private val courseDao = database.courseDao()
    private val configDao = database.configDao()

    val state = combine(courseDao.observeCourses(), configDao.observeConfig(), configDao.observePeriods()) { courses, config, periods ->
        AppState(courses = courses, config = config ?: defaultConfig(), periods = periods.ifEmpty { defaultPeriods() }, loaded = true)
    }

    suspend fun ensureDefaults() {
        if (configDao.getConfig() == null) configDao.upsertConfig(defaultConfig())
        if (configDao.getPeriods().isEmpty()) configDao.upsertPeriods(defaultPeriods())
    }

    suspend fun addCourse(course: CourseEntity) {
        courseDao.insertCourse(course)
    }

    suspend fun updateCourse(course: CourseEntity) {
        courseDao.updateCourse(course)
    }

    suspend fun updateCourseSingleWeek(original: CourseEntity, edited: CourseEntity, targetWeek: Int) {
        database.withTransaction {
            val remainingWeeks = original.weeks.filter { it != targetWeek }
            if (remainingWeeks.isEmpty()) {
                courseDao.deleteCourse(original.id)
            } else {
                courseDao.updateCourse(original.copy(weeks = remainingWeeks))
            }
            courseDao.insertCourse(edited.copy(id = 0, weeks = listOf(targetWeek)))
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
        if (related.isNotEmpty()) courseDao.insertCourses(related)
    }

    suspend fun deleteCourse(course: CourseEntity) {
        courseDao.deleteCourse(course.id)
    }

    suspend fun importDraft(draft: ImportDraft) {
        database.withTransaction {
            configDao.upsertConfig(draft.config)
            configDao.deletePeriods()
            configDao.upsertPeriods(draft.periods)
            courseDao.deleteAll()
            courseDao.insertCourses(draft.courses)
        }
    }

    suspend fun saveConfig(config: ScheduleConfigEntity, periods: List<PeriodEntity>) {
        database.withTransaction {
            configDao.upsertConfig(config)
            configDao.deletePeriods()
            configDao.upsertPeriods(periods)
        }
    }

    suspend fun saveConfigOnly(config: ScheduleConfigEntity) {
        configDao.upsertConfig(config)
    }

    suspend fun snapshot(): AppState {
        return AppState(
            courses = courseDao.getCourses(),
            config = configDao.getConfig() ?: defaultConfig(),
            periods = configDao.getPeriods().ifEmpty { defaultPeriods() },
            loaded = true
        )
    }
}

fun defaultConfig() = ScheduleConfigEntity(totalWeeks = 20, currentWeek = 1, notificationLeadMinutes = 10, termStartDate = null, autoCurrentWeek = false, notificationsEnabled = true, notificationMode = NotificationMode.STANDARD, wallpaperUri = null, wallpaperBlur = 0f, wallpaperBrightness = 1f, cardColorArgb = 0xFFD6E9FF, cardAlpha = 1f, courseCardBlur = 18f, courseCardGlassEnabled = true, weekCardHeightDp = null, homeTextLight = false, followSystemDarkMode = true, darkMode = false, defaultWallpaperStyle = DefaultWallpaperStyle.KANBAN, hideEmptyWeekends = false, dockAlignment = DockAlignment.LEFT, defaultHomeMode = HomeStartMode.WEEK, liveUpdateActionsEnabled = true, liveUpdateChipTextMode = LiveUpdateChipTextMode.LOCATION, classDurationMinutes = 45, breakDurationMinutes = 10, hideFromRecents = false)

fun defaultPeriods() = listOf(
    PeriodEntity(1, "08:00", "08:45"), PeriodEntity(2, "08:55", "09:40"),
    PeriodEntity(3, "10:00", "10:45"), PeriodEntity(4, "10:55", "11:40"),
    PeriodEntity(5, "14:00", "14:45"), PeriodEntity(6, "14:55", "15:40"),
    PeriodEntity(7, "16:00", "16:45"), PeriodEntity(8, "16:55", "17:40"),
    PeriodEntity(9, "19:00", "19:45"), PeriodEntity(10, "19:55", "20:40"),
    PeriodEntity(11, "20:50", "21:35"), PeriodEntity(12, "21:45", "22:30")
)
