package com.xiaomanjun.sleepdownschedule.feature.importing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

internal suspend fun requestAiEduRouting(
    fingerprint: AiEduFingerprint,
    settings: AiImportSettings,
    onHttpPhase: (AiImportHttpPhase) -> Unit
): String = withContext(Dispatchers.IO) {
    require(settings.hasCredentialConfiguration()) { "请先配置 AI，或直接使用文本导入" }
    val config = settings.toProviderConfig().normalizedForRequest()
    val prompt = AiEduRoutingPrompt + "\n" + fingerprint.modelInput()
    val responses = config.endpointStyle == AiEndpointStyle.RESPONSES
    val body = buildJsonObject {
        put("model", config.model)
        if (responses) {
            put("store", false)
            put("input", prompt)
            putResponsesReasoning(config)
            put("max_output_tokens", 2048)
        } else {
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "user"); put("content", prompt) })
            })
            putChatSamplingAndReasoning(config)
            put(if (config.usesMimoProtocol()) "max_completion_tokens" else "max_tokens", 2048)
        }
    }
    val response = postJson(config.resolveRequestEndpoint(), config.apiKey, body.toString(),
        config.authType, config.providerId, AiImportNetworkContext("EDU_ROUTING", onPhase = onHttpPhase))
    val result = if (responses) parseResponsesTextResult(response) else parseChatCompletionTextResult(response)
    // Do not persist arbitrary model output in the route or execute model-supplied code.
    val candidate = parseAiEduRoutingDecision(result.content, fingerprint)
    buildJsonObject {
        put("action", if (candidate == null) "text" else "adapter")
        put("candidate", candidate?.let { JsonPrimitive(fingerprint.candidates.indexOf(it)) } ?: JsonNull)
        put("confidence", if (candidate == null) 0 else 1)
    }.toString()
}
