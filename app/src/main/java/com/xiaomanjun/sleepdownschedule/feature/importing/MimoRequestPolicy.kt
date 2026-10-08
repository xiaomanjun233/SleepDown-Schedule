package com.xiaomanjun.sleepdownschedule.feature.importing

import java.net.URI

private val officialMimoHosts = setOf(
    "api.xiaomimimo.com", "token-plan-cn.xiaomimimo.com",
    "token-plan-sgp.xiaomimimo.com", "token-plan-ams.xiaomimimo.com"
)

internal fun AiProviderConfig.usesMimoProtocol(): Boolean =
    providerId == AiProviderPresets.mimo.id || providerId == AiProviderPresets.mimoTokenPlan.id ||
        (providerId == AiProviderPresets.dailyFree.id && officialMimoUri(baseUrl) != null)

private fun officialMimoUri(value: String): URI? = runCatching { URI(value.trim()) }.getOrNull()
    ?.takeIf { it.scheme.equals("https", true) && it.host?.lowercase() in officialMimoHosts }

internal fun isOfficialMimoEndpoint(value: String): Boolean = officialMimoUri(value) != null

/** Managed credentials follow the currently published endpoint, not the previous provider. */
internal fun managedAiAuthType(baseUrl: String): AiAuthType =
    if (isOfficialMimoEndpoint(baseUrl)) AiAuthType.CustomHeader else AiAuthType.ApiKeyBearer

internal fun mimoResponsesEffort(effort: AiReasoningEffort): String = when (effort) {
    AiReasoningEffort.NONE -> "none"
    AiReasoningEffort.MINIMAL, AiReasoningEffort.LOW -> "low"
    AiReasoningEffort.MEDIUM -> "medium"
    else -> "high"
}
