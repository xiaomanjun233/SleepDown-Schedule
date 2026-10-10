package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.backup.*
import kotlinx.serialization.Serializable


object AiProviderPresets {
    val codexCompatibleModelIds = listOf(
        "gpt-5.6",
        "gpt-5.6-sol",
        "gpt-5.6-terra",
        "gpt-5.6-luna",
        "gpt-5.5",
        "gpt-5.4",
        "gpt-5.4-mini",
        "gpt-5.4-nano",
        "gpt-5.3-codex",
        "gpt-5.1-codex-mini",
        "gpt-4.1-mini",
        "gpt-4.1-nano"
    )

    val none = AiProviderProfile(
        id = "none",
        displayName = "无",
        providerType = AiProviderType.OpenAIChatCompatible,
        baseUrl = "",
        defaultModel = "",
        capabilities = AiProviderCapabilities(
            supportsTextInput = false
        ),
        endpointStyle = AiEndpointStyle.CHAT_COMPLETIONS,
        structuredOutputMode = StructuredOutputMode.PROMPT_ONLY
    )

    val openAI = AiProviderProfile(
        id = "openai",
        displayName = "旧版 OpenAI API（保留）",
        providerType = AiProviderType.OpenAIResponses,
        baseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-5.6",
        capabilities = AiProviderCapabilities(
            supportsPdfFileInput = true,
            supportsImageInput = true,
            supportsTextInput = true,
            supportsJsonSchema = true,
            supportsJsonMode = true,
            supportsFileUpload = true,
            supportsResponses = true
        ),
        endpointStyle = AiEndpointStyle.RESPONSES,
        structuredOutputMode = StructuredOutputMode.JSON_SCHEMA,
        supportsVision = true,
        supportsFileUpload = true,
        supportsPdfDirect = true
    )

    // ChatGPT account credentials never enter AiImportSettings or editable API settings.
    // The model catalog is discovered for the signed-in account, not guessed from API models.
    val chatGpt = AiProviderProfile(
        id = "chatgpt",
        displayName = "ChatGPT 账号",
        providerType = AiProviderType.OpenAIResponses,
        authType = AiAuthType.ChatGptOAuth,
        baseUrl = "https://api.openai.com/v1",
        defaultModel = "",
        capabilities = AiProviderCapabilities(
            supportsTextInput = true,
            supportsJsonSchema = true,
            supportsJsonMode = true,
            supportsStreaming = true,
            supportsResponses = true
        ),
        endpointStyle = AiEndpointStyle.RESPONSES,
        structuredOutputMode = StructuredOutputMode.JSON_SCHEMA
    )

    val dailyFree = AiProviderProfile(
        id = "sleepdown_daily_free",
        displayName = "每日免费 AI",
        providerType = AiProviderType.OpenAIResponses,
        // The active endpoint resolves authentication when the managed profile is loaded.
        authType = AiAuthType.ApiKeyBearer,
        baseUrl = "",
        defaultModel = "",
        capabilities = AiProviderCapabilities(
            supportsImageInput = true,
            supportsTextInput = true,
            supportsJsonSchema = true,
            supportsJsonMode = true,
            supportsResponses = true
        ),
        endpointStyle = AiEndpointStyle.RESPONSES,
        structuredOutputMode = StructuredOutputMode.JSON_SCHEMA,
        supportsVision = true,
        // 每日免费 AI 的地址由后端下发，按原样使用、不再额外补 /responses 等后缀
        responsesPath = "",
        chatCompletionsPath = "",
        availableModels = emptyList()
    )

    val deepSeek = AiProviderProfile(
        id = "deepseek",
        displayName = "DeepSeek",
        providerType = AiProviderType.OpenAIResponses,
        baseUrl = "https://api.deepseek.com",
        defaultModel = "deepseek-v4-flash",
        capabilities = AiProviderCapabilities(
            supportsTextInput = true,
            supportsJsonMode = true,
            supportsResponses = true
        ),
        endpointStyle = AiEndpointStyle.RESPONSES,
        structuredOutputMode = StructuredOutputMode.PROMPT_ONLY,
        reasoningEffort = AiReasoningEffort.HIGH
    )

    val dashScope = AiProviderProfile(
        id = "dashscope",
        displayName = "通义千问 / 百炼",
        providerType = AiProviderType.OpenAIChatCompatible,
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        defaultModel = "qwen-plus",
        capabilities = AiProviderCapabilities(
            supportsImageInput = true,
            supportsTextInput = true,
            supportsJsonMode = true
        ),
        endpointStyle = AiEndpointStyle.CHAT_COMPLETIONS,
        structuredOutputMode = StructuredOutputMode.JSON_OBJECT,
        supportsVision = true
    )

    val kimi = AiProviderProfile(
        id = "kimi",
        displayName = "Kimi",
        providerType = AiProviderType.OpenAIChatCompatible,
        baseUrl = "https://api.moonshot.cn/v1",
        defaultModel = "kimi-k2.6",
        capabilities = AiProviderCapabilities(
            supportsImageInput = true,
            supportsTextInput = true,
            supportsJsonMode = true
        ),
        endpointStyle = AiEndpointStyle.CHAT_COMPLETIONS,
        structuredOutputMode = StructuredOutputMode.JSON_OBJECT,
        supportsVision = true,
        supportsFileUpload = true,
        supportsPdfDirect = true
    )

    val zhipu = AiProviderProfile(
        id = "zhipu",
        displayName = "智谱 GLM",
        providerType = AiProviderType.OpenAIChatCompatible,
        baseUrl = "https://open.bigmodel.cn/api/paas/v4",
        defaultModel = "glm-4-flash",
        capabilities = AiProviderCapabilities(
            supportsImageInput = true,
            supportsTextInput = true,
            supportsJsonMode = true
        ),
        endpointStyle = AiEndpointStyle.CHAT_COMPLETIONS,
        structuredOutputMode = StructuredOutputMode.JSON_OBJECT,
        supportsVision = true
    )

    val qianfan = AiProviderProfile(
        id = "qianfan",
        displayName = "百度千帆",
        providerType = AiProviderType.OpenAIChatCompatible,
        baseUrl = "https://qianfan.baidubce.com/v2",
        defaultModel = "ernie-4.0-turbo-8k",
        capabilities = AiProviderCapabilities(
            supportsImageInput = true,
            supportsTextInput = true,
            supportsJsonMode = true
        ),
        endpointStyle = AiEndpointStyle.CHAT_COMPLETIONS,
        structuredOutputMode = StructuredOutputMode.JSON_OBJECT,
        supportsVision = true
    )

    val doubao = AiProviderProfile(
        id = "doubao",
        displayName = "火山方舟 / 豆包",
        providerType = AiProviderType.OpenAIChatCompatible,
        baseUrl = "https://ark.cn-beijing.volces.com/api/v3",
        defaultModel = "doubao-seed-1-6",
        capabilities = AiProviderCapabilities(
            supportsImageInput = true,
            supportsTextInput = true,
            supportsJsonMode = true
        ),
        endpointStyle = AiEndpointStyle.CHAT_COMPLETIONS,
        structuredOutputMode = StructuredOutputMode.JSON_OBJECT,
        supportsVision = true
    )

    val hunyuan = AiProviderProfile(
        id = "hunyuan",
        displayName = "腾讯混元",
        providerType = AiProviderType.OpenAIChatCompatible,
        baseUrl = "https://api.hunyuan.cloud.tencent.com/v1",
        defaultModel = "hunyuan-turbos-latest",
        capabilities = AiProviderCapabilities(
            supportsImageInput = true,
            supportsTextInput = true,
            supportsJsonMode = true
        ),
        endpointStyle = AiEndpointStyle.CHAT_COMPLETIONS,
        structuredOutputMode = StructuredOutputMode.JSON_OBJECT,
        supportsVision = true
    )

    val siliconFlow = AiProviderProfile(
        id = "siliconflow",
        displayName = "SiliconFlow",
        providerType = AiProviderType.OpenAIChatCompatible,
        baseUrl = "https://api.siliconflow.cn/v1",
        defaultModel = "Qwen/Qwen2.5-72B-Instruct",
        capabilities = AiProviderCapabilities(
            supportsImageInput = true,
            supportsTextInput = true,
            supportsJsonMode = true
        ),
        endpointStyle = AiEndpointStyle.CHAT_COMPLETIONS,
        structuredOutputMode = StructuredOutputMode.JSON_OBJECT,
        supportsVision = true
    )

    val miniMax = AiProviderProfile(
        id = "minimax",
        displayName = "MiniMax",
        providerType = AiProviderType.OpenAIChatCompatible,
        baseUrl = "https://api.minimax.chat/v1",
        defaultModel = "MiniMax-M1",
        capabilities = AiProviderCapabilities(
            supportsImageInput = true,
            supportsTextInput = true,
            supportsJsonMode = true
        ),
        endpointStyle = AiEndpointStyle.CHAT_COMPLETIONS,
        structuredOutputMode = StructuredOutputMode.JSON_OBJECT,
        supportsVision = true
    )

    val mimo = AiProviderProfile(
        id = "mimo",
        displayName = "小米 MiMo",
        providerType = AiProviderType.OpenAIChatCompatible,
        baseUrl = "https://api.xiaomimimo.com/v1",
        defaultModel = "mimo-v2.5-pro",
        authType = AiAuthType.CustomHeader,
        capabilities = AiProviderCapabilities(
            supportsImageInput = true,
            supportsTextInput = true,
            supportsJsonMode = false,
            supportsResponses = true
        ),
        endpointStyle = AiEndpointStyle.CHAT_COMPLETIONS,
        structuredOutputMode = StructuredOutputMode.PROMPT_ONLY,
        supportsVision = true
    )

    val mimoTokenPlan = mimo.copy(
        id = "mimo_token_plan",
        displayName = "小米 MiMo Token Plan",
        baseUrl = "https://token-plan-cn.xiaomimimo.com/v1"
    )

    val custom = AiProviderProfile(
        id = "custom",
        displayName = "自定义兼容接口",
        providerType = AiProviderType.OpenAIChatCompatible,
        baseUrl = "",
        defaultModel = codexCompatibleModelIds.first(),
        capabilities = AiProviderCapabilities(supportsResponses = true),
        endpointStyle = AiEndpointStyle.CHAT_COMPLETIONS,
        structuredOutputMode = StructuredOutputMode.PROMPT_ONLY,
        availableModels = codexCompatibleModelIds
    )

    val selectable = listOf(none, dailyFree, chatGpt, deepSeek, mimo, custom)

    val all = listOf(none, dailyFree, chatGpt, openAI, deepSeek, dashScope, kimi, zhipu, qianfan, doubao, hunyuan, siliconFlow, miniMax, mimo, mimoTokenPlan, custom)

    fun isManagedFreeId(id: String): Boolean = id == dailyFree.id

    fun isChatGptId(id: String): Boolean = id == chatGpt.id

    /** Only account model/vision metadata and reasoning are configurable for this provider. */
    fun normalizeChatGptProfile(profile: AiProviderProfile): AiProviderProfile = chatGpt.copy(
        defaultModel = profile.defaultModel.trim(),
        availableModels = profile.availableModels.map(String::trim).filter(String::isNotBlank).distinct(),
        supportsVision = profile.supportsVision,
        capabilities = chatGpt.capabilities.copy(supportsImageInput = profile.supportsVision),
        reasoningEffort = profile.reasoningEffort
    )

    fun isCustomId(id: String): Boolean = id == custom.id || id.startsWith("${custom.id}:")

    fun customProfile(id: String, displayName: String = custom.displayName): AiProviderProfile =
        custom.copy(id = id, displayName = displayName)

    fun byId(id: String): AiProviderProfile = when {
        isCustomId(id) -> customProfile(id)
        else -> all.firstOrNull { it.id == id } ?: openAI
    }

    fun modelOptions(providerId: String): List<AiModelOption> = when (providerId) {
        dailyFree.id -> emptyList()
        openAI.id -> listOf(
            AiModelOption("GPT-5.6", "gpt-5.6", supportsImageInput = true, supportsResponses = true),
            AiModelOption("5.6 Sol", "gpt-5.6-sol", supportsImageInput = true, supportsResponses = true),
            AiModelOption("5.6 Terra", "gpt-5.6-terra", supportsImageInput = true, supportsResponses = true),
            AiModelOption("5.6 Luna", "gpt-5.6-luna", supportsImageInput = true, supportsResponses = true),
            AiModelOption("GPT-5.5", "gpt-5.5", supportsImageInput = true, supportsResponses = true),
            AiModelOption("GPT-5.4", "gpt-5.4", supportsImageInput = true, supportsResponses = true),
            AiModelOption("5.4 mini", "gpt-5.4-mini", supportsImageInput = true, supportsResponses = true),
            AiModelOption("5.4 nano", "gpt-5.4-nano", supportsImageInput = true, supportsResponses = true),
            AiModelOption("5.3 Codex", "gpt-5.3-codex", supportsResponses = true),
            AiModelOption("5.1 Codex mini", "gpt-5.1-codex-mini", supportsResponses = true),
            AiModelOption("4.1 mini", "gpt-4.1-mini", supportsResponses = true),
            AiModelOption("4.1 nano", "gpt-4.1-nano", supportsResponses = true)
        )
        deepSeek.id -> listOf(
            AiModelOption("V4 Flash", "deepseek-v4-flash", supportsResponses = true),
            AiModelOption("V4 Flash Vision Exp", "deepseek-v4-flash-vision-exp", supportsImageInput = true, supportsResponses = true),
            AiModelOption("V4 Pro", "deepseek-v4-pro", supportsResponses = true)
        )
        dashScope.id -> listOf(
            AiModelOption("Qwen Plus", "qwen-plus"),
            AiModelOption("Qwen VL Plus", "qwen-vl-plus", supportsImageInput = true),
            AiModelOption("Qwen VL Max", "qwen-vl-max", supportsImageInput = true)
        )
        kimi.id -> listOf(
            AiModelOption("K2.6", "kimi-k2.6", supportsImageInput = true),
            AiModelOption("K2.5", "kimi-k2.5", supportsImageInput = true),
            AiModelOption("Vision 32K", "moonshot-v1-32k-vision-preview", supportsImageInput = true)
        )
        zhipu.id -> listOf(
            AiModelOption("GLM 4 Flash", "glm-4-flash"),
            AiModelOption("GLM 4V Flash", "glm-4v-flash", supportsImageInput = true),
            AiModelOption("GLM 4 Plus", "glm-4-plus")
        )
        qianfan.id -> listOf(
            AiModelOption("ERNIE 4 Turbo", "ernie-4.0-turbo-8k"),
            AiModelOption("ERNIE X1", "ernie-x1-turbo-32k")
        )
        doubao.id -> listOf(
            AiModelOption("Doubao Seed", "doubao-seed-1-6"),
            AiModelOption("Doubao Vision", "doubao-1-5-vision-pro", supportsImageInput = true)
        )
        hunyuan.id -> listOf(
            AiModelOption("Hunyuan Turbo", "hunyuan-turbos-latest"),
            AiModelOption("Hunyuan Vision", "hunyuan-vision", supportsImageInput = true)
        )
        siliconFlow.id -> listOf(
            AiModelOption("Qwen 72B", "Qwen/Qwen2.5-72B-Instruct"),
            AiModelOption("Qwen VL", "Qwen/Qwen2.5-VL-72B-Instruct", supportsImageInput = true),
            AiModelOption("DeepSeek V3", "deepseek-ai/DeepSeek-V3")
        )
        miniMax.id -> listOf(
            AiModelOption("MiniMax M1", "MiniMax-M1"),
            AiModelOption("MiniMax Text", "abab6.5s-chat")
        )
        mimo.id, mimoTokenPlan.id -> listOf(
            AiModelOption("MiMo V2.5 Pro", "mimo-v2.5-pro", supportsImageInput = true, supportsResponses = true),
            AiModelOption("MiMo V2.5", "mimo-v2.5", supportsImageInput = true, supportsResponses = true)
        )
        else -> emptyList()
    }

    /**
     * Provider capabilities describe the endpoint, while this answers whether the concrete
     * selected model accepts image input. Known presets are explicit; a custom/unknown model
     * keeps the user's saved capability switch.
     */
    fun supportsImageInput(profile: AiProviderProfile): Boolean {
        if (isCustomId(profile.id)) {
            return profile.supportsVision || profile.capabilities.supportsImageInput
        }
        val selected = modelOptions(profile).firstOrNull {
            it.model.equals(profile.defaultModel.trim(), ignoreCase = true)
        }
        return selected?.supportsImageInput
            ?: (profile.supportsVision || profile.capabilities.supportsImageInput)
    }

    fun modelOptions(profile: AiProviderProfile): List<AiModelOption> {
        if (isChatGptId(profile.id)) {
            return profile.availableModels.filter(String::isNotBlank).distinct().map { model ->
                AiModelOption(
                    label = model,
                    model = model,
                    supportsImageInput = profile.supportsVision && model == profile.defaultModel,
                    supportsResponses = true
                )
            }
        }
        val known = modelOptions(profile.id)
        val configured = profile.availableModels.mapNotNull { modelId ->
            val normalized = modelId.trim()
            if (normalized.isBlank()) null else known.firstOrNull {
                it.model.equals(normalized, ignoreCase = true)
            } ?: AiModelOption(
                label = normalized,
                model = normalized,
                supportsImageInput = profile.supportsVision || profile.capabilities.supportsImageInput,
                supportsResponses = profile.capabilities.supportsResponses
            )
        }
        return (known + configured).distinctBy { it.model.lowercase() }
    }

    fun supportsResponses(profile: AiProviderProfile): Boolean {
        val selected = modelOptions(profile).firstOrNull {
            it.model.equals(profile.defaultModel.trim(), ignoreCase = true)
        }
        return selected?.supportsResponses ?: profile.capabilities.supportsResponses
    }

    fun shouldUseResponses(profile: AiProviderProfile): Boolean =
        profile.endpointStyle == AiEndpointStyle.RESPONSES && supportsResponses(profile)

    fun reasoningEfforts(profile: AiProviderProfile): List<AiReasoningEffort> {
        // Account model discovery does not advertise supported effort levels yet.
        if (isChatGptId(profile.id)) return emptyList()
        if (!supportsResponses(profile)) return emptyList()
        val model = profile.defaultModel.trim().lowercase()
        return when {
            model.startsWith("gpt-5.6") -> AiReasoningEffort.entries
            profile.id == deepSeek.id -> listOf(
                AiReasoningEffort.NONE,
                AiReasoningEffort.LOW,
                AiReasoningEffort.HIGH,
                AiReasoningEffort.MAX
            )
            profile.id == openAI.id -> listOf(
                AiReasoningEffort.NONE,
                AiReasoningEffort.MINIMAL,
                AiReasoningEffort.LOW,
                AiReasoningEffort.MEDIUM,
                AiReasoningEffort.HIGH
            )
            else -> listOf(
                AiReasoningEffort.NONE,
                AiReasoningEffort.LOW,
                AiReasoningEffort.MEDIUM,
                AiReasoningEffort.HIGH
            )
        }
    }
}

@Serializable
internal data class AiCustomProviderEntry(
    val id: String,
    val displayName: String
)

