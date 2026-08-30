package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.AiImportForegroundService
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.model.ImportDraft
import com.xiaomanjun.sleepdownschedule.model.ImportDraftSource

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Owns the single active AI import after the user has confirmed sending its input. */
object AiImportTaskManager {
    const val EXTRA_TASK_ID = "ai_import_task_id"

    private val pendingTasks = ConcurrentHashMap<String, suspend () -> Unit>()

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

    fun startRevision(
        context: Context,
        baseDraft: ImportDraft,
        instruction: String,
        baseProgress: AiEduImportProgress,
        settings: AiImportSettings,
        historicalEntryId: String?
    ): String {
        val appContext = context.applicationContext
        val taskId = baseProgress.taskId.ifBlank { UUID.randomUUID().toString() }
        AiEduImportProgressSession.setPreviewDraft(baseDraft)
        AiEduImportProgressSession.update(
            baseProgress.copy(
                taskId = taskId,
                steps = baseProgress.steps + "正在理解你的修改要求",
                userPrompt = instruction,
                liveSummary = "正在理解你的修改要求，并核对现有课程、周次和节次。",
                requestSent = true,
                finished = false,
                error = null
            )
        )
        pendingTasks.clear()
        pendingTasks[taskId] = {
            try {
                runRevision(
                    context = appContext,
                    taskId = taskId,
                    baseDraft = baseDraft,
                    instruction = instruction,
                    baseProgress = baseProgress,
                    settings = settings,
                    historicalEntryId = historicalEntryId
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                finishFailure(appContext, taskId, error, "AI 修改任务未完成")
            }
        }
        AiImportForegroundService.start(appContext, taskId, "正在理解修改要求")
        return taskId
    }

    private fun startTask(
        context: Context,
        initialProgress: AiEduImportProgress,
        scheduleConfig: ScheduleConfigEntity,
        request: suspend (() -> Unit) -> Result<AiScheduleImportResult>
    ): String {
        val appContext = context.applicationContext
        val taskId = UUID.randomUUID().toString()
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
        pendingTasks.clear()
        pendingTasks[taskId] = {
            try {
                runTask(appContext, taskId, scheduleConfig, request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                finishFailure(appContext, taskId, error, "AI 导入任务未完成")
            }
        }
        AiImportForegroundService.start(appContext, taskId, "正在整理输入")
        return taskId
    }

    internal fun launchPending(taskId: String, scope: CoroutineScope): Job? =
        pendingTasks.remove(taskId)?.let { task -> scope.launch { task() } }

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

    private suspend fun runRevision(
        context: Context,
        taskId: String,
        baseDraft: ImportDraft,
        instruction: String,
        baseProgress: AiEduImportProgress,
        settings: AiImportSettings,
        historicalEntryId: String?
    ) = coroutineScope {
        appendMainStep(taskId, context, "AI 正在修改课程", "正在核对现有课表结构并生成修改方案。")
        val summaryTicker = launch {
            listOf(
                "正在核对课程、周次和节次",
                "正在生成课程修改方案",
                "正在等待模型返回完整结果"
            ).forEach { summary ->
                delay(2_600)
                if (isActive) updateMicroStatus(taskId, summary)
            }
        }
        val result = AiScheduleImportService(context)
            .reviseSchedule(baseDraft, instruction, baseProgress, settings)
            .getOrElse { error ->
                summaryTicker.cancel()
                finishFailure(context, taskId, error, "AI 修改请求失败")
                return@coroutineScope
            }
        summaryTicker.cancel()
        update(taskId) {
            it.copy(reasoningOutput = result.reasoningOutput, aiOutput = result.rawOutput)
        }
        appendMainStep(taskId, context, "正在校验课程数据", "正在检查修改后的星期、节次和周次。")
        val revised = ScheduleImportParser.parse(
            result.output.ifBlank { result.rawOutput },
            baseDraft.config
        ).getOrElse { error ->
            finishFailure(context, taskId, error, "修改结果校验失败")
            return@coroutineScope
        }.copy(source = ImportDraftSource.AI_EDU)
        appendMainStep(taskId, context, "正在生成导入预览", "修改结果已通过校验，正在更新导入预览。")
        AiEduImportProgressSession.setPreviewDraft(revised)
        val previousTurns = baseProgress.conversationTurns.ifEmpty {
            listOf(
                AiEduImportConversationTurn(
                    userPrompt = baseProgress.userPrompt,
                    reasoningOutput = baseProgress.reasoningOutput,
                    aiOutput = baseProgress.aiOutput
                )
            )
        }
        val completed = update(taskId) {
            it.copy(
                steps = it.steps + "完成",
                userPrompt = instruction,
                requestSent = true,
                reasoningOutput = result.reasoningOutput,
                aiOutput = result.rawOutput,
                liveSummary = result.reasoningOutput.ifBlank {
                    "本轮已按你的要求更新课表，并通过本地校验。"
                },
                finished = true,
                error = null,
                conversationTurns = previousTurns + AiEduImportConversationTurn(
                    userPrompt = instruction,
                    reasoningOutput = result.reasoningOutput,
                    aiOutput = result.rawOutput
                )
            )
        }
        if (completed != null) {
            if (historicalEntryId != null) {
                AiImportHistoryStore.update(context, historicalEntryId, revised, completed)
            } else {
                AiImportHistoryStore.updateMatching(context, baseDraft, revised, completed)
            }
        }
        AiImportForegroundService.complete(context, taskId, revised.courses.size)
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
