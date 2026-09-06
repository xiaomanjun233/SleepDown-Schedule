package com.xiaomanjun.sleepdownschedule.feature.backup

import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.widget.*
import com.xiaomanjun.sleepdownschedule.feature.agent.*

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSettingsCompletenessTest {
    @Test
    fun everyPortableRoomFieldAcrossAllNineTablesHasAProtocolCounterpart() {
        assertPortableFields(
            ScheduleProfileEntity::class.java,
            BackupSchedule::class.java,
            roomOnly = setOf("id"),
            protocolOnly = setOf(
                "id", "config", "courses", "periods", "periodSchemes",
                "agentDailySessions", "agentMessages"
            )
        )
        assertPortableFields(
            CourseEntity::class.java,
            BackupCourse::class.java,
            roomOnly = setOf("id", "scheduleId"),
            protocolOnly = setOf("id")
        )
        assertPortableFields(
            PeriodEntity::class.java,
            BackupPeriod::class.java,
            roomOnly = setOf("scheduleId")
        )
        assertPortableFields(
            PeriodSchemeEntity::class.java,
            BackupPeriodScheme::class.java,
            roomOnly = setOf("id", "scheduleId"),
            protocolOnly = setOf("id", "times")
        )
        assertPortableFields(
            PeriodSchemeTimeEntity::class.java,
            BackupPeriodSchemeTime::class.java,
            roomOnly = setOf("schemeId")
        )
        assertPortableFields(
            AgentDailySessionEntity::class.java,
            BackupAgentDailySession::class.java,
            roomOnly = setOf("scheduleId"),
            protocolOnly = setOf("id")
        )
        assertPortableFields(
            AgentMessageEntity::class.java,
            BackupAgentMessage::class.java,
            roomOnly = setOf("id", "scheduleId"),
            protocolOnly = setOf("id", "sessionId", "attachmentAssetIds")
        )
    }

    @Test
    fun everyScheduleConfigFieldHasAnExplicitBackupCounterpart() {
        val roomFields = instanceFieldNames(ScheduleConfigEntity::class.java) - setOf("id", "wallpaperUri")
        val protocolFields = instanceFieldNames(BackupScheduleConfig::class.java) - "wallpaperAssetId"

        assertEquals(
            "新增 ScheduleConfigEntity 字段时必须同步定义备份协议字段",
            roomFields,
            protocolFields
        )
    }

    @Test
    fun everyPortableWidgetAppearanceFieldHasAnExplicitBackupCounterpart() {
        val roomFields = instanceFieldNames(WidgetAppearanceEntity::class.java) -
            setOf("appWidgetId", "wallpaperUri", "updatedAt")
        val protocolFields = instanceFieldNames(BackupWidgetAppearance::class.java) -
            setOf("id", "scope", "wallpaperAssetId")

        assertEquals(
            "新增可迁移 widget 外观字段时必须同步定义备份协议字段",
            roomFields,
            protocolFields
        )
    }

    @Test
    fun nonDefaultScheduleSettingsSurviveExportCodecAndRestoreMapping() {
        val sourceWallpaperUri = "content://local/full-settings-wallpaper"
        val restoredWallpaperUri = "file:///restored/full-settings-wallpaper.webp"
        val source = defaultConfig(7).copy(
            totalWeeks = 37,
            currentWeek = 12,
            notificationLeadMinutes = 27,
            termStartDate = "2026-02-23",
            autoCurrentWeek = true,
            termState = ScheduleTermState.ACTIVE,
            notificationsEnabled = false,
            notificationMode = NotificationMode.LIVE_UPDATE,
            wallpaperUri = sourceWallpaperUri,
            wallpaperBlur = 7.25f,
            wallpaperBrightness = 0.62f,
            wallpaperPortraitCenterX = 0.31f,
            wallpaperPortraitCenterY = 0.67f,
            wallpaperPortraitScale = 2.15f,
            wallpaperLandscapeCenterX = 0.44f,
            wallpaperLandscapeCenterY = 0.58f,
            wallpaperLandscapeScale = 1.72f,
            wallpaperSourceWidth = 2560,
            wallpaperSourceHeight = 1600,
            cardColorArgb = 0xFF123456,
            cardAlpha = 0.72f,
            courseCardBlur = 12.5f,
            courseCardGlassEnabled = false,
            courseCardOutlineLightEnabled = false,
            courseCardRefractionStrength = 0.71f,
            courseCardGaussianBlurEnabled = false,
            courseCardFontScale = 1.18f,
            courseCardColorMode = CourseCardColorMode.GRADIENT,
            courseCardPalette = "FF123456,FF345678",
            alternateCardColorArgb = 0xFF654321,
            alternateCardAlpha = 0.43f,
            alternateCourseCardBlur = 8.5f,
            alternateCourseCardFontScale = 0.91f,
            alternateCourseCardColorMode = CourseCardColorMode.COLORFUL,
            alternateCourseCardPalette = "FF654321,FF765432",
            weekCardHeightDp = 63f,
            weekCardHeightScale = 1.24f,
            weekCardCornerProgress = 0.82f,
            homeTextLight = true,
            homeChromeBlurScale = 1.35f,
            homeChromeSamplingScale = 0.7f,
            followSystemDarkMode = false,
            darkMode = true,
            defaultWallpaperStyle = DefaultWallpaperStyle.KANBAN,
            hideEmptyWeekends = true,
            dockAlignment = DockAlignment.RIGHT,
            defaultHomeMode = HomeStartMode.DAY,
            liveUpdateActionsEnabled = false,
            liveUpdateChipTextMode = LiveUpdateChipTextMode.COUNTDOWN,
            classDurationMinutes = 50,
            breakDurationMinutes = 15,
            morningPeriodCount = 5,
            noonPeriodCount = 1,
            afternoonPeriodCount = 4,
            eveningPeriodCount = 2,
            hideFromRecents = true,
            autoCheckUpdates = false
        )
        val preferences = BackupPreferences(
            preferencesVersion = BackupFormatV1.PREFERENCES_VERSION,
            aiImportHistoryRetentionDays = 90
        )
        val archive = BackupExportMapper.toArchive(
            metadata = metadata(),
            snapshot = BackupRoomSnapshot(
                schedules = listOf(ScheduleProfileEntity(7, "完整设置", true)),
                configs = listOf(source)
            ),
            preferences = preferences,
            assetInputs = listOf(
                BackupExportAssetInput(
                    sourceKey = "schedule-wallpaper:7:$sourceWallpaperUri",
                    bytes = byteArrayOf(1, 2, 3, 4),
                    mediaType = "image/webp"
                )
            )
        )

        val decoded = BackupCodec.decode(BackupCodec.encode(archive))
        val decodedSchedule = decoded.data.schedules.single()
        val wallpaperAssetId = requireNotNull(decodedSchedule.config.wallpaperAssetId)
        val plan = BackupImportPlanBuilder.build(decoded, "restore-complete-settings")
        val rows = BackupRoomRestoreMapper.map(
            archive = decoded,
            plan = plan,
            assetUrisById = mapOf(wallpaperAssetId to restoredWallpaperUri)
        )
        val restored = rows.configs.single()
        val expected = source.copy(
            id = plan.scheduleIds.getValue(decodedSchedule.id),
            wallpaperUri = restoredWallpaperUri
        )

        assertEquals(expected, restored)
        assertEquals(90, decoded.preferences.aiImportHistoryRetentionDays)
        assertTrue(decoded.assets.single { it.assetId == wallpaperAssetId }.bytes!!.isNotEmpty())
    }

    private fun instanceFieldNames(type: Class<*>): Set<String> = type.declaredFields
        .asSequence()
        .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
        .map { it.name }
        .toSet()

    private fun assertPortableFields(
        roomType: Class<*>,
        protocolType: Class<*>,
        roomOnly: Set<String> = emptySet(),
        protocolOnly: Set<String> = emptySet()
    ) {
        assertEquals(
            "${roomType.simpleName} 的可迁移字段必须全部进入 ${protocolType.simpleName}",
            instanceFieldNames(roomType) - roomOnly,
            instanceFieldNames(protocolType) - protocolOnly
        )
    }

    private fun metadata() = BackupSourceMetadata(
        createdAt = "2026-08-10T12:00:00Z",
        sourceAppVersionName = "1.1.4",
        sourceVersionCode = 24,
        sourcePackageName = "com.example.courseschedule",
        sourceDatabaseVersion = APP_DATABASE_VERSION,
        devicePlatform = "Android"
    )
}
