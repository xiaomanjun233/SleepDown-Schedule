package com.xiaomanjun.sleepdownschedule

data class BackupPreviewMissingAsset(
    val assetId: String,
    val purpose: String,
    val ownerId: String?,
    val reason: String
)

data class BackupPreviewReport(
    val sourcePackageName: String,
    val sourceAppVersionName: String,
    val sourceVersionCode: Int,
    val sourceDatabaseVersion: Int,
    val createdAt: String,
    val scheduleCount: Int,
    val courseCount: Int,
    val periodCount: Int,
    val periodSchemeCount: Int,
    val agentSessionCount: Int,
    val agentMessageCount: Int,
    val widgetAppearanceCount: Int,
    val presentAssetCount: Int,
    val missingAssetCount: Int,
    val missingAssets: List<BackupPreviewMissingAsset>,
    val warnings: List<BackupImportWarning>,
    val requiresExplicitReplaceConfirmation: Boolean,
    val nonMigratableItems: List<String>
)

/** Pure Preview data; it performs no Room, file, SharedPreferences or system-state writes. */
object BackupPreviewReportBuilder {
    fun build(
        archive: DecodedBackupArchive,
        plan: BackupImportPlan
    ): BackupPreviewReport {
        val schedules = archive.data.schedules
        val descriptors = archive.manifest.assets
        return BackupPreviewReport(
            sourcePackageName = archive.manifest.sourcePackageName,
            sourceAppVersionName = archive.manifest.sourceAppVersionName,
            sourceVersionCode = archive.manifest.sourceVersionCode,
            sourceDatabaseVersion = archive.manifest.sourceDatabaseVersion,
            createdAt = archive.manifest.createdAt,
            scheduleCount = schedules.size,
            courseCount = schedules.sumOf { it.courses.size },
            periodCount = schedules.sumOf { it.periods.size },
            periodSchemeCount = schedules.sumOf { it.periodSchemes.size },
            agentSessionCount = schedules.sumOf { it.agentDailySessions.size },
            agentMessageCount = schedules.sumOf { it.agentMessages.size },
            widgetAppearanceCount = archive.data.widgetAppearances.size,
            presentAssetCount = descriptors.count { it.present },
            missingAssetCount = descriptors.count { !it.present },
            missingAssets = descriptors.filterNot { it.present }.map { descriptor ->
                BackupPreviewMissingAsset(
                    assetId = descriptor.assetId,
                    purpose = descriptor.purpose,
                    ownerId = descriptor.ownerId,
                    reason = descriptor.missingReason ?: "备份中缺少资源"
                )
            },
            warnings = plan.warnings,
            requiresExplicitReplaceConfirmation = plan.requiresExplicitReplaceConfirmation,
            nonMigratableItems = buildList {
                add("AI 服务的 API Key 和教务登录状态需要重新填写或登录")
                add("通知、闹钟和其他系统权限需要在新设备重新允许")
                if (archive.data.widgetAppearances.any { it.scope == "instance" }) {
                    add("桌面小组件需要在新设备重新添加")
                }
            }
        )
    }
}
