package com.example.courseschedule

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Stable, Room-independent identifiers used inside a .sleepdown archive.
 *
 * These identifiers intentionally do not preserve Room auto-increment values. They only need to
 * be stable for the lifetime of one archive and its import plan.
 */
object BackupStableId {
    const val SCHEDULE_PREFIX = "schedule"
    const val COURSE_PREFIX = "course"
    const val SCHEME_PREFIX = "scheme"
    const val SESSION_PREFIX = "session"
    const val MESSAGE_PREFIX = "message"
    const val WIDGET_PREFIX = "widget"
    const val ASSET_PREFIX = "asset"
    const val HISTORY_PREFIX = "history"

    private val uuidPattern = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")

    fun new(prefix: String): String {
        require(prefix.matches(Regex("[a-z][a-z0-9_-]{0,31}"))) { "非法备份 ID 前缀" }
        return "${prefix}_${UUID.randomUUID()}"
    }

    fun isValid(value: String, prefix: String? = null): Boolean {
        if (value.length > 128) return false
        val separator = value.indexOf('_')
        if (separator <= 0 || separator == value.lastIndex) return false
        val actualPrefix = value.substring(0, separator)
        if (prefix != null && actualPrefix != prefix) return false
        return actualPrefix.matches(Regex("[a-z][a-z0-9_-]{0,31}")) &&
            value.substring(separator + 1).matches(uuidPattern)
    }

    fun requireValid(value: String, prefix: String? = null): String {
        require(isValid(value, prefix)) { "非法备份 stable ID: $value" }
        return value
    }
}

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
    const val DEFAULT_AI_IMPORT_HISTORY_RETENTION_DAYS = 30
    val AI_IMPORT_HISTORY_RETENTION_OPTIONS = listOf(7, 30, 90, 0)

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
    val widgetAppearances: List<BackupWidgetAppearance> = emptyList()
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
    val agentMessages: List<BackupAgentMessage> = emptyList()
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
    val courseCardFontScale: Float,
    val alternateCardColorArgb: Long = 0xFFD6E9FF,
    val alternateCardAlpha: Float = 1f,
    val alternateCourseCardBlur: Float = 18f,
    val alternateCourseCardFontScale: Float = 1f,
    val weekCardHeightDp: Float?,
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
    val homeChromeSamplingScale: Float = DefaultHomeChromeSamplingScale
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
    val note: String?
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
    val times: List<BackupPeriodSchemeTime> = emptyList()
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
    val appIcon: BackupAppIconPreferences? = null,
    val dayAgent: BackupDayAgentPreferences? = null,
    val aiImport: BackupAiImportPreferences? = null,
    val aiImportHistoryRetentionDays: Int = BackupFormatV1.DEFAULT_AI_IMPORT_HISTORY_RETENTION_DAYS,
    val aiImportHistory: List<BackupAiImportHistoryEntry> = emptyList()
)

@Serializable
data class BackupAppIconPreferences(
    val mode: String,
    val followsSystemDarkMode: Boolean,
    val darkTheme: Boolean
)

@Serializable
data class BackupDayAgentPreferences(
    val hasDecision: Boolean,
    val enabled: Boolean,
    val dailyAiEnabled: Boolean,
    val weatherEnabled: Boolean,
    val memoryEnabled: Boolean,
    val memory: String,
    val memoryTurnDay: String?,
    val memoryTurnCount: Int,
    val memoryLastAgentUpdateDay: String?,
    val appliedActionsBySchedule: Map<String, List<String>> = emptyMap()
)

@Serializable
data class BackupAiImportPreferences(
    val selectedProviderId: String,
    val managedFreeOfferDecision: String? = null,
    val providers: List<BackupAiProvider> = emptyList()
)

@Serializable
data class BackupAiProvider(
    val id: String,
    val displayName: String,
    val providerType: String,
    val baseUrl: String,
    val model: String,
    val authType: String,
    val supportsImageInput: Boolean,
    val supportsPdfFileInput: Boolean,
    val supportsJsonSchema: Boolean,
    val supportsJsonMode: Boolean,
    val supportsFileUpload: Boolean,
    val supportsResponses: Boolean,
    val supportsVision: Boolean,
    val supportsPdfDirect: Boolean,
    val endpointStyle: String,
    val structuredOutputMode: String,
    val inputMode: String,
    val availableModels: List<String> = emptyList(),
    val reasoningEffort: String
)

@Serializable
data class BackupAiImportHistoryEntry(
    val id: String,
    val createdAt: Long,
    val title: String,
    val prompt: String,
    val sourceSummary: String,
    val payload: String,
    val contextAssetId: String? = null
)
