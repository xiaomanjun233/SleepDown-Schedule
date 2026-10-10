package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.*

import com.xiaomanjun.sleepdownschedule.feature.agent.*

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class AiEduImportProgress(
    val taskId: String = "",
    val steps: List<String> = emptyList(),
    val routeLabel: String = "",
    val requestPreview: String = "",
    val pageText: String = "",
    val hasReadablePageText: Boolean? = null,
    val screenshotPreviews: List<RenderedPageImage> = emptyList(),
    val userPrompt: String = "帮我按规则导入这份课表",
    val requestInstructions: String = "",
    val attachmentTitle: String = "",
    val requestSent: Boolean = false,
    val reasoningOutput: String = "",
    val aiOutput: String = "",
    val assistantMessage: String = "",
    /** Human-readable state while the model is still processing the active request. */
    val liveSummary: String = "",
    val awaitingConfirmation: Boolean = false,
    val awaitingUserInput: Boolean = false,
    val clarificationQuestions: List<String> = emptyList(),
    val confirmActionLabel: String = "",
    val secondaryConfirmActionLabel: String = "",
    val screenModeActionLabel: String = "",
    val cancelActionLabel: String = "返回重抓",
    val confirmationTitle: String = "",
    val confirmationMessage: String = "",
    val returnToBrowser: Boolean = false,
    val finished: Boolean = false,
    val error: String? = null,
    val conversationTurns: List<AiEduImportConversationTurn> = emptyList(),
    val checkpoints: List<AiImportCheckpoint> = emptyList(),
    val selectedCheckpointId: String? = null,
    val checkpointSelectionLocked: Boolean = false,
    val checkpointNotice: String? = null,
    val checkpointRevision: Long = 0,
    val checkpointLineageId: String = "",
    val checkpointGeneration: Long = 0,
    val activityReports: List<AiImportReport> = emptyList()
)

data class AiEduImportConversationTurn(
    val userPrompt: String,
    val reasoningOutput: String = "",
    val aiOutput: String = "",
    val artifactPayload: String = "",
    val status: String = "已完成",
    val assistantMessage: String = ""
)

internal fun archiveImportTurn(progress: AiEduImportProgress, draft: ImportDraft? = null): List<AiEduImportConversationTurn> {
    val turn = AiEduImportConversationTurn(progress.userPrompt, progress.reasoningOutput, progress.aiOutput,
        draft?.let { draftToPayload(it).toString() }.orEmpty(),
        when { progress.error != null -> progress.error; progress.awaitingUserInput -> "已停止，等待补充"; else -> "已完成" },
        progress.assistantMessage)
    val previous = progress.conversationTurns
    val archived = if (previous.lastOrNull()?.let { it.userPrompt == turn.userPrompt && it.aiOutput == turn.aiOutput } == true)
        previous.dropLast(1) + turn else previous + turn
    return boundedImportConversationTurns(archived)
}

enum class AiEduImportStepStatus {
    Done,
    Current,
    Pending,
    Error
}

data class AiEduImportStepRow(
    val text: String,
    val status: AiEduImportStepStatus
)

/** Maps the import pipeline onto the same user-visible execution vocabulary as Today Agent. */
fun aiEduAgentRunStatuses(progress: AiEduImportProgress): List<AgentRunStatus> =
    progress.steps.map { step ->
        val icon = when {
            step.contains("页面") || step.contains("DOM") || step.contains("截图") || step.contains("识屏") ->
                AgentRunStatusIcon.SEARCH
            step.contains("配置") -> AgentRunStatusIcon.SETTINGS
            step.contains("校验") || step.contains("预览") || step.contains("课表") ->
                AgentRunStatusIcon.SCHEDULE
            else -> AgentRunStatusIcon.THINKING
        }
        AgentRunStatus(icon = icon, text = step)
    }

private val AiEduImportPendingSteps = listOf(
    "读取当前页面",
    "DOM 深度抓取",
    "滚动补抓页面",
    "截图兜底判断",
    "检查是否为课表页",
    "读取 AI 配置",
    "发送给 AI 解析",
    "等待 AI 返回",
    "本地校验",
    "进入导入预览"
)

fun aiEduImportStepRows(progress: AiEduImportProgress): List<AiEduImportStepRow> {
    val rows = progress.steps.mapIndexed { index, step ->
        val isLast = index == progress.steps.lastIndex
        val status = when {
            progress.error != null && isLast -> AiEduImportStepStatus.Error
            progress.finished -> AiEduImportStepStatus.Done
            isLast -> AiEduImportStepStatus.Current
            else -> AiEduImportStepStatus.Done
        }
        AiEduImportStepRow(step, status)
    }.toMutableList()
    if (!progress.finished && progress.error == null) {
        AiEduImportPendingSteps.drop(progress.steps.size).forEach { step ->
            rows += AiEduImportStepRow(step, AiEduImportStepStatus.Pending)
        }
    }
    return rows
}

/**
 * Process-local bridge between the import WebView/manual flow and its dedicated progress Activity.
 *
 * Actions are one-shot: choosing any confirmation path releases every captured callback before
 * invoking the selected one. Terminal progress also releases callbacks, preventing an Activity or
 * captured WebView from being retained after the task has finished.
 */
object AiEduImportProgressSession {
    private val lock = Any()
    private val _progress = MutableStateFlow<AiEduImportProgress?>(null)

    val progress: StateFlow<AiEduImportProgress?> = _progress.asStateFlow()
    private var reasoningGeneration = 0L
    private var checkpointGenerationClock = 0L
    private val _liveReasoning = MutableStateFlow(AiImportLiveReasoning())
    internal val liveReasoning: StateFlow<AiImportLiveReasoning> = _liveReasoning.asStateFlow()

    internal fun beginReasoning(taskId: String): (String) -> Unit = synchronized(lock) {
        if (_progress.value?.let { it.taskId == taskId && !it.finished } != true) {
            return@synchronized { _: String -> }
        }
        val generation = ++reasoningGeneration
        _liveReasoning.value = AiImportLiveReasoning(taskId)
        return@synchronized { text: String ->
            synchronized(lock) {
                val current = _progress.value
                if (generation == reasoningGeneration && current?.taskId == taskId && !current.finished) {
                    _liveReasoning.value = _liveReasoning.value.copy(taskId = taskId, text = text,
                        waitPhase = AiImportWaitPhase.MODEL_OUTPUT, activityAtNanos = System.nanoTime())
                }
            }
        }
    }
    internal fun updateStream(taskId: String, nativeReasoning: Boolean, output: String,
                              nowNanos: Long = System.nanoTime()) = synchronized(lock) {
        if (_progress.value?.let { it.taskId == taskId && !it.finished } == true) {
            _liveReasoning.value = _liveReasoning.value.copy(taskId = taskId, nativeReasoning = nativeReasoning,
                courses = completeStreamingCourses(output), waitPhase = AiImportWaitPhase.MODEL_OUTPUT,
                activityAtNanos = nowNanos)
        }
    }
    internal fun updateActivity(taskId: String, activity: AiImportActivity) = synchronized(lock) {
        if (_progress.value?.let { it.taskId == taskId && !it.finished && !it.awaitingUserInput } == true) {
            // Presentation can be coalesced/delayed. It must not rewind the actual transport/tool clock.
            _liveReasoning.value = _liveReasoning.value.copy(taskId = taskId, activity = activity)
        }
    }
    internal fun updateWaitPhase(taskId: String, phase: AiImportWaitPhase,
                                 nowNanos: Long = System.nanoTime()) = synchronized(lock) {
        if (_progress.value?.let { it.taskId == taskId && !it.finished && !it.awaitingUserInput } == true) {
            _liveReasoning.value = _liveReasoning.value.copy(taskId = taskId, waitPhase = phase,
                activityAtNanos = nowNanos)
        }
    }
    private val _historySelection = MutableStateFlow<ImportDraft?>(null)
    val historySelection: StateFlow<ImportDraft?> = _historySelection.asStateFlow()
    private val _previewDraft = MutableStateFlow<ImportDraft?>(null)
    val previewDraft: StateFlow<ImportDraft?> = _previewDraft.asStateFlow()
    private val _finalImportRequest = MutableStateFlow<AiEduFinalImportRequest?>(null)
    val finalImportRequest: StateFlow<AiEduFinalImportRequest?> = _finalImportRequest.asStateFlow()

    private var onConfirm: (() -> Unit)? = null
    private var onSecondaryConfirm: (() -> Unit)? = null
    private var onScreenMode: (() -> Unit)? = null
    private var onCancel: (() -> Unit)? = null

    fun update(progress: AiEduImportProgress?) {
        synchronized(lock) {
            val current = _progress.value
            val next = if (progress != null && current != null && current.taskId == progress.taskId)
                progress.withCheckpointsFrom(current) else progress?.copy(checkpoints = emptyList(),
                    selectedCheckpointId = null, checkpointSelectionLocked = false, checkpointNotice = null,
                    checkpointRevision = 0, checkpointLineageId = progress.taskId, checkpointGeneration = 0)
            publishProgressLocked(next)
        }
    }

    /** A new attempt only inherits stages when its caller explicitly names their previous task. */
    fun beginTask(
        progress: AiEduImportProgress,
        baseDraft: ImportDraft? = null,
        preserveCheckpointsFromTaskId: String? = null
    ) = synchronized(lock) {
        require(progress.taskId.isNotBlank()) { "导入任务标识不能为空" }
        val source = preserveCheckpointsFromTaskId?.let { taskId ->
            // Explicitly supplied history is independent of the live task being replaced.
            progress.takeIf { it.checkpoints.any { checkpoint -> checkpoint.taskId == taskId } }
                ?: _progress.value?.takeIf { it.taskId == taskId }
        }
        checkpointGenerationClock = maxOf(checkpointGenerationClock, source?.checkpointGeneration ?: 0L) + 1
        var next = progress.copy(
            checkpoints = immutableCheckpoints(source?.checkpoints.orEmpty()
                .filter { it.taskId == preserveCheckpointsFromTaskId }.map { it.forTask(progress.taskId, baseDraft?.config) }),
            selectedCheckpointId = source?.selectedCheckpointId,
            checkpointSelectionLocked = source?.checkpointSelectionLocked ?: false,
            checkpointNotice = source?.checkpointNotice,
            checkpointRevision = source?.checkpointRevision ?: 0,
            checkpointLineageId = source?.checkpointLineageId?.ifBlank { preserveCheckpointsFromTaskId.orEmpty() }
                ?: progress.taskId,
            checkpointGeneration = checkpointGenerationClock
        )
        if (baseDraft != null) {
            next = appendImportCheckpoint(next, baseDraft, "修改前课表", AiImportCheckpointKind.BASELINE)
                .fold(onSuccess = { it.first }, onFailure = { next.copy(checkpointNotice = it.message) })
        }
        clearActionsLocked()
        _previewDraft.value = null
        _finalImportRequest.value = null
        publishProgressLocked(boundedCheckpointProgress(next))
        restoreSelectedPreviewLocked()
    }

    fun publishCheckpoint(
        taskId: String,
        draft: ImportDraft,
        label: String = "已校验结果",
        kind: AiImportCheckpointKind = AiImportCheckpointKind.AI
    ): Result<AiImportCheckpoint> = synchronized(lock) {
        val current = _progress.value?.takeIf { it.taskId == taskId && !it.finished && !it.awaitingUserInput }
            ?: return@synchronized Result.failure(IllegalStateException("导入任务已停止或已切换"))
        appendImportCheckpoint(current, draft, label, kind).map { (next, checkpoint) ->
            publishProgressLocked(next)
            restoreSelectedPreviewLocked()
            checkpoint
        }.onFailure { publishProgressLocked(current.copy(checkpointNotice = it.message)) }
    }

    /** Atomically gate small task-owned side effects against a replacement attempt. */
    internal fun withCurrentTask(taskId: String, action: () -> Unit): Boolean = synchronized(lock) {
        if (_progress.value?.taskId != taskId) return@synchronized false
        action()
        true
    }

    /** User history actions branch explicitly without changing a currently running workspace. */
    internal fun forkHistoryCheckpointProgress(progress: AiEduImportProgress): AiEduImportProgress = synchronized(lock) {
        val live = _progress.value?.takeIf { sameCheckpointLineage(it, progress) }
        val taskId = "history-${UUID.randomUUID()}"
        val checkpoints = (live?.checkpoints.orEmpty() + progress.checkpoints).distinctBy { it.id }
            .map { it.forTask(taskId) }
        require(checkpoints.size <= AiImportCheckpoint.MaxRetainedCount &&
            checkpoints.sumOf { it.retainedBytes }.toLong() <= AiImportCheckpoint.MaxRetainedBytes) {
            AiImportCheckpoint.LimitMessage
        }
        if (live != null && checkpoints.map { it.id }.toSet() != live.checkpoints.map { it.id }.toSet()) {
            // Keep the shared immutable stages discoverable, without changing the live choice or run.
            publishProgressLocked(live.copy(checkpoints = immutableCheckpoints(checkpoints.map { it.forTask(live.taskId) }),
                checkpointRevision = live.checkpointRevision + 1))
        }
        checkpointGenerationClock = maxOf(checkpointGenerationClock, progress.checkpointGeneration,
            live?.checkpointGeneration ?: 0L) + 1
        progress.copy(taskId = taskId, checkpoints = immutableCheckpoints(checkpoints),
            checkpointLineageId = progress.checkpointLineageId.ifBlank { progress.taskId.ifBlank { taskId } },
            checkpointGeneration = checkpointGenerationClock)
    }

    fun selectCheckpoint(taskId: String, checkpointId: String): Result<ImportDraft> = synchronized(lock) {
        val current = _progress.value?.takeIf { it.taskId == taskId }
            ?: return@synchronized Result.failure(IllegalStateException("导入任务已切换"))
        val checkpoint = current.checkpoints.firstOrNull { it.id == checkpointId }
            ?: return@synchronized Result.failure(IllegalArgumentException("未找到这个阶段"))
        val base = checkpoint.restore().getOrElse { return@synchronized Result.failure(it) }.config
        selectImportCheckpoint(current, checkpointId, base).map { (next, draft) ->
            checkpointGenerationClock = maxOf(checkpointGenerationClock, next.checkpointGeneration) + 1
            publishProgressLocked(next.copy(checkpointGeneration = checkpointGenerationClock))
            _previewDraft.value = draft
            draft
        }
    }

    fun editCheckpointJson(taskId: String, checkpointId: String, json: String): Result<AiImportCheckpoint> = synchronized(lock) {
        val current = _progress.value?.takeIf { it.taskId == taskId }
            ?: return@synchronized Result.failure(IllegalStateException("导入任务已切换"))
        val checkpoint = current.checkpoints.firstOrNull { it.id == checkpointId }
            ?: return@synchronized Result.failure(IllegalArgumentException("未找到这个阶段"))
        val base = checkpoint.restore().getOrElse { return@synchronized Result.failure(it) }.config
        editImportCheckpoint(current, checkpointId, json, base).map { (next, edited) ->
            checkpointGenerationClock = maxOf(checkpointGenerationClock, next.checkpointGeneration) + 1
            publishProgressLocked(next.copy(checkpointGeneration = checkpointGenerationClock))
            restoreSelectedPreviewLocked()
            edited
        }.onFailure { publishProgressLocked(current.copy(checkpointNotice = it.message)) }
    }

    fun setActions(
        onConfirm: (() -> Unit)? = null,
        onSecondaryConfirm: (() -> Unit)? = null,
        onScreenMode: (() -> Unit)? = null,
        onCancel: (() -> Unit)? = null
    ) {
        synchronized(lock) {
            this.onConfirm = onConfirm
            this.onSecondaryConfirm = onSecondaryConfirm
            this.onScreenMode = onScreenMode
            this.onCancel = onCancel
        }
    }

    /** Publish a task update and its validated preview together, rejecting stopped/stale attempts. */
    internal fun updateActiveTask(
        taskId: String,
        preview: ImportDraft? = null,
        transform: (AiEduImportProgress) -> AiEduImportProgress
    ): AiEduImportProgress? = synchronized(lock) {
        val current = _progress.value?.takeIf {
            it.taskId == taskId && !it.finished && !it.awaitingUserInput
        } ?: return@synchronized null
        var next = transform(current).copy(taskId = taskId).withCheckpointsFrom(current)
        if (preview != null) {
            next = appendImportCheckpoint(next, preview, "完成结果", AiImportCheckpointKind.AI)
                .fold(onSuccess = { it.first }, onFailure = { next.copy(checkpointNotice = it.message) })
        }
        publishProgressLocked(next)
        if (preview != null) restoreSelectedPreviewLocked()
        _progress.value
    }

    fun clearActions() {
        synchronized(lock) {
            clearActionsLocked()
        }
    }

    fun confirm() = consumeAction { onConfirm }

    fun secondaryConfirm() = consumeAction { onSecondaryConfirm }

    fun useScreenMode() = consumeAction { onScreenMode }

    fun cancel() {
        val action = synchronized(lock) {
            val callback = onCancel
            clearActionsLocked()
            _progress.value?.takeIf { it.awaitingConfirmation }?.let { current ->
                _previewDraft.value = null
                update(current.copy(awaitingConfirmation = false, finished = true, returnToBrowser = true))
            }
            callback
        }
        action?.invoke()
    }

    fun selectHistoryDraft(draft: ImportDraft) {
        _historySelection.value = draft
    }

    fun consumeHistoryDraft() {
        _historySelection.value = null
    }

    fun setPreviewDraft(draft: ImportDraft?) {
        synchronized(lock) {
            val current = _progress.value
            if (current?.selectedCheckpointId != null) {
                restoreSelectedPreviewLocked()
            } else {
                _previewDraft.value = draft?.let { runCatching { ScheduleImportParser.validateEditedDraft(it) }.getOrNull() }
            }
        }
    }

    fun requestFinalImport(draft: ImportDraft, createNewSchedule: Boolean) {
        _finalImportRequest.value = AiEduFinalImportRequest(draft, createNewSchedule)
    }

    fun consumeFinalImportRequest() {
        synchronized(lock) {
            // Importing history must not erase a different live workspace or its chosen stage.
            _finalImportRequest.value = null
        }
    }

    private fun consumeAction(selector: () -> (() -> Unit)?) {
        val action = synchronized(lock) {
            selector().also { clearActionsLocked() }
        }
        action?.invoke()
    }

    private fun clearActionsLocked() {
        onConfirm = null
        onSecondaryConfirm = null
        onScreenMode = null
        onCancel = null
    }

    private fun publishProgressLocked(next: AiEduImportProgress?) {
        if (next?.taskId != _progress.value?.taskId || next == null) {
            reasoningGeneration++
            _liveReasoning.value = AiImportLiveReasoning()
            _previewDraft.value = null
        }
        _progress.value = next
        if (next?.awaitingConfirmation == true && !next.requestSent && next.checkpoints.isEmpty()) {
            _previewDraft.value = null
        }
        if (next == null || (next.finished && !next.awaitingConfirmation)) clearActionsLocked()
    }

    private fun restoreSelectedPreviewLocked() {
        val current = _progress.value
        _previewDraft.value = current?.checkpoints?.firstOrNull {
            it.id == current.selectedCheckpointId && it.taskId == current.taskId
        }?.restore()?.getOrNull()
    }
}

internal data class AiImportLiveReasoning(val taskId: String = "", val text: String = "",
    val nativeReasoning: Boolean = false, val courses: List<CourseEntity> = emptyList(),
    val activity: AiImportActivity? = null, val activityAtNanos: Long = System.nanoTime(),
    val waitPhase: AiImportWaitPhase = AiImportWaitPhase.LOCAL_OPERATION)

private fun AiEduImportProgress.withCheckpointsFrom(source: AiEduImportProgress): AiEduImportProgress = copy(
    checkpoints = source.checkpoints, selectedCheckpointId = source.selectedCheckpointId,
    checkpointSelectionLocked = source.checkpointSelectionLocked, checkpointNotice = source.checkpointNotice,
    checkpointRevision = source.checkpointRevision, checkpointLineageId = source.checkpointLineageId,
    checkpointGeneration = source.checkpointGeneration)

data class AiEduFinalImportRequest(
    val draft: ImportDraft,
    val createNewSchedule: Boolean
)
