package com.xiaomanjun.sleepdownschedule.feature.importing

import kotlinx.serialization.json.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.HttpURLConnection

/** Logout/account replacement cancels streams and rejects late completed results. */
internal object ChatGptInferenceSessions {
    private var generation = 0L
    private val connections = mutableMapOf<HttpURLConnection, Long>()
    @Synchronized fun currentGeneration(): Long = generation
    @Synchronized fun register(connection: HttpURLConnection, expected: Long) {
        if (expected != generation) {
            connection.disconnect()
            error("ChatGPT 账号已变更，请重新发起请求。")
        }
        connections[connection] = expected
    }
    @Synchronized fun checkActive(connection: HttpURLConnection) {
        check(connections[connection] == generation) { "ChatGPT 账号已退出或变更，本次结果已取消。" }
    }
    @Synchronized fun release(connection: HttpURLConnection) { connections.remove(connection) }
    fun invalidate() {
        val active = synchronized(this) {
            generation++
            connections.keys.toList().also { connections.clear() }
        }
        active.forEach { runCatching { it.disconnect() } }
    }
}

data class ChatGptUsageSnapshot(
    val status: String = "尚未发起调用",
    val checkedAtMillis: Long? = null,
    val limitReached: Boolean = false,
    val diagnostic: String? = null
)

/** A local status indicator, never an estimate of the server's subscription allowance. */
object ChatGptUsageStatus {
    private val mutableState = MutableStateFlow(ChatGptUsageSnapshot())
    val state = mutableState.asStateFlow()
    fun clear() { mutableState.value = ChatGptUsageSnapshot() }
    internal fun completed() {
        mutableState.value = ChatGptUsageSnapshot("最近调用成功", System.currentTimeMillis())
    }
    internal fun failed(status: Int? = null, code: String? = null, diagnostic: String? = null) {
        mutableState.value = ChatGptUsageSnapshot(
            when {
                code == "subscription_sharing_usage_limit_exceeded" -> "此应用的套餐使用受限"
                status == 429 -> "请求受到限流，额度详情请在 ChatGPT 中查看"
                code == "subscription_sharing_user_not_eligible" -> "当前账户暂不符合套餐调用资格"
                code == "subscription_sharing_usage_unavailable" -> "套餐用量服务暂不可用"
                else -> "最近调用未成功，请检查连接与授权"
            },
            System.currentTimeMillis(), code == "subscription_sharing_usage_limit_exceeded", diagnostic
        )
    }
}

/** Isolate the preview subscription protocol from API-key / compatible-provider behavior. */
internal fun requireChatGptInferenceEndpoint(endpoint: String) {
    require(endpoint == "https://api.openai.com/v1/responses") {
        "ChatGPT 登录仅可用于官方 Responses 接口"
    }
}

internal fun chatGptInferenceError(status: Int, body: String = "", requestId: String? = null): String {
    val root = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
    val error = (root?.get("error") as? JsonObject)
        ?: ((root?.get("response") as? JsonObject)?.get("error") as? JsonObject)
        ?: (root?.get("detail") as? JsonObject)
    val code = (error?.get("code") as? JsonPrimitive)?.contentOrNull
    val safeCode = code?.takeIf { it in ChatGptKnownErrorCodes }
    val safeParam = (error?.get("param") as? JsonPrimitive)?.contentOrNull?.takeIf {
        it in setOf("model", "tools", "input", "service_tier", "reasoning", "tool_choice")
    }
    val safeRequestId = requestId?.takeIf { it.matches(Regex("req_[A-Za-z0-9_-]{1,100}")) }
    val diagnostic = listOfNotNull(status.takeIf { it > 0 }?.let { "HTTP $it" }, safeCode,
        safeParam?.let { "param=$it" }, safeRequestId).joinToString(" · ").ifBlank { null }
    ChatGptUsageStatus.failed(status, safeCode, diagnostic)
    return when (code) {
        "subscription_sharing_usage_limit_exceeded" -> "ChatGPT 此应用的套餐使用受限，请打开“管理用量”查看。不会自动切换到 API 付费。"
        "subscription_sharing_usage_unavailable" -> "ChatGPT 用量服务暂不可用，请稍后重试；已保留登录状态。"
        "subscription_sharing_user_not_eligible" -> "当前 ChatGPT 账户不符合套餐调用资格，请在 ChatGPT 中检查计划或工作区。"
        "subscription_sharing_invalid_user" -> "ChatGPT 账户上下文未被接受，请检查账户权限；已保留登录状态。"
        "subscription_sharing_unsupported_capability" -> "当前 ChatGPT 模型不支持此功能，请选择其他可用模型。"
        "subscription_sharing_route_not_supported" -> "ChatGPT 套餐调用暂不支持此请求路径，请检查客户端版本。"
        "subscription_sharing_user_unavailable" -> "ChatGPT 账户或工作区信息暂不可用，请稍后重试；已保留登录状态。"
        "chatpass_v2_scope_not_authorized", "chatpass_v2_invalid_authorization_context" -> "ChatGPT 授权范围不允许此操作，请检查应用与账户授权配置。"
        else -> when (status) {
    401 -> "ChatGPT 未接受当前账户或调用权限，请在 AI 设置中检查授权。"
    403 -> "当前 ChatGPT 账户、工作区或模型没有此调用权限，请在 AI 设置中检查授权和可用模型。"
    429 -> "ChatGPT 套餐额度或请求速率已达上限，请在 ChatGPT 中管理用量并稍后重试。不会自动切换到 API 付费。"
    else -> "ChatGPT 请求失败 ($status)，请稍后重试或在 AI 设置中检查账户。"
        }
    }
}

private val ChatGptKnownErrorCodes = setOf(
    "subscription_sharing_user_not_eligible", "subscription_sharing_usage_limit_exceeded",
    "subscription_sharing_usage_unavailable", "subscription_sharing_unsupported_capability",
    "subscription_sharing_route_not_supported", "subscription_sharing_invalid_user",
    "subscription_sharing_user_unavailable", "chatpass_v2_scope_not_authorized",
    "chatpass_v2_invalid_authorization_context"
)

/** No server conversation storage, upload API, implicit billing switch, or unsupported fields. */
internal fun chatGptResponsesBody(body: JsonObject): JsonObject {
    val values = body.toMutableMap()
    setOf("background", "conversation", "max_output_tokens", "max_tool_calls", "metadata",
        "moderation", "multi_agent", "prompt", "prompt_cache_retention", "safety_identifier",
        "temperature", "top_logprobs", "top_p", "truncation", "user", "previous_response_id",
        // Let the account/model choose its supported default rather than guessing its effort range.
        "reasoning").forEach(values::remove)
    values["store"] = JsonPrimitive(false)
    values["stream"] = JsonPrimitive(true)
    val input = when (val raw = values["input"]) {
        is JsonArray -> raw
        is JsonPrimitive -> buildJsonArray {
            add(buildJsonObject { put("role", "user"); put("content", raw) })
        }
        else -> error("ChatGPT 请求缺少输入内容")
    }
    values["input"] = JsonArray(input.map { item ->
        if (item is JsonObject && item["role"]?.jsonPrimitive?.contentOrNull == "system") {
            JsonObject(item + ("role" to JsonPrimitive("developer")))
        } else item
    })
    (values["tools"] as? JsonArray)?.let { tools ->
        require(tools.all { (it as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull == "function" }) {
            "ChatGPT 套餐调用仅支持本地函数工具"
        }
        if (tools.isNotEmpty()) values["tools"] = buildJsonArray {
            add(buildJsonObject {
                put("type", "namespace")
                put("name", "sleepdown")
                put("description", "SleepDown local schedule tools")
                put("tools", tools)
            })
        }
        // Forced function selectors outside namespaces are not valid on this preview route.
        if (values["tool_choice"] is JsonObject) values["tool_choice"] = JsonPrimitive("required")
    }
    return JsonObject(values)
}
