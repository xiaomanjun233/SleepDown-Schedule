package com.example.courseschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupExportImportPlanTest {
    @Test
    fun exportMapperReadsEveryRoomRelationAndStripsPrivateAgentAttachmentMarker() {
        val wallpaperUri = "content://local/schedule-wallpaper"
        val widgetWallpaperUri = "content://local/widget-wallpaper"
        val snapshot = BackupRoomSnapshot(
            schedules = listOf(
                ScheduleProfileEntity(id = 7, name = "主课表", isActive = true),
                ScheduleProfileEntity(id = 42, name = "备用课表", isActive = false)
            ),
            configs = listOf(
                defaultConfig(7).copy(wallpaperUri = wallpaperUri),
                defaultConfig(42)
            ),
            periods = listOf(
                PeriodEntity(1, "08:00", "08:45", 7)
            ),
            courses = listOf(
                CourseEntity(
                    id = 101,
                    name = "数据结构",
                    teacher = "老师",
                    location = "A101",
                    weekday = 2,
                    periods = listOf(1),
                    weeks = listOf(1, 2),
                    weekParity = WeekParity.ALL,
                    note = "保留",
                    scheduleId = 7
                )
            ),
            periodSchemes = listOf(
                PeriodSchemeEntity(
                    id = 501,
                    scheduleId = 7,
                    name = "默认作息",
                    isActive = true
                )
            ),
            periodSchemeTimes = listOf(
                PeriodSchemeTimeEntity(501, 1, "08:00", "08:45")
            ),
            agentDailySessions = listOf(
                AgentDailySessionEntity(
                    scheduleId = 7,
                    date = "2026-08-10",
                    dailyPackJson = "{}",
                    providerId = "none",
                    model = "",
                    createdAt = 1L,
                    updatedAt = 2L,
                    generationStatus = "READY"
                )
            ),
            agentMessages = listOf(
                AgentMessageEntity(
                    id = 601,
                    scheduleId = 7,
                    sessionDate = "2026-08-10",
                    role = "user",
                    content = "[[agent_image:attachment.jpg]]\n请看图",
                    createdAt = 3L,
                    status = "READY"
                )
            ),
            widgetAppearances = listOf(
                WidgetAppearanceEntity(
                    variant = "COURSES_LARGE",
                    appWidgetId = WidgetDefaultAppearanceId,
                    enabled = true,
                    wallpaperUri = widgetWallpaperUri
                ),
                WidgetAppearanceEntity(
                    variant = "COURSES_LARGE",
                    appWidgetId = 99,
                    enabled = true
                )
            )
        )
        val idMapping = BackupExportMapper.createIdMapping(snapshot)
        val archive = BackupExportMapper.toArchive(
            metadata = metadata(),
            snapshot = snapshot,
            preferences = BackupPreferences(
                preferencesVersion = BackupFormatV1.PREFERENCES_VERSION,
                dayAgent = BackupDayAgentPreferences(
                    hasDecision = false,
                    enabled = false,
                    dailyAiEnabled = true,
                    weatherEnabled = true,
                    memoryEnabled = false,
                    memory = "",
                    memoryTurnDay = null,
                    memoryTurnCount = 0,
                    memoryLastAgentUpdateDay = null,
                    appliedActionsBySchedule = mapOf(idMapping.scheduleIds.getValue(7) to listOf("action"))
                )
            ),
            assetInputs = listOf(
                BackupExportAssetInput(
                    sourceKey = "schedule-wallpaper:7:$wallpaperUri",
                    bytes = byteArrayOf(1, 2, 3),
                    mediaType = "image/png"
                ),
                BackupExportAssetInput(
                    sourceKey = "agent-attachment:7:601:attachment.jpg",
                    bytes = byteArrayOf(4, 5, 6),
                    mediaType = "image/jpeg"
                ),
                BackupExportAssetInput(
                    sourceKey = "widget-wallpaper:COURSES_LARGE:0:$widgetWallpaperUri",
                    bytes = byteArrayOf(7, 8, 9),
                    mediaType = "image/webp"
                )
            ),
            idMapping = idMapping
        )

        val encoded = BackupCodec.encode(archive)
        val decoded = BackupCodec.decode(encoded)
        val schedule = decoded.data.schedules.first { it.name == "主课表" }
        val message = schedule.agentMessages.single()
        val defaultWidget = decoded.data.widgetAppearances.first { it.scope == "default" }
        val instanceWidget = decoded.data.widgetAppearances.first { it.scope == "instance" }

        assertNotEquals("7", schedule.id)
        assertTrue(BackupStableId.isValid(schedule.id, BackupStableId.SCHEDULE_PREFIX))
        assertTrue(BackupStableId.isValid(schedule.courses.single().id, BackupStableId.COURSE_PREFIX))
        assertEquals("请看图", message.content)
        assertEquals(schedule.agentDailySessions.single().id, message.sessionId)
        assertEquals(1, message.attachmentAssetIds.size)
        assertNotNull(schedule.config.wallpaperAssetId)
        assertNotNull(defaultWidget.wallpaperAssetId)
        assertEquals("instance", instanceWidget.scope)
        assertEquals(3, decoded.assets.count { it.bytes != null })
        assertEquals(
            listOf("action"),
            decoded.preferences.dayAgent?.appliedActionsBySchedule?.get(schedule.id)
        )
    }

    @Test
    fun importPlanAllocatesCollisionFreeIdsAndReportsNonRestorableWidgetInstances() {
        val archive = BackupCodec.decode(
            BackupCodec.encode(
                BackupExportMapper.toArchive(
                    metadata = metadata(),
                    snapshot = BackupRoomSnapshot(
                        schedules = listOf(ScheduleProfileEntity(7, "主课表", true)),
                        configs = listOf(defaultConfig(7)),
                        periods = listOf(PeriodEntity(1, "08:00", "08:45", 7)),
                        courses = listOf(
                            CourseEntity(
                                id = 101,
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
                        widgetAppearances = listOf(
                            WidgetAppearanceEntity("COURSES_LARGE", 0),
                            WidgetAppearanceEntity("COURSES_LARGE", 99)
                        )
                    ),
                    preferences = BackupPreferences(BackupFormatV1.PREFERENCES_VERSION)
                )
            )
        )

        val plan = BackupImportPlanBuilder.build(
            archive = archive,
            operationId = "restore-20260810-01",
            existing = BackupImportTargetSnapshot(
                scheduleIds = setOf(7),
                courseIds = setOf(100),
                messageIds = setOf(200),
                hasPreferences = true
            )
        )

        val scheduleTarget = plan.scheduleIds.getValue(archive.data.schedules.single().id)
        val courseTarget = plan.courseIds.getValue(archive.data.schedules.single().courses.single().id)
        assertEquals(8, scheduleTarget)
        assertEquals(101L, courseTarget)
        assertTrue(plan.requiresExplicitReplaceConfirmation)
        assertEquals(2, plan.widgetTargets.size)
        assertEquals(0, plan.widgetTargets.values.first { it.appWidgetId == 0 }.appWidgetId)
        assertTrue(plan.widgetTargets.values.any { it.appWidgetId == null })
        assertTrue(plan.warnings.any { it.code == BackupImportWarningCode.WIDGET_INSTANCE_REQUIRES_REBUILD })
        assertFalse(plan.warnings.any { it.code == BackupImportWarningCode.MISSING_ASSET })
    }

    @Test
    fun unreadableReferencedResourceBecomesMissingManifestWarningWithoutDroppingSchedule() {
        val archive = BackupCodec.decode(
            BackupCodec.encode(
                BackupExportMapper.toArchive(
                    metadata = metadata(),
                    snapshot = BackupRoomSnapshot(
                        schedules = listOf(ScheduleProfileEntity(7, "主课表", true)),
                        configs = listOf(defaultConfig(7).copy(wallpaperUri = "content://lost"))
                    ),
                    preferences = BackupPreferences(BackupFormatV1.PREFERENCES_VERSION)
                )
            )
        )

        assertEquals(1, archive.data.schedules.size)
        assertEquals(1, archive.assets.size)
        assertTrue(archive.assets.single().bytes == null)
        val plan = BackupImportPlanBuilder.build(archive, "restore-missing-resource")
        assertTrue(plan.warnings.any { it.code == BackupImportWarningCode.MISSING_ASSET })
        assertEquals(BackupImportAssetAction.WARN_MISSING, plan.assetTargets.values.single().action)
    }

    @Test
    fun historyContextAssetIsManifestListedAndCanBeMissingWithoutLosingHistoryEntry() {
        val historyId = BackupStableId.new(BackupStableId.HISTORY_PREFIX)
        val contextAssetId = BackupStableId.new(BackupStableId.ASSET_PREFIX)
        val archive = BackupCodec.decode(
            BackupCodec.encode(
                BackupExportMapper.toArchive(
                    metadata = metadata(),
                    snapshot = BackupRoomSnapshot(
                        schedules = listOf(ScheduleProfileEntity(7, "主课表", true)),
                        configs = listOf(defaultConfig(7))
                    ),
                    preferences = BackupPreferences(
                        preferencesVersion = BackupFormatV1.PREFERENCES_VERSION,
                        aiImportHistory = listOf(
                            BackupAiImportHistoryEntry(
                                id = historyId,
                                createdAt = 1L,
                                title = "历史导入",
                                prompt = "导入",
                                sourceSummary = "截图",
                                payload = "{}",
                                contextAssetId = contextAssetId
                            )
                        )
                    ),
                    additionalAssets = listOf(
                        BackupAsset(
                            assetId = contextAssetId,
                            category = BackupAssetCategory.OTHER,
                            purpose = BackupAssetPurpose.AI_IMPORT_HISTORY_CONTEXT,
                            mediaType = "application/json",
                            bytes = null,
                            ownerId = historyId,
                            missingReason = "上下文已过期"
                        )
                    )
                )
            )
        )

        assertEquals(contextAssetId, archive.preferences.aiImportHistory.single().contextAssetId)
        assertEquals(BackupAssetPurpose.AI_IMPORT_HISTORY_CONTEXT, archive.assets.single().purpose)
        val plan = BackupImportPlanBuilder.build(archive, "restore-history")
        assertTrue(plan.warnings.any { it.code == BackupImportWarningCode.MISSING_ASSET })
        assertEquals(BackupImportAssetAction.WARN_MISSING, plan.assetTargets.getValue(contextAssetId).action)
    }

    @Test
    fun importPlanRejectsPathLikeOperationIdBeforeStagingCanBegin() {
        try {
            BackupImportPlanBuilder.build(
                archive = BackupCodec.decode(
                    BackupCodec.encode(
                        BackupExportMapper.toArchive(
                            metadata = metadata(),
                            snapshot = BackupRoomSnapshot(),
                            preferences = BackupPreferences(BackupFormatV1.PREFERENCES_VERSION)
                        )
                    )
                ),
                operationId = "../escape"
            )
            fail("预期拒绝 path-like operationId")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
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
