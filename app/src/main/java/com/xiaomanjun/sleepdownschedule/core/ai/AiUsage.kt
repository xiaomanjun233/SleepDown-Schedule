package com.xiaomanjun.sleepdownschedule.core.ai

import kotlinx.serialization.json.*

internal data class AiRequestUsage(val input: Long?, val output: Long?, val cached: Long?, val contextLimit: Long?, val elapsedNanos: Long)
internal data class AiUsageTotals(
    val input: Long? = null, val output: Long? = null, val cached: Long? = null,
    val contextUsed: Long? = null, val contextLimit: Long? = null, val elapsedNanos: Long = 0
) {
    fun plus(value: AiRequestUsage) = copy(
        input = add(input, value.input), output = add(output, value.output), cached = add(cached, value.cached),
        contextUsed = value.input ?: contextUsed, contextLimit = value.contextLimit ?: contextLimit,
        elapsedNanos = elapsedNanos + value.elapsedNanos.coerceAtLeast(0))
    val averageOutputPerSecond: Double? get() = output?.takeIf { elapsedNanos > 0 }?.let { it * 1_000_000_000.0 / elapsedNanos }
    companion object { private fun add(a: Long?, b: Long?): Long? = if (a == null && b == null) null else (a ?: 0) + (b ?: 0) }
}

/** Missing usage is unknown, not zero. No text/token estimation and no raw data in telemetry. */
internal fun parseAiRequestUsage(response: String, elapsedNanos: Long): AiRequestUsage? = runCatching {
    val parsed = Json.parseToJsonElement(response).jsonObject
    val root = parsed["response"] as? JsonObject ?: parsed
    val usage = root["usage"] as? JsonObject ?: return null
    fun number(obj: JsonObject?, vararg fields: String): Long? = fields.firstNotNullOfOrNull {
        (obj?.get(it) as? JsonPrimitive)?.longOrNull?.takeIf { value -> value >= 0 }
    }
    val details = usage["input_tokens_details"] as? JsonObject ?: usage["prompt_tokens_details"] as? JsonObject
    AiRequestUsage(number(usage, "input_tokens", "prompt_tokens"), number(usage, "output_tokens", "completion_tokens"),
        number(details, "cached_tokens") ?: number(usage, "prompt_cache_hit_tokens"),
        number(root, "context_window") ?: number(usage, "context_window"), elapsedNanos)
}.getOrNull()
