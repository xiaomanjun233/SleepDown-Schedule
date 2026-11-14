package com.xiaomanjun.sleepdownschedule.feature.importing.history

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.CardSelectionCheck
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.DetailActivityScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsVisualConfig
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsForwardIndicator
import com.xiaomanjun.sleepdownschedule.feature.importing.*
import com.xiaomanjun.sleepdownschedule.feature.importing.progress.*
import com.xiaomanjun.sleepdownschedule.glass.ui.glassForegroundColor
import com.xiaomanjun.sleepdownschedule.transition.ActivityTransitionCoordinator
import com.xiaomanjun.sleepdownschedule.transition.CrossActivityTransitionHost
import com.xiaomanjun.sleepdownschedule.transition.legacy.returnToScheduleHome
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.MiuixTheme

open class AiImportHistoryDetailActivityHost : ComponentActivity() {
    companion object {
        const val EntryIdExtra = "ai_import_history_entry_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ActivityTransitionCoordinator.prepareDestinationBeforeOnCreate(this)
        super.onCreate(savedInstanceState)
        ActivityTransitionCoordinator.installDestinationWindowBackground(this)
        enableEdgeToEdge()
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        val entryId = intent.getStringExtra(EntryIdExtra).orEmpty()
        val app = application as CourseScheduleApp
        setContent {
            val state by app.repository.state.collectAsStateWithLifecycle(AppState())
            CourseScheduleTheme(config = state.config) {
                val record by rememberHistoryRecord(entryId)
                CrossActivityTransitionHost(
                    activity = this@AiImportHistoryDetailActivityHost,
                    sourceContent = {
                        Box(Modifier.fillMaxSize().background(Color.Transparent)) {
                            record?.entry?.let {
                                AiImportHistoryRowContent(
                                    entry = it,
                                    modifier = Modifier.fillMaxSize(),
                                    textColor = glassForegroundColor(settingsVisualConfig(state.config))
                                )
                            }
                        }
                    }
                ) { requestClose ->
                    AiImportHistoryRecordContent(state.config, record, requestClose, { returnToScheduleHome() })
                }
            }
        }
    }
}

private data class LoadedHistoryRecord(val entry: AiImportHistoryEntry?)

@Composable
private fun rememberHistoryRecord(entryId: String): State<LoadedHistoryRecord?> {
    val context = LocalContext.current
    return produceState<LoadedHistoryRecord?>(null, context, entryId) {
        value = LoadedHistoryRecord(withContext(Dispatchers.IO) { AiImportHistoryStore.loadEntry(context, entryId) })
    }
}

@Composable
internal fun AiImportHistoryRecordPane(
    config: ScheduleConfigEntity,
    entryId: String,
    onBack: () -> Unit,
    onImportSubmitted: () -> Unit,
) {
    val record by rememberHistoryRecord(entryId)
    AiImportHistoryRecordContent(config, record, onBack, onImportSubmitted, embedded = true)
}

@Composable
private fun AiImportHistoryRecordContent(
    config: ScheduleConfigEntity,
    record: LoadedHistoryRecord?,
    onBack: () -> Unit,
    onImportSubmitted: () -> Unit,
    embedded: Boolean = false,
) {
    val entry = record?.entry
    val draft = remember(entry, config.id) { entry?.let { AiImportHistoryStore.restore(it, config).getOrNull() } }
    if (entry == null || draft == null) {
        DetailActivityScaffold(
            title = "导入历史", config = config, onBack = onBack, compactTopBar = true,
            centerCompactTitle = true, avoidHomeTabBar = embedded
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (record != null) "这条导入记录已不存在" else "正在读取导入记录…",
                    color = glassForegroundColor(settingsVisualConfig(config)).copy(alpha = 0.52f)
                )
            }
        }
        return
    }
    val progress = remember(entry, draft) { historyConversationProgress(entry, draft) }
    if (embedded) {
        AiEduImportConversationPane(
            config = config, onClose = onBack, onImportSubmitted = onImportSubmitted,
            historicalProgress = progress, historicalDraft = draft, historicalEntryId = entry.id,
            showHistoryAction = false, embedded = true
        )
    } else {
        AiEduImportProgressPage(
            config = config, onClose = onBack, onImportSubmitted = onImportSubmitted,
            historicalProgress = progress, historicalDraft = draft, historicalEntryId = entry.id,
            showHistoryAction = false
        )
    }
}

@Composable
internal fun AiImportHistoryRowContent(
    entry: AiImportHistoryEntry,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    checkedForDeletion: Boolean? = null,
    selectionEnabled: Boolean = true,
) {
    val locale = LocalConfiguration.current.locales[0]
    val (date, time) = remember(entry.createdAt, locale) {
        val created = Date(entry.createdAt)
        DateFormat.getDateInstance(DateFormat.SHORT, locale).format(created) to
            DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(created)
    }
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 68.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(entry.title, color = textColor, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ImportHistoryChip(date, textColor, Icons.Outlined.DateRange)
                ImportHistoryChip(time, textColor, Icons.Outlined.Schedule)
                ImportHistoryChip(entry.sourceSummary.ifBlank { "课表导入" }, textColor)
            }
        }
        if (checkedForDeletion == null) {
            SettingsForwardIndicator()
        } else {
            CardSelectionCheck(checkedForDeletion, selectionEnabled,
                dark = MaterialTheme.colorScheme.background.luminance() < .5f)
        }
    }
}

@Composable
private fun ImportHistoryChip(label: String, textColor: Color, icon: ImageVector? = null) {
    val foreground = textColor.copy(alpha = 0.78f)
    Row(
        modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            RoundedCornerShape(14.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let { Icon(it, null, Modifier.size(13.dp), tint = foreground.copy(alpha = 0.68f)) }
        Text(
            label,
            color = foreground,
            style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp, lineHeight = 17.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

internal fun historyConversationProgress(
    entry: AiImportHistoryEntry,
    draft: ImportDraft
): AiEduImportProgress {
    entry.context?.let { progress ->
        return progress.copy(
            awaitingConfirmation = false,
            confirmActionLabel = "",
            secondaryConfirmActionLabel = "",
            screenModeActionLabel = "",
            cancelActionLabel = "",
            finished = true,
            error = null
        )
    }
    val regularImport = draft.source == ImportDraftSource.STANDARD
    return AiEduImportProgress(
        routeLabel = entry.sourceSummary.ifBlank { "课表导入" },
        steps = listOf("已读取导入记录", "已恢复导入结果", "本地校验通过，可继续修改"),
        requestPreview = "记录时间：${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.createdAt))}",
        pageText = if (regularImport) "" else buildString {
            if (entry.sourceSummary.isNotBlank()) appendLine(entry.sourceSummary)
            append("已恢复 ${draft.courses.size} 门课程、${draft.periods.size} 个节次、${draft.config.totalWeeks} 周配置。")
        },
        userPrompt = if (regularImport) "" else entry.prompt.ifBlank { "帮我按规则导入这份课表" },
        attachmentTitle = if (regularImport) "" else entry.sourceSummary.ifBlank { "历史导入上下文" },
        requestSent = true,
        finished = true
    )
}
