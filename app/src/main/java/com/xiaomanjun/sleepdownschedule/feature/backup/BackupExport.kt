package com.xiaomanjun.sleepdownschedule.feature.backup

import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.widget.*
import com.xiaomanjun.sleepdownschedule.feature.agent.*

import androidx.room.withTransaction

/**
 * Room-only read snapshot used by the backup adapter. It deliberately contains entities, not a
 * Room database handle, so the mapping below can be tested without opening or mutating a database.
 */
data class BackupRoomSnapshot(
    val schedules: List<ScheduleProfileEntity> = emptyList(),
    val configs: List<ScheduleConfigEntity> = emptyList(),
    val periods: List<PeriodEntity> = emptyList(),
    val courses: List<CourseEntity> = emptyList(),
    val periodSchemes: List<PeriodSchemeEntity> = emptyList(),
    val periodSchemeTimes: List<PeriodSchemeTimeEntity> = emptyList(),
    val agentDailySessions: List<AgentDailySessionEntity> = emptyList(),
    val agentMessages: List<AgentMessageEntity> = emptyList(),
    val widgetAppearances: List<WidgetAppearanceEntity> = emptyList()
)

/** Reads every Room table in one transaction; active-only repository snapshots are not enough. */
class BackupRoomSnapshotReader(private val database: AppDatabase) {
    suspend fun read(): BackupRoomSnapshot = database.withTransaction {
        BackupRoomSnapshot(
            schedules = database.scheduleProfileDao().getProfiles(),
            configs = database.configDao().getAllConfigs(),
            periods = database.configDao().getAllPeriods(),
            courses = database.courseDao().getAllCourses(),
            periodSchemes = database.periodSchemeDao().getAllSchemes(),
            periodSchemeTimes = database.periodSchemeDao().getAllTimes(),
            agentDailySessions = database.agentDao().getAllDailySessions(),
            agentMessages = database.agentDao().getAllMessages(),
            widgetAppearances = database.widgetAppearanceDao().getAll()
        )
    }
}

/** Asset bytes collected by the Android-facing exporter before the archive is encoded. */
data class BackupExportAssetInput(
    val sourceKey: String,
    val bytes: ByteArray?,
    val mediaType: String? = null,
    val optional: Boolean = true,
    val missingReason: String? = null
)

private data class BackupExportAssetRequest(
    val sourceKey: String,
    val category: String,
    val purpose: String,
    val fallbackMediaType: String,
    val ownerId: String
)

private class BackupExportAssetCatalog(inputs: Collection<BackupExportAssetInput>) {
    private val inputsBySourceKey = inputs.associateBy { it.sourceKey }
    private val assetIdsBySourceKey = LinkedHashMap<String, String>()

    init {
        require(inputsBySourceKey.size == inputs.size) { "备份 asset sourceKey 不得重复" }
    }

    fun resolve(request: BackupExportAssetRequest): BackupAsset {
        val input = inputsBySourceKey[request.sourceKey]
        val assetId = assetIdsBySourceKey.getOrPut(request.sourceKey) {
            BackupStableId.new(BackupStableId.ASSET_PREFIX)
        }
        val bytes = input?.bytes
        return BackupAsset(
            assetId = assetId,
            category = request.category,
            purpose = request.purpose,
            mediaType = input?.mediaType ?: request.fallbackMediaType,
            bytes = bytes,
            optional = input?.optional ?: true,
            ownerId = request.ownerId,
            missingReason = if (bytes == null) {
                input?.missingReason ?: "源资源不可读取"
            } else {
                null
            }
        )
    }
}

data class BackupExportIdMapping(
    val scheduleIds: Map<Int, String>
)

/**
 * Converts the full Room read boundary to protocol DTOs. Room auto IDs never cross this
 * boundary; all relationships are rewritten through archive-local stable IDs.
 */
object BackupExportMapper {
    fun createIdMapping(snapshot: BackupRoomSnapshot): BackupExportIdMapping {
        val scheduleIds = snapshot.schedules.associate { profile ->
            require(profile.id > 0) { "schedule profile 的 Room ID 必须为正数" }
            profile.id to BackupStableId.new(BackupStableId.SCHEDULE_PREFIX)
        }
        require(scheduleIds.size == snapshot.schedules.size) { "schedule profile 存在重复 Room ID" }
        return BackupExportIdMapping(scheduleIds)
    }

    fun toArchive(
        metadata: BackupSourceMetadata,
        snapshot: BackupRoomSnapshot,
        preferences: BackupPreferences,
        assetInputs: Collection<BackupExportAssetInput> = emptyList(),
        additionalAssets: Collection<BackupAsset> = emptyList(),
        idMapping: BackupExportIdMapping? = null
    ): BackupArchive {
        val schedules = snapshot.schedules
        val scheduleIds = idMapping?.scheduleIds ?: createIdMapping(snapshot).scheduleIds
        require(scheduleIds.keys == schedules.mapTo(HashSet()) { it.id }) {
            "export stable schedule mapping 与 Room snapshot 不一致"
        }
        require(scheduleIds.values.toSet().size == scheduleIds.size) { "export stable schedule ID 重复" }
        scheduleIds.values.forEach { BackupStableId.requireValid(it, BackupStableId.SCHEDULE_PREFIX) }

        val configs = snapshot.configs.associateBy { it.id }
        require(configs.size == snapshot.configs.size) { "schedule config 存在重复 Room ID" }
        val scheduleRoomIds = scheduleIds.keys
        require(snapshot.configs.all { it.id in scheduleRoomIds }) {
            "schedule config 引用了不存在的 schedule profile"
        }
        require(snapshot.periods.all { it.scheduleId in scheduleRoomIds }) {
            "period 引用了不存在的 schedule profile"
        }
        require(snapshot.courses.all { it.scheduleId in scheduleRoomIds }) {
            "course 引用了不存在的 schedule profile"
        }
        require(snapshot.periodSchemes.all { it.scheduleId in scheduleRoomIds }) {
            "period scheme 引用了不存在的 schedule profile"
        }
        val schemeRoomIds = snapshot.periodSchemes.mapTo(HashSet()) { it.id }
        require(snapshot.periodSchemeTimes.all { it.schemeId in schemeRoomIds }) {
            "period scheme time 引用了不存在的 period scheme"
        }
        require(snapshot.agentDailySessions.all { it.scheduleId in scheduleRoomIds }) {
            "agent session 引用了不存在的 schedule profile"
        }
        require(snapshot.agentMessages.all { it.scheduleId in scheduleRoomIds }) {
            "agent message 引用了不存在的 schedule profile"
        }
        val periodsBySchedule = snapshot.periods.groupBy { it.scheduleId }
        val coursesBySchedule = snapshot.courses.groupBy { it.scheduleId }
        val schemesBySchedule = snapshot.periodSchemes.groupBy { it.scheduleId }

        val courseIds = snapshot.courses.associate { course ->
            require(course.id > 0) { "course 的 Room ID 必须为正数" }
            course.id to BackupStableId.new(BackupStableId.COURSE_PREFIX)
        }
        require(courseIds.size == snapshot.courses.size) { "course 存在重复 Room ID" }

        val schemeIds = snapshot.periodSchemes.associate { scheme ->
            require(scheme.id > 0) { "period scheme 的 Room ID 必须为正数" }
            scheme.id to BackupStableId.new(BackupStableId.SCHEME_PREFIX)
        }
        require(schemeIds.size == snapshot.periodSchemes.size) { "period scheme 存在重复 Room ID" }

        val sessionsByKey = snapshot.agentDailySessions.associateBy { AgentSessionRoomKey(it.scheduleId, it.date) }
        require(sessionsByKey.size == snapshot.agentDailySessions.size) { "agent session 存在重复复合主键" }
        val sessionIds = sessionsByKey.keys.associateWith {
            BackupStableId.new(BackupStableId.SESSION_PREFIX)
        }

        val messageIds = snapshot.agentMessages.associate { message ->
            require(message.id > 0) { "agent message 的 Room ID 必须为正数" }
            message.id to BackupStableId.new(BackupStableId.MESSAGE_PREFIX)
        }
        require(messageIds.size == snapshot.agentMessages.size) { "agent message 存在重复 Room ID" }

        val widgetIds = snapshot.widgetAppearances.associate { appearance ->
            WidgetRoomKey(appearance.variant, appearance.appWidgetId) to
                BackupStableId.new(BackupStableId.WIDGET_PREFIX)
        }
        require(widgetIds.size == snapshot.widgetAppearances.size) { "widget appearance 存在重复复合主键" }

        val assetCatalog = BackupExportAssetCatalog(assetInputs)
        val assetsById = LinkedHashMap<String, BackupAsset>()
        additionalAssets.forEach { asset ->
            require(assetsById.put(asset.assetId, asset) == null) {
                "备份包含重复 additional asset ID: ${asset.assetId}"
            }
        }

        fun resolveAsset(request: BackupExportAssetRequest): String {
            val asset = assetCatalog.resolve(request)
            assetsById.putIfAbsent(asset.assetId, asset)
            return asset.assetId
        }

        fun configToBackup(config: ScheduleConfigEntity, scheduleStableId: String): BackupScheduleConfig {
            val wallpaperAssetId = config.wallpaperUri?.takeIf { it.isNotBlank() }?.let { uri ->
                resolveAsset(
                    BackupExportAssetRequest(
                        sourceKey = "schedule-wallpaper:${config.id}:$uri",
                        category = BackupAssetCategory.WALLPAPERS,
                        purpose = BackupAssetPurpose.SCHEDULE_WALLPAPER,
                        fallbackMediaType = "image/jpeg",
                        ownerId = scheduleStableId
                    )
                )
            }
            return BackupScheduleConfig(
                totalWeeks = config.totalWeeks,
                currentWeek = config.currentWeek,
                notificationLeadMinutes = config.notificationLeadMinutes,
                termStartDate = config.termStartDate,
                autoCurrentWeek = config.autoCurrentWeek,
                termState = config.termState.name,
                notificationsEnabled = config.notificationsEnabled,
                notificationMode = config.notificationMode.name,
                wallpaperAssetId = wallpaperAssetId,
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
                courseCardColorMode = config.courseCardColorMode.name,
                courseCardPalette = config.courseCardPalette,
                alternateCardColorArgb = config.alternateCardColorArgb,
                alternateCardAlpha = config.alternateCardAlpha,
                alternateCourseCardBlur = config.alternateCourseCardBlur,
                alternateCourseCardFontScale = config.alternateCourseCardFontScale,
                alternateCourseCardColorMode = config.alternateCourseCardColorMode.name,
                alternateCourseCardPalette = config.alternateCourseCardPalette,
                weekCardHeightDp = config.weekCardHeightDp,
                weekCardHeightScale = config.weekCardHeightScale,
                weekCardCornerProgress = config.weekCardCornerProgress,
                homeTextLight = config.homeTextLight,
                followSystemDarkMode = config.followSystemDarkMode,
                darkMode = config.darkMode,
                defaultWallpaperStyle = config.defaultWallpaperStyle.name,
                hideEmptyWeekends = config.hideEmptyWeekends,
                dockAlignment = config.dockAlignment.name,
                defaultHomeMode = config.defaultHomeMode.name,
                liveUpdateActionsEnabled = config.liveUpdateActionsEnabled,
                liveUpdateChipTextMode = config.liveUpdateChipTextMode.name,
                classDurationMinutes = config.classDurationMinutes,
                breakDurationMinutes = config.breakDurationMinutes,
                morningPeriodCount = config.morningPeriodCount,
                noonPeriodCount = config.noonPeriodCount,
                afternoonPeriodCount = config.afternoonPeriodCount,
                eveningPeriodCount = config.eveningPeriodCount,
                hideFromRecents = config.hideFromRecents,
                autoCheckUpdates = config.autoCheckUpdates,
                homeChromeBlurScale = config.homeChromeBlurScale,
                homeChromeSamplingScale = config.homeChromeSamplingScale
            )
        }

        val backupSchedules = schedules.map { profile ->
            val scheduleStableId = scheduleIds.getValue(profile.id)
            val config = configs[profile.id]
                ?: error("schedule ${profile.id} 缺少 config；导出不能伪造默认配置")
            val periodIndexes = periodsBySchedule[profile.id].orEmpty()
                .sortedBy { it.periodIndex }
                .map { BackupPeriod(it.periodIndex, it.startTime, it.endTime) }
            val schemeTimesByScheme = snapshot.periodSchemeTimes.groupBy { it.schemeId }
            val backupSchemes = schemesBySchedule[profile.id].orEmpty().map { scheme ->
                BackupPeriodScheme(
                    id = schemeIds.getValue(scheme.id),
                    name = scheme.name,
                    mode = scheme.mode.name,
                    isActive = scheme.isActive,
                    classDurationMinutes = scheme.classDurationMinutes,
                    breakDurationMinutes = scheme.breakDurationMinutes,
                    morningStartTime = scheme.morningStartTime,
                    noonStartTime = scheme.noonStartTime,
                    afternoonStartTime = scheme.afternoonStartTime,
                    eveningStartTime = scheme.eveningStartTime,
                    specialBreaksJson = scheme.specialBreaksJson,
                    overridesJson = scheme.overridesJson,
                    times = schemeTimesByScheme[scheme.id].orEmpty()
                        .sortedBy { it.periodIndex }
                        .map { BackupPeriodSchemeTime(it.periodIndex, it.startTime, it.endTime) }
                )
            }
            val backupCourses = coursesBySchedule[profile.id].orEmpty().map { course ->
                BackupCourse(
                    id = courseIds.getValue(course.id),
                    name = course.name,
                    teacher = course.teacher,
                    location = course.location,
                    weekday = course.weekday,
                    periods = course.periods,
                    weeks = course.weeks,
                    weekParity = course.weekParity.name,
                    note = course.note,
                    customStartTime = course.customStartTime,
                    customEndTime = course.customEndTime,
                    customColorArgb = course.customColorArgb
                )
            }
            val backupSessions = snapshot.agentDailySessions
                .filter { it.scheduleId == profile.id }
                .sortedBy { it.date }
                .map { session ->
                    BackupAgentDailySession(
                        id = sessionIds.getValue(AgentSessionRoomKey(session.scheduleId, session.date)),
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
            val backupMessages = snapshot.agentMessages
                .filter { it.scheduleId == profile.id }
                .sortedWith(compareBy<AgentMessageEntity> { it.createdAt }.thenBy { it.id })
                .map { message ->
                    val parsed = parseAgentMessageContent(message.content)
                    val attachmentAssetIds = parsed.attachmentFileName?.let { fileName ->
                        listOf(
                            resolveAsset(
                                BackupExportAssetRequest(
                                    sourceKey = "agent-attachment:${message.scheduleId}:${message.id}:$fileName",
                                    category = BackupAssetCategory.SCHEDULES,
                                    purpose = BackupAssetPurpose.AGENT_ATTACHMENT,
                                    fallbackMediaType = "image/jpeg",
                                    ownerId = messageIds.getValue(message.id)
                                )
                            )
                        )
                    }.orEmpty()
                    BackupAgentMessage(
                        id = messageIds.getValue(message.id),
                        sessionId = sessionIds[AgentSessionRoomKey(message.scheduleId, message.sessionDate)],
                        sessionDate = message.sessionDate,
                        role = message.role,
                        content = parsed.text,
                        createdAt = message.createdAt,
                        status = message.status,
                        attachmentAssetIds = attachmentAssetIds
                    )
                }
            BackupSchedule(
                id = scheduleStableId,
                name = profile.name,
                isActive = profile.isActive,
                config = configToBackup(config, scheduleStableId),
                courses = backupCourses,
                periods = periodIndexes,
                periodSchemes = backupSchemes,
                agentDailySessions = backupSessions,
                agentMessages = backupMessages
            )
        }

        val backupWidgets = snapshot.widgetAppearances.map { appearance ->
            val widgetStableId = widgetIds.getValue(WidgetRoomKey(appearance.variant, appearance.appWidgetId))
            val wallpaperAssetId = appearance.wallpaperUri?.takeIf { it.isNotBlank() }?.let { uri ->
                resolveAsset(
                    BackupExportAssetRequest(
                        sourceKey = "widget-wallpaper:${appearance.variant}:${appearance.appWidgetId}:$uri",
                        category = BackupAssetCategory.WIDGETS,
                        purpose = BackupAssetPurpose.WIDGET_WALLPAPER,
                        fallbackMediaType = "image/jpeg",
                        ownerId = widgetStableId
                    )
                )
            }
            BackupWidgetAppearance(
                id = widgetStableId,
                variant = appearance.variant,
                scope = if (appearance.appWidgetId == WidgetDefaultAppearanceId) "default" else "instance",
                enabled = appearance.enabled,
                wallpaperAssetId = wallpaperAssetId,
                centerX = appearance.centerX,
                centerY = appearance.centerY,
                scale = appearance.scale,
                sourceWidth = appearance.sourceWidth,
                sourceHeight = appearance.sourceHeight,
                blurDp = appearance.blurDp,
                brightness = appearance.brightness
            )
        }

        return BackupArchive(
            metadata = metadata,
            data = BackupData(
                dataVersion = BackupFormatV1.DATA_VERSION,
                schedules = backupSchedules,
                widgetAppearances = backupWidgets
            ),
            preferences = preferences,
            assets = assetsById.values.toList()
        )
    }
}

data class AgentSessionRoomKey(val scheduleId: Int, val date: String)

private data class WidgetRoomKey(val variant: String, val appWidgetId: Int)
