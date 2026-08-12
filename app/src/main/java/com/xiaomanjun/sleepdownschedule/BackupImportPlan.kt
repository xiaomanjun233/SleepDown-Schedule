package com.xiaomanjun.sleepdownschedule

import kotlinx.serialization.Serializable

@Serializable
enum class BackupImportMode {
    REPLACE
}

@Serializable
enum class BackupImportWarningCode {
    MISSING_ASSET,
    WIDGET_INSTANCE_REQUIRES_REBUILD,
    NO_ACTIVE_SCHEDULE_SELECTED
}

@Serializable
data class BackupImportWarning(
    val code: BackupImportWarningCode,
    val message: String,
    val stableId: String? = null
)

/** Existing Room IDs are used only to keep a Preview plan collision-free. */
@Serializable
data class BackupImportTargetSnapshot(
    val scheduleIds: Set<Int> = emptySet(),
    val courseIds: Set<Long> = emptySet(),
    val schemeIds: Set<Long> = emptySet(),
    val messageIds: Set<Long> = emptySet(),
    val hasPreferences: Boolean = false,
    val hasWidgetAppearances: Boolean = false
) {
    val isNonEmpty: Boolean
        get() = scheduleIds.isNotEmpty() ||
            courseIds.isNotEmpty() ||
            schemeIds.isNotEmpty() ||
            messageIds.isNotEmpty() ||
            hasPreferences ||
            hasWidgetAppearances
}

@Serializable
data class BackupImportSessionTarget(
    val scheduleId: Int,
    val date: String
)

@Serializable
data class BackupImportWidgetTarget(
    val variant: String,
    val appWidgetId: Int?
)

@Serializable
enum class BackupImportAssetAction {
    RESTORE,
    WARN_MISSING
}

@Serializable
data class BackupImportAssetTarget(
    val action: BackupImportAssetAction,
    val relativePath: String,
    val optional: Boolean,
    val missingReason: String?
)

@Serializable
data class BackupImportPlan(
    val operationId: String,
    val mode: BackupImportMode,
    val requiresExplicitReplaceConfirmation: Boolean,
    val scheduleIds: Map<String, Int>,
    val courseIds: Map<String, Long>,
    val schemeIds: Map<String, Long>,
    val sessionTargets: Map<String, BackupImportSessionTarget>,
    val messageIds: Map<String, Long>,
    val widgetTargets: Map<String, BackupImportWidgetTarget>,
    val assetTargets: Map<String, BackupImportAssetTarget>,
    val warnings: List<BackupImportWarning>
)

object BackupImportPlanBuilder {
    private val operationIdPattern = Regex("[A-Za-z0-9_-]{1,64}")

    fun build(
        archive: DecodedBackupArchive,
        operationId: String,
        existing: BackupImportTargetSnapshot = BackupImportTargetSnapshot()
    ): BackupImportPlan {
        require(operationIdPattern.matches(operationId)) { "非法恢复 operationId" }
        validateImportReferencesAndEnums(archive)
        require(existing.scheduleIds.all { it > 0 }) { "existing schedule Room ID 必须为正数" }
        require(existing.courseIds.all { it > 0 }) { "existing course Room ID 必须为正数" }
        require(existing.schemeIds.all { it > 0 }) { "existing scheme Room ID 必须为正数" }
        require(existing.messageIds.all { it > 0 }) { "existing message Room ID 必须为正数" }

        val schedules = archive.data.schedules
        val scheduleIds = allocateIntIds(
            stableIds = schedules.map { it.id },
            existingIds = existing.scheduleIds
        )
        val courseIds = allocateLongIds(
            stableIds = schedules.flatMap { it.courses }.map { it.id },
            existingIds = existing.courseIds
        )
        val schemeIds = allocateLongIds(
            stableIds = schedules.flatMap { it.periodSchemes }.map { it.id },
            existingIds = existing.schemeIds
        )
        val sessionTargets = linkedMapOf<String, BackupImportSessionTarget>()
        schedules.forEach { schedule ->
            val targetScheduleId = scheduleIds.getValue(schedule.id)
            schedule.agentDailySessions.forEach { session ->
                check(sessionTargets.put(session.id, BackupImportSessionTarget(targetScheduleId, session.date)) == null) {
                    "重复 agent session stable ID: ${session.id}"
                }
            }
        }
        val messageIds = allocateLongIds(
            stableIds = schedules.flatMap { it.agentMessages }.map { it.id },
            existingIds = existing.messageIds
        )

        val widgetTargets = linkedMapOf<String, BackupImportWidgetTarget>()
        val warnings = mutableListOf<BackupImportWarning>()
        val activeSchedules = schedules.filter { it.isActive }
        if (activeSchedules.isEmpty() && schedules.isNotEmpty()) {
            warnings += BackupImportWarning(
                code = BackupImportWarningCode.NO_ACTIVE_SCHEDULE_SELECTED,
                message = "备份没有 active 课表，恢复时将显式选择第一个课表",
                stableId = schedules.first().id
            )
        }
        archive.data.widgetAppearances.forEach { appearance ->
            val target = if (appearance.scope == "default") {
                BackupImportWidgetTarget(appearance.variant, WidgetDefaultAppearanceId)
            } else {
                warnings += BackupImportWarning(
                    code = BackupImportWarningCode.WIDGET_INSTANCE_REQUIRES_REBUILD,
                    message = "widget 实例外观已保留为逻辑记录，恢复后需要系统重新绑定实例",
                    stableId = appearance.id
                )
                BackupImportWidgetTarget(appearance.variant, null)
            }
            widgetTargets[appearance.id] = target
        }

        val assetTargets = linkedMapOf<String, BackupImportAssetTarget>()
        archive.assets.forEach { asset ->
            val descriptor = archive.manifest.assets.firstOrNull { it.assetId == asset.assetId }
                ?: error("asset 缺少 manifest descriptor: ${asset.assetId}")
            val action = if (asset.bytes != null) {
                BackupImportAssetAction.RESTORE
            } else {
                warnings += BackupImportWarning(
                    code = BackupImportWarningCode.MISSING_ASSET,
                    message = descriptor.missingReason ?: "备份中缺少资源",
                    stableId = asset.assetId
                )
                BackupImportAssetAction.WARN_MISSING
            }
            assetTargets[asset.assetId] = BackupImportAssetTarget(
                action = action,
                relativePath = descriptor.relativePath,
                optional = descriptor.optional,
                missingReason = descriptor.missingReason
            )
        }

        return BackupImportPlan(
            operationId = operationId,
            mode = BackupImportMode.REPLACE,
            requiresExplicitReplaceConfirmation = existing.isNonEmpty,
            scheduleIds = scheduleIds,
            courseIds = courseIds,
            schemeIds = schemeIds,
            sessionTargets = sessionTargets,
            messageIds = messageIds,
            widgetTargets = widgetTargets,
            assetTargets = assetTargets,
            warnings = warnings
        )
    }

    private fun validateImportReferencesAndEnums(archive: DecodedBackupArchive) {
        val assets = archive.assets.associateBy { it.assetId }
        val descriptors = archive.manifest.assets.associateBy { it.assetId }

        fun assetReference(assetId: String, category: String, purpose: String, ownerId: String) {
            val asset = assets[assetId] ?: error("引用了不存在的 asset: $assetId")
            val descriptor = descriptors[assetId] ?: error("asset 缺少 manifest descriptor: $assetId")
            require(asset.category == category && descriptor.category == category) {
                "asset category 不匹配: $assetId"
            }
            require(asset.purpose == purpose && descriptor.purpose == purpose) {
                "asset purpose 不匹配: $assetId"
            }
            require(asset.ownerId == ownerId && descriptor.ownerId == ownerId) {
                "asset owner 不匹配: $assetId"
            }
        }

        archive.data.schedules.forEach { schedule ->
            strictEnum<ScheduleTermState>(schedule.config.termState, "termState")
            strictEnum<NotificationMode>(schedule.config.notificationMode, "notificationMode")
            strictEnum<DefaultWallpaperStyle>(schedule.config.defaultWallpaperStyle, "defaultWallpaperStyle")
            strictEnum<DockAlignment>(schedule.config.dockAlignment, "dockAlignment")
            strictEnum<HomeStartMode>(schedule.config.defaultHomeMode, "defaultHomeMode")
            strictEnum<LiveUpdateChipTextMode>(schedule.config.liveUpdateChipTextMode, "liveUpdateChipTextMode")
            schedule.config.wallpaperAssetId?.let {
                assetReference(it, BackupAssetCategory.WALLPAPERS, BackupAssetPurpose.SCHEDULE_WALLPAPER, schedule.id)
            }
            schedule.courses.forEach { strictEnum<WeekParity>(it.weekParity, "weekParity") }
            schedule.periodSchemes.forEach { strictEnum<PeriodSchemeMode>(it.mode, "period scheme mode") }
            schedule.agentMessages.forEach { message ->
                require(message.attachmentAssetIds.size <= 1) {
                    "一个 Agent message 不能包含多个 attachment marker: ${message.id}"
                }
                message.attachmentAssetIds.singleOrNull()?.let {
                    assetReference(it, BackupAssetCategory.SCHEDULES, BackupAssetPurpose.AGENT_ATTACHMENT, message.id)
                }
            }
        }
        archive.data.widgetAppearances.forEach { appearance ->
            require(WidgetAppearanceVariant.entries.any { it.key == appearance.variant }) {
                "未知 widget variant: ${appearance.variant}"
            }
            appearance.wallpaperAssetId?.let {
                assetReference(it, BackupAssetCategory.WIDGETS, BackupAssetPurpose.WIDGET_WALLPAPER, appearance.id)
            }
        }
        archive.preferences.appIcon?.let {
            strictEnum<AppIconMode>(it.mode, "app icon mode")
        }
        archive.preferences.aiImport?.let { ai ->
            ai.providers.forEach { provider ->
                strictEnum<AiProviderType>(provider.providerType, "AI providerType")
                strictEnum<AiAuthType>(provider.authType, "AI authType")
                strictEnum<AiEndpointStyle>(provider.endpointStyle, "AI endpointStyle")
                strictEnum<StructuredOutputMode>(provider.structuredOutputMode, "AI structuredOutputMode")
                strictEnum<AiInputMode>(provider.inputMode, "AI inputMode")
                strictEnum<AiReasoningEffort>(provider.reasoningEffort, "AI reasoningEffort")
            }
            require(ai.providers.any { it.id == ai.selectedProviderId } ||
                (ai.providers.isEmpty() && ai.selectedProviderId == AiProviderPresets.none.id)
            ) { "AI selectedProviderId 不在 provider 列表中" }
        }
        archive.preferences.aiImportHistory.forEach { history ->
            history.contextAssetId?.let {
                assetReference(it, BackupAssetCategory.OTHER, BackupAssetPurpose.AI_IMPORT_HISTORY_CONTEXT, history.id)
            }
        }
    }

    private inline fun <reified T : Enum<T>> strictEnum(value: String, label: String) {
        runCatching { enumValueOf<T>(value) }
            .getOrElse { throw IllegalArgumentException("未知 $label: $value") }
    }

    private fun allocateIntIds(stableIds: List<String>, existingIds: Set<Int>): Map<String, Int> {
        require(stableIds.size == stableIds.toSet().size) { "stable ID 重复" }
        val used = existingIds.toMutableSet()
        var next = (used.maxOrNull() ?: 0).toLong() + 1L
        return stableIds.associateWith { stableId ->
            require(next in 1..Int.MAX_VALUE) { "schedule Room ID 空间耗尽" }
            val result = next.toInt()
            used += result
            next += 1L
            result
        }
    }

    private fun allocateLongIds(stableIds: List<String>, existingIds: Set<Long>): Map<String, Long> {
        require(stableIds.size == stableIds.toSet().size) { "stable ID 重复" }
        val used = existingIds.toMutableSet()
        var next = (used.maxOrNull() ?: 0L) + 1L
        return stableIds.associateWith { stableId ->
            require(next > 0L) { "Room ID 空间耗尽" }
            val result = next
            used += result
            next += 1L
            result
        }
    }
}
