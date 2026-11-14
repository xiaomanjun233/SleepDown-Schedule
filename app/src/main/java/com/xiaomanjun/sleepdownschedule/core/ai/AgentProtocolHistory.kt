package com.xiaomanjun.sleepdownschedule.core.ai

import kotlinx.serialization.json.*

/** A provider replay must not duplicate an already settled Responses item/call ID. */
internal fun newResponseItems(history: List<JsonElement>, incoming: List<JsonElement>): List<JsonElement> {
    fun identity(element: JsonElement): String? {
        val item = element as? JsonObject ?: return null
        val id = if (item["type"]?.jsonPrimitive?.contentOrNull == "function_call")
            item["call_id"]?.jsonPrimitive?.contentOrNull ?: item["id"]?.jsonPrimitive?.contentOrNull else item["id"]?.jsonPrimitive?.contentOrNull
        return id?.takeIf(String::isNotBlank)?.let { "${item["type"]?.jsonPrimitive?.contentOrNull}:$it" }
    }
    val seen = history.mapNotNull(::identity).toMutableSet()
    return incoming.filter { identity(it)?.let(seen::add) ?: true }
}

internal fun responseToolReceipt(history: List<JsonElement>, callId: String): Boolean = history.any {
    val item = it as? JsonObject
    item?.get("type")?.jsonPrimitive?.contentOrNull == "function_call_output" && item["call_id"]?.jsonPrimitive?.contentOrNull == callId
}
