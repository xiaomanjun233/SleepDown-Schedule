package com.xiaomanjun.sleepdownschedule.feature.backup


import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.widget.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPreviewReportTest {
    @Test
    fun previewCountsAllRowsAndSurfacesMissingAssetsAndNonMigratableState() {
        val archive = BackupCodec.decode(
            BackupCodec.encode(
                BackupExportMapper.toArchive(
                    metadata = metadata(),
                    snapshot = BackupRoomSnapshot(
                        schedules = listOf(ScheduleProfileEntity(7, "主课表", true)),
                        configs = listOf(defaultConfig(7).copy(wallpaperUri = "content://lost")),
                        periods = listOf(PeriodEntity(1, "08:00", "08:45", 7)),
                        courses = listOf(
                            CourseEntity(
                                id = 11,
                                name = "课程",
                                teacher = null,
                                location = null,
                                weekday = 1,
                                periods = listOf(1),
                                weeks = listOf(1),
                                weekParity = WeekParity.ALL,
                                note = null,
                                scheduleId = 7
                            )
                        ),
                        widgetAppearances = listOf(WidgetAppearanceEntity("COURSES_LARGE", 42))
                    ),
                    preferences = BackupPreferences(BackupFormatV1.PREFERENCES_VERSION)
                )
            )
        )
        val plan = BackupImportPlanBuilder.build(
            archive,
            operationId = "preview-report",
            existing = BackupImportTargetSnapshot(scheduleIds = setOf(1))
        )
        val report = BackupPreviewReportBuilder.build(archive, plan)

        assertEquals(1, report.scheduleCount)
        assertEquals(1, report.courseCount)
        assertEquals(1, report.periodCount)
        assertEquals(1, report.widgetAppearanceCount)
        assertEquals(1, report.missingAssetCount)
        assertTrue(report.missingAssets.single().purpose == BackupAssetPurpose.SCHEDULE_WALLPAPER)
        assertTrue(report.warnings.any { it.code == BackupImportWarningCode.MISSING_ASSET })
        assertTrue(report.warnings.any { it.code == BackupImportWarningCode.WIDGET_INSTANCE_REQUIRES_REBUILD })
        assertTrue(report.nonMigratableItems.any { it.contains("API key", ignoreCase = true) })
        assertTrue(report.nonMigratableItems.any { it.contains("小组件") && it.contains("重新添加") })
        assertTrue(report.requiresExplicitReplaceConfirmation)
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
