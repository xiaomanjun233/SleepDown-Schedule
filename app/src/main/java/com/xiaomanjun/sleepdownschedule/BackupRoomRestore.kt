package com.xiaomanjun.sleepdownschedule

import androidx.room.withTransaction

data class BackupRoomRestoreRows(
    val schedules: List<ScheduleProfileEntity>,
    val configs: List<ScheduleConfigEntity>,
    val periods: List<PeriodEntity>,
    val courses: List<CourseEntity>,
    val periodSchemes: List<PeriodSchemeEntity>,
    val periodSchemeTimes: List<PeriodSchemeTimeEntity>,
    val agentDailySessions: List<AgentDailySessionEntity>,
    val agentMessages: List<AgentMessageEntity>,
    val widgetAppearances: List<WidgetAppearanceEntity>
)

/**
 * Maps an already codec-validated archive to target Room entities. The only IDs entering this
 * boundary come from the explicit ImportPlan; neither source nor array order is used as a key.
 */
object BackupRoomRestoreMapper {
    fun map(
        archive: DecodedBackupArchive,
        plan: BackupImportPlan,
        assetUrisById: Map<String, String> = emptyMap(),
        attachmentFileNamesByAssetId: Map<String, String> = emptyMap()
    ): BackupRoomRestoreRows {
        val schedules = archive.data.schedules
        require(plan.scheduleIds.keys == schedules.mapTo(linkedSetOf()) { it.id }) {
            "ImportPlan schedule 映射与 archive 不一致"
        }

        val activeSchedules = schedules.filter { it.isActive }
        require(activeSchedules.size <= 1) { "备份不能包含多个 active schedule" }
        val selectedActiveId = activeSchedules.singleOrNull()?.id ?: schedules.firstOrNull()?.id
        val assetsById = archive.assets.associateBy { it.assetId }
        require(assetsById.size == archive.assets.size) { "archive 包含重复 asset ID" }
        val descriptorsById = archive.manifest.assets.associateBy { it.assetId }
        require(descriptorsById.size == archive.manifest.assets.size) { "manifest 包含重复 asset ID" }

        fun assetUri(
            assetId: String?,
            category: String,
            purpose: String,
            ownerId: String
        ): String? {
            if (assetId == null) return null
            val asset = assetsById[assetId] ?: error("引用了不存在的 asset: $assetId")
            val descriptor = descriptorsById[assetId] ?: error("asset 缺少 manifest descriptor: $assetId")
            require(asset.category == category && descriptor.category == category) {
                "asset category 与引用槽位不一致: $assetId"
            }
            require(asset.purpose == purpose && descriptor.purpose == purpose) {
                "asset purpose 与引用槽位不一致: $assetId"
            }
            require(asset.ownerId == ownerId && descriptor.ownerId == ownerId) {
                "asset owner 与引用槽位不一致: $assetId"
            }
            if (asset.bytes == null) return null
            return assetUrisById[assetId]
                ?: error("present asset 尚未生成目标私有 URI: $assetId")
        }

        val schedulesByStableId = schedules.associateBy { it.id }
        val sessionsByStableId = buildMap {
            schedules.forEach { schedule ->
                schedule.agentDailySessions.forEach { session ->
                    check(put(session.id, session to schedule.id) == null) {
                        "Agent session stable ID 重复: ${session.id}"
                    }
                }
            }
        }

        val configRows = schedules.map { schedule ->
            val targetScheduleId = plan.scheduleIds.getValue(schedule.id)
            val config = schedule.config
            ScheduleConfigEntity(
                id = targetScheduleId,
                totalWeeks = config.totalWeeks,
                currentWeek = config.currentWeek,
                notificationLeadMinutes = config.notificationLeadMinutes,
                termStartDate = config.termStartDate,
                autoCurrentWeek = config.autoCurrentWeek,
                termState = strictBackupEnum<ScheduleTermState>(config.termState, "termState"),
                notificationsEnabled = config.notificationsEnabled,
                notificationMode = strictBackupEnum<NotificationMode>(config.notificationMode, "notificationMode"),
                wallpaperUri = assetUri(
                    assetId = config.wallpaperAssetId,
                    category = BackupAssetCategory.WALLPAPERS,
                    purpose = BackupAssetPurpose.SCHEDULE_WALLPAPER,
                    ownerId = schedule.id
                ),
                wallpaperBlur = config.wallpaperBlur,
                wallpaperBrightness = config.wallpaperBrightness,
                wallpaperPortraitCenterX = config.wallpaperPortraitCenterX,
                wallpaperPortraitCenterY = config.wallpaperPortraitCenterY,
                wallpaperPortraitScale = config.wallpaperPortraitScale,
                wallpaperLandscapeCenterX = config.wallpaperLandscapeCenterX,
                wallpaperLandscapeCenterY = config.wallpaperLandscapeCenterY,
                wallpaperLandscapeScale = config.wallpaperLandscapeScale,
                wallpaperSourceWidth = config.wallpaperSourceWidth,
                wallpaperSourceHeight = config.wallpaperSourceHeight,
                cardColorArgb = config.cardColorArgb,
                cardAlpha = config.cardAlpha,
                courseCardBlur = config.courseCardBlur,
                courseCardGlassEnabled = config.courseCardGlassEnabled,
                courseCardFontScale = config.courseCardFontScale,
                alternateCardColorArgb = config.alternateCardColorArgb,
                alternateCardAlpha = config.alternateCardAlpha,
                alternateCourseCardBlur = config.alternateCourseCardBlur,
                alternateCourseCardFontScale = config.alternateCourseCardFontScale,
                weekCardHeightDp = config.weekCardHeightDp,
                homeTextLight = config.homeTextLight,
                homeChromeBlurScale = config.homeChromeBlurScale,
                homeChromeSamplingScale = config.homeChromeSamplingScale,
                followSystemDarkMode = config.followSystemDarkMode,
                darkMode = config.darkMode,
                defaultWallpaperStyle = strictBackupEnum<DefaultWallpaperStyle>(
                    config.defaultWallpaperStyle,
                    "defaultWallpaperStyle"
                ),
                hideEmptyWeekends = config.hideEmptyWeekends,
                dockAlignment = strictBackupEnum<DockAlignment>(config.dockAlignment, "dockAlignment"),
                defaultHomeMode = strictBackupEnum<HomeStartMode>(config.defaultHomeMode, "defaultHomeMode"),
                liveUpdateActionsEnabled = config.liveUpdateActionsEnabled,
                liveUpdateChipTextMode = strictBackupEnum<LiveUpdateChipTextMode>(
                    config.liveUpdateChipTextMode,
                    "liveUpdateChipTextMode"
                ),
                classDurationMinutes = config.classDurationMinutes,
                breakDurationMinutes = config.breakDurationMinutes,
                morningPeriodCount = config.morningPeriodCount,
                noonPeriodCount = config.noonPeriodCount,
                afternoonPeriodCount = config.afternoonPeriodCount,
                eveningPeriodCount = config.eveningPeriodCount,
                hideFromRecents = config.hideFromRecents,
                autoCheckUpdates = config.autoCheckUpdates
            )
        }

        val scheduleRows = schedules.map { schedule ->
            ScheduleProfileEntity(
                id = plan.scheduleIds.getValue(schedule.id),
                name = schedule.name,
                isActive = schedule.id == selectedActiveId
            )
        }

        val periodRows = schedules.flatMap { schedule ->
            val targetScheduleId = plan.scheduleIds.getValue(schedule.id)
            schedule.periods.map { period ->
                PeriodEntity(
                    periodIndex = period.periodIndex,
                    startTime = period.startTime,
                    endTime = period.endTime,
                    scheduleId = targetScheduleId
                )
            }
        }

        val courseRows = schedules.flatMap { schedule ->
            val targetScheduleId = plan.scheduleIds.getValue(schedule.id)
            schedule.courses.map { course ->
                CourseEntity(
                    id = plan.courseIds.getValue(course.id),
                    name = course.name,
                    teacher = course.teacher,
                    location = course.location,
                    weekday = course.weekday,
                    periods = course.periods,
                    weeks = course.weeks,
                    weekParity = strictBackupEnum<WeekParity>(course.weekParity, "weekParity"),
                    note = course.note,
                    customStartTime = course.customStartTime,
                    customEndTime = course.customEndTime,
                    customColorArgb = course.customColorArgb,
                    scheduleId = targetScheduleId
                )
            }
        }

        val schemeRows = schedules.flatMap { schedule ->
            val targetScheduleId = plan.scheduleIds.getValue(schedule.id)
            schedule.periodSchemes.map { scheme ->
                PeriodSchemeEntity(
                    id = plan.schemeIds.getValue(scheme.id),
                    scheduleId = targetScheduleId,
                    name = scheme.name,
                    mode = strictBackupEnum<PeriodSchemeMode>(scheme.mode, "period scheme mode"),
                    isActive = scheme.isActive,
                    classDurationMinutes = scheme.classDurationMinutes,
                    breakDurationMinutes = scheme.breakDurationMinutes,
                    morningStartTime = scheme.morningStartTime,
                    noonStartTime = scheme.noonStartTime,
                    afternoonStartTime = scheme.afternoonStartTime,
                    eveningStartTime = scheme.eveningStartTime,
                    specialBreaksJson = scheme.specialBreaksJson,
                    overridesJson = scheme.overridesJson
                )
            }
        }

        val schemeTimeRows = schedules.flatMap { schedule ->
            schedule.periodSchemes.flatMap { scheme ->
                val targetSchemeId = plan.schemeIds.getValue(scheme.id)
                scheme.times.map { time ->
                    PeriodSchemeTimeEntity(
                        schemeId = targetSchemeId,
                        periodIndex = time.periodIndex,
                        startTime = time.startTime,
                        endTime = time.endTime
                    )
                }
            }
        }

        val sessionRows = schedules.flatMap { schedule ->
            val targetScheduleId = plan.scheduleIds.getValue(schedule.id)
            schedule.agentDailySessions.map { session ->
                AgentDailySessionEntity(
                    scheduleId = targetScheduleId,
                    date = session.date,
                    dailyPackJson = session.dailyPackJson,
                    providerId = session.providerId,
                    model = session.model,
                    createdAt = session.createdAt,
                    updatedAt = session.updatedAt,
                    generationStatus = session.generationStatus,
                    lastError = session.lastError
                )
            }
        }

        val messageRows = schedules.flatMap { schedule ->
            val targetScheduleId = plan.scheduleIds.getValue(schedule.id)
            schedule.agentMessages.map { message ->
                val session = message.sessionId?.let { sessionId ->
                    val source = sessionsByStableId[sessionId]
                        ?: error("Agent message 引用了不存在的 session: $sessionId")
                    require(source.second == schedule.id && source.first.date == message.sessionDate) {
                        "Agent message session/date 与所属 schedule 不一致: ${message.id}"
                    }
                    source.first
                }
                val attachmentIds = message.attachmentAssetIds.distinct()
                require(attachmentIds.size <= 1) {
                    "一个 Agent message 不能恢复多个 attachment marker: ${message.id}"
                }
                val plainContent = parseAgentMessageContent(message.content).text
                val attachmentFileName = attachmentIds.singleOrNull()?.let { assetId ->
                    val asset = assetsById[assetId] ?: error("Agent message 引用了不存在的 asset: $assetId")
                    val descriptor = descriptorsById[assetId] ?: error("Agent attachment 缺少 manifest descriptor")
                    require(asset.purpose == BackupAssetPurpose.AGENT_ATTACHMENT &&
                        descriptor.purpose == BackupAssetPurpose.AGENT_ATTACHMENT &&
                        asset.ownerId == message.id && descriptor.ownerId == message.id
                    ) { "Agent attachment owner/purpose 不匹配: $assetId" }
                    if (asset.bytes == null) null else attachmentFileNamesByAssetId[assetId]
                        ?: error("Agent attachment 尚未生成目标文件名: $assetId")
                }
                AgentMessageEntity(
                    id = plan.messageIds.getValue(message.id),
                    scheduleId = targetScheduleId,
                    sessionDate = session?.date ?: message.sessionDate,
                    role = message.role,
                    content = if (attachmentFileName == null) {
                        plainContent
                    } else {
                        agentMessageContent(plainContent, attachmentFileName)
                    },
                    createdAt = message.createdAt,
                    status = message.status
                )
            }
        }

        val widgetRows = archive.data.widgetAppearances.mapNotNull { appearance ->
            val target = plan.widgetTargets[appearance.id]
                ?: error("widget appearance 缺少 ImportPlan: ${appearance.id}")
            val appWidgetId = target.appWidgetId ?: return@mapNotNull null
            require(target.variant == appearance.variant) { "widget target variant 不一致: ${appearance.id}" }
            require(WidgetAppearanceVariant.entries.any { it.key == appearance.variant }) {
                "未知 widget variant: ${appearance.variant}"
            }
            WidgetAppearanceEntity(
                variant = appearance.variant,
                appWidgetId = appWidgetId,
                enabled = appearance.enabled,
                wallpaperUri = assetUri(
                    assetId = appearance.wallpaperAssetId,
                    category = BackupAssetCategory.WIDGETS,
                    purpose = BackupAssetPurpose.WIDGET_WALLPAPER,
                    ownerId = appearance.id
                ),
                centerX = appearance.centerX,
                centerY = appearance.centerY,
                scale = appearance.scale,
                sourceWidth = appearance.sourceWidth,
                sourceHeight = appearance.sourceHeight,
                blurDp = appearance.blurDp,
                brightness = appearance.brightness,
                updatedAt = 0L
            ).normalized()
        }

        return BackupRoomRestoreRows(
            schedules = scheduleRows,
            configs = configRows,
            periods = periodRows,
            courses = courseRows,
            periodSchemes = schemeRows,
            periodSchemeTimes = schemeTimeRows,
            agentDailySessions = sessionRows,
            agentMessages = messageRows,
            widgetAppearances = widgetRows
        )
    }
}

class BackupRoomReplaceTransaction(private val database: AppDatabase) {
    suspend fun replace(rows: BackupRoomRestoreRows) {
        database.withTransaction {
            database.agentDao().deleteAllMessages()
            database.agentDao().deleteAllDailySessions()
            database.widgetAppearanceDao().deleteAll()
            database.periodSchemeDao().deleteAllTimes()
            database.periodSchemeDao().deleteAllSchemes()
            database.courseDao().deleteAllRows()
            database.configDao().deleteAllPeriods()
            database.configDao().deleteAllConfigs()
            database.scheduleProfileDao().deleteAllProfiles()

            if (rows.schedules.isNotEmpty()) database.scheduleProfileDao().upsertProfiles(rows.schedules)
            if (rows.configs.isNotEmpty()) database.configDao().upsertConfigs(rows.configs)
            if (rows.periods.isNotEmpty()) database.configDao().upsertPeriods(rows.periods)
            if (rows.courses.isNotEmpty()) database.courseDao().insertCourses(rows.courses)
            if (rows.periodSchemes.isNotEmpty()) database.periodSchemeDao().upsertSchemes(rows.periodSchemes)
            if (rows.periodSchemeTimes.isNotEmpty()) database.periodSchemeDao().upsertTimes(rows.periodSchemeTimes)
            if (rows.agentDailySessions.isNotEmpty()) database.agentDao().upsertDailySessions(rows.agentDailySessions)
            if (rows.agentMessages.isNotEmpty()) database.agentDao().upsertMessages(rows.agentMessages)
            rows.widgetAppearances.forEach { database.widgetAppearanceDao().upsert(it) }
        }
    }
}

private inline fun <reified T : Enum<T>> strictBackupEnum(value: String, label: String): T =
    runCatching { enumValueOf<T>(value) }
        .getOrElse { throw IllegalArgumentException("未知 $label: $value") }
