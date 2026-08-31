package com.xiaomanjun.sleepdownschedule.feature.importing.shiguang

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebView
import android.widget.Toast
import com.xiaomanjun.sleepdownschedule.ImportDraft
import com.xiaomanjun.sleepdownschedule.PeriodEntity
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.feature.importing.EduBridgeInteractionRequest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString

internal class ShiguangBridgeHost(
    private val context: Context,
    private val onDraft: (ImportDraft) -> Unit,
    private val onMessage: (String) -> Unit,
    private val onInteractionRequest: (EduBridgeInteractionRequest) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val session = ShiguangImportSession()
    private var activeWebView: WebView? = null
    private var initialPromptAnswer: String? = null

    fun bindWebView(webView: WebView?) {
        activeWebView = webView
    }

    fun evaluateJavascript(script: String, callback: ValueCallback<String?>? = null): Boolean {
        val target = activeWebView ?: return false
        target.evaluateJavascript(script, callback)
        return true
    }

    fun beginTask(
        config: ScheduleConfigEntity,
        periods: List<PeriodEntity>,
        initialPromptAnswer: String? = null
    ) {
        session.begin(config, periods)
        this.initialPromptAnswer = initialPromptAnswer
    }

    fun onMessageReceived(jsonString: String) {
        val message = try {
            ShiguangBridgeJson.decodeFromString(ShiguangBridgeMessage.serializer(), jsonString)
        } catch (error: Exception) {
            mainHandler.post { onMessage("拾光 Bridge 消息无效：${error.message}") }
            return
        }
        try {
            when (message.action) {
                "showToast" -> parsePayload<ShiguangShowToastPayload>(message).let { payload ->
                    mainHandler.post {
                        Toast.makeText(context, payload.message, Toast.LENGTH_SHORT).show()
                        onMessage(payload.message)
                    }
                }

                "showAlert" -> parsePayload<ShiguangShowAlertPayload>(message).let { payload ->
                    val callbackId = requireCallbackId(message)
                    mainHandler.post {
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
                    initialPromptAnswer?.let { answer ->
                        initialPromptAnswer = null
                        resolve(
                            callbackId,
                            ShiguangBridgeJson.encodeToString(String.serializer(), answer)
                        )
                        return@let
                    }
                    mainHandler.post {
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
                    mainHandler.post {
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
                    session.stageCourses(payload.coursesJsonString)
                    resolve(requireCallbackId(message), "true")
                }

                "saveCourseConfig" -> parsePayload<ShiguangSaveConfigPayload>(message).let { payload ->
                    session.stageCourseConfig(payload.configJsonString)
                    resolve(requireCallbackId(message), "true")
                }

                "savePresetTimeSlots" -> parsePayload<ShiguangSaveTimeSlotsPayload>(message).let { payload ->
                    session.stageTimeSlots(payload.timeSlotsJsonString)
                    resolve(requireCallbackId(message), "true")
                }

                "notifyTaskCompletion" -> {
                    val draft = session.complete()
                    mainHandler.post { onDraft(draft) }
                }

                else -> throw IllegalArgumentException("未知拾光 Bridge action：${message.action}")
            }
        } catch (error: Exception) {
            val detail = error.message ?: "拾光 Bridge 处理失败"
            message.callbackId?.let { reject(it, detail) }
                ?: mainHandler.post { onMessage(detail) }
        }
    }

    private inline fun <reified T> parsePayload(message: ShiguangBridgeMessage): T {
        val payload = requireNotNull(message.payload) { "${message.action} 缺少 payload" }
        return ShiguangBridgeJson.decodeFromString(payload)
    }

    private fun requireCallbackId(message: ShiguangBridgeMessage): String =
        requireNotNull(message.callbackId) { "${message.action} 缺少 callbackId" }

    private fun resolve(callbackId: String, resultRawJs: String) {
        val script = buildShiguangJsCallbackScript(callbackId, success = true, resultRawJs)
        mainHandler.post { evaluateJavascript(script) }
    }

    private fun reject(callbackId: String, errorText: String) {
        val errorJson = ShiguangBridgeJson.encodeToString(String.serializer(), errorText)
        val script = buildShiguangJsCallbackScript(callbackId, success = false, errorJson)
        mainHandler.post { evaluateJavascript(script) }
    }
}

internal class ShiguangNativeBridge(private val host: ShiguangBridgeHost) {
    @JavascriptInterface
    fun postMessage(jsonMessage: String) {
        host.onMessageReceived(jsonMessage)
    }
}
