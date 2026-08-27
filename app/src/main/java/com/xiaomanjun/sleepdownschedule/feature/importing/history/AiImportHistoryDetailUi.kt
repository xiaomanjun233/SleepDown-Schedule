package com.xiaomanjun.sleepdownschedule.feature.importing.history

import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.*

import com.xiaomanjun.sleepdownschedule.feature.importing.*
import com.xiaomanjun.sleepdownschedule.feature.importing.progress.*

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import top.yukonga.miuix.kmp.basic.BasicComponent as MiuixBasicComponent
import com.xiaomanjun.sleepdownschedule.transition.ActivityTransitionCoordinator
import com.xiaomanjun.sleepdownschedule.transition.CrossActivityTransitionHost
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId

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
                val entry = remember(entryId) {
                    AiImportHistoryStore.load(this).firstOrNull { it.id == entryId }
                }
                val draft = remember(entry, state.config.id) {
                    entry?.let { AiImportHistoryStore.restore(it, state.config).getOrNull() }
                }
                var returnToMain by remember { mutableStateOf(false) }
                CrossActivityTransitionHost(
                    activity = this@AiImportHistoryDetailActivityHost,
                    onFinished = {
                        if (returnToMain) {
                            ActivityTransitionCoordinator.openImmediate(
                                this@AiImportHistoryDetailActivityHost,
                                TransitionRouteId.ReturnToHome,
                                Intent(this@AiImportHistoryDetailActivityHost, MainActivity::class.java).addFlags(
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                )
                            )
                        }
                    },
                    sourceContent = {
                        Box(Modifier.fillMaxSize().background(Color.Transparent)) {
                            entry?.let {
                                AiImportHistoryRowContent(
                                    entry = it,
                                    modifier = Modifier.fillMaxSize(),
                                    textColor = glassForegroundColor(settingsVisualConfig(state.config))
                                )
                            }
                        }
                    }
                ) { requestClose ->
                    if (entry == null || draft == null) {
                        DetailActivityScaffold(
                            title = "导入历史",
                            config = state.config,
                            onBack = requestClose,
                            compactTopBar = true,
                            centerCompactTitle = true
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "这条导入记录已不存在",
                                    color = glassForegroundColor(settingsVisualConfig(state.config)).copy(alpha = 0.52f)
                                )
                            }
                        }
                    } else {
                        AiEduImportProgressPage(
                            config = state.config,
                            onClose = requestClose,
                            historicalProgress = historyConversationProgress(entry, draft),
                            historicalDraft = draft,
                            historicalEntryId = entry.id,
                            showHistoryAction = false,
                            onImportRequested = { restored, createNewSchedule ->
                                AiEduImportProgressSession.requestFinalImport(restored, createNewSchedule)
                                returnToMain = true
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AiImportHistoryRowContent(
    entry: AiImportHistoryEntry,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White
) {
    val summary = listOf(
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(entry.createdAt)),
        entry.sourceSummary,
        entry.prompt
    ).filter { it.isNotBlank() }.joinToString(" · ")
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(entry.title, color = textColor, style = MaterialTheme.typography.titleMedium)
        Text(
            summary,
            color = textColor.copy(alpha = 0.68f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2
        )
    }
}

internal fun historyConversationProgress(
    entry: AiImportHistoryEntry,
    draft: ImportDraft
): AiEduImportProgress = entry.context?.copy(
    awaitingConfirmation = false,
    confirmActionLabel = "",
    secondaryConfirmActionLabel = "",
    screenModeActionLabel = "",
    cancelActionLabel = "",
    finished = true,
    error = null
) ?: AiEduImportProgress(
    routeLabel = "AI 教务导入",
    steps = listOf("已读取历史会话", "已恢复导入结果", "本地校验通过，可继续修改"),
    requestPreview = "记录时间：${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.createdAt))}",
    pageText = buildString {
        if (entry.sourceSummary.isNotBlank()) appendLine(entry.sourceSummary)
        append("已恢复 ${draft.courses.size} 门课程、${draft.periods.size} 个节次、${draft.config.totalWeeks} 周配置。")
    },
    userPrompt = entry.prompt.ifBlank { "帮我按规则导入这份课表" },
    attachmentTitle = entry.sourceSummary.ifBlank { "历史导入上下文" },
    requestSent = true,
    finished = true
)
