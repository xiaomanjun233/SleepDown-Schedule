package com.xiaomanjun.sleepdownschedule.feature.importing.shiguang

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebView
import com.xiaomanjun.sleepdownschedule.ImportDraft
import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.feature.importing.EduBridgeInteractionRequest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString

internal class ShiguangBridgeHost(
    private var onDraft: (ImportDraft) -> Unit,
    private var onMessage: (String) -> Unit,
    private var onInteractionRequest: (EduBridgeInteractionRequest) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private data class Task(val generation: Long, val session: ShiguangImportSession, var promptAnswer: String?,
                            val capture: ((Result<ImportDraft>) -> Unit)? = null,
                            val interaction: ((EduBridgeInteractionRequest) -> Unit)? = null,
                            val executionToken: String? = null)
    @Volatile private var task: Task? = null
    @Volatile private var generation = 0L
    @Volatile private var closed = false
    private var activeWebView: WebView? = null
    private val _running = kotlinx.coroutines.flow.MutableStateFlow(false)
    val running: kotlinx.coroutines.flow.StateFlow<Boolean> = _running

    fun cancelCapture(capture: (Result<ImportDraft>) -> Unit) {
        if (task?.capture === capture) finishExecution()
    }

    /** Cancel this task without allowing already queued results to cross into the next one. */
    fun finishExecution() {
        val previous = task
        generation++
        task = null
        _running.value = false
        previous?.capture?.invoke(Result.failure(IllegalStateException("教务脚本已停止或页面发生变化")))
    }

    fun close() {
        if (closed) return
        closed = true
        finishExecution()
        mainHandler.removeCallbacksAndMessages(null)
        activeWebView = null
        onDraft = {}
        onMessage = {}
        onInteractionRequest = {}
    }

    private fun postForTask(expectedGeneration: Long, action: () -> Unit) {
        mainHandler.post {
            if (!closed && generation == expectedGeneration) action()
        }
    }

    fun bindWebView(webView: WebView?) {
        if (!closed) activeWebView = webView
    }

    fun evaluateJavascript(script: String, callback: ValueCallback<String?>? = null): Boolean {
        if (closed) return false
        val target = activeWebView ?: return false
        val expectedGeneration = generation
        target.evaluateJavascript(script) { result ->
            if (!closed && generation == expectedGeneration && activeWebView === target) callback?.onReceiveValue(result)
        }
        return true
    }

    fun resolveInteraction(requestId: String, resultRawJs: String) {
        resolve(requestId, resultRawJs)
    }

    fun beginTask(
        config: ScheduleConfigEntity,
        periods: List<PeriodEntity>,
        initialPromptAnswer: String? = null,
        mergeOverlappingTimeSlots: Boolean = false,
        allowImportedBellTimes: Boolean = false,
        capture: ((Result<ImportDraft>) -> Unit)? = null,
        captureInteraction: ((EduBridgeInteractionRequest) -> Unit)? = null,
        executionToken: String? = null
    ) {
        check(!closed) { "教务会话已关闭" }
        if (task?.capture != null) finishExecution()
        val next = ShiguangImportSession()
        next.begin(config, periods, mergeOverlappingTimeSlots, allowImportedBellTimes)
        generation++
        task = Task(generation, next, initialPromptAnswer, capture, captureInteraction, executionToken)
        _running.value = true
    }

    fun onMessageReceived(jsonString: String) {
        if (closed) return
        val currentTask = task
        val expectedGeneration = currentTask?.generation ?: generation
        val message = try {
            ShiguangBridgeJson.decodeFromString(ShiguangBridgeMessage.serializer(), jsonString)
        } catch (error: Exception) {
            postForTask(expectedGeneration) { onMessage("拾光 Bridge 消息无效：${error.message}") }
            return
        }
        if (message.executionToken != currentTask?.executionToken) {
            message.callbackId?.let { reject(it, "此脚本所属导入任务已结束，旧结果未交付", expectedGeneration) }
            return
        }
        try {
            when (message.action) {
                "showToast" -> parsePayload<ShiguangShowToastPayload>(message).let { payload ->
                    postForTask(expectedGeneration) {
                        onMessage(payload.message)
                        // Non-returning official flows also signal cancellation through these
                        // terminal messages. Release their busy state without treating warnings
                        // such as a failed optional bell request as a completed import.
                        if (payload.message.trim().trimEnd('。', '.') in setOf("用户取消了导入", "导入已取消")) {
                            currentTask?.capture?.invoke(Result.failure(IllegalStateException("用户取消了导入")))
                            task = null
                            _running.value = false
                        }
                    }
                }

                "showAlert" -> parsePayload<ShiguangShowAlertPayload>(message).let { payload ->
                    val callbackId = requireCallbackId(message)
                    postForTask(expectedGeneration) {
                        (currentTask?.interaction ?: onInteractionRequest)(
                            EduBridgeInteractionRequest.Alert(
                                requestId = callbackId,
                                title = payload.titleText.ifBlank { "提示" },
                                message = payload.contentText,
                                confirmText = payload.confirmText.orEmpty().ifBlank { "确定" }
                            )
                        )
                    }
                }

                "showPrompt" -> parsePayload<ShiguangShowPromptPayload>(message).let { payload ->
                    val callbackId = requireCallbackId(message)
                    currentTask?.promptAnswer?.let { answer ->
                        currentTask.promptAnswer = null
                        resolve(
                            callbackId,
                            ShiguangBridgeJson.encodeToString(String.serializer(), answer), expectedGeneration
                        )
                        return@let
                    }
                    postForTask(expectedGeneration) {
                        (currentTask?.interaction ?: onInteractionRequest)(
                            EduBridgeInteractionRequest.Prompt(
                                requestId = callbackId,
                                title = payload.titleText.ifBlank { "请输入" },
                                message = payload.tipText,
                                defaultValue = payload.defaultText,
                                validator = payload.validatorJsFunction?.takeIf(String::isNotBlank)
                            )
                        )
                    }
                }

                "showSingleSelection" -> parsePayload<ShiguangShowSingleSelectionPayload>(message).let { payload ->
                    val callbackId = requireCallbackId(message)
                    val options = ShiguangPayloadJson.decodeFromString(
                        ListSerializer(String.serializer()),
                        payload.itemsJsonString
                    )
                    postForTask(expectedGeneration) {
                        (currentTask?.interaction ?: onInteractionRequest)(
                            EduBridgeInteractionRequest.SingleSelection(
                                requestId = callbackId,
                                title = payload.titleText.ifBlank { "请选择" },
                                options = options,
                                defaultIndex = payload.defaultSelectedIndex.takeIf { it in options.indices } ?: -1
                            )
                        )
                    }
                }

                "saveImportedCourses" -> parsePayload<ShiguangSaveCoursesPayload>(message).let { payload ->
                    requireNotNull(currentTask) { "当前没有正在执行的拾光导入任务" }.session.stageCourses(payload.coursesJsonString)
                    resolve(requireCallbackId(message), "true", expectedGeneration)
                }

                "saveCourseConfig" -> parsePayload<ShiguangSaveConfigPayload>(message).let { payload ->
                    requireNotNull(currentTask) { "当前没有正在执行的拾光导入任务" }.session.stageCourseConfig(payload.configJsonString)
                    resolve(requireCallbackId(message), "true")
                }

                "savePresetTimeSlots" -> parsePayload<ShiguangSaveTimeSlotsPayload>(message).let { payload ->
                    requireNotNull(currentTask) { "当前没有正在执行的拾光导入任务" }.session.stageTimeSlots(payload.timeSlotsJsonString)
                    resolve(requireCallbackId(message), "true")
                }

                "saveComboSchedule" -> parsePayload<ShiguangSaveComboSchedulePayload>(message).let { payload ->
                    requireNotNull(currentTask) { "当前没有正在执行的拾光导入任务" }.session.stageComboSchedule(payload.comboScheduleJsonString)
                    resolve(requireCallbackId(message), "true", expectedGeneration)
                }

                "notifyTaskCompletion" -> {
                    val draft = requireNotNull(currentTask) { "当前没有正在执行的拾光导入任务" }.session.complete()
                    postForTask(expectedGeneration) {
                        task = null
                        _running.value = false
                        currentTask.capture?.invoke(Result.success(draft)) ?: onDraft(draft)
                    }
                }

                "notifyExecutionFinished" -> postForTask(expectedGeneration) {
                    task = null
                    _running.value = false
                    currentTask?.capture?.invoke(Result.failure(IllegalStateException("脚本执行结束，未提交完整课程；请核对登录状态和脚本输出")))
                }

                else -> throw IllegalArgumentException("未知拾光 Bridge action：${message.action}")
            }
        } catch (error: Exception) {
            val detail = error.message ?: "拾光 Bridge 处理失败"
            postForTask(expectedGeneration) { onMessage(detail) }
            message.callbackId?.let { reject(it, detail, expectedGeneration) }
            if (message.callbackId == null) postForTask(expectedGeneration) {
                task = null
                _running.value = false
                currentTask?.capture?.invoke(Result.failure(IllegalArgumentException(detail)))
            }
        }
    }

    private inline fun <reified T> parsePayload(message: ShiguangBridgeMessage): T {
        val payload = requireNotNull(message.payload) { "${message.action} 缺少 payload" }
        return ShiguangBridgeJson.decodeFromString(payload)
    }

    private fun requireCallbackId(message: ShiguangBridgeMessage): String =
        requireNotNull(message.callbackId) { "${message.action} 缺少 callbackId" }

    private fun resolve(callbackId: String, resultRawJs: String, expectedGeneration: Long = generation) {
        val script = buildShiguangJsCallbackScript(callbackId, success = true, resultRawJs)
        postForTask(expectedGeneration) { evaluateJavascript(script) }
    }

    private fun reject(callbackId: String, errorText: String, expectedGeneration: Long) {
        val errorJson = ShiguangBridgeJson.encodeToString(String.serializer(), errorText)
        val script = buildShiguangJsCallbackScript(callbackId, success = false, errorJson)
        postForTask(expectedGeneration) { evaluateJavascript(script) }
    }
}

internal class ShiguangNativeBridge(private val host: ShiguangBridgeHost) {
    @JavascriptInterface
    fun postMessage(jsonMessage: String) {
        host.onMessageReceived(jsonMessage)
    }
}
