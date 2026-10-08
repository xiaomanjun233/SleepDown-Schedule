package com.xiaomanjun.sleepdownschedule.feature.importing

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import java.net.HttpURLConnection

/** One attempt's cancellation and user instructions. Original attachments stay in the task owner. */
class AiImportInteraction(val instruction: String) {
    internal var onHttpPhase: (AiImportHttpPhase) -> Unit = {}
    internal var onStream: ((Boolean, String) -> Unit)? = null
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

    internal fun markCancelled() { cancelled = true }
    internal fun disconnect() { connection?.disconnect() }
    internal fun checkActive() {
        if (cancelled) throw CancellationException("AI 导入已暂停")
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
