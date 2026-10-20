package com.xiaomanjun.sleepdownschedule.feature.importing

import android.content.Context
import com.xiaomanjun.sleepdownschedule.BuildConfig
import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*

/** Provider resolution belongs to importing; the shared bootstrap store knows no feature. */
internal object ManagedAiRemoteConfig {
    fun managedFreeSettings(context: Context, effort: AiReasoningEffort): AiImportSettings {
        val candidates = managedFreeCandidates(context, effort)
        val selected = candidates.firstOrNull()
            ?: return AiImportSettings(managedProfile(SleepDownRemoteConfig.state.value.bootstrap?.managedAiConfigs()?.firstOrNull(), effort))
        // The entry point exposes pool capabilities. Each network attempt still uses its own flags.
        val vision = candidates.any { it.profile.supportsVision }
        return selected.copy(profile = selected.profile.copy(supportsVision = vision,
            capabilities = selected.profile.capabilities.copy(supportsImageInput = vision)))
    }

    internal fun managedFreeCandidates(context: Context, effort: AiReasoningEffort): List<AiImportSettings> {
        if (!BuildConfig.SLEEPDOWN_REMOTE_AI_ENABLED || BuildConfig.SLEEPDOWN_REMOTE_CONFIG_SECRET.isBlank()) return emptyList()
        val certificate = SigningCertificateDigest.current(context)
        val now = SleepDownRemoteConfig.estimatedServerTimeSeconds()
        return SleepDownRemoteConfig.state.value.bootstrap?.managedAiConfigs().orEmpty()
            .filter { it.availability(now) == RemoteAiAvailability.AVAILABLE }
            .mapNotNull { ai ->
                val key = runCatching {
                    RemoteSecretCrypto.decrypt(BuildConfig.SLEEPDOWN_REMOTE_CONFIG_SECRET, certificate, ai, context.packageName)
                }.getOrNull()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                AiImportSettings(managedProfile(ai, effort), key, "${ai.keyId}:${ai.configVersion}")
            }
    }

    fun isManagedFreeAvailable(context: Context): Boolean = managedFreeSettings(
        context,
        AiProviderPresets.dailyFree.reasoningEffort
    ).let { it.apiKey.isNotBlank() && it.profile.baseUrl.isNotBlank() && it.profile.defaultModel.isNotBlank() }

    fun managedFreeStatusMessage(context: Context): String {
        val candidates = managedFreeCandidates(context, AiProviderPresets.dailyFree.reasoningEffort)
        val configs = SleepDownRemoteConfig.state.value.bootstrap?.managedAiConfigs().orEmpty()
        val ai = configs.firstOrNull { "${it.keyId}:${it.configVersion}" == candidates.firstOrNull()?.managedRouteId }
            ?: configs.firstOrNull() ?: return "暂无每日免费 AI 配置，请刷新后重试。"
        when (ai.availability(SleepDownRemoteConfig.estimatedServerTimeSeconds())) {
            RemoteAiAvailability.DISABLED -> return ai.message.ifBlank { "每日免费 AI 当前正在维护，请稍后重试，或使用自己的 API Key。" }
            RemoteAiAvailability.EXPIRED -> return ai.message.ifBlank { "每日免费 AI 配置已过期，请稍后重试。" }
            RemoteAiAvailability.UNSUPPORTED -> return "每日免费 AI 配置版本暂不受支持，请更新应用。"
            RemoteAiAvailability.AVAILABLE -> Unit
        }
        if (!BuildConfig.SLEEPDOWN_REMOTE_AI_ENABLED) return "当前构建未启用每日免费 AI，请使用自己的 API Key。"
        if (!isManagedFreeAvailable(context)) return "每日免费 AI 安全配置校验失败，请刷新配置或使用自己的 API Key。"
        return ai.message.ifBlank { "服务正常 · ${ai.model}" } +
            if (candidates.size > 1) " · ${candidates.size} 组配置自动切换" else ""
    }

    internal fun activateResolvedProvider(context: Context) {
        runCatching { AiImportSettingsStore.activateAvailableSettings(context) }
            .onFailure { error ->
                // A malformed/expired remote credential must remain a normal unavailable state;
                // it must never prevent the rest of the app from starting.
                android.util.Log.w("SleepDownRemoteConfig", "Unable to activate cached AI config", error)
            }
    }

    private fun managedProfile(ai: RemoteAiConfig?, effort: AiReasoningEffort): AiProviderProfile {
        val endpoint = if (ai?.endpointStyle == "chat_completions") AiEndpointStyle.CHAT_COMPLETIONS else AiEndpointStyle.RESPONSES
        val supportsResponses = endpoint == AiEndpointStyle.RESPONSES
        return AiProviderPresets.dailyFree.copy(
            baseUrl = ai?.baseUrl.orEmpty(),
            defaultModel = ai?.model.orEmpty(),
            authType = managedAiAuthType(ai?.baseUrl.orEmpty()),
            capabilities = AiProviderPresets.dailyFree.capabilities.copy(
                supportsImageInput = ai?.supportsVision == true,
                supportsResponses = supportsResponses
            ),
            endpointStyle = endpoint,
            supportsVision = ai?.supportsVision == true,
            availableModels = listOfNotNull(ai?.model?.takeIf(String::isNotBlank)),
            reasoningEffort = effort
        )
    }
}
