package com.example.courseschedule

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AiEduImportProgress(
    val steps: List<String> = emptyList(),
    val routeLabel: String = "",
    val requestPreview: String = "",
    val pageText: String = "",
    val screenshotPreviews: List<RenderedPageImage> = emptyList(),
    val reasoningOutput: String = "",
    val aiOutput: String = "",
    val awaitingConfirmation: Boolean = false,
    val confirmActionLabel: String = "",
    val secondaryConfirmActionLabel: String = "",
    val screenModeActionLabel: String = "",
    val cancelActionLabel: String = "返回重抓",
    val finished: Boolean = false,
    val error: String? = null
)

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

    private var onConfirm: (() -> Unit)? = null
    private var onSecondaryConfirm: (() -> Unit)? = null
    private var onScreenMode: (() -> Unit)? = null
    private var onCancel: (() -> Unit)? = null

    fun update(progress: AiEduImportProgress?) {
        synchronized(lock) {
            _progress.value = progress
            if (progress == null || (progress.finished && !progress.awaitingConfirmation)) {
                clearActionsLocked()
            }
        }
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

    fun clearActions() {
        synchronized(lock) {
            clearActionsLocked()
        }
    }

    fun confirm() = consumeAction { onConfirm }

    fun secondaryConfirm() = consumeAction { onSecondaryConfirm }

    fun useScreenMode() = consumeAction { onScreenMode }

    fun cancel() = consumeAction { onCancel }

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
}
