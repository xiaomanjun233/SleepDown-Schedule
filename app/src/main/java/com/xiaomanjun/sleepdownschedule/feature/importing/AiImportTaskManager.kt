package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.AiImportForegroundService
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.model.ImportDraft
import com.xiaomanjun.sleepdownschedule.model.ImportDraftSource

import android.content.Context
import android.os.PowerManager
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private typealias RestartableAiImportRequest = suspend (
    AiImportInteraction, (AiImportHttpPhase) -> Unit, (String) -> Unit
) -> Result<AiScheduleImportResult>

/** Owns the single active AI import after the user has confirmed sending its input. */
object AiImportTaskManager {
    const val EXTRA_TASK_ID = "ai_import_task_id"

    private val pendingTasks = ConcurrentHashMap<String, suspend () -> Unit>()
    private data class RestartableImport(
        val taskId: String, val config: ScheduleConfigEntity, val settings: AiImportSettings,
        val interaction: AiImportInteraction, val request: RestartableAiImportRequest? = null,
        val resumeRevision: ((Context, AiEduImportProgress, String) -> String)? = null
    )
    @Volatile private var restartableImport: RestartableImport? = null

    fun canInterrupt(taskId: String): Boolean = restartableImport?.taskId == taskId

    fun pauseImport(context: Context, taskId: String): Boolean {
        val task = restartableImport?.takeIf { it.taskId == taskId } ?: return false
        val current = AiEduImportProgressSession.progress.value?.takeIf { it.taskId == taskId }
            ?: return false
        if (current.finished) return current.awaitingUserInput
        task.interaction.markCancelled()
        pendingTasks.remove(taskId)
        update(taskId) { it.copy(
            finished = true, awaitingUserInput = true, error = null,
            reasoningOutput = AiEduImportProgressSession.liveReasoning.value.text,
            liveSummary = "已停止当前解析。补充要求后可继续，已选材料会保留。"
        ) } ?: return false
        activeJob?.cancel()
        (context.applicationContext as CourseScheduleApp).applicationScope.launch(Dispatchers.IO) {
            task.interaction.disconnect()
        }
        AiImportForegroundService.finishRouting(context, taskId)
        return true
    }

    private fun cancelPreviousAttempt(context: Context) {
        val previous = restartableImport
        previous?.interaction?.markCancelled()
        activeJob?.cancel()
        restartableImport = null
        if (previous != null) {
            (context.applicationContext as CourseScheduleApp).applicationScope.launch(Dispatchers.IO) {
                previous.interaction.disconnect()
            }
        }
    }

    fun continueImport(context: Context, taskId: String, instruction: String): String? {
        val task = restartableImport?.takeIf { it.taskId == taskId } ?: return null
        val current = AiEduImportProgressSession.progress.value?.takeIf { it.taskId == taskId }
            ?: return null
        if (instruction.isBlank()) return null
        if (!current.finished) pauseImport(context, taskId)
        val prompt = buildString {
            appendLine(current.userPrompt)
            if (current.clarificationQuestions.isNotEmpty()) {
                appendLine("待确认：${current.clarificationQuestions.joinToString("；")}")
            }
            append("用户补充：${instruction.trim()}")
        }
        task.resumeRevision?.let { return it(context, current, prompt) }
        return startTask(context, current.copy(userPrompt = prompt), task.config, task.settings,
            task.request ?: return null)
    }

    /** Classification uses the existing transport/service, but never enters course JSON repair. */
    internal fun startEduRouting(
        context: Context,
        fingerprint: AiEduFingerprint,
        settings: AiImportSettings,
        initialProgress: AiEduImportProgress
    ): String {
        val appContext = context.applicationContext
        cancelPreviousAttempt(appContext)
        val taskId = UUID.randomUUID().toString()
        AiEduImportProgressSession.clearActions()
        AiEduImportProgressSession.setPreviewDraft(null)
        AiEduImportProgressSession.update(initialProgress.copy(taskId = taskId,
            awaitingConfirmation = false, finished = false, error = null,
            liveSummary = "正在判断可用的通用教务工具。"))
        pendingTasks.clear()
        pendingTasks[taskId] = {
            try {
                val decision = requestAiEduRouting(fingerprint, settings) { phase ->
                    if (phase == AiImportHttpPhase.BODY_WRITE_END) {
                        update(taskId) { it.copy(requestSent = true) }
                    }
                }
                update(taskId) { it.copy(aiOutput = decision, finished = true,
                    liveSummary = "教务识别完成，正在准备导入方式。") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Route failure can always return to the local text preview; do not retry or
                // attach a server response that might echo page data to the routing history.
                update(taskId) { it.copy(finished = true, aiOutput = "",
                    liveSummary = "教务识别未完成，可继续使用页面文本解析。") }
            } finally {
                AiImportForegroundService.finishRouting(appContext, taskId)
            }
        }
        AiImportForegroundService.start(appContext, taskId, "正在识别教务系统")
        return taskId
    }

    fun startFileImport(
        context: Context,
        file: AiImportFile,
        settings: AiImportSettings,
        scheduleConfig: ScheduleConfigEntity,
        initialProgress: AiEduImportProgress
    ): String {
        val appContext = context.applicationContext
        return startTask(appContext, initialProgress, scheduleConfig, settings) { interaction, onHttpPhase, onReasoningUpdate ->
            AiScheduleImportService(appContext, interaction).parseScheduleFile(
                file = file,
                settings = settings,
                onHttpPhase = onHttpPhase,
                onReasoningUpdate = onReasoningUpdate
            )
        }
    }

    fun startTextImport(
        context: Context,
        text: String,
        sourceName: String,
        settings: AiImportSettings,
        scheduleConfig: ScheduleConfigEntity,
        initialProgress: AiEduImportProgress
    ): String {
        val appContext = context.applicationContext
        return startTask(appContext, initialProgress, scheduleConfig, settings) { interaction, onHttpPhase, onReasoningUpdate ->
            AiScheduleImportService(appContext, interaction).parseScheduleText(
                text = text,
                sourceName = sourceName,
                settings = settings,
                onHttpPhase = onHttpPhase,
                onReasoningUpdate = onReasoningUpdate
            )
        }
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
    ): String {
        val appContext = context.applicationContext
        return startTask(appContext, initialProgress, scheduleConfig, settings) { interaction, onHttpPhase, onReasoningUpdate ->
            AiScheduleImportService(appContext, interaction).parseScheduleCapturedPage(
                text = text,
                screenshots = screenshots,
                sourceName = sourceName,
                warnings = warnings,
                settings = settings,
                onHttpPhase = onHttpPhase,
                onReasoningUpdate = onReasoningUpdate
            )
        }
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
        cancelPreviousAttempt(appContext)
        val taskId = UUID.randomUUID().toString()
        val interaction = AiImportInteraction(instruction)
        restartableImport = RestartableImport(taskId, baseDraft.config, settings, interaction,
            resumeRevision = { nextContext, progress, prompt ->
                startRevision(nextContext, baseDraft, prompt, progress, settings, historicalEntryId)
            })
        AiEduImportProgressSession.setPreviewDraft(baseDraft)
        AiEduImportProgressSession.update(
            baseProgress.copy(
                taskId = taskId,
                steps = baseProgress.steps + "正在整理输入",
                userPrompt = instruction,
                awaitingUserInput = false,
                clarificationQuestions = emptyList(),
                liveSummary = "正在整理修改要求、现有课程和原始材料。",
                requestSent = false,
                reasoningOutput = "",
                aiOutput = "",
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
                    historicalEntryId = historicalEntryId,
                    interaction = interaction
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                finishFailure(appContext, taskId, error, "AI 修改任务未完成")
            }
        }
        AiImportForegroundService.start(appContext, taskId, "正在整理输入")
        return taskId
    }

    private fun startTask(
        context: Context,
        initialProgress: AiEduImportProgress,
        scheduleConfig: ScheduleConfigEntity,
        settings: AiImportSettings,
        request: RestartableAiImportRequest
    ): String {
        val appContext = context.applicationContext
        cancelPreviousAttempt(appContext)
        val taskId = UUID.randomUUID().toString()
        val interaction = AiImportInteraction(initialProgress.userPrompt)
        restartableImport = RestartableImport(taskId, scheduleConfig, settings, interaction, request)
        AiEduImportProgressSession.setPreviewDraft(null)
        AiEduImportProgressSession.update(
            initialProgress.copy(
                taskId = taskId,
                awaitingConfirmation = false,
                awaitingUserInput = false,
                clarificationQuestions = emptyList(),
                confirmActionLabel = "",
                secondaryConfirmActionLabel = "",
                screenModeActionLabel = "",
                cancelActionLabel = "",
                requestSent = false,
                reasoningOutput = "",
                aiOutput = "",
                finished = false,
                error = null
            )
        )
        pendingTasks.clear()
        pendingTasks[taskId] = {
            try {
                runTask(appContext, taskId, scheduleConfig, settings, interaction) { phase, reasoning ->
                    request(interaction, phase, reasoning)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                finishFailure(appContext, taskId, error, "AI 导入任务未完成")
            }
        }
        AiImportForegroundService.start(appContext, taskId, "正在整理输入")
        return taskId
    }

    @Volatile
    private var activeJob: Job? = null

    // The workflow runs on the process application scope, not on the foreground service:
    // OEM battery guards may stop the service at any moment while the app is backgrounded,
    // and the in-flight AI request must survive that instead of dying with the service scope.
    internal fun launchPending(context: Context, taskId: String): Job? {
        val task = pendingTasks.remove(taskId) ?: return null
        activeJob?.cancel()
        val appContext = context.applicationContext
        val scope = (appContext as CourseScheduleApp).applicationScope
        return scope.launch(Dispatchers.IO) {
            // Each attempt owns its lock; cancelling an old attempt cannot release the new one.
            val wakeLock = acquireWakeLock(appContext)
            try {
                task()
            } finally {
                wakeLock?.takeIf { it.isHeld }?.release()
            }
        }.also { activeJob = it }
    }

    private fun acquireWakeLock(context: Context): PowerManager.WakeLock? =
        context.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SleepDown:ai_import")
            ?.apply { acquire(AI_IMPORT_WAKE_LOCK_TIMEOUT_MILLIS) }

    private suspend fun runTask(
        context: Context,
        taskId: String,
        scheduleConfig: ScheduleConfigEntity,
        settings: AiImportSettings,
        interaction: AiImportInteraction,
        request: suspend ((AiImportHttpPhase) -> Unit, (String) -> Unit) -> Result<AiScheduleImportResult>
    ) = coroutineScope {
        appendMainStep(taskId, context, "正在整理输入", "正在整理课程材料，准备发送给 AI。")
        val onHttpPhase: (AiImportHttpPhase) -> Unit = { phase ->
            when (phase) {
                AiImportHttpPhase.BODY_WRITE_START ->
                    appendMainStep(taskId, context, "正在上传材料", "正在上传课表材料。")
                AiImportHttpPhase.BODY_WRITE_END ->
                    appendMainStep(taskId, context, "已发送给 AI", "材料已发送，正在等待模型响应。")
                AiImportHttpPhase.FIRST_EVENT,
                AiImportHttpPhase.BODY_READ_START -> {
                    appendMainStep(taskId, context, "AI 正在解析课程", "模型已响应，正在等待摘要和课程结果。")
                }
                else -> Unit
            }
        }
        val onReasoningUpdate = AiEduImportProgressSession.beginReasoning(taskId)
        val result = request(onHttpPhase, onReasoningUpdate)
        currentCoroutineContext().ensureActive()
        val aiResult = result.getOrElse { error ->
            if (error is AiImportClarificationRequired) {
                val waiting = update(taskId) { it.copy(
                    finished = true, awaitingUserInput = true, clarificationQuestions = error.questions,
                    reasoningOutput = AiEduImportProgressSession.liveReasoning.value.text,
                    liveSummary = "材料还有关键信息需要确认，请回答下面的问题后继续。", error = null
                ) }
                if (waiting != null) AiImportForegroundService.finishRouting(context, taskId)
                return@coroutineScope
            }
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
        val repaired = AiImportRepairManager.parseWithRepair(
            initialResult = aiResult,
            scheduleConfig = scheduleConfig,
            onRepairAttempt = { attempt, _ ->
                appendMainStep(
                    taskId,
                    context,
                    "正在修复课程数据（$attempt/${AiImportRepairManager.MaxRepairAttempts}）",
                    "AI 返回格式需要调整，正在自动修复，不会重新上传原始材料。"
                )
            },
            requestRepair = { output, failure, _ ->
                currentCoroutineContext().ensureActive()
                AiScheduleImportService(context, interaction).repairScheduleJson(
                    output = output,
                    failure = failure,
                    settings = settings,
                    onHttpPhase = { phase ->
                        if (phase == AiImportHttpPhase.BODY_WRITE_END) {
                            updateMicroStatus(taskId, "修复请求已发送，正在等待模型返回 JSON。")
                        }
                    },
                    onReasoningUpdate = AiEduImportProgressSession.beginReasoning(taskId)
                ).onSuccess { repairResult ->
                    update(taskId) { progress ->
                        progress.copy(
                            reasoningOutput = repairResult.reasoningOutput.ifBlank { progress.reasoningOutput },
                            aiOutput = repairResult.rawOutput
                        )
                    }
                }
            }
        ).getOrElse { error ->
            finishFailure(context, taskId, error, "课程数据校验失败")
            return@coroutineScope
        }
        val parsed = repaired.draft
        currentCoroutineContext().ensureActive()
        appendMainStep(taskId, context, "正在生成导入预览", "课程数据已通过校验，正在整理导入预览。")
        val preview = parsed.copy(source = ImportDraftSource.AI_EDU)
        val completed = AiEduImportProgressSession.updateActiveTask(taskId, preview) {
            it.copy(
                steps = it.steps + "完成",
                liveSummary = "已整理出 ${preview.courses.size} 门课程，可以检查导入预览。",
                requestSent = true,
                finished = true,
                error = null
            )
        } ?: return@coroutineScope
        AiImportHistoryStore.record(context, preview, completed)
        if (restartableImport?.taskId == taskId) restartableImport = null
        AiImportForegroundService.complete(context, taskId, preview.courses.size)
    }

    private suspend fun runRevision(
        context: Context,
        taskId: String,
        baseDraft: ImportDraft,
        instruction: String,
        baseProgress: AiEduImportProgress,
        settings: AiImportSettings,
        historicalEntryId: String?,
        interaction: AiImportInteraction
    ) = coroutineScope {
        appendMainStep(taskId, context, "正在整理输入", "正在整理修改要求、现有课程和原始材料。")
        val onHttpPhase: (AiImportHttpPhase) -> Unit = { phase ->
            when (phase) {
                AiImportHttpPhase.BODY_WRITE_START ->
                    appendMainStep(taskId, context, "正在上传材料", "正在上传修改要求和课表材料。")
                AiImportHttpPhase.BODY_WRITE_END ->
                    appendMainStep(taskId, context, "已发送给 AI", "修改材料已发送，正在等待模型响应。")
                AiImportHttpPhase.FIRST_EVENT,
                AiImportHttpPhase.BODY_READ_START -> {
                    appendMainStep(taskId, context, "AI 正在解析课程", "模型已响应，正在等待课表修改结果。")
                }
                else -> Unit
            }
        }
        val result = AiScheduleImportService(context, interaction)
            .reviseSchedule(baseDraft, instruction, baseProgress, settings, onHttpPhase,
                AiEduImportProgressSession.beginReasoning(taskId))
            .getOrElse { error ->
                finishFailure(context, taskId, error, "AI 修改请求失败")
                return@coroutineScope
            }
        currentCoroutineContext().ensureActive()
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
        val previousTurns = baseProgress.conversationTurns.ifEmpty {
            listOf(
                AiEduImportConversationTurn(
                    userPrompt = baseProgress.userPrompt,
                    reasoningOutput = baseProgress.reasoningOutput,
                    aiOutput = baseProgress.aiOutput
                )
            )
        }
        val completed = AiEduImportProgressSession.updateActiveTask(taskId, revised) {
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
        } ?: return@coroutineScope
        if (historicalEntryId != null) {
            AiImportHistoryStore.update(context, historicalEntryId, revised, completed)
        } else {
            AiImportHistoryStore.updateMatching(context, baseDraft, revised, completed)
        }
        if (restartableImport?.taskId == taskId) restartableImport = null
        AiImportForegroundService.complete(context, taskId, revised.courses.size)
    }

    private fun appendMainStep(
        taskId: String,
        context: Context,
        step: String,
        summary: String
    ) {
        val updated = update(taskId) { progress ->
            progress.copy(
                steps = if (step in progress.steps) progress.steps else progress.steps + step,
                liveSummary = summary,
                requestSent = progress.requestSent || step == "已发送给 AI" || step == "AI 正在解析课程"
            )
        }
        if (updated != null) AiImportForegroundService.update(context, taskId, step)
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
        val updated = update(taskId) { progress ->
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
        if (updated != null) AiImportForegroundService.fail(context, taskId, error.message ?: step)
    }

    private fun update(
        taskId: String,
        transform: (AiEduImportProgress) -> AiEduImportProgress
    ): AiEduImportProgress? = AiEduImportProgressSession.updateActiveTask(taskId, transform = transform)

    private const val AI_IMPORT_WAKE_LOCK_TIMEOUT_MILLIS = 30L * 60L * 1_000L
}
