package com.xiaomanjun.sleepdownschedule.feature.importing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.net.HttpURLConnection

/** One attempt's cancellation and user instructions. Original attachments stay in the task owner. */
class AiImportInteraction(val instruction: String) {
    internal var onHttpPhase: (AiImportHttpPhase) -> Unit = {}
    internal var onStream: ((Boolean, String) -> Unit)? = null
    internal var onActivity: ((AiImportActivity) -> Unit)? = null
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activityPublisher = AiImportActivityPublisher(schedule = { delayMs, flush ->
        activityScope.launch {
            delay(delayMs)
            checkActive()
            flush()
        }
    }) { activity ->
        checkActive()
        onActivity?.invoke(activity)
    }

    /** Public provider summaries and real execution events only; never tool arguments or prompts. */
    internal fun publishActivity(text: CharSequence, source: AiImportActivitySource = AiImportActivitySource.STATUS) {
        checkActive()
        activityPublisher.publish(AiImportActivity(text.toString(), source))
    }

    internal fun publishActivity(activity: AiImportActivity?) {
        checkActive()
        activity?.let(activityPublisher::publish)
    }
    private var lastStreamAt = 0L
    internal fun publishStream(nativeReasoning: Boolean, output: String, force: Boolean = false) {
        checkActive()
        val now = System.nanoTime()
        if (force || now - lastStreamAt >= 100_000_000L) {
            lastStreamAt = now
            onStream?.invoke(nativeReasoning, output)
        }
    }
    @Volatile private var cancelled = false
    @Volatile private var connection: HttpURLConnection? = null

    internal fun attach(connection: HttpURLConnection) {
        this.connection = connection
        if (cancelled) {
            connection.disconnect()
            checkActive()
        }
    }

    internal fun markCancelled() {
        cancelled = true
        activityScope.cancel()
    }
    internal fun disconnect() { connection?.disconnect() }
    internal fun checkActive() {
        if (cancelled) throw CancellationException("AI 导入已暂停")
    }
}

internal enum class AiImportActivitySource { STATUS, PROVIDER_SUMMARY, MODEL_PROGRESS }

internal data class AiImportActivity(val text: String, val source: AiImportActivitySource)

internal const val AiImportActivityMaxChars = 180

/** Bounded, single-line live text. It is deliberately separate from the complete result buffers. */
internal fun importActivityText(text: CharSequence): String {
    val window = text.takeLast(AiImportActivityMaxChars * 4).toString()
        .replace(Regex("[\\s\\p{Z}\\p{Cc}\\p{Cf}]+"), " ").trim()
    if (window.length <= AiImportActivityMaxChars) return window
    var start = window.length - AiImportActivityMaxChars + 1
    if (window[start].isLowSurrogate()) start++
    return "…" + window.substring(start).trimStart()
}

/** No force bypass: even end-of-stream and rapid phase changes share the ten-Hz limit. */
internal class AiImportActivityPublisher(
    private val nanoTime: () -> Long = System::nanoTime,
    private val schedule: ((Long, () -> Unit) -> Unit)? = null,
    private val onUpdate: (AiImportActivity) -> Unit
) {
    private var lastPublishedAt: Long? = null
    private var lastActivity: AiImportActivity? = null
    private var pending: AiImportActivity? = null
    private var flushScheduled = false

    @Synchronized
    fun publish(activity: AiImportActivity) {
        val bounded = activity.copy(text = importActivityText(activity.text))
        if (bounded.text.isBlank()) return
        if (bounded == lastActivity) {
            pending = null
            return
        }
        val now = nanoTime()
        val remaining = lastPublishedAt?.let { 100_000_000L - (now - it) } ?: 0L
        if (remaining > 0L) {
            pending = bounded
            if (!flushScheduled && schedule != null) {
                flushScheduled = true
                schedule.invoke((remaining + 999_999L) / 1_000_000L, ::flush)
            }
            return
        }
        pending = null
        lastPublishedAt = now
        lastActivity = bounded
        onUpdate(bounded)
    }

    @Synchronized
    private fun flush() {
        flushScheduled = false
        pending?.let(::publish)
    }
}

internal const val AskImportDetailsToolName = "ASK_IMPORT_DETAILS"

internal class AiImportClarificationRequired(val questions: List<String>) :
    IllegalStateException("课表材料缺少关键信息，需要补充后继续。")

internal fun askImportDetailsTool(responses: Boolean): JsonObject {
    val definition = buildJsonObject {
        put("name", JsonPrimitive(AskImportDetailsToolName))
        put("description", JsonPrimitive("材料缺少排课必需信息时向用户提问并等待回答，禁止猜测星期、节次或周次。最多三个简短问题。"))
        put("strict", JsonPrimitive(true))
        put("parameters", buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("additionalProperties", JsonPrimitive(false))
            put("properties", buildJsonObject {
                put("questions", buildJsonObject {
                    put("type", JsonPrimitive("array"))
                    put("items", buildJsonObject { put("type", JsonPrimitive("string")) })
                    put("minItems", JsonPrimitive(1))
                    put("maxItems", JsonPrimitive(3))
                })
            })
            put("required", JsonArray(listOf(JsonPrimitive("questions"))))
        })
    }
    return if (responses) JsonObject(definition + ("type" to JsonPrimitive("function"))) else {
        buildJsonObject {
            put("type", JsonPrimitive("function"))
            put("function", definition)
        }
    }
}

internal fun importClarificationQuestions(response: String): List<String> {
    val root = Json.parseToJsonElement(response).jsonObject
    val chatCalls = root.optionalArray("choices").firstOrNull()?.jsonObject
        ?.get("message")?.jsonObject?.optionalArray("tool_calls").orEmpty()
        .mapNotNull { (it as? JsonObject)?.get("function") as? JsonObject }
    val calls = chatCalls + root.optionalArray("output").mapNotNull { it as? JsonObject }
    val call = calls.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull == AskImportDetailsToolName }
        ?: return emptyList()
    val arguments = call["arguments"]?.let { if (it is JsonPrimitive) it.content else it.toString() }
        ?: throw AiServiceResponseException("模型提问缺少问题内容。", response)
    val questions = Json.parseToJsonElement(arguments).jsonObject.optionalArray("questions")
        .map { it.jsonPrimitive.content.trim().take(300) }.filter(String::isNotBlank).distinct().take(3)
    if (questions.isEmpty()) throw AiServiceResponseException("模型返回了空的问题列表。", response)
    return questions
}
