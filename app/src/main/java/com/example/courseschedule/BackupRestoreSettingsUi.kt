package com.example.courseschedule

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private data class BackupRestorePreviewState(
    val archive: DecodedBackupArchive,
    val operationId: String,
    val report: BackupPreviewReport
)

private data class BackupGuidePage(
    val iconRes: Int,
    val title: String,
    val message: String,
    val tint: Color? = null
)

/** Settings-facing SAF bridge. Selecting and previewing a backup never mutates app data. */
@Composable
fun BackupRestoreSettingsScreen(
    state: AppState,
    backdrop: Backdrop?,
    onOpenPreview: (Uri) -> Unit = {}
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as CourseScheduleApp }
    val scope = rememberCoroutineScope()
    val topPadding = detailContentTopPadding()
    val defaultFileName = remember { defaultBackupFileName() }
    var busyLabel by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    fun showFailure(title: String, guidance: String, error: Throwable) {
        Log.e("SleepDownBackup", title, error)
        statusIsError = true
        statusMessage = "$title。$guidance"
    }

    val exportLauncher = rememberLauncherForActivityResult(
        // A generic octet-stream makes some OEM document providers force a `.bin` suffix even
        // when the suggested display name ends in `.sleepdown`.
        contract = ActivityResultContracts.CreateDocument(BackupDocumentMimeType)
    ) { destination ->
        if (destination == null) return@rememberLauncherForActivityResult
        scope.launch {
            busyLabel = "正在整理课表、设置和图片…"
            statusMessage = null
            statusIsError = false
            runCatching {
                val archive = BackupExportService(app, app.database).export()
                val output = context.contentResolver.openOutputStream(destination, "wt")
                    ?: error("系统未能打开备份保存位置")
                output.use { BackupCodec.write(it, archive) }
            }.onSuccess {
                statusMessage = "备份已保存。应用里的课表和设置没有变化。"
            }.onFailure { error ->
                showFailure(
                    title = "备份没有保存成功",
                    guidance = "请重新选择保存位置，并确认设备还有足够的可用空间",
                    error = error
                )
            }
            busyLabel = null
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { source ->
        source?.let(onOpenPreview)
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topPadding,
            bottom = DockScrollPadding
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "backup-scope") {
            BackupGuideCarousel(
                sectionTitle = "会备份什么",
                pages = listOf(
                    BackupGuidePage(
                        R.drawable.ic_material_event,
                        "全部课表与课程",
                        "保存全部课表、课程、上课周数、节次时间，以及你调整过的作息。",
                        tint = Color(0xFF4B8DFF)
                    ),
                    BackupGuidePage(
                        R.drawable.ic_material_settings_backup_restore,
                        "个性化设置",
                        "保存卡片样式、深色模式、首页、课程提醒、今日助手、AI 服务选项和小组件样式。",
                        tint = Color(0xFF8C78E8)
                    ),
                    BackupGuidePage(
                        R.drawable.ic_material_photo_library,
                        "图片与导入记录",
                        "保存正在使用的课表和小组件壁纸、助手图片，以及 AI 导入记录。",
                        tint = Color(0xFF39A89A)
                    ),
                    BackupGuidePage(
                        R.drawable.ic_material_verified_user,
                        "敏感信息留在本机",
                        "API Key、教务登录状态和系统权限不会进入备份。备份文件没有加密，请不要发送给他人。",
                        tint = Color(0xFFE09B3F)
                    )
                ),
                backdrop = backdrop,
                config = state.config
            )
        }
        item(key = "backup-actions") {
            GlassPreferenceSection("保存或恢复") {
                SettingsGroup(backdrop = backdrop, config = state.config, modifier = Modifier.fillMaxWidth()) {
                    SettingsActionRow(
                        title = "保存当前数据",
                        subtitle = "把上面的内容保存成一个 .sleepdown 备份文件",
                        buttonText = "保存",
                        iconRes = R.drawable.ic_share_schedule,
                        backdrop = backdrop,
                        onClick = {
                            if (busyLabel == null) exportLauncher.launch(defaultFileName)
                        }
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        title = "从备份恢复",
                        subtitle = "先看看备份里有什么；确认前不会改动现在的数据",
                        buttonText = "选择",
                        iconRes = R.drawable.ic_download,
                        backdrop = backdrop,
                        onClick = {
                            if (busyLabel == null) importLauncher.launch(arrayOf("*/*"))
                        }
                    )
                }
            }
        }
        if (busyLabel != null || statusMessage != null) {
            item(key = "backup-status") {
                BackupTaskStatusSection(
                    busyLabel = busyLabel,
                    statusMessage = statusMessage,
                    statusIsError = statusIsError,
                    backdrop = backdrop,
                    config = state.config
                )
            }
        }
    }
}

@Composable
fun BackupRestorePreviewScreen(
    state: AppState,
    backdrop: Backdrop?,
    source: Uri
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as CourseScheduleApp }
    val scope = rememberCoroutineScope()
    val topPadding = detailContentTopPadding()
    var busyLabel by remember(source) { mutableStateOf<String?>("正在检查备份文件…") }
    var statusMessage by remember(source) { mutableStateOf<String?>(null) }
    var statusIsError by remember(source) { mutableStateOf(false) }
    var preview by remember(source) { mutableStateOf<BackupRestorePreviewState?>(null) }
    var showReplaceConfirmation by remember(source) { mutableStateOf(false) }

    fun showFailure(title: String, guidance: String, error: Throwable) {
        Log.e("SleepDownBackup", title, error)
        statusIsError = true
        statusMessage = "$title。$guidance"
    }

    LaunchedEffect(source) {
        preview = null
        showReplaceConfirmation = false
        busyLabel = "正在检查备份文件…"
        statusMessage = null
        statusIsError = false
        runCatching {
            val input = context.contentResolver.openInputStream(source)
                ?: error("系统未能打开备份文件")
            val archive = input.use { BackupCodec.decode(it) }
            require(archive.manifest.sourcePackageName == app.packageName) {
                "备份来源 package 与当前应用不一致"
            }
            val operationId = newBackupRestoreOperationId()
            val restoreService = BackupRestoreService(app, app.database)
            val existing = restoreService.readTargetSnapshot()
            val plan = BackupImportPlanBuilder.build(archive, operationId, existing)
            BackupRestorePreviewState(
                archive = archive,
                operationId = operationId,
                report = BackupPreviewReportBuilder.build(archive, plan)
            )
        }.onSuccess { nextPreview ->
            preview = nextPreview
        }.onFailure { error ->
            showFailure(
                title = "无法读取这份备份",
                guidance = "它可能不是 SleepDown 备份、文件不完整，或来自暂不支持的版本；你现有的数据没有变化",
                error = error
            )
        }
        busyLabel = null
    }

    fun restorePreview(previewState: BackupRestorePreviewState) {
        scope.launch {
            busyLabel = "正在恢复，请不要关闭应用…"
            statusMessage = null
            statusIsError = false
            runCatching {
                // Read the latest target again immediately before Replace, because data may have
                // changed after this preview was created.
                BackupRestoreService(app, app.database).restore(
                    archive = previewState.archive,
                    operationId = previewState.operationId,
                    replaceConfirmed = true
                )
            }.onSuccess { result ->
                preview = null
                val warningSuffix = if (result.warnings.isEmpty()) {
                    ""
                } else {
                    " 有 ${result.warnings.size} 项内容需要你在恢复后重新检查。"
                }
                statusMessage = "恢复完成。$warningSuffix"
            }.onFailure { error ->
                showFailure(
                    title = "恢复没有完成",
                    guidance = "请重新打开 SleepDown 查看结果；如果恢复已经开始，应用会自动继续处理未完成的步骤",
                    error = error
                )
            }
            busyLabel = null
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topPadding,
            bottom = DockScrollPadding
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (busyLabel != null || statusMessage != null) {
            item(key = "backup-preview-status") {
                BackupTaskStatusSection(
                    busyLabel = busyLabel,
                    statusMessage = statusMessage,
                    statusIsError = statusIsError,
                    backdrop = backdrop,
                    config = state.config
                )
            }
        }
        preview?.let { previewState ->
            item(key = "backup-preview-content") {
                BackupPreviewContent(
                    preview = previewState,
                    backdrop = backdrop,
                    config = state.config,
                    onConfirm = { showReplaceConfirmation = true }
                )
            }
        }
    }

    val previewState = preview
    if (showReplaceConfirmation && previewState != null) {
        val report = previewState.report
        val replacementMessage = if (report.requiresExplicitReplaceConfirmation) {
            "确认后，当前课表、设置和相关图片会被这份备份替换，应用里不能撤销。API Key、登录状态和系统权限不会被改动。"
        } else {
            "确认后，会把这份备份里的课表、设置和图片恢复到应用。"
        }
        LiquidAlertDialog(
            title = if (report.requiresExplicitReplaceConfirmation) {
                "要用这份备份替换当前数据吗？"
            } else {
                "要恢复这份备份吗？"
            },
            message = replacementMessage,
            actions = listOf(
                LiquidAlertAction("返回检查", LiquidAlertActionStyle.Secondary) {
                    showReplaceConfirmation = false
                },
                LiquidAlertAction(
                    if (report.requiresExplicitReplaceConfirmation) "替换并恢复" else "确认恢复",
                    LiquidAlertActionStyle.Destructive
                ) {
                    showReplaceConfirmation = false
                    restorePreview(previewState)
                }
            ),
            backdrop = backdrop,
            config = state.config,
            onDismissRequest = { showReplaceConfirmation = false }
        )
    }
}

@Composable
private fun BackupTaskStatusSection(
    busyLabel: String?,
    statusMessage: String?,
    statusIsError: Boolean,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity
) {
    GlassPreferenceSection("当前进度") {
        SettingsGroup(backdrop = backdrop, config = config, modifier = Modifier.fillMaxWidth()) {
            busyLabel?.let { label ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
            }
            statusMessage?.let { message ->
                if (busyLabel != null) SettingsDivider()
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (statusIsError) "需要处理" else "已完成",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupPreviewContent(
    preview: BackupRestorePreviewState,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onConfirm: () -> Unit
) {
    val report = preview.report
    val visibleWarnings = report.warnings
        .filterNot { it.code == BackupImportWarningCode.MISSING_ASSET }
        .distinctBy { it.code }
    val nonMigratableItems = report.nonMigratableItems
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .filterNot { item ->
            item.contains("小组件") && visibleWarnings.any {
                it.code == BackupImportWarningCode.WIDGET_INSTANCE_REQUIRES_REBUILD
            }
        }
    val hasAttentionItems = report.missingAssetCount > 0 ||
        visibleWarnings.isNotEmpty() || nonMigratableItems.isNotEmpty()
    val contentPages = listOf(
        BackupGuidePage(
            R.drawable.ic_material_restore,
            "备份来源",
            "来自 SleepDown ${report.sourceAppVersionName}，保存于 ${formatBackupCreatedAt(report.createdAt)}。",
            tint = Color(0xFF4F8EE8)
        ),
        BackupGuidePage(
            R.drawable.ic_material_event,
            "课表与作息",
            "${report.scheduleCount} 份课表、${report.courseCount} 门课程、${report.periodSchemeCount} 套作息，共 ${report.periodCount} 个节次。",
            tint = Color(0xFF45A36B)
        ),
        BackupGuidePage(
            R.drawable.ic_material_settings_backup_restore,
            "助手与小组件",
            "包含 ${report.agentSessionCount} 天助手会话、${report.agentMessageCount} 条消息和 ${report.widgetAppearanceCount} 份小组件样式。",
            tint = Color(0xFF8A72D6)
        ),
        BackupGuidePage(
            R.drawable.ic_material_photo_library,
            "壁纸与图片",
            if (report.missingAssetCount == 0) {
                "${report.presentAssetCount} 张壁纸或图片都可以恢复。"
            } else {
                "${report.presentAssetCount} 张可以恢复，${report.missingAssetCount} 张在备份中找不到。"
            },
            tint = Color(0xFFE28A45)
        )
    )
    val checkPages = buildList {
        add(
            BackupGuidePage(
                R.drawable.ic_material_verified_user,
                if (!hasAttentionItems) "检查完成，可以恢复" else "检查完成，有内容需留意",
                if (!hasAttentionItems) {
                    "备份文件完整，可以继续恢复。"
                } else {
                    "课表和设置可以恢复；其余提示请左右滑动查看。"
                },
                tint = Color(0xFF43A36F)
            )
        )
        if (report.missingAssets.isNotEmpty()) {
            add(
                BackupGuidePage(
                    R.drawable.ic_material_photo_library,
                    "部分图片会跳过",
                    report.missingAssets
                        .map { backupAssetPurposeLabel(it.purpose) }
                        .distinct()
                        .joinToString("、", postfix = "在备份中找不到，其余内容仍可恢复。"),
                    tint = Color(0xFFE26D5A)
                )
            )
        }
        visibleWarnings.forEach { warning ->
            add(
                BackupGuidePage(
                    R.drawable.ic_material_settings_backup_restore,
                    "恢复提示",
                    userFacingBackupWarning(warning),
                    tint = Color(0xFFD99A36)
                )
            )
        }
        nonMigratableItems.forEachIndexed { index, item ->
            add(
                BackupGuidePage(
                    R.drawable.ic_material_verified_user,
                    "恢复后需要重新设置",
                    item,
                    tint = if (index % 2 == 0) Color(0xFF5A86D6) else Color(0xFF7B72D2)
                )
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackupGuideCarousel("备份里有什么", contentPages, backdrop, config)
        BackupGuideCarousel("恢复前检查", checkPages, backdrop, config)
        GlassPreferenceSection("准备恢复") {
            SettingsGroup(backdrop = backdrop, config = config, modifier = Modifier.fillMaxWidth()) {
                SettingsInfoRow(
                    "用这份备份恢复",
                    if (report.requiresExplicitReplaceConfirmation) {
                        "继续后还会再确认一次。确认后，当前课表和设置会被这份备份替换。"
                    } else {
                        "继续后还会再确认一次，然后把备份内容恢复到应用。"
                    }
                )
                SettingsDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    SettingsActionButton(
                        label = "继续恢复",
                        backdrop = backdrop,
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxWidth(),
                        destructive = true
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupGuideCarousel(
    sectionTitle: String,
    pages: List<BackupGuidePage>,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity
) {
    if (pages.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { pages.size })
    GlassPreferenceSection(sectionTitle) {
        SettingsGroup(backdrop = backdrop, config = config, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(272.dp)
                ) { pageIndex ->
                    val page = pages[pageIndex]
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 22.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(page.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(52.dp),
                            tint = page.tint ?: MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = page.title,
                            modifier = Modifier.padding(top = 15.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = page.message,
                            modifier = Modifier.padding(top = 7.dp),
                            style = if (page.message.length > 56) {
                                MaterialTheme.typography.bodySmall
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                ProjectPagerIndicator(
                    pagerState = pagerState,
                    pageCount = pages.size,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
    }
}

private fun formatBackupCreatedAt(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(value))
}.getOrDefault(value)

private fun defaultBackupFileName(): String =
    "SleepDown-Backup-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.sleepdown"

private fun backupAssetPurposeLabel(purpose: String): String = when (purpose) {
    BackupAssetPurpose.SCHEDULE_WALLPAPER -> "课表壁纸"
    BackupAssetPurpose.WIDGET_WALLPAPER -> "小组件壁纸"
    BackupAssetPurpose.AGENT_ATTACHMENT -> "助手图片"
    BackupAssetPurpose.AI_IMPORT_HISTORY_CONTEXT -> "AI 导入记录中的文件"
    BackupAssetPurpose.AI_IMPORT_SCREENSHOT -> "AI 导入截图"
    else -> "相关图片或文件"
}

private fun userFacingBackupWarning(warning: BackupImportWarning): String = when (warning.code) {
    BackupImportWarningCode.MISSING_ASSET -> "有图片或文件已找不到，恢复时会自动跳过"
    BackupImportWarningCode.WIDGET_INSTANCE_REQUIRES_REBUILD ->
        "小组件样式会保留，但桌面上的小组件需要重新添加"
    BackupImportWarningCode.NO_ACTIVE_SCHEDULE_SELECTED ->
        "备份没有标记当前课表，恢复后会自动选中第一份课表"
}

private const val BackupDocumentMimeType = "application/vnd.sleepdown"
