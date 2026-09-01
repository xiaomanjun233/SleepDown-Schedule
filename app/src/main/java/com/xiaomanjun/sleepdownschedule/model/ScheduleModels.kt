package com.xiaomanjun.sleepdownschedule.model

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class WeekParity { ALL, ODD, EVEN }
enum class NotificationMode { STANDARD, LIVE_UPDATE }
enum class DefaultWallpaperStyle { KANBAN, NONE }
enum class DockAlignment { LEFT, CENTER, RIGHT }
enum class HomeStartMode { DAY, TWO_DAY, WEEK }
enum class LiveUpdateChipTextMode { LOCATION, COUNTDOWN, SHORT, NORMAL }
enum class PeriodSchemeMode { MANUAL, AUTO_MATCH }
enum class ScheduleTermState { MANUAL, UPCOMING, ACTIVE, ENDED, INVALID }
enum class CourseCardColorMode { SOLID, GRADIENT, COLORFUL }

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
    val customStartTime: String? = null,
    val customEndTime: String? = null,
    val customColorArgb: Long? = null,
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
    @ColumnInfo(defaultValue = "'SOLID'") val courseCardColorMode: CourseCardColorMode = CourseCardColorMode.SOLID,
    @ColumnInfo(defaultValue = "''") val courseCardPalette: String = "",
    @ColumnInfo(defaultValue = "4293516543") val alternateCardColorArgb: Long = 0xFFD6E9FF,
    @ColumnInfo(defaultValue = "1") val alternateCardAlpha: Float = 1f,
    @ColumnInfo(defaultValue = "18") val alternateCourseCardBlur: Float = 18f,
    @ColumnInfo(defaultValue = "1") val alternateCourseCardFontScale: Float = 1f,
    @ColumnInfo(defaultValue = "'SOLID'") val alternateCourseCardColorMode: CourseCardColorMode = CourseCardColorMode.SOLID,
    @ColumnInfo(defaultValue = "''") val alternateCourseCardPalette: String = "",
    val weekCardHeightDp: Float? = null,
    @ColumnInfo(defaultValue = "1") val weekCardHeightScale: Float = 1f,
    @ColumnInfo(defaultValue = "0.5") val weekCardCornerProgress: Float = 0.5f,
    val homeTextLight: Boolean = false,
    @ColumnInfo(defaultValue = "1") val homeChromeBlurScale: Float = DefaultHomeChromeBlurScale,
    @ColumnInfo(defaultValue = "1") val homeChromeSamplingScale: Float = DefaultHomeChromeSamplingScale,
    val followSystemDarkMode: Boolean = true,
    val darkMode: Boolean = false,
    val defaultWallpaperStyle: DefaultWallpaperStyle = DefaultWallpaperStyle.NONE,
    val hideEmptyWeekends: Boolean = false,
    val dockAlignment: DockAlignment = DockAlignment.CENTER,
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

fun defaultConfig(id: Int = 1) = ScheduleConfigEntity(id = id, totalWeeks = 20, currentWeek = 1, notificationLeadMinutes = 10, termStartDate = null, autoCurrentWeek = false, notificationsEnabled = true, notificationMode = NotificationMode.STANDARD, wallpaperUri = null, wallpaperBlur = 0f, wallpaperBrightness = 1f, wallpaperPortraitCenterX = 0.5f, wallpaperPortraitCenterY = 0.5f, wallpaperPortraitScale = 1f, wallpaperLandscapeCenterX = 0.5f, wallpaperLandscapeCenterY = 0.5f, wallpaperLandscapeScale = 1f, wallpaperSourceWidth = null, wallpaperSourceHeight = null, cardColorArgb = 0xFFD6E9FF, cardAlpha = 1f, courseCardBlur = 18f, courseCardGlassEnabled = true, courseCardFontScale = 1f, courseCardColorMode = CourseCardColorMode.SOLID, courseCardPalette = "", weekCardHeightDp = null, weekCardHeightScale = 1f, weekCardCornerProgress = 0.5f, homeTextLight = false, homeChromeBlurScale = DefaultHomeChromeBlurScale, homeChromeSamplingScale = DefaultHomeChromeSamplingScale, followSystemDarkMode = true, darkMode = false, defaultWallpaperStyle = DefaultWallpaperStyle.NONE, hideEmptyWeekends = false, dockAlignment = DockAlignment.CENTER, defaultHomeMode = HomeStartMode.WEEK, liveUpdateActionsEnabled = true, liveUpdateChipTextMode = LiveUpdateChipTextMode.LOCATION, classDurationMinutes = 45, breakDurationMinutes = 10, hideFromRecents = false, autoCheckUpdates = true)

fun defaultPeriods(scheduleId: Int = 1) = listOf(
    PeriodEntity(1, "08:00", "08:45", scheduleId), PeriodEntity(2, "08:55", "09:40", scheduleId),
    PeriodEntity(3, "10:00", "10:45", scheduleId), PeriodEntity(4, "10:55", "11:40", scheduleId),
    PeriodEntity(5, "14:00", "14:45", scheduleId), PeriodEntity(6, "14:55", "15:40", scheduleId),
    PeriodEntity(7, "16:00", "16:45", scheduleId), PeriodEntity(8, "16:55", "17:40", scheduleId),
    PeriodEntity(9, "19:00", "19:45", scheduleId), PeriodEntity(10, "19:55", "20:40", scheduleId),
    PeriodEntity(11, "20:50", "21:35", scheduleId), PeriodEntity(12, "21:45", "22:30", scheduleId)
)
