package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.AiImportForegroundService
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.model.ImportDraftSource

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/** Owns the single active AI import after the user has confirmed sending its input. */
object AiImportTaskManager {
    const val EXTRA_TASK_ID = "ai_import_task_id"

    private val taskScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    fun startFileImport(
        context: Context,
        file: AiImportFile,
        settings: AiImportSettings,
        scheduleConfig: ScheduleConfigEntity,
        initialProgress: AiEduImportProgress
    ): String = startTask(context, initialProgress, scheduleConfig) { onRequestStarted ->
        AiScheduleImportService(context.applicationContext).parseScheduleFile(
            file = file,
            settings = settings,
            onRequestStarted = onRequestStarted
        )
    }

    fun startTextImport(
        context: Context,
        text: String,
        sourceName: String,
        settings: AiImportSettings,
        scheduleConfig: ScheduleConfigEntity,
        initialProgress: AiEduImportProgress
    ): String = startTask(context, initialProgress, scheduleConfig) { onRequestStarted ->
        AiScheduleImportService(context.applicationContext).parseScheduleText(
            text = text,
            sourceName = sourceName,
            settings = settings,
            onRequestStarted = onRequestStarted
        )
    }

    fun startCapturedPageImport(
        context: Context,
        text: String,
        screenshots: List<RenderedPageImage>,
        sourceName: String,
        warnings: List<String>,
        settings: AiImportSettings,
        scheduleConfig: ScheduleConfigEntity,
        initialProgress: AiEduImportProgress
    ): String = startTask(context, initialProgress, scheduleConfig) { onRequestStarted ->
        AiScheduleImportService(context.applicationContext).parseScheduleCapturedPage(
            text = text,
            screenshots = screenshots,
            sourceName = sourceName,
            warnings = warnings,
            settings = settings,
            onRequestStarted = onRequestStarted
        )
    }

    private fun startTask(
        context: Context,
        initialProgress: AiEduImportProgress,
        scheduleConfig: ScheduleConfigEntity,
        request: suspend (() -> Unit) -> Result<AiScheduleImportResult>
    ): String {
        val appContext = context.applicationContext
        val taskId = UUID.randomUUID().toString()
        activeJob?.cancel()
        AiEduImportProgressSession.setPreviewDraft(null)
        AiEduImportProgressSession.update(
            initialProgress.copy(
                taskId = taskId,
                awaitingConfirmation = false,
                confirmActionLabel = "",
                secondaryConfirmActionLabel = "",
                screenModeActionLabel = "",
                cancelActionLabel = "",
                requestSent = false,
                finished = false,
                error = null
            )
        )
        AiImportForegroundService.start(appContext, taskId, "正在整理输入")
        activeJob = taskScope.launch {
            try {
                runTask(appContext, taskId, scheduleConfig, request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                finishFailure(appContext, taskId, error, "AI 导入任务未完成")
            }
        }
        return taskId
    }

    private suspend fun runTask(
        context: Context,
        taskId: String,
        scheduleConfig: ScheduleConfigEntity,
        request: suspend (() -> Unit) -> Result<AiScheduleImportResult>
    ) = coroutineScope {
        appendMainStep(taskId, context, "正在整理输入", "正在整理课程材料，准备发送给 AI。")
        var summaryTicker: Job? = null
        val result = request {
            appendMainStep(taskId, context, "已发送给 AI", "材料已发送，正在等待模型开始解析。")
            appendMainStep(taskId, context, "AI 正在解析课程", "正在识别课程结构。")
            summaryTicker = launch {
                listOf(
                    "正在整理课程名称与教师",
                    "正在核对星期和节次",
                    "正在检查周次范围",
                    "正在等待模型返回完整结果"
                ).forEach { summary ->
                    delay(2_600)
                    if (isActive) updateMicroStatus(taskId, summary)
                }
            }
        }
        summaryTicker?.cancel()
        val aiResult = result.getOrElse { error ->
            finishFailure(context, taskId, error, "AI 请求失败")
            return@coroutineScope
        }
        update(taskId) {
            it.copy(
                reasoningOutput = aiResult.reasoningOutput,
                aiOutput = aiResult.rawOutput
            )
        }
        appendMainStep(taskId, context, "正在校验课程数据", "正在检查星期、节次、周次和重复课程。")
        val parsed = ScheduleImportParser.parse(
            aiResult.output.ifBlank { aiResult.rawOutput },
            scheduleConfig
        ).getOrElse { error ->
            finishFailure(context, taskId, error, "课程数据校验失败")
            return@coroutineScope
        }
        appendMainStep(taskId, context, "正在生成导入预览", "课程数据已通过校验，正在整理导入预览。")
        val preview = parsed.copy(source = ImportDraftSource.AI_EDU)
        AiEduImportProgressSession.setPreviewDraft(preview)
        val completed = update(taskId) {
            it.copy(
                steps = it.steps + "完成",
                liveSummary = "已整理出 ${preview.courses.size} 门课程，可以检查导入预览。",
                requestSent = true,
                finished = true,
                error = null
            )
        }
        AiImportHistoryStore.record(context, preview, completed)
        AiImportForegroundService.complete(context, taskId, preview.courses.size)
    }

    private fun appendMainStep(
        taskId: String,
        context: Context,
        step: String,
        summary: String
    ) {
        update(taskId) { progress ->
            progress.copy(
                steps = if (progress.steps.lastOrNull() == step) progress.steps else progress.steps + step,
                liveSummary = summary,
                requestSent = progress.requestSent || step == "已发送给 AI" || step == "AI 正在解析课程"
            )
        }
        AiImportForegroundService.update(context, taskId, step)
    }

    private fun updateMicroStatus(taskId: String, summary: String) {
        update(taskId) { progress -> progress.copy(liveSummary = summary) }
    }

    private fun finishFailure(
        context: Context,
        taskId: String,
        error: Throwable,
        step: String
    ) {
        val rawBody = error.aiRawResponseBody().orEmpty()
        update(taskId) { progress ->
            progress.copy(
                steps = progress.steps + step,
                liveSummary = error.message ?: step,
                reasoningOutput = extractAiReasoningForDisplay(rawBody).ifBlank { progress.reasoningOutput },
                aiOutput = sanitizeAiOutputForDisplay(rawBody).ifBlank { progress.aiOutput },
                requestSent = progress.requestSent,
                error = error.message ?: step,
                finished = true
            )
        }
        AiImportForegroundService.fail(context, taskId, error.message ?: step)
    }

    private fun update(
        taskId: String,
        transform: (AiEduImportProgress) -> AiEduImportProgress
    ): AiEduImportProgress? {
        val current = AiEduImportProgressSession.progress.value
            ?.takeIf { it.taskId == taskId }
            ?: return null
        return transform(current).copy(taskId = taskId).also(AiEduImportProgressSession::update)
    }
}
