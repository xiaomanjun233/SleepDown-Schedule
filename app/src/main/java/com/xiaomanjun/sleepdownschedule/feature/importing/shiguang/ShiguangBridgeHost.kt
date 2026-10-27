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
    private data class Task(val generation: Long, val session: ShiguangImportSession, var promptAnswer: String?)
    @Volatile private var task: Task? = null
    @Volatile private var generation = 0L
    @Volatile private var closed = false
    private var activeWebView: WebView? = null
    private val _running = kotlinx.coroutines.flow.MutableStateFlow(false)
    val running: kotlinx.coroutines.flow.StateFlow<Boolean> = _running

    /** Cancel this task without allowing already queued results to cross into the next one. */
    fun finishExecution() {
        generation++
        task = null
        _running.value = false
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
        allowImportedBellTimes: Boolean = false
    ) {
        check(!closed) { "教务会话已关闭" }
        val next = ShiguangImportSession()
        next.begin(config, periods, mergeOverlappingTimeSlots, allowImportedBellTimes)
        generation++
        task = Task(generation, next, initialPromptAnswer)
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
        try {
            when (message.action) {
                "showToast" -> parsePayload<ShiguangShowToastPayload>(message).let { payload ->
                    postForTask(expectedGeneration) { onMessage(payload.message) }
                }

                "showAlert" -> parsePayload<ShiguangShowAlertPayload>(message).let { payload ->
                    val callbackId = requireCallbackId(message)
                    postForTask(expectedGeneration) {
                        onInteractionRequest(
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
                        onInteractionRequest(
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
                        onInteractionRequest(
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
                        onDraft(draft)
                    }
                }

                "notifyExecutionFinished" -> postForTask(expectedGeneration) {
                    task = null
                    _running.value = false
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
