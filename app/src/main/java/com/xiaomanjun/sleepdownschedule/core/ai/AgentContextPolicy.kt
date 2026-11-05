package com.xiaomanjun.sleepdownschedule.core.ai

import kotlinx.serialization.json.*

internal const val DefaultAgentContextTokens = 256_000

/** A local admission estimate is separate from provider-reported usage shown in the UI. */
internal class AgentContextPolicy(val windowTokens: Int = DefaultAgentContextTokens) {
    data class ChatProjection(val messages: List<JsonObject>, val rereadTools: Set<String>)
    val inputBudget = (windowTokens.coerceAtLeast(8_192) * 0.8).toInt()
    fun estimatedTokens(element: JsonElement): Int = when (element) {
        is JsonArray -> element.sumOf(::estimatedTokens)
        is JsonObject -> when (element["type"]?.jsonPrimitive?.contentOrNull) {
            "input_image", "image_url" -> 4_096
            "input_file", "file" -> 16_384
            else -> element.entries.sumOf { (_, value) -> estimatedTokens(value) }
        }
        is JsonPrimitive -> (element.content.length + 1) / 2 + 4
        else -> 0
    }

    /** Summarize only settled older tool pairs; keep instructions, user input and recent steps. */
    fun compactResponses(input: List<JsonObject>): List<JsonObject> {
        if (estimatedTokens(JsonArray(input)) <= inputBudget) return input.toList()
        val outputById = input.filter { it["type"]?.jsonPrimitive?.contentOrNull == "function_call_output" }
            .associateBy { it["call_id"]?.jsonPrimitive?.contentOrNull }
        val settled = input.filter { it["type"]?.jsonPrimitive?.contentOrNull == "function_call" && it["call_id"]?.jsonPrimitive?.contentOrNull in outputById }
        val older = settled.dropLast(4)
        if (older.isEmpty()) return input.toList()
        val ids = older.mapNotNull { it["call_id"]?.jsonPrimitive?.contentOrNull }.toSet()
        val facts = older.map { call -> buildJsonObject {
            put("tool", call["name"] ?: JsonNull)
            put("arguments", call["arguments"]?.jsonPrimitive?.contentOrNull.orEmpty().take(2_000))
            val content = outputById[call["call_id"]?.jsonPrimitive?.contentOrNull]?.get("output")
                ?.jsonPrimitive?.contentOrNull.orEmpty()
            put("result", content.take(8_000)); put("truncated", content.length > 8_000)
        } }
        val summary = buildJsonObject {
            put("role", "user")
            put("content", buildJsonObject {
                put("kind", "completed_tool_context"); put("trust", "untrusted_tool_data")
                put("note", "这是已完成操作记录；不要重复执行写入。截短结果需要重新读取相应只读工具；当前用户要求和最近草稿优先。")
                put("results", JsonArray(facts))
            }.toString())
        }
        val firstOld = input.indexOfFirst { it["call_id"]?.jsonPrimitive?.contentOrNull in ids }
        return buildList {
            input.forEachIndexed { index, item ->
                if (index == firstOld) add(summary)
                if (item["call_id"]?.jsonPrimitive?.contentOrNull !in ids) add(item)
            }
        }
    }

    fun compactChat(messages: List<JsonObject>): ChatProjection {
        if (estimatedTokens(JsonArray(messages)) <= inputBudget) return ChatProjection(messages.toList(), emptySet())
        val reread = mutableSetOf<String>()
        val compacted = messages.map { message ->
            if (message["role"]?.jsonPrimitive?.contentOrNull != "system") return@map message
            val content = (message["content"] as? JsonPrimitive)?.contentOrNull ?: return@map message
            val facts = runCatching { Json.parseToJsonElement(content) as? JsonObject }.getOrNull() ?: return@map message
            if (facts["kind"]?.jsonPrimitive?.contentOrNull != "local_tool_facts") return@map message
            val results = (facts["results"] as? JsonArray).orEmpty().map { result ->
                val item = result.jsonObject
                val text = item["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (text.length <= 8_000) item else {
                    item["tool"]?.jsonPrimitive?.contentOrNull?.let(reread::add)
                    JsonObject(item + ("content" to JsonPrimitive(text.take(8_000) + "\n[较早结果已压缩；需要完整事实时重新调用该只读工具，不要重复写入操作。]")))
                }
            }
            JsonObject(message + ("content" to JsonPrimitive(JsonObject(facts + ("results" to JsonArray(results))).toString())))
        }
        val settledIds = compacted.filter { it["role"]?.jsonPrimitive?.contentOrNull == "tool" }
            .mapNotNull { it["tool_call_id"]?.jsonPrimitive?.contentOrNull }.toSet()
        val calls = compacted.flatMap { (it["tool_calls"] as? JsonArray).orEmpty().map { call -> call.jsonObject } }
            .filter { it["id"]?.jsonPrimitive?.contentOrNull in settledIds }
        val oldCalls = calls.dropLast(4)
        if (oldCalls.isEmpty()) return ChatProjection(compacted, reread)
        val oldIds = oldCalls.mapNotNull { it["id"]?.jsonPrimitive?.contentOrNull }.toSet()
        val receipts = compacted.filter { it["role"]?.jsonPrimitive?.contentOrNull == "tool" }
            .associateBy { it["tool_call_id"]?.jsonPrimitive?.contentOrNull }
        val summary = buildJsonObject {
            put("role", "user")
            put("content", buildJsonObject {
                put("kind", "completed_tool_context"); put("trust", "untrusted_tool_data")
                put("note", "这是较早已结算工具的摘要。不要重复执行写入；截短的事实可重新读取。用户要求和最新草稿保留在原消息中。")
                put("results", buildJsonArray { oldCalls.forEach { call -> add(buildJsonObject {
                    val function = call["function"]!!.jsonObject
                    put("tool", function["name"] ?: JsonNull)
                    put("arguments", function["arguments"]?.jsonPrimitive?.contentOrNull.orEmpty().take(2_000))
                    val text = receipts[call["id"]?.jsonPrimitive?.contentOrNull]?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
                    put("result", text.take(8_000)); put("truncated", text.length > 8_000)
                }) } })
            }.toString())
        }
        var inserted = false
        val paired = buildList {
            compacted.forEach { message ->
                val toolCalls = message["tool_calls"] as? JsonArray
                val isOldResult = message["role"]?.jsonPrimitive?.contentOrNull == "tool" && message["tool_call_id"]?.jsonPrimitive?.contentOrNull in oldIds
                val hasOldCall = toolCalls?.any { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull in oldIds } == true
                if ((isOldResult || hasOldCall) && !inserted) { add(summary); inserted = true }
                if (isOldResult) return@forEach
                if (!hasOldCall) add(message) else {
                    val remaining = toolCalls!!.filter { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull !in oldIds }
                    if (remaining.isNotEmpty()) add(JsonObject(message + ("tool_calls" to JsonArray(remaining))))
                    else if ((message["content"] as? JsonPrimitive)?.contentOrNull?.isNotBlank() == true) add(JsonObject(message - "tool_calls"))
                }
            }
        }
        return ChatProjection(paired, reread)
    }
}
