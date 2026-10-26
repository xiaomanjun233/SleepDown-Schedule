package com.xiaomanjun.sleepdownschedule.feature.backup

import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.model.AppIconPreferencesSnapshot
import kotlinx.serialization.Serializable

// Keep existing backup source imports while shared snapshot values live in model.
typealias BackupStableId = com.xiaomanjun.sleepdownschedule.model.backup.BackupStableId
typealias BackupDayAgentPreferences = com.xiaomanjun.sleepdownschedule.model.backup.BackupDayAgentPreferences
typealias BackupAiImportPreferences = com.xiaomanjun.sleepdownschedule.model.backup.BackupAiImportPreferences
typealias BackupAiProvider = com.xiaomanjun.sleepdownschedule.model.backup.BackupAiProvider
typealias BackupAiImportHistoryEntry = com.xiaomanjun.sleepdownschedule.model.backup.BackupAiImportHistoryEntry

object BackupAssetCategory {
    const val SCHEDULES = "schedules"
    const val WALLPAPERS = "wallpapers"
    const val WIDGETS = "widgets"
    const val OTHER = "other"

    val all = setOf(SCHEDULES, WALLPAPERS, WIDGETS, OTHER)
}

object BackupAssetPurpose {
    const val SCHEDULE_WALLPAPER = "schedule_wallpaper"
    const val WIDGET_WALLPAPER = "widget_wallpaper"
    const val AGENT_ATTACHMENT = "agent_attachment"
    const val AI_IMPORT_HISTORY_CONTEXT = "ai_import_history_context"
    const val AI_IMPORT_SCREENSHOT = "ai_import_screenshot"
    const val OTHER = "other"

    val all = setOf(
        SCHEDULE_WALLPAPER,
        WIDGET_WALLPAPER,
        AGENT_ATTACHMENT,
        AI_IMPORT_HISTORY_CONTEXT,
        AI_IMPORT_SCREENSHOT,
        OTHER
    )
}

object BackupFormatV1 {
    const val FORMAT_VERSION = 1
    const val PRODUCT = "SleepDown Backup"
    const val DATA_VERSION = 1
    const val PREFERENCES_VERSION = 1
    const val CHECKSUM_VERSION = 1
    const val CHECKSUM_ALGORITHM = "SHA-256"
    const val DEFAULT_AI_IMPORT_HISTORY_RETENTION_DAYS = com.xiaomanjun.sleepdownschedule.model.backup.DefaultAiImportHistoryRetentionDays
    val AI_IMPORT_HISTORY_RETENTION_OPTIONS = com.xiaomanjun.sleepdownschedule.model.backup.AiImportHistoryRetentionOptions

    const val MANIFEST_ENTRY = "manifest.json"
    const val DATA_ENTRY = "data.json"
    const val PREFERENCES_ENTRY = "preferences.json"
    const val CHECKSUMS_ENTRY = "checksums.json"

    val fixedEntries = setOf(MANIFEST_ENTRY, DATA_ENTRY, PREFERENCES_ENTRY, CHECKSUMS_ENTRY)
}

@Serializable
data class BackupSourceMetadata(
    val createdAt: String,
    val sourceAppVersionName: String,
    val sourceVersionCode: Int,
    val sourcePackageName: String,
    val sourceDatabaseVersion: Int,
    val devicePlatform: String
)

@Serializable
data class BackupManifest(
    val formatVersion: Int,
    val product: String,
    val createdAt: String,
    val sourceAppVersionName: String,
    val sourceVersionCode: Int,
    val sourcePackageName: String,
    val sourceDatabaseVersion: Int,
    val devicePlatform: String,
    val assetCount: Int,
    val missingAssetCount: Int,
    val assets: List<BackupAssetManifestEntry> = emptyList()
)

@Serializable
data class BackupAssetManifestEntry(
    val assetId: String,
    val category: String,
    val purpose: String,
    val relativePath: String,
    val mediaType: String,
    val byteLength: Long,
    val sha256: String,
    val present: Boolean,
    val optional: Boolean,
    val ownerId: String? = null,
    val missingReason: String? = null
)

/** Asset bytes are deliberately kept outside the serializable DTO and are written as ZIP entries. */
data class BackupAsset(
    val assetId: String,
    val category: String,
    val purpose: String,
    val mediaType: String,
    val bytes: ByteArray?,
    val optional: Boolean = false,
    val ownerId: String? = null,
    val missingReason: String? = null
)

data class BackupArchive(
    val metadata: BackupSourceMetadata,
    val data: BackupData,
    val preferences: BackupPreferences,
    val assets: List<BackupAsset> = emptyList()
)

data class DecodedBackupArchive(
    val manifest: BackupManifest,
    val data: BackupData,
    val preferences: BackupPreferences,
    val checksums: BackupChecksums,
    val assets: List<BackupAsset>
)

@Serializable
data class BackupChecksums(
    val checksumVersion: Int,
    val algorithm: String,
    val entries: Map<String, String>
)

@Serializable
data class BackupData(
    val dataVersion: Int,
    val schedules: List<BackupSchedule> = emptyList(),
    val widgetAppearances: List<BackupWidgetAppearance> = emptyList(),
    /** Null identifies older archives whose schemes belonged to individual schedules. */
    val sharedPeriodSchemes: List<BackupPeriodScheme>? = null
)

@Serializable
data class BackupSchedule(
    val id: String,
    val name: String,
    val isActive: Boolean,
    val config: BackupScheduleConfig,
    val courses: List<BackupCourse> = emptyList(),
    val periods: List<BackupPeriod> = emptyList(),
    val periodSchemes: List<BackupPeriodScheme> = emptyList(),
    val agentDailySessions: List<BackupAgentDailySession> = emptyList(),
    val agentMessages: List<BackupAgentMessage> = emptyList(),
    val activePeriodSchemeId: String? = null
)

/** Protocol DTO for ScheduleConfigEntity. It intentionally has no database id or URI field. */
@Serializable
data class BackupScheduleConfig(
    val totalWeeks: Int,
    val currentWeek: Int,
    val notificationLeadMinutes: Int,
    val termStartDate: String?,
    val autoCurrentWeek: Boolean,
    val termState: String,
    val notificationsEnabled: Boolean,
    val notificationMode: String,
    val wallpaperAssetId: String?,
    val wallpaperBlur: Float,
    val wallpaperBrightness: Float,
    val wallpaperPortraitCenterX: Float?,
    val wallpaperPortraitCenterY: Float?,
    val wallpaperPortraitScale: Float?,
    val wallpaperLandscapeCenterX: Float?,
    val wallpaperLandscapeCenterY: Float?,
    val wallpaperLandscapeScale: Float?,
    val wallpaperSourceWidth: Int?,
    val wallpaperSourceHeight: Int?,
    val cardColorArgb: Long,
    val cardAlpha: Float,
    val courseCardBlur: Float,
    val courseCardGlassEnabled: Boolean,
    val courseCardOutlineLightEnabled: Boolean = true,
    val courseCardRefractionStrength: Float = 0.5f,
    val courseCardGaussianBlurEnabled: Boolean = true,
    val courseCardColoredTextEnabled: Boolean = false,
    val courseCardFontScale: Float,
    val courseCardColorMode: String = "SOLID",
    val courseCardPalette: String = "",
    val alternateCardColorArgb: Long = 0xFFD6E9FF,
    val alternateCardAlpha: Float = 1f,
    val alternateCourseCardBlur: Float = 18f,
    val alternateCourseCardFontScale: Float = 1f,
    val alternateCourseCardColorMode: String = "SOLID",
    val alternateCourseCardPalette: String = "",
    val weekCardHeightDp: Float?,
    val weekCardHeightScale: Float = 1f,
    val weekCardCornerProgress: Float = 0.5f,
    val weekCardShowLocation: Boolean = true,
    val weekCardShowTeacher: Boolean = true,
    val weekCardTextAlignment: String = "CENTER",
    val weekCardContentLayout: String = "CURRENT",
    val homeTextLight: Boolean,
    val followSystemDarkMode: Boolean,
    val darkMode: Boolean,
    val defaultWallpaperStyle: String,
    val hideEmptyWeekends: Boolean,
    val dockAlignment: String,
    val defaultHomeMode: String,
    val liveUpdateActionsEnabled: Boolean,
    val liveUpdateChipTextMode: String,
    val classDurationMinutes: Int,
    val breakDurationMinutes: Int,
    val morningPeriodCount: Int,
    val noonPeriodCount: Int,
    val afternoonPeriodCount: Int,
    val eveningPeriodCount: Int,
    val hideFromRecents: Boolean,
    val autoCheckUpdates: Boolean,
    val homeChromeBlurScale: Float = DefaultHomeChromeBlurScale,
    val homeChromeSamplingScale: Float = DefaultHomeChromeSamplingScale,
    val scheduleAdjustmentsJson: String = "",
    val showNonCurrentWeekCourses: Boolean = false,
    val weekStartsOnSunday: Boolean = false,
    val periodAlignmentMode: String = "INDEX",
    val timeSlotCoursesJson: String = ""
)

@Serializable
data class BackupCourse(
    val id: String,
    val name: String,
    val teacher: String?,
    val location: String?,
    val weekday: Int,
    val periods: List<Int>,
    val weeks: List<Int>,
    val weekParity: String,
    val note: String?,
    val customStartTime: String? = null,
    val customEndTime: String? = null,
    val customColorArgb: Long? = null,
    val customPeriodTimes: String? = null,
    val originalPeriodTimes: String? = null
)

@Serializable
data class BackupPeriod(
    val periodIndex: Int,
    val startTime: String,
    val endTime: String
)

@Serializable
data class BackupPeriodScheme(
    val id: String,
    val name: String,
    val mode: String,
    val isActive: Boolean,
    val classDurationMinutes: Int,
    val breakDurationMinutes: Int,
    val morningStartTime: String,
    val noonStartTime: String,
    val afternoonStartTime: String,
    val eveningStartTime: String,
    val specialBreaksJson: String,
    val overridesJson: String,
    val times: List<BackupPeriodSchemeTime> = emptyList(),
    val publicId: String = "",
    val sourceScheduleName: String = "",
    val morningPeriodCount: Int = 0,
    val noonPeriodCount: Int = 0,
    val afternoonPeriodCount: Int = 0,
    val eveningPeriodCount: Int = 0
)

@Serializable
data class BackupPeriodSchemeTime(
    val periodIndex: Int,
    val startTime: String,
    val endTime: String
)

@Serializable
data class BackupAgentDailySession(
    val id: String,
    val date: String,
    val dailyPackJson: String,
    val providerId: String,
    val model: String,
    val createdAt: Long,
    val updatedAt: Long,
    val generationStatus: String,
    val lastError: String?
)

@Serializable
data class BackupAgentMessage(
    val id: String,
    val sessionId: String?,
    val sessionDate: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val status: String,
    val attachmentAssetIds: List<String> = emptyList()
)

/** Widget appearance DTO intentionally omits Android's real appWidgetId. */
@Serializable
data class BackupWidgetAppearance(
    val id: String,
    val variant: String,
    val scope: String,
    val enabled: Boolean,
    val wallpaperAssetId: String?,
    val centerX: Float,
    val centerY: Float,
    val scale: Float,
    val sourceWidth: Int?,
    val sourceHeight: Int?,
    val blurDp: Float,
    val brightness: Float
)

@Serializable
data class BackupPreferences(
    val preferencesVersion: Int,
    val materialLevel: String? = null,
    val appIcon: AppIconPreferencesSnapshot? = null,
    val dayAgent: BackupDayAgentPreferences? = null,
    val aiImport: BackupAiImportPreferences? = null,
    val aiImportHistoryRetentionDays: Int = BackupFormatV1.DEFAULT_AI_IMPORT_HISTORY_RETENTION_DAYS,
    val aiImportHistory: List<BackupAiImportHistoryEntry> = emptyList(),
    val savedPeriodSchemes: List<com.xiaomanjun.sleepdownschedule.domain.schedule.SavedPeriodScheme>? = null,
    val courseQuietSettings: com.xiaomanjun.sleepdownschedule.domain.schedule.CourseQuietSettings? = null
)
