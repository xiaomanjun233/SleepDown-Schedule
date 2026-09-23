package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.feature.importing.history.*
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.*
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.feature.home.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.*
import com.xiaomanjun.sleepdownschedule.feature.settings.*
import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.agent.*
import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import android.os.Message
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.catalog.components.LiquidPanel
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


private fun importDraftConfigurationSummary(draft: ImportDraft): String = buildList {
    add("${draft.config.totalWeeks} 周")
    add("${draft.periods.size} 个节次")
    draft.config.termStartDate?.takeIf(String::isNotBlank)?.let { add("开学 $it") }
    add("每节 ${draft.config.classDurationMinutes} 分钟")
    add("课间 ${draft.config.breakDurationMinutes} 分钟")
}.joinToString(" · ")

@Composable
fun ConfirmScheduleScreen(
    draft: ImportDraft,
    warning: String? = null,
    backdrop: Backdrop? = null,
    onCancel: () -> Unit,
    onDraftChanged: ((ImportDraft) -> Unit)? = null,
    onConfirm: (Boolean) -> Unit
) {
    val historyContext = LocalContext.current
    val previewDraft = remember(draft) {
        draft.copy(
            periods = draft.periods.distinctBy { it.periodIndex }.sortedBy { it.periodIndex },
            courses = draft.courses.map {
                it.copy(
                    periods = it.periods.distinct().sorted(),
                    weeks = it.weeks.distinct().sorted()
                )
            }
        )
    }
    if (previewDraft.source == ImportDraftSource.AI_EDU) {
        LaunchedEffect(previewDraft) {
            AiImportHistoryStore.record(historyContext, previewDraft, AiEduImportProgressSession.progress.value)
        }
        AiImportChatPreview(
            draft = previewDraft,
            warning = warning,
            backdrop = backdrop,
            onCancel = onCancel,
            onDraftChanged = onDraftChanged,
            onConfirm = onConfirm
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("即将导入 " + previewDraft.courses.size + " 门课程，请确认后写入课表。") }
        warning?.let { text ->
            item { Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
        item { Text(importDraftConfigurationSummary(previewDraft)) }
        if (previewDraft.courses.isEmpty()) {
            item { Text("没有解析到课程", color = MaterialTheme.colorScheme.error) }
        } else {
            itemsIndexed(
                previewDraft.courses,
                key = { index, course ->
                    "preview_${index}_${course.name}_${course.weekday}_${course.periods.joinToString("_")}_${course.weeks.take(3).joinToString("_")}"
                }
            ) { _, course ->
                ImportPreviewCourseCard(course, previewDraft.periods, previewDraft.config)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("请选择导入方式：", style = MaterialTheme.typography.bodyMedium)
                LiquidAlertActions(
                    actions = listOf(
                        LiquidAlertAction("创建新课表", LiquidAlertActionStyle.Primary) { onConfirm(true) },
                        LiquidAlertAction("覆盖当前课表", LiquidAlertActionStyle.Destructive) { onConfirm(false) },
                        LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary, onClick = onCancel)
                    ),
                    backdrop = backdrop,
                    config = previewDraft.config
                )
            }
        }
    }
}

internal fun AiImportHistoryBackgroundCapture.cropToAiHistorySource(bounds: Rect): Bitmap? =
    runCatching {
        val left = (bounds.left - rootLeftInWindow).roundToInt()
        val top = (bounds.top - rootTopInWindow).roundToInt()
        val cropWidth = bounds.width.roundToInt()
        val cropHeight = bounds.height.roundToInt()
        // Do not clamp an invalid window/root conversion to an edge pixel. A partial source is
        // worse than no transition because it makes the button disappear before a malformed
        // black shell takes over.
        if (left < 0 || top < 0 || cropWidth <= 0 || cropHeight <= 0 ||
            left + cropWidth > bitmap.width || top + cropHeight > bitmap.height
        ) {
            return@runCatching null
        }
        Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
    }.getOrNull()

@Composable
private fun AiImportChatPreview(
    draft: ImportDraft,
    warning: String?,
    backdrop: Backdrop?,
    onCancel: () -> Unit,
    onDraftChanged: ((ImportDraft) -> Unit)?,
    onConfirm: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val progress by AiEduImportProgressSession.progress.collectAsStateWithLifecycle()
    val sessionDraft by AiEduImportProgressSession.previewDraft.collectAsStateWithLifecycle()
    val textColor = glassForegroundColor(settingsVisualConfig(draft.config))
    var traceExpanded by remember { mutableStateOf(false) }
    var revisionText by remember(draft) { mutableStateOf("") }
    var revising by remember { mutableStateOf(false) }
    var ownedRevisionTaskId by remember { mutableStateOf<String?>(null) }
    var revisionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ownedRevisionTaskId, progress?.taskId, progress?.finished, progress?.error, sessionDraft) {
        val taskId = ownedRevisionTaskId ?: return@LaunchedEffect
        val taskProgress = progress?.takeIf { it.taskId == taskId && it.finished } ?: return@LaunchedEffect
        revisionError = taskProgress.error
        if (taskProgress.error == null) {
            sessionDraft?.let { revised ->
                revisionText = ""
                onDraftChanged?.invoke(revised)
            }
        }
        revising = false
        ownedRevisionTaskId = null
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedRectangle(10.dp))
                            .background(ComposeColor(0xFF0A84FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "AI",
                            color = ComposeColor.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "我已整理出 ${draft.courses.size} 门课程，并完成节次、周次和时间校验。请检查下面的预览，确认后才会写入课表。",
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        warning?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            progress?.let { importProgress ->
                val statuses = aiEduAgentRunStatuses(importProgress)
                if (statuses.isNotEmpty()) {
                    item {
                        AgentRunTrace(
                            statuses = statuses,
                            expanded = traceExpanded,
                            foreground = textColor,
                            active = false,
                            onToggle = { traceExpanded = !traceExpanded }
                        )
                    }
                }
            }
            item {
                Text(
                    "导入预览 · ${importDraftConfigurationSummary(draft)}",
                    color = textColor.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (draft.courses.isEmpty()) {
                item { Text("没有解析到课程", color = MaterialTheme.colorScheme.error) }
            } else {
                itemsIndexed(
                    draft.courses,
                    key = { index, course ->
                        "ai_preview_${index}_${course.name}_${course.weekday}_${course.periods.joinToString("_")}"
                    }
                ) { _, course ->
                    ImportPreviewCourseCard(course, draft.periods, draft.config)
                }
            }
            revisionError?.let { message ->
                item {
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onDraftChanged != null) {
                AiEduRevisionComposer(
                    value = revisionText,
                    enabled = !revising,
                    textColor = textColor,
                    config = draft.config,
                    backdrop = backdrop,
                    onValueChange = { revisionText = it.take(500) },
                    onSend = {
                        val instruction = revisionText.trim()
                        if (instruction.isNotEmpty() && !revising) {
                            revising = true
                            revisionError = null
                            val settings = AiImportSettingsStore.load(context)
                            val baseProgress = progress ?: AiEduImportProgress(routeLabel = "AI 手动导入")
                            ownedRevisionTaskId = AiImportTaskManager.startRevision(
                                context = context,
                                baseDraft = draft,
                                instruction = instruction,
                                baseProgress = baseProgress,
                                settings = settings,
                                historicalEntryId = null
                            )
                        }
                    }
                )
            }
            LiquidAlertActions(
                actions = listOf(
                    LiquidAlertAction("创建新课表", LiquidAlertActionStyle.Primary) { onConfirm(true) },
                    LiquidAlertAction("覆盖当前课表", LiquidAlertActionStyle.Destructive) { onConfirm(false) },
                    LiquidAlertAction("返回检查", LiquidAlertActionStyle.Secondary, onClick = onCancel)
                ),
                backdrop = backdrop,
                config = draft.config
            )
        }
    }
}

@Composable
private fun AiEduRevisionComposer(
    value: String,
    enabled: Boolean,
    textColor: ComposeColor,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val shape = RoundedRectangle(28.dp)
    val content: @Composable BoxScope.() -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                singleLine = false,
                maxLines = 4,
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) {
                            Text(
                                if (enabled) "告诉 AI 哪里需要修改…" else "AI 正在修改…",
                                color = textColor.copy(alpha = 0.46f)
                            )
                        }
                        inner()
                    }
                }
            )
            DialogLiquidButton(
                backdrop = backdrop,
                label = if (enabled) "发送" else "处理中",
                role = DialogButtonRole.Confirm,
                roundIcon = false,
                modifier = Modifier.height(44.dp),
                onClick = onSend
            )
        }
    }
    if (backdrop != null) {
        LiquidPanel(
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            surfaceColor = if (glassUsesLightStyle(config)) ComposeColor.White.copy(alpha = 0.22f) else ComposeColor(0xFF161618).copy(alpha = 0.40f),
            blurRadius = 12.dp,
            content = content
        )
    } else {
        Box(
            modifier = Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            content = content
        )
    }
}

internal fun buildAiRevisionInput(
    draft: ImportDraft,
    instruction: String,
    history: AiEduImportProgress? = null
): String {
    val compactSchedule = buildString {
        appendLine("总周数：${draft.config.totalWeeks}")
        appendLine("节次：${draft.periods.sortedBy { it.periodIndex }.joinToString("；") { "${it.periodIndex}:${it.startTime}-${it.endTime}" }}")
        draft.courses.forEachIndexed { index, course ->
            appendLine(
                "#${index + 1} ${course.name} | 教师:${course.teacher.orEmpty()} | 地点:${course.location.orEmpty()} | " +
                    "周${course.weekday} | 节:${course.periods.joinToString(",")} | 周次:${course.weeks.joinToString(",")} | ${course.weekParity} | " +
                    "真实时间:${course.customStartTime?.let { "$it-${course.customEndTime}" } ?: "未设置"} | 逐节:${course.customPeriodTimes ?: "默认"} | 备注:${course.note.orEmpty()}"
            )
        }
    }.trim()
    val priorRequests = history?.conversationTurns.orEmpty()
        .map { it.userPrompt }
        .ifEmpty { listOfNotNull(history?.userPrompt?.takeIf(String::isNotBlank)) }
        .joinToString("\n") { "- $it" }
    return """
        这是当前已经通过本地校验的课表索引。每条课程前的 #编号 是稳定定位符：
        $compactSchedule

        ${if (priorRequests.isNotBlank()) "此前用户要求：\n$priorRequests\n" else ""}
        用户要求：$instruction
        只修改用户明确指出的内容，保留其他课程、周次和节次。
        课程真实时间与默认节次作息是独立字段。同节次在不同教学区域可能有不同时间和课间。replace_course 的 customStartTime、customEndTime 填 null 表示保留原值，两个都填空字符串表示清除，修改时两个都填 HH:mm。教务导入的逐节铃声只能保留原值，不能由 AI 新增或推算。
        直接调用 PATCH_SCHEDULE：replace_course 以 #编号完整替换一门课，add_course 新增，remove_course 删除，replace_periods 修改作息时间，set_total_weeks 修改总周数。只提交必要操作，不要回传完整课表。
        changeSummary 必须逐项说明本轮实际改变了哪些课程及字段；没有改动时明确说明原因，禁止写泛泛的“已完成修改”。
        只有用户要求复核、重新识别或核对原网页/附件，而当前课表不足以判断时，才调用 READ_ORIGINAL_IMPORT_SOURCE。不要为了普通字段修改读取原始材料。
    """.trimIndent()
}

internal fun buildAiOriginalSourceContext(history: AiEduImportProgress): String = buildString {
    appendLine("以下是本次导入留存的原始上下文：")
    if (history.attachmentTitle.isNotBlank()) appendLine("附件：${history.attachmentTitle}")
    if (history.requestPreview.isNotBlank()) appendLine("请求信息：\n${history.requestPreview}")
    if (history.pageText.isNotBlank()) appendLine("原始文本：\n${history.pageText}")
    val turns = history.conversationTurns.ifEmpty {
        listOf(
            AiEduImportConversationTurn(
                userPrompt = history.userPrompt,
                reasoningOutput = history.reasoningOutput,
                aiOutput = history.aiOutput
            )
        )
    }
    turns.forEachIndexed { index, turn ->
        appendLine("第 ${index + 1} 轮用户要求：${turn.userPrompt}")
        if (turn.reasoningOutput.isNotBlank()) appendLine("第 ${index + 1} 轮推理摘要：\n${turn.reasoningOutput}")
        if (turn.aiOutput.isNotBlank()) appendLine("第 ${index + 1} 轮原始输出：\n${turn.aiOutput}")
    }
    if (history.screenshotPreviews.isNotEmpty()) {
        append("另附 ${history.screenshotPreviews.size} 张按原顺序保存的视觉材料。")
    }
}

