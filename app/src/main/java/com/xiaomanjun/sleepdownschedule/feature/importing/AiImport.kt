package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.app.config.SleepDownRemoteConfig
import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*
import com.xiaomanjun.sleepdownschedule.*

import com.xiaomanjun.sleepdownschedule.feature.backup.*

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.security.KeyStore
import java.util.UUID
import java.util.zip.InflaterInputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val AiImportLogTag = "AiImport"
internal const val MaxAiImportFileBytes = 20 * 1024 * 1024

enum class AiEndpointStyle {
    CHAT_COMPLETIONS,
    RESPONSES,
    KIMI_FILE_EXTRACT
}

enum class StructuredOutputMode {
    JSON_SCHEMA,
    JSON_OBJECT,
    PROMPT_ONLY
}

enum class AiInputMode {
    AUTO,
    TEXT_ONLY,
    IMAGE_URL_BASE64,
    FILE_UPLOAD_EXTRACT,
    RESPONSES_FILE
}

enum class AiProviderType {
    OpenAIResponses,
    OpenAIChatCompatible
}

@Serializable
enum class AiReasoningEffort(val apiValue: String, val label: String) {
    NONE("none", "关闭"),
    MINIMAL("minimal", "极低"),
    LOW("low", "低"),
    MEDIUM("medium", "中"),
    HIGH("high", "高"),
    XHIGH("xhigh", "极高"),
    MAX("max", "最高")
}

enum class AiAuthType {
    ApiKeyBearer,
    OpenAIProjectKey,
    CustomHeader
}

@Serializable
data class AiProviderCapabilities(
    val supportsPdfFileInput: Boolean = false,
    val supportsImageInput: Boolean = false,
    val supportsTextInput: Boolean = true,
    val supportsJsonSchema: Boolean = false,
    val supportsJsonMode: Boolean = true,
    val supportsFileUpload: Boolean = false,
    val supportsStreaming: Boolean = false,
    val supportsResponses: Boolean = false
)

@Serializable
data class AiProviderProfile(
    val id: String,
    val displayName: String,
    val providerType: AiProviderType,
    val baseUrl: String,
    val chatCompletionsPath: String = "/chat/completions",
    val responsesPath: String = "/responses",
    val filesPath: String = "/files",
    val defaultModel: String,
    val authType: AiAuthType = AiAuthType.ApiKeyBearer,
    val capabilities: AiProviderCapabilities = AiProviderCapabilities(),
    val endpointStyle: AiEndpointStyle = when (providerType) {
        AiProviderType.OpenAIResponses -> AiEndpointStyle.RESPONSES
        AiProviderType.OpenAIChatCompatible -> AiEndpointStyle.CHAT_COMPLETIONS
    },
    val structuredOutputMode: StructuredOutputMode = when {
        capabilities.supportsJsonSchema -> StructuredOutputMode.JSON_SCHEMA
        capabilities.supportsJsonMode -> StructuredOutputMode.JSON_OBJECT
        else -> StructuredOutputMode.PROMPT_ONLY
    },
    val inputMode: AiInputMode = AiInputMode.AUTO,
    val supportsVision: Boolean = capabilities.supportsImageInput,
    val supportsFileUpload: Boolean = capabilities.supportsFileUpload,
    val supportsPdfDirect: Boolean = capabilities.supportsPdfFileInput,
    val availableModels: List<String> = emptyList(),
    val reasoningEffort: AiReasoningEffort = AiReasoningEffort.MEDIUM
)

data class AiImportSettings(
    val profile: AiProviderProfile = AiProviderPresets.none,
    val apiKey: String = ""
)

data class AiProviderConfig(
    val providerId: String,
    val displayName: String,
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val endpointStyle: AiEndpointStyle,
    val structuredOutputMode: StructuredOutputMode,
    val supportsVision: Boolean,
    val supportsFileUpload: Boolean,
    val supportsPdfDirect: Boolean,
    val supportsResponses: Boolean,
    val inputMode: AiInputMode,
    val reasoningEffort: AiReasoningEffort,
    val authType: AiAuthType = AiAuthType.ApiKeyBearer
)

sealed interface AiScheduleInput {
    data class ExtractedText(
        val text: String,
        val sourceName: String
    ) : AiScheduleInput

    data class ImageBase64(
        val mimeType: String,
        val base64: String,
        val sourceName: String
    ) : AiScheduleInput

    data class Images(
        val images: List<RenderedPageImage>,
        val sourceName: String
    ) : AiScheduleInput

    data class CapturedPage(
        val text: String,
        val images: List<RenderedPageImage>,
        val sourceName: String,
        val warnings: List<String>
    ) : AiScheduleInput

    data class RawFile(
        val mimeType: String,
        val fileName: String,
        val bytes: ByteArray
    ) : AiScheduleInput
}

data class RenderedPageImage(
    val pageIndex: Int,
    val mimeType: String,
    val base64: String
) {
    val dataUrl: String get() = "data:$mimeType;base64,$base64"
}

sealed interface PreprocessResult {
    val routeMessage: String

    data class Text(
        val text: String,
        override val routeMessage: String
    ) : PreprocessResult

    data class Images(
        val images: List<RenderedPageImage>,
        override val routeMessage: String
    ) : PreprocessResult

    data class Raw(
        val file: AiImportFile,
        val bytes: ByteArray,
        override val routeMessage: String
    ) : PreprocessResult
}

data class AiScheduleImportResult(
    val output: String,
    val routeMessage: String,
    val rawOutput: String = output,
    val reasoningOutput: String = ""
)

data class AiProviderTextResult(
    val content: String,
    val reasoning: String = "",
    val finishReason: String = ""
)

class AiServiceResponseException(
    message: String,
    val rawBody: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

fun Throwable.aiRawResponseBody(): String? {
    return when (this) {
        is AiServiceResponseException -> rawBody
        else -> cause?.aiRawResponseBody()
    }
}

fun sanitizeAiOutputForDisplay(output: String): String {
    return redactReasoningFields(output)
        .stripInlineReasoningBlocks()
        .trim()
}

fun extractAiReasoningForDisplay(output: String): String {
    return collectReasoningText(output)
        .joinToString("\n\n")
        .trim()
}

data class AiModelOption(
    val label: String,
    val model: String,
    val supportsImageInput: Boolean = false,
    val supportsResponses: Boolean = false
)

data class AiImportFile(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val bytes: ByteArray
) {
    val sizeBytes: Int get() = bytes.size
    internal val documentKind: AiImportDocumentKind get() = classifyAiImportDocument(displayName, mimeType)
    val isPdf: Boolean get() = documentKind == AiImportDocumentKind.PDF
    val isImage: Boolean get() = documentKind == AiImportDocumentKind.IMAGE
    val isText: Boolean get() = documentKind == AiImportDocumentKind.PLAIN_TEXT
    val isLocalTextDocument: Boolean get() = documentKind in setOf(
        AiImportDocumentKind.PLAIN_TEXT,
        AiImportDocumentKind.XLSX,
        AiImportDocumentKind.DOCX,
        AiImportDocumentKind.PPTX,
        AiImportDocumentKind.ODS
    )
    val isIcs: Boolean get() = mimeType.equals("text/calendar", ignoreCase = true) ||
        mimeType.equals("application/ics", ignoreCase = true) ||
        displayName.endsWith(".ics", ignoreCase = true) ||
        bytes.copyOfRange(0, minOf(bytes.size, 256)).toString(Charsets.UTF_8).contains("BEGIN:VCALENDAR", ignoreCase = true)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AiImportFile) return false
        return uri == other.uri &&
            displayName == other.displayName &&
            mimeType == other.mimeType &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = uri.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

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
        displayName = "OpenAI",
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

    val dailyFree = AiProviderProfile(
        id = "sleepdown_daily_free",
        displayName = "每日免费 AI",
        providerType = AiProviderType.OpenAIResponses,
        // The hosted MiMo endpoint authenticates with `api-key`, not an OpenAI Bearer token.
        authType = AiAuthType.CustomHeader,
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

    val selectable = listOf(none, dailyFree, openAI, deepSeek, mimo, custom)

    val all = listOf(none, dailyFree, openAI, deepSeek, dashScope, kimi, zhipu, qianfan, doubao, hunyuan, siliconFlow, miniMax, mimo, mimoTokenPlan, custom)

    fun isManagedFreeId(id: String): Boolean = id == dailyFree.id

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
private data class AiCustomProviderEntry(
    val id: String,
    val displayName: String
)

object AiImportSettingsStore {
    private const val PrefName = "ai_import_settings"
    private const val KeyProviderId = "provider_id"
    private const val KeyBaseUrl = "base_url"
    private const val KeyModel = "model"
    private const val KeyProviderType = "provider_type"
    private const val KeyImage = "supports_image"
    private const val KeyPdf = "supports_pdf"
    private const val KeyJsonSchema = "supports_json_schema"
    private const val KeyJsonMode = "supports_json_mode"
    private const val KeyFileUpload = "supports_file_upload"
    private const val KeyResponses = "supports_responses"
    private const val KeyEndpointStyle = "endpoint_style"
    private const val KeyStructuredOutputMode = "structured_output_mode"
    private const val KeyInputMode = "input_mode"
    private const val KeyVision = "supports_vision"
    private const val KeyPdfDirect = "supports_pdf_direct"
    private const val KeyAvailableModels = "available_models_v1"
    private const val KeyReasoningEffort = "reasoning_effort"
    private const val KeyEncryptedApiKey = "encrypted_api_key"
    private const val KeyCustomProviders = "custom_provider_profiles_v1"
    private const val KeyManagedFreeOfferDecision = "managed_free_offer_decision_v1"
    private const val ManagedFreeOfferEnabled = "enabled"
    private const val ManagedFreeOfferDeclined = "declined"
    private val settingsJson = Json { ignoreUnknownKeys = true }
    private val changeVersion = MutableStateFlow(0L)
    val changes = changeVersion.asStateFlow()
    private fun apiKeyKey(providerId: String): String = "${KeyEncryptedApiKey}_${providerId}"
    private fun providerKey(key: String, providerId: String): String = "${key}_${providerId}"
    private fun notifyChanged() {
        changeVersion.value = changeVersion.value + 1L
    }

    fun notifyRemoteConfigChanged() = notifyChanged()

    private fun managedFreeSettings(context: Context, prefs: android.content.SharedPreferences): AiImportSettings {
        val effort = runCatching {
            AiReasoningEffort.valueOf(
                prefs.getString(
                    providerKey(KeyReasoningEffort, AiProviderPresets.dailyFree.id),
                    AiProviderPresets.dailyFree.reasoningEffort.name
                ).orEmpty()
            )
        }.getOrDefault(AiProviderPresets.dailyFree.reasoningEffort)
        return SleepDownRemoteConfig.managedFreeSettings(context, effort)
    }

    fun hasUserConfiguredApiKey(context: Context): Boolean {
        val profiles = (AiProviderPresets.all + selectableProfiles(context))
            .distinctBy(AiProviderProfile::id)
            .filterNot { it.id == AiProviderPresets.none.id || AiProviderPresets.isManagedFreeId(it.id) }
        return profiles.any { profile -> loadProvider(context, profile.id).apiKey.isNotBlank() }
    }

    private fun AiImportSettings.isReadyForUse(): Boolean =
        profile.id != AiProviderPresets.none.id &&
            apiKey.isNotBlank() &&
            profile.baseUrl.isNotBlank() &&
            profile.defaultModel.isNotBlank()

    /**
     * Resolves the configuration an AI entry point can actually use.
     *
     * The settings page keeps keys scoped to each provider, so the selected provider can be
     * "关闭" (or an incomplete draft) while a valid bound provider still exists. Entry points
     * must not interpret that state as "no key". If no user provider is usable, the remotely
     * managed daily-free provider is a valid fallback whenever its signed configuration is ready.
     */
    fun resolveAvailableSettings(context: Context): AiImportSettings? {
        load(context).takeIf { it.isReadyForUse() }?.let { return it }

        val userSettings = selectableProfiles(context)
            .asSequence()
            .filterNot { it.id == AiProviderPresets.none.id || AiProviderPresets.isManagedFreeId(it.id) }
            .map { loadProvider(context, it.id) }
            .firstOrNull { it.isReadyForUse() }
        if (userSettings != null) return userSettings

        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        // A user who explicitly declined the managed offer must not be silently switched back
        // after a later remote-config refresh. Otherwise an updated bootstrap would overwrite the
        // user's provider choice even though the encrypted credential is correctly cached.
        if (prefs.getString(KeyManagedFreeOfferDecision, null) == ManagedFreeOfferDeclined) return null
        return managedFreeSettings(context, prefs).takeIf { it.isReadyForUse() }
    }

    /** Makes the resolved fallback active so the service and runtime picker read the same model. */
    fun activateAvailableSettings(context: Context): AiImportSettings? {
        val current = load(context)
        if (current.isReadyForUse()) return current
        return resolveAvailableSettings(context)?.also { resolved ->
            if (resolved.profile.id != current.profile.id) save(context, resolved)
        }
    }

    /**
     * Reads the configuration that a network request can actually use. UI settings may display an
     * incomplete selected draft, but request services must fall back to a complete user profile or
     * the signed daily-free configuration instead of sending an empty key.
     */
    fun loadForRuntime(context: Context): AiImportSettings? = resolveAvailableSettings(context)

    fun shouldOfferManagedFreeAi(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        if (prefs.contains(KeyManagedFreeOfferDecision)) return false
        if (load(context).profile.id == AiProviderPresets.dailyFree.id) return false
        return !hasUserConfiguredApiKey(context) && SleepDownRemoteConfig.isManagedFreeAvailable(context)
    }

    fun enableManagedFreeAi(context: Context) {
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        prefs.edit { putString(KeyManagedFreeOfferDecision, ManagedFreeOfferEnabled) }
        save(context, managedFreeSettings(context, prefs))
    }

    fun declineManagedFreeAi(context: Context) {
        context.getSharedPreferences(PrefName, Context.MODE_PRIVATE).edit {
            putString(KeyManagedFreeOfferDecision, ManagedFreeOfferDeclined)
        }
    }

    fun selectableProfiles(context: Context): List<AiProviderProfile> {
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        val entries = readCustomProviders(prefs)
        val namesById = entries.associate { it.id to it.displayName }
        val builtIns = AiProviderPresets.selectable.map { preset ->
            if (AiProviderPresets.isCustomId(preset.id)) {
                preset.copy(displayName = namesById[preset.id] ?: preset.displayName)
            } else {
                preset
            }
        }
        val additionalCustomProfiles = entries
            .filter { entry -> entry.id != AiProviderPresets.custom.id }
            .map { entry -> AiProviderPresets.customProfile(entry.id, entry.displayName) }
        return builtIns + additionalCustomProfiles
    }

    /**
     * Reads provider configuration for backup without touching encrypted API-key values. The
     * selected provider uses the legacy global keys; other providers use their scoped keys.
     */
    fun exportForBackup(context: Context): BackupAiImportPreferences {
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        val selectedProviderId = prefs.getString(KeyProviderId, AiProviderPresets.none.id).orEmpty()
        val presets = (selectableProfiles(context) + AiProviderPresets.byId(selectedProviderId))
            .distinctBy(AiProviderProfile::id)
        val profiles = presets.map { preset ->
            readProfileWithoutSecret(prefs, preset, preset.id == selectedProviderId)
        }
        return BackupAiImportPreferences(
            selectedProviderId = selectedProviderId.ifBlank { AiProviderPresets.none.id },
            managedFreeOfferDecision = prefs.getString(KeyManagedFreeOfferDecision, null),
            providers = profiles.map { it.toBackupProvider() }
        )
    }

    /** Applies only the non-secret provider fields; existing encrypted API keys are untouched. */
    fun applyBackupPreferences(context: Context, backup: BackupAiImportPreferences) {
        val providerProfiles = backup.providers
            .map { provider -> provider.fromBackupProvider(context) }
            .distinctBy(AiProviderProfile::id)
        val profiles = if (providerProfiles.isEmpty() && backup.selectedProviderId == AiProviderPresets.none.id) {
            listOf(AiProviderPresets.none)
        } else {
            providerProfiles
        }
        val selected = profiles.firstOrNull { it.id == backup.selectedProviderId }
            ?: throw IllegalArgumentException("AI selectedProviderId 不在备份 provider 列表中")
        val customEntries = profiles
            .filter { AiProviderPresets.isCustomId(it.id) }
            .map { AiCustomProviderEntry(it.id, it.displayName.trim().ifBlank { AiProviderPresets.custom.displayName }) }
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        val committed = prefs.edit().apply {
            profiles.forEach { profile ->
                writeProviderSettings(this, AiImportSettings(profile, ""))
            }
            if (customEntries.isEmpty()) remove(KeyCustomProviders)
            else putString(KeyCustomProviders, settingsJson.encodeToString(customEntries))
            putString(KeyProviderId, selected.id)
            writeGlobalSettings(this, selected)
            if (backup.managedFreeOfferDecision == null) {
                remove(KeyManagedFreeOfferDecision)
            } else {
                putString(KeyManagedFreeOfferDecision, backup.managedFreeOfferDecision)
            }
        }.commit()
        check(committed) { "无法提交 AI import preferences" }
        notifyChanged()
    }

    fun createCustomProvider(): AiProviderProfile {
        val id = "${AiProviderPresets.custom.id}:${UUID.randomUUID()}"
        // This remains an in-memory draft until the user enters actual content.
        // Merely opening "add custom provider" must not grow the saved list.
        return AiProviderPresets.customProfile(id, "")
    }

    fun deleteCustomProvider(context: Context, providerId: String): Boolean {
        if (!AiProviderPresets.isCustomId(providerId)) return false
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        val wasActive = prefs.getString(KeyProviderId, AiProviderPresets.none.id) == providerId
        val remainingEntries = readCustomProviders(prefs).filterNot { it.id == providerId }
        prefs.edit {
            putString(KeyCustomProviders, settingsJson.encodeToString(remainingEntries))
            remove(apiKeyKey(providerId))
            listOf(
                KeyBaseUrl,
                KeyModel,
                KeyProviderType,
                KeyImage,
                KeyPdf,
                KeyJsonSchema,
                KeyJsonMode,
                KeyFileUpload,
                KeyResponses,
                KeyEndpointStyle,
                KeyStructuredOutputMode,
                KeyInputMode,
                KeyVision,
                KeyPdfDirect,
                KeyAvailableModels,
                KeyReasoningEffort
            ).forEach { key -> remove(providerKey(key, providerId)) }
        }
        if (wasActive) {
            save(context, AiImportSettings(AiProviderPresets.none, ""))
        }
        return true
    }

    private fun presetFor(context: Context, providerId: String): AiProviderProfile =
        selectableProfiles(context).firstOrNull { it.id == providerId }
            ?: AiProviderPresets.byId(providerId)

    private fun readProfileWithoutSecret(
        prefs: android.content.SharedPreferences,
        preset: AiProviderProfile,
        useGlobalKeys: Boolean
    ): AiProviderProfile {
        if (AiProviderPresets.isManagedFreeId(preset.id)) {
            return preset.copy(
                reasoningEffort = runCatching {
                    AiReasoningEffort.valueOf(
                        prefs.getString(
                            providerKey(KeyReasoningEffort, preset.id),
                            preset.reasoningEffort.name
                        ).orEmpty()
                    )
                }.getOrDefault(preset.reasoningEffort)
            )
        }
        if (!useGlobalKeys && !prefs.contains(providerKey(KeyBaseUrl, preset.id))) return preset
        fun key(name: String): String = if (useGlobalKeys) name else providerKey(name, preset.id)
        val providerType = runCatching {
            AiProviderType.valueOf(prefs.getString(key(KeyProviderType), preset.providerType.name).orEmpty())
        }.getOrDefault(preset.providerType)
        val endpointStyle = runCatching {
            AiEndpointStyle.valueOf(prefs.getString(key(KeyEndpointStyle), preset.endpointStyle.name).orEmpty())
        }.getOrDefault(preset.endpointStyle)
        val structuredOutputMode = runCatching {
            StructuredOutputMode.valueOf(
                prefs.getString(key(KeyStructuredOutputMode), preset.structuredOutputMode.name).orEmpty()
            )
        }.getOrDefault(preset.structuredOutputMode)
        val inputMode = runCatching {
            AiInputMode.valueOf(prefs.getString(key(KeyInputMode), preset.inputMode.name).orEmpty())
        }.getOrDefault(preset.inputMode)
        val capabilities = preset.capabilities.copy(
            supportsImageInput = prefs.getBoolean(key(KeyImage), preset.capabilities.supportsImageInput),
            supportsPdfFileInput = prefs.getBoolean(key(KeyPdf), preset.capabilities.supportsPdfFileInput),
            supportsJsonSchema = prefs.getBoolean(key(KeyJsonSchema), preset.capabilities.supportsJsonSchema),
            supportsJsonMode = prefs.getBoolean(key(KeyJsonMode), preset.capabilities.supportsJsonMode),
            supportsFileUpload = prefs.getBoolean(key(KeyFileUpload), preset.capabilities.supportsFileUpload),
            supportsResponses = prefs.getBoolean(key(KeyResponses), preset.capabilities.supportsResponses)
        )
        val defaultModel = prefs.getString(key(KeyModel), preset.defaultModel).orEmpty()
        return preset.copy(
            providerType = providerType,
            baseUrl = normalizeAiBaseUrlForProvider(
                preset.id,
                prefs.getString(key(KeyBaseUrl), preset.baseUrl).orEmpty()
            ),
            defaultModel = defaultModel,
            capabilities = capabilities,
            endpointStyle = endpointStyle,
            structuredOutputMode = structuredOutputMode,
            inputMode = inputMode,
            supportsVision = prefs.getBoolean(
                key(KeyVision),
                preset.supportsVision || capabilities.supportsImageInput
            ),
            supportsFileUpload = prefs.getBoolean(
                key(KeyFileUpload),
                preset.supportsFileUpload || capabilities.supportsFileUpload
            ),
            supportsPdfDirect = prefs.getBoolean(
                key(KeyPdfDirect),
                preset.supportsPdfDirect || capabilities.supportsPdfFileInput
            ),
            availableModels = readModelIds(
                prefs.getString(key(KeyAvailableModels), null),
                preset.availableModels,
                defaultModel
            ),
            reasoningEffort = runCatching {
                AiReasoningEffort.valueOf(
                    prefs.getString(key(KeyReasoningEffort), preset.reasoningEffort.name).orEmpty()
                )
            }.getOrDefault(preset.reasoningEffort)
        )
    }

    private fun AiProviderProfile.toBackupProvider(): BackupAiProvider = BackupAiProvider(
        id = id,
        displayName = displayName,
        providerType = providerType.name,
        baseUrl = baseUrl,
        model = defaultModel,
        authType = authType.name,
        supportsImageInput = capabilities.supportsImageInput,
        supportsPdfFileInput = capabilities.supportsPdfFileInput,
        supportsJsonSchema = capabilities.supportsJsonSchema,
        supportsJsonMode = capabilities.supportsJsonMode,
        supportsFileUpload = capabilities.supportsFileUpload,
        supportsResponses = capabilities.supportsResponses,
        supportsVision = supportsVision,
        supportsPdfDirect = supportsPdfDirect,
        endpointStyle = endpointStyle.name,
        structuredOutputMode = structuredOutputMode.name,
        inputMode = inputMode.name,
        availableModels = availableModels,
        reasoningEffort = reasoningEffort.name
    )

    private fun BackupAiProvider.fromBackupProvider(context: Context): AiProviderProfile {
        require(id.matches(Regex("[A-Za-z0-9:_-]{1,128}"))) { "AI provider ID 非法" }
        val preset = selectableProfiles(context).firstOrNull { it.id == id }
            ?: AiProviderPresets.customProfile(id, displayName)
        val providerType = runCatching { AiProviderType.valueOf(this.providerType) }
            .getOrElse { throw IllegalArgumentException("未知 AI providerType: ${this.providerType}") }
        val authType = runCatching { AiAuthType.valueOf(this.authType) }
            .getOrElse { throw IllegalArgumentException("未知 AI authType: ${this.authType}") }
        val endpointStyle = runCatching { AiEndpointStyle.valueOf(this.endpointStyle) }
            .getOrElse { throw IllegalArgumentException("未知 AI endpointStyle: ${this.endpointStyle}") }
        val structuredOutputMode = runCatching { StructuredOutputMode.valueOf(this.structuredOutputMode) }
            .getOrElse { throw IllegalArgumentException("未知 AI structuredOutputMode: ${this.structuredOutputMode}") }
        val inputMode = runCatching { AiInputMode.valueOf(this.inputMode) }
            .getOrElse { throw IllegalArgumentException("未知 AI inputMode: ${this.inputMode}") }
        val reasoningEffort = runCatching { AiReasoningEffort.valueOf(this.reasoningEffort) }
            .getOrElse { throw IllegalArgumentException("未知 AI reasoningEffort: ${this.reasoningEffort}") }
        val capabilities = preset.capabilities.copy(
            supportsImageInput = supportsImageInput,
            supportsPdfFileInput = supportsPdfFileInput,
            supportsJsonSchema = supportsJsonSchema,
            supportsJsonMode = supportsJsonMode,
            supportsFileUpload = supportsFileUpload,
            supportsResponses = supportsResponses
        )
        return preset.copy(
            id = id,
            displayName = displayName,
            providerType = providerType,
            baseUrl = normalizeAiBaseUrlForProvider(id, baseUrl),
            defaultModel = model,
            authType = authType,
            capabilities = capabilities,
            endpointStyle = endpointStyle,
            structuredOutputMode = structuredOutputMode,
            inputMode = inputMode,
            supportsVision = supportsVision,
            supportsFileUpload = supportsFileUpload,
            supportsPdfDirect = supportsPdfDirect,
            availableModels = (availableModels + model).filter(String::isNotBlank).distinct(),
            reasoningEffort = reasoningEffort
        )
    }

    private fun readCustomProviders(prefs: android.content.SharedPreferences): List<AiCustomProviderEntry> {
        val encoded = prefs.getString(KeyCustomProviders, null) ?: return emptyList()
        return runCatching {
            settingsJson.decodeFromString<List<AiCustomProviderEntry>>(encoded)
                .filter { AiProviderPresets.isCustomId(it.id) }
                .distinctBy { it.id }
        }.getOrDefault(emptyList())
    }

    private fun writeCustomProviderEntry(
        prefs: android.content.SharedPreferences,
        editor: android.content.SharedPreferences.Editor,
        profile: AiProviderProfile
    ) {
        if (!AiProviderPresets.isCustomId(profile.id)) return
        val displayName = profile.displayName.trim().ifBlank { AiProviderPresets.custom.displayName }
        val entries = readCustomProviders(prefs).toMutableList()
        val index = entries.indexOfFirst { it.id == profile.id }
        val entry = AiCustomProviderEntry(profile.id, displayName)
        if (index >= 0) entries[index] = entry else entries += entry
        editor.putString(KeyCustomProviders, settingsJson.encodeToString(entries))
    }

    private fun readModelIds(encoded: String?, fallback: List<String>, defaultModel: String): List<String> {
        val decoded = encoded?.let { value ->
            runCatching { settingsJson.decodeFromString<List<String>>(value) }.getOrNull()
        }.orEmpty()
        return (decoded.ifEmpty { fallback } + defaultModel)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy(String::lowercase)
    }

    fun load(context: Context): AiImportSettings {
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        val savedProviderId = prefs.getString(KeyProviderId, AiProviderPresets.none.id).orEmpty()
        val preset = selectableProfiles(context).firstOrNull { it.id == savedProviderId }
            ?: AiProviderPresets.none
        if (AiProviderPresets.isManagedFreeId(preset.id)) return managedFreeSettings(context, prefs)
        val providerType = runCatching {
            AiProviderType.valueOf(prefs.getString(KeyProviderType, preset.providerType.name).orEmpty())
        }.getOrDefault(preset.providerType)
        val endpointStyle = runCatching {
            AiEndpointStyle.valueOf(prefs.getString(KeyEndpointStyle, preset.endpointStyle.name).orEmpty())
        }.getOrDefault(preset.endpointStyle)
        val structuredOutputMode = runCatching {
            StructuredOutputMode.valueOf(prefs.getString(KeyStructuredOutputMode, preset.structuredOutputMode.name).orEmpty())
        }.getOrDefault(preset.structuredOutputMode)
        val inputMode = runCatching {
            AiInputMode.valueOf(prefs.getString(KeyInputMode, preset.inputMode.name).orEmpty())
        }.getOrDefault(preset.inputMode)
        val capabilities = preset.capabilities.copy(
            supportsImageInput = prefs.getBoolean(KeyImage, preset.capabilities.supportsImageInput),
            supportsPdfFileInput = prefs.getBoolean(KeyPdf, preset.capabilities.supportsPdfFileInput),
            supportsJsonSchema = prefs.getBoolean(KeyJsonSchema, preset.capabilities.supportsJsonSchema),
            supportsJsonMode = prefs.getBoolean(KeyJsonMode, preset.capabilities.supportsJsonMode),
            supportsFileUpload = prefs.getBoolean(KeyFileUpload, preset.capabilities.supportsFileUpload),
            supportsResponses = prefs.getBoolean(KeyResponses, preset.capabilities.supportsResponses)
        )
        val defaultModel = prefs.getString(KeyModel, preset.defaultModel).orEmpty()
        val profile = preset.copy(
            providerType = providerType,
            baseUrl = normalizeAiBaseUrlForProvider(preset.id, prefs.getString(KeyBaseUrl, preset.baseUrl).orEmpty()),
            defaultModel = defaultModel,
            capabilities = capabilities,
            endpointStyle = endpointStyle,
            structuredOutputMode = structuredOutputMode,
            inputMode = inputMode,
            supportsVision = prefs.getBoolean(KeyVision, preset.supportsVision || capabilities.supportsImageInput),
            supportsFileUpload = prefs.getBoolean(KeyFileUpload, preset.supportsFileUpload || capabilities.supportsFileUpload),
            supportsPdfDirect = prefs.getBoolean(KeyPdfDirect, preset.supportsPdfDirect || capabilities.supportsPdfFileInput),
            availableModels = readModelIds(
                prefs.getString(KeyAvailableModels, null),
                preset.availableModels,
                defaultModel
            ),
            reasoningEffort = runCatching {
                AiReasoningEffort.valueOf(
                    prefs.getString(KeyReasoningEffort, preset.reasoningEffort.name).orEmpty()
                )
            }.getOrDefault(preset.reasoningEffort)
        )
        val scopedEncryptedApiKey = prefs.getString(apiKeyKey(profile.id), null)
        val legacyEncryptedApiKey = prefs.getString(KeyEncryptedApiKey, null)
        val apiKey = scopedEncryptedApiKey?.let { decrypt(context, it) }
            ?: legacyEncryptedApiKey?.let { decrypt(context, it) }.orEmpty()
        if (scopedEncryptedApiKey == null && legacyEncryptedApiKey != null) {
            prefs.edit {
                putString(apiKeyKey(profile.id), legacyEncryptedApiKey)
                remove(KeyEncryptedApiKey)
            }
        }
        return AiImportSettings(profile, apiKey)
    }

    fun loadProvider(context: Context, providerId: String): AiImportSettings {
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        val current = load(context)
        val preset = presetFor(context, providerId)
        if (AiProviderPresets.isManagedFreeId(preset.id)) return managedFreeSettings(context, prefs)
        val profile = when {
            current.profile.id == preset.id -> current.profile
            !prefs.contains(providerKey(KeyBaseUrl, preset.id)) -> preset
            else -> {
                val providerType = runCatching {
                    AiProviderType.valueOf(
                        prefs.getString(
                            providerKey(KeyProviderType, preset.id),
                            preset.providerType.name
                        ).orEmpty()
                    )
                }.getOrDefault(preset.providerType)
                val endpointStyle = runCatching {
                    AiEndpointStyle.valueOf(
                        prefs.getString(
                            providerKey(KeyEndpointStyle, preset.id),
                            preset.endpointStyle.name
                        ).orEmpty()
                    )
                }.getOrDefault(preset.endpointStyle)
                val structuredOutputMode = runCatching {
                    StructuredOutputMode.valueOf(
                        prefs.getString(
                            providerKey(KeyStructuredOutputMode, preset.id),
                            preset.structuredOutputMode.name
                        ).orEmpty()
                    )
                }.getOrDefault(preset.structuredOutputMode)
                val inputMode = runCatching {
                    AiInputMode.valueOf(
                        prefs.getString(
                            providerKey(KeyInputMode, preset.id),
                            preset.inputMode.name
                        ).orEmpty()
                    )
                }.getOrDefault(preset.inputMode)
                val capabilities = preset.capabilities.copy(
                    supportsImageInput = prefs.getBoolean(
                        providerKey(KeyImage, preset.id),
                        preset.capabilities.supportsImageInput
                    ),
                    supportsPdfFileInput = prefs.getBoolean(
                        providerKey(KeyPdf, preset.id),
                        preset.capabilities.supportsPdfFileInput
                    ),
                    supportsJsonSchema = prefs.getBoolean(
                        providerKey(KeyJsonSchema, preset.id),
                        preset.capabilities.supportsJsonSchema
                    ),
                    supportsJsonMode = prefs.getBoolean(
                        providerKey(KeyJsonMode, preset.id),
                        preset.capabilities.supportsJsonMode
                    ),
                    supportsFileUpload = prefs.getBoolean(
                        providerKey(KeyFileUpload, preset.id),
                        preset.capabilities.supportsFileUpload
                    ),
                    supportsResponses = prefs.getBoolean(
                        providerKey(KeyResponses, preset.id),
                        preset.capabilities.supportsResponses
                    )
                )
                val defaultModel = prefs.getString(
                    providerKey(KeyModel, preset.id),
                    preset.defaultModel
                ).orEmpty()
                preset.copy(
                    providerType = providerType,
                    baseUrl = normalizeAiBaseUrlForProvider(
                        preset.id,
                        prefs.getString(providerKey(KeyBaseUrl, preset.id), preset.baseUrl).orEmpty()
                    ),
                    defaultModel = defaultModel,
                    capabilities = capabilities,
                    endpointStyle = endpointStyle,
                    structuredOutputMode = structuredOutputMode,
                    inputMode = inputMode,
                    supportsVision = prefs.getBoolean(
                        providerKey(KeyVision, preset.id),
                        preset.supportsVision || capabilities.supportsImageInput
                    ),
                    supportsFileUpload = prefs.getBoolean(
                        providerKey(KeyFileUpload, preset.id),
                        preset.supportsFileUpload || capabilities.supportsFileUpload
                    ),
                    supportsPdfDirect = prefs.getBoolean(
                        providerKey(KeyPdfDirect, preset.id),
                        preset.supportsPdfDirect || capabilities.supportsPdfFileInput
                    ),
                    availableModels = readModelIds(
                        prefs.getString(providerKey(KeyAvailableModels, preset.id), null),
                        preset.availableModels,
                        defaultModel
                    ),
                    reasoningEffort = runCatching {
                        AiReasoningEffort.valueOf(
                            prefs.getString(
                                providerKey(KeyReasoningEffort, preset.id),
                                preset.reasoningEffort.name
                            ).orEmpty()
                        )
                    }.getOrDefault(preset.reasoningEffort)
                )
            }
        }
        val apiKey = prefs.getString(apiKeyKey(profile.id), null)?.let { decrypt(context, it) }.orEmpty()
        return AiImportSettings(profile, apiKey)
    }

    fun save(context: Context, settings: AiImportSettings) {
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        if (AiProviderPresets.isManagedFreeId(settings.profile.id)) {
            prefs.edit {
                putString(KeyProviderId, AiProviderPresets.dailyFree.id)
                putString(
                    providerKey(KeyReasoningEffort, AiProviderPresets.dailyFree.id),
                    settings.profile.reasoningEffort.name
                )
                putString(KeyManagedFreeOfferDecision, ManagedFreeOfferEnabled)
                remove(apiKeyKey(AiProviderPresets.dailyFree.id))
            }
            notifyChanged()
            return
        }
        prefs.edit {
            putString(KeyProviderId, settings.profile.id)
            putString(KeyBaseUrl, normalizeAiBaseUrlForProvider(settings.profile.id, settings.profile.baseUrl))
            putString(KeyModel, settings.profile.defaultModel)
            putString(KeyProviderType, settings.profile.providerType.name)
            putString(KeyEndpointStyle, settings.profile.endpointStyle.name)
            putString(KeyStructuredOutputMode, settings.profile.structuredOutputMode.name)
            putString(KeyInputMode, settings.profile.inputMode.name)
            putBoolean(KeyImage, settings.profile.capabilities.supportsImageInput)
            putBoolean(KeyPdf, settings.profile.capabilities.supportsPdfFileInput)
            putBoolean(KeyJsonSchema, settings.profile.capabilities.supportsJsonSchema)
            putBoolean(KeyJsonMode, settings.profile.capabilities.supportsJsonMode)
            putBoolean(KeyFileUpload, settings.profile.capabilities.supportsFileUpload)
            putBoolean(KeyResponses, settings.profile.capabilities.supportsResponses)
            putBoolean(KeyVision, settings.profile.supportsVision)
            putBoolean(KeyPdfDirect, settings.profile.supportsPdfDirect)
            putString(KeyAvailableModels, settingsJson.encodeToString(settings.profile.availableModels))
            putString(KeyReasoningEffort, settings.profile.reasoningEffort.name)
            writeCustomProviderEntry(prefs, this, settings.profile)
            writeProviderSettings(this, settings)
            if (settings.apiKey.isBlank()) {
                remove(apiKeyKey(settings.profile.id))
            } else {
                putString(apiKeyKey(settings.profile.id), encrypt(context, settings.apiKey))
            }
        }
        notifyChanged()
    }

    fun saveProvider(context: Context, settings: AiImportSettings) {
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        if (AiProviderPresets.isManagedFreeId(settings.profile.id)) {
            prefs.edit {
                putString(
                    providerKey(KeyReasoningEffort, AiProviderPresets.dailyFree.id),
                    settings.profile.reasoningEffort.name
                )
                remove(apiKeyKey(AiProviderPresets.dailyFree.id))
            }
            return
        }
        prefs.edit {
            writeCustomProviderEntry(prefs, this, settings.profile)
            writeProviderSettings(this, settings)
            if (settings.apiKey.isNotBlank()) {
                putString(apiKeyKey(settings.profile.id), encrypt(context, settings.apiKey))
            }
        }
    }

    private fun writeProviderSettings(
        editor: android.content.SharedPreferences.Editor,
        settings: AiImportSettings
    ) {
        val profile = settings.profile
        val id = profile.id
        editor
            .putString(providerKey(KeyBaseUrl, id), normalizeAiBaseUrlForProvider(id, profile.baseUrl))
            .putString(providerKey(KeyModel, id), profile.defaultModel)
            .putString(providerKey(KeyProviderType, id), profile.providerType.name)
            .putString(providerKey(KeyEndpointStyle, id), profile.endpointStyle.name)
            .putString(providerKey(KeyStructuredOutputMode, id), profile.structuredOutputMode.name)
            .putString(providerKey(KeyInputMode, id), profile.inputMode.name)
            .putBoolean(providerKey(KeyImage, id), profile.capabilities.supportsImageInput)
            .putBoolean(providerKey(KeyPdf, id), profile.capabilities.supportsPdfFileInput)
            .putBoolean(providerKey(KeyJsonSchema, id), profile.capabilities.supportsJsonSchema)
            .putBoolean(providerKey(KeyJsonMode, id), profile.capabilities.supportsJsonMode)
            .putBoolean(providerKey(KeyFileUpload, id), profile.capabilities.supportsFileUpload)
            .putBoolean(providerKey(KeyResponses, id), profile.capabilities.supportsResponses)
            .putBoolean(providerKey(KeyVision, id), profile.supportsVision)
            .putBoolean(providerKey(KeyPdfDirect, id), profile.supportsPdfDirect)
            .putString(providerKey(KeyAvailableModels, id), settingsJson.encodeToString(profile.availableModels))
             .putString(providerKey(KeyReasoningEffort, id), profile.reasoningEffort.name)
    }

    private fun writeGlobalSettings(
        editor: android.content.SharedPreferences.Editor,
        profile: AiProviderProfile
    ) {
        editor
            .putString(KeyBaseUrl, normalizeAiBaseUrlForProvider(profile.id, profile.baseUrl))
            .putString(KeyModel, profile.defaultModel)
            .putString(KeyProviderType, profile.providerType.name)
            .putString(KeyEndpointStyle, profile.endpointStyle.name)
            .putString(KeyStructuredOutputMode, profile.structuredOutputMode.name)
            .putString(KeyInputMode, profile.inputMode.name)
            .putBoolean(KeyImage, profile.capabilities.supportsImageInput)
            .putBoolean(KeyPdf, profile.capabilities.supportsPdfFileInput)
            .putBoolean(KeyJsonSchema, profile.capabilities.supportsJsonSchema)
            .putBoolean(KeyJsonMode, profile.capabilities.supportsJsonMode)
            .putBoolean(KeyFileUpload, profile.capabilities.supportsFileUpload)
            .putBoolean(KeyResponses, profile.capabilities.supportsResponses)
            .putBoolean(KeyVision, profile.supportsVision)
            .putBoolean(KeyPdfDirect, profile.supportsPdfDirect)
            .putString(KeyAvailableModels, settingsJson.encodeToString(profile.availableModels))
            .putString(KeyReasoningEffort, profile.reasoningEffort.name)
    }

    fun clearApiKey(context: Context, providerId: String? = null) {
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        val currentProviderId = providerId ?: prefs.getString(KeyProviderId, AiProviderPresets.none.id).orEmpty()
        if (AiProviderPresets.isManagedFreeId(currentProviderId)) return
        context.getSharedPreferences(PrefName, Context.MODE_PRIVATE).edit {
            remove(apiKeyKey(currentProviderId))
        }
        notifyChanged()
    }

    private fun encrypt(context: Context, value: String): String {
        if (value.isBlank()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(context: Context, value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val payload = Base64.decode(value, Base64.NO_WRAP)
            val iv = payload.copyOfRange(0, 12)
            val encrypted = payload.copyOfRange(12, payload.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey("sleepdown_ai_import_key", null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                "sleepdown_ai_import_key",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}

internal fun customProviderDraftHasContent(
    name: String,
    baseUrl: String,
    model: String,
    apiKey: String
): Boolean = listOf(name, baseUrl, model, apiKey).any { it.isNotBlank() }

fun normalizeAiBaseUrlForProvider(providerId: String, value: String): String {
    if (value.isBlank()) return ""
    var url = value.trim().trimEnd('/')
    listOf("/chat/completions", "/responses", "/files").forEach { suffix ->
        if (url.endsWith(suffix, ignoreCase = true)) {
            url = url.dropLast(suffix.length).trimEnd('/')
        }
    }
    return when (providerId) {
        AiProviderPresets.openAI.id ->
            if (url.equals("https://api.openai.com", ignoreCase = true)) "$url/v1" else url
        AiProviderPresets.deepSeek.id ->
            if (url.equals("https://api.deepseek.com/v1", ignoreCase = true)) {
                "https://api.deepseek.com"
            } else {
                url
            }
        AiProviderPresets.kimi.id ->
            if (url.equals("https://api.moonshot.cn", ignoreCase = true)) "$url/v1" else url
        AiProviderPresets.dashScope.id ->
            if (url.equals("https://dashscope.aliyuncs.com", ignoreCase = true)) "$url/compatible-mode/v1" else url
        AiProviderPresets.zhipu.id ->
            if (url.equals("https://open.bigmodel.cn", ignoreCase = true)) "$url/api/paas/v4" else url
        AiProviderPresets.qianfan.id ->
            if (url.equals("https://qianfan.baidubce.com", ignoreCase = true)) "$url/v2" else url
        AiProviderPresets.doubao.id ->
            if (url.equals("https://ark.cn-beijing.volces.com", ignoreCase = true)) "$url/api/v3" else url
        AiProviderPresets.siliconFlow.id ->
            if (url.equals("https://api.siliconflow.cn", ignoreCase = true)) "$url/v1" else url
        AiProviderPresets.miniMax.id ->
            if (url.equals("https://api.minimax.chat", ignoreCase = true)) "$url/v1" else url
        AiProviderPresets.mimo.id ->
            if (url.equals("https://api.xiaomimimo.com", ignoreCase = true)) "$url/v1" else url
        AiProviderPresets.mimoTokenPlan.id ->
            if (url.equals("https://token-plan-cn.xiaomimimo.com", ignoreCase = true)) "$url/v1" else url
        else -> url
    }
}

class AiScheduleImportService(private val context: Context) {
    suspend fun parseScheduleFile(
        file: AiImportFile,
        settings: AiImportSettings,
        onRequestStarted: () -> Unit
    ): Result<AiScheduleImportResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                require(settings.apiKey.isNotBlank()) { "请先在设置中配置 AI API Key" }
                require(settings.profile.baseUrl.isNotBlank()) { "请先配置接口地址" }
                require(settings.profile.defaultModel.isNotBlank()) { "请先配置模型名称" }
                require(file.bytes.size <= MaxAiImportFileBytes) { "文件不能超过 20MB" }
                val config = settings.toProviderConfig().normalizedForRequest()
                val preprocess = DefaultScheduleFilePreprocessor(context).preprocess(file, config)
                val input = preprocess.toScheduleInput(file)
                onRequestStarted()
                val result = when {
                    config.endpointStyle == AiEndpointStyle.RESPONSES -> OpenAiResponsesProvider().parseSchedule(config, input)
                    else -> OpenAiCompatibleChatProvider().parseSchedule(config, input)
                }
                AiScheduleImportResult(
                    output = result.content,
                    routeMessage = preprocess.routeMessage,
                    rawOutput = result.content,
                    reasoningOutput = result.reasoning
                )
            }
        }
    }

    suspend fun parseScheduleText(
        text: String,
        sourceName: String,
        settings: AiImportSettings,
        onRequestStarted: () -> Unit
    ): Result<AiScheduleImportResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                require(settings.apiKey.isNotBlank()) { "请先在设置中配置 AI API Key" }
                require(settings.profile.baseUrl.isNotBlank()) { "请先配置接口地址" }
                require(settings.profile.defaultModel.isNotBlank()) { "请先配置模型名称" }
                val cleaned = text.trim().take(60_000)
                require(cleaned.count { !it.isWhitespace() } >= 40) { "当前页面可提取文本太少，请确认已经进入课表页面" }
                val config = settings.toProviderConfig().normalizedForRequest()
                val input = AiScheduleInput.ExtractedText(cleaned, sourceName)
                onRequestStarted()
                val result = when {
                    config.endpointStyle == AiEndpointStyle.RESPONSES -> OpenAiResponsesProvider().parseSchedule(config, input)
                    else -> OpenAiCompatibleChatProvider().parseSchedule(config, input)
                }
                AiScheduleImportResult(
                    output = result.content,
                    routeMessage = "已提取当前教务页面文本，使用 AI 解析。",
                    rawOutput = result.content,
                    reasoningOutput = result.reasoning
                )
            }
        }
    }

    suspend fun parseScheduleCapturedPage(
        text: String,
        screenshots: List<RenderedPageImage>,
        sourceName: String,
        warnings: List<String>,
        settings: AiImportSettings,
        onRequestStarted: () -> Unit
    ): Result<AiScheduleImportResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                require(settings.apiKey.isNotBlank()) { "请先在设置中配置 AI API Key" }
                require(settings.profile.baseUrl.isNotBlank()) { "请先配置接口地址" }
                require(settings.profile.defaultModel.isNotBlank()) { "请先配置模型名称" }
                val cleaned = text.trim().take(60_000)
                val config = settings.toProviderConfig().normalizedForRequest()
                if (screenshots.isNotEmpty()) {
                    require(config.supportsVision) {
                        "当前模型无法识别截图，请换视觉模型，或手动进入可复制文本课表页后重试。"
                    }
                } else {
                    require(cleaned.count { !it.isWhitespace() } >= 40) { "当前页面可提取文本太少，请确认已经进入课表页面" }
                }
                val input = if (screenshots.isEmpty()) {
                    AiScheduleInput.ExtractedText(cleaned, sourceName)
                } else {
                    AiScheduleInput.CapturedPage(cleaned, screenshots, sourceName, warnings)
                }
                onRequestStarted()
                val result = when {
                    config.endpointStyle == AiEndpointStyle.RESPONSES -> OpenAiResponsesProvider().parseSchedule(config, input)
                    else -> OpenAiCompatibleChatProvider().parseSchedule(config, input)
                }
                val routeMessage = if (screenshots.isEmpty()) {
                    "已提取当前教务页面文本，使用 AI 解析。"
                } else {
                    "已抓取页面文本并生成 ${screenshots.size} 张截图，交给视觉模型解析。"
                }
                AiScheduleImportResult(
                    output = result.content,
                    routeMessage = routeMessage,
                    rawOutput = result.content,
                    reasoningOutput = result.reasoning
                )
            }
        }
    }

    suspend fun reviseSchedule(
        draft: ImportDraft,
        instruction: String,
        history: AiEduImportProgress,
        settings: AiImportSettings
    ): Result<AiScheduleImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(settings.apiKey.isNotBlank()) { "请先在设置中配置 AI API Key" }
            require(settings.profile.baseUrl.isNotBlank()) { "请先配置接口地址" }
            require(settings.profile.defaultModel.isNotBlank()) { "请先配置模型名称" }
            val config = settings.toProviderConfig().normalizedForRequest()
            val request = buildAiRevisionInput(draft, instruction, history)
            val result = when {
                config.endpointStyle == AiEndpointStyle.RESPONSES ->
                    OpenAiResponsesProvider().reviseSchedule(config, request, history)
                else -> OpenAiCompatibleChatProvider().reviseSchedule(config, request, history)
            }
            val revisedDraft = applyAiSchedulePatch(draft, result.content)
            AiScheduleImportResult(
                output = draftToPayload(revisedDraft).toString(),
                routeMessage = "已按要求修改课表。",
                rawOutput = result.content,
                reasoningOutput = result.reasoning
            )
        }
    }
}

suspend fun testAiProviderConnection(settings: AiImportSettings): Result<String> {
    return withContext(Dispatchers.IO) {
        runCatching {
            require(settings.apiKey.isNotBlank()) { "请先配置 AI API Key" }
            require(settings.profile.baseUrl.isNotBlank()) { "请先配置接口地址" }
            require(settings.profile.defaultModel.isNotBlank()) { "请先配置模型名称" }
            val config = settings.toProviderConfig().normalizedForRequest()
            val response = if (config.endpointStyle == AiEndpointStyle.RESPONSES) {
                val body = buildJsonObject {
                    put("model", JsonPrimitive(config.model))
                    put("store", JsonPrimitive(false))
                    put("input", JsonPrimitive("请只回复 OK"))
                    putResponsesReasoning(config)
                    put("max_output_tokens", JsonPrimitive(32))
                }
                postJson(config.baseUrl.trimEnd('/') + "/responses", config.apiKey, body.toString(), config.authType, config.providerId)
            } else {
                val body = buildJsonObject {
                    put("model", JsonPrimitive(config.model))
                    put("messages", buildJsonArray {
                        add(buildJsonObject {
                            put("role", JsonPrimitive("user"))
                            put("content", JsonPrimitive("请只回复 OK"))
                        })
                    })
                    putChatSamplingAndReasoning(config)
                    if (config.providerId == AiProviderPresets.mimo.id || config.providerId == AiProviderPresets.mimoTokenPlan.id) {
                        put("max_completion_tokens", JsonPrimitive(32))
                    } else {
                        put("max_tokens", JsonPrimitive(32))
                    }
                }
                postJson(config.baseUrl.trimEnd('/') + "/chat/completions", config.apiKey, body.toString(), config.authType, config.providerId)
            }
            "连接测试成功\n" + response.compactForSettingsResult()
        }
    }
}

suspend fun diagnoseAiProviderNetwork(settings: AiImportSettings): Result<String> {
    return withContext(Dispatchers.IO) {
        runCatching {
            require(settings.profile.baseUrl.isNotBlank()) { "请先配置接口地址" }
            require(settings.profile.defaultModel.isNotBlank()) { "请先配置模型名称" }
            val config = settings.toProviderConfig().normalizedForRequest()
            val endpoint = config.baseUrl.trimEnd('/') + if (config.endpointStyle == AiEndpointStyle.RESPONSES) {
                "/responses"
            } else {
                "/chat/completions"
            }
            val endpointUrl = URL(endpoint)
            val port = if (endpointUrl.port > 0) endpointUrl.port else endpointUrl.defaultPort.takeIf { it > 0 } ?: 443
            val result = StringBuilder()
            result.appendLine("接口：${redactAiUrl(endpoint)}")
            result.appendLine("模型：${config.model}")

            val addresses = runCatching { InetAddress.getAllByName(endpointUrl.host).toList() }
                .getOrElse {
                    result.appendLine("DNS：失败，${it.message.orEmpty()}")
                    return@runCatching result.toString().trim()
                }
            result.appendLine("DNS：${addresses.joinToString { it.hostAddress.orEmpty() }}")

            val tcpAddress = addresses.firstOrNull() ?: error("DNS 没有返回可用地址")
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(tcpAddress, port), 6_000)
                }
            }.onSuccess {
                result.appendLine("TCP $port：已连通 ${tcpAddress.hostAddress}")
            }.onFailure {
                result.appendLine("TCP $port：失败，${it.message.orEmpty()}")
                return@runCatching result.toString().trim()
            }

            runCatching {
                val rootUrl = URL("${endpointUrl.protocol}://${endpointUrl.host}${if (endpointUrl.port > 0) ":${endpointUrl.port}" else ""}")
                val connection = (rootUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6_000
                    readTimeout = 6_000
                    setRequestProperty("Accept", "application/json,text/plain,*/*")
                }
                try {
                    val status = connection.responseCode
                    result.appendLine("HTTPS：握手成功，根路径 HTTP $status")
                } finally {
                    connection.disconnect()
                }
            }.onFailure {
                result.appendLine("HTTPS：失败，${it.message.orEmpty()}")
            }

            if (settings.apiKey.isBlank()) {
                result.appendLine("请求测试：跳过，未配置 API Key")
            } else {
                testAiProviderConnection(settings)
                    .onSuccess { result.appendLine("请求测试：成功") }
                    .onFailure { result.appendLine("请求测试：失败，${it.message.orEmpty()}") }
            }
            result.toString().trim()
        }
    }
}

private fun String.compactForSettingsResult(maxLength: Int = 360): String {
    val compact = replace(Regex("\\s+"), " ").trim()
    return if (compact.length <= maxLength) compact else compact.take(maxLength) + "..."
}

private fun AiImportSettings.toProviderConfig(): AiProviderConfig {
    return AiProviderConfig(
        providerId = profile.id,
        displayName = profile.displayName,
        apiKey = apiKey,
        baseUrl = profile.baseUrl,
        model = profile.defaultModel,
        endpointStyle = profile.endpointStyle,
        structuredOutputMode = profile.structuredOutputMode,
        supportsVision = AiProviderPresets.supportsImageInput(profile),
        supportsFileUpload = profile.supportsFileUpload || profile.capabilities.supportsFileUpload,
        supportsPdfDirect = profile.supportsPdfDirect || profile.capabilities.supportsPdfFileInput,
        supportsResponses = AiProviderPresets.supportsResponses(profile),
        inputMode = profile.inputMode,
        reasoningEffort = profile.reasoningEffort,
        authType = profile.authType
    )
}

internal fun AiProviderConfig.normalizedForRequest(): AiProviderConfig {
    val normalizedBaseUrl = normalizeAiBaseUrlForProvider(providerId, baseUrl)
    val useResponses = endpointStyle == AiEndpointStyle.RESPONSES && supportsResponses
    val isMimo = providerId == AiProviderPresets.mimo.id || providerId == AiProviderPresets.mimoTokenPlan.id
    val outputMode = if (providerId == AiProviderPresets.deepSeek.id || isMimo) {
        StructuredOutputMode.PROMPT_ONLY
    } else {
        structuredOutputMode
    }
    return copy(
        baseUrl = normalizedBaseUrl,
        model = if (isMimo && model.equals("mimo-v2.5-omni", ignoreCase = true)) {
            "mimo-v2.5"
        } else {
            model
        },
        endpointStyle = if (useResponses) AiEndpointStyle.RESPONSES else AiEndpointStyle.CHAT_COMPLETIONS,
        structuredOutputMode = outputMode,
        supportsPdfDirect = useResponses && supportsPdfDirect,
        // Compatible chat endpoints cannot consume a raw PDF, but the import
        // pipeline still uses this capability flag to render the PDF into page
        // images before creating the multi-modal request.
        supportsFileUpload = supportsFileUpload,
        authType = if (isMimo) AiAuthType.CustomHeader else authType
    )
}

internal fun isOfficialOpenAIBaseUrl(value: String): Boolean {
    val url = value.trim().trimEnd('/')
    return url.equals("https://api.openai.com/v1", ignoreCase = true)
}

interface ScheduleFilePreprocessor {
    suspend fun preprocess(file: AiImportFile, config: AiProviderConfig): PreprocessResult
}

private class DefaultScheduleFilePreprocessor(private val context: Context) : ScheduleFilePreprocessor {
    override suspend fun preprocess(file: AiImportFile, config: AiProviderConfig): PreprocessResult {
        return when {
            file.isLocalTextDocument -> {
                val extracted = extractLocalAiDocumentText(file.displayName, file.mimeType, file.bytes)
                    ?: error("无法从 ${file.displayName} 提取文字")
                PreprocessResult.Text(
                    text = extracted.text,
                    routeMessage = "已在本机从${extracted.formatLabel}提取文字，原文件不会上传。"
                )
            }
            file.isPdf -> preprocessPdf(file, config)
            file.isImage -> {
                require(config.inputMode != AiInputMode.TEXT_ONLY) { "当前输入模式为仅本地文本，无法解析图片课表。" }
                require(config.supportsVision) {
                    "当前模型不支持图片输入。请换视觉模型，或上传可提取文字的 PDF、XLSX、CSV、DOCX 等文件。"
                }
                PreprocessResult.Images(
                    images = listOf(file.toRenderedImage(0)),
                    routeMessage = "图片课表将交给视觉模型解析。"
                )
            }
            canSendNativeFile(file, config) -> PreprocessResult.Raw(
                file = file,
                bytes = file.bytes,
                routeMessage = "当前官方接口支持该格式，确认后将直接发送原文件解析。"
            )
            else -> error(
                "暂不支持该文件类型。请使用 PDF、图片、XLSX、CSV、TSV、TXT、Markdown、JSON、XML、HTML、DOCX、PPTX 或 ODS；" +
                    "旧版 XLS/DOC/PPT 请先另存为新版格式或 CSV。"
            )
        }
    }

    private fun preprocessPdf(file: AiImportFile, config: AiProviderConfig): PreprocessResult {
        val extracted = extractPdfTextBestEffort(file.bytes)
        if (isUsefulExtractedScheduleText(extracted)) {
            return PreprocessResult.Text(extracted, "已优先在本机从 PDF 提取有效文字，原 PDF 不会上传。")
        }
        require(config.inputMode != AiInputMode.TEXT_ONLY) {
            "PDF 文本提取结果不足，当前输入模式禁止视觉解析。"
        }
        require(config.supportsVision) {
            "PDF 文本提取结果不足，且当前模型不支持视觉输入，请换视觉模型或上传文本版课表。"
        }
        return PreprocessResult.Images(
            images = renderPdfPageImages(context, file, maxPages = 10),
            routeMessage = "PDF 未提取到足够有效文字，已按页转成图片交给视觉模型解析。"
        )
    }
}

private val NativeResponsesDocumentExtensions = setOf("xls", "doc", "ppt", "rtf", "odt", "odp")

private fun canSendNativeFile(file: AiImportFile, config: AiProviderConfig): Boolean {
    val extension = file.displayName.substringAfterLast('.', "").lowercase()
    val officialOpenAiResponses = config.providerId == AiProviderPresets.openAI.id &&
        isOfficialOpenAIBaseUrl(config.baseUrl) &&
        config.endpointStyle == AiEndpointStyle.RESPONSES
    val explicitlyEnabledCompatibleResponses = config.endpointStyle == AiEndpointStyle.RESPONSES &&
        config.inputMode == AiInputMode.RESPONSES_FILE
    return config.supportsFileUpload &&
        config.inputMode != AiInputMode.TEXT_ONLY &&
        extension in NativeResponsesDocumentExtensions &&
        (officialOpenAiResponses || explicitlyEnabledCompatibleResponses)
}

internal fun extractAiImportTextPreview(file: AiImportFile): LocalAiDocumentText? = when {
    file.isLocalTextDocument -> extractLocalAiDocumentText(file.displayName, file.mimeType, file.bytes)
    file.isPdf -> extractPdfTextBestEffort(file.bytes)
        .takeIf(::isUsefulExtractedScheduleText)
        ?.let { LocalAiDocumentText(it, "PDF") }
    else -> null
}

internal fun isUsefulExtractedScheduleText(text: String): Boolean {
    val compact = text.filterNot(Char::isWhitespace)
    if (compact.length < 80) return false
    val readable = compact.count { it.isLetterOrDigit() || it in "，。,:：;；-_/()（）[]【】" }
    if (readable.toFloat() / compact.length.coerceAtLeast(1) < 0.72f) return false
    val normalized = text.lowercase()
    val scheduleSignals = listOf(
        "星期", "周一", "周二", "周三", "周四", "周五", "节次", "课程", "教师", "教室",
        "monday", "tuesday", "wednesday", "thursday", "friday", "course", "teacher", "classroom"
    ).count(normalized::contains)
    return scheduleSignals >= 2
}

private fun PreprocessResult.toScheduleInput(file: AiImportFile): AiScheduleInput {
    return when (this) {
        is PreprocessResult.Text -> AiScheduleInput.ExtractedText(text, file.displayName)
        is PreprocessResult.Images -> AiScheduleInput.Images(images, file.displayName)
        is PreprocessResult.Raw -> AiScheduleInput.RawFile(
            mimeType = if (file.isPdf) "application/pdf" else file.mimeType,
            fileName = file.displayName,
            bytes = bytes
        )
    }
}

private interface AiScheduleImportProvider {
    fun parseSchedule(config: AiProviderConfig, input: AiScheduleInput): AiProviderTextResult
}

private fun JsonObjectBuilder.putChatSamplingAndReasoning(config: AiProviderConfig) {
    if (config.providerId != AiProviderPresets.deepSeek.id) {
        put("temperature", JsonPrimitive(0.1))
        return
    }
    val thinkingEnabled = config.reasoningEffort != AiReasoningEffort.NONE
    put("thinking", buildJsonObject {
        put("type", JsonPrimitive(if (thinkingEnabled) "enabled" else "disabled"))
    })
    if (thinkingEnabled) {
        val effort = when (config.reasoningEffort) {
            AiReasoningEffort.XHIGH, AiReasoningEffort.MAX -> "max"
            else -> "high"
        }
        // DeepSeek Chat Completions defines reasoning_effort as a top-level field.
        put("reasoning_effort", JsonPrimitive(effort))
    }
}

private class OpenAiCompatibleChatProvider : AiScheduleImportProvider {
    override fun parseSchedule(config: AiProviderConfig, input: AiScheduleInput): AiProviderTextResult {
        val userContent: JsonElement = when (input) {
            is AiScheduleInput.ExtractedText -> JsonPrimitive(
                aiSchedulePrompt() + "\n\n课表原文（${input.sourceName}）：\n" + input.text
            )
            is AiScheduleInput.ImageBase64 -> buildJsonArray {
                addTextPart(aiSchedulePrompt() + "\n\n请识别图片课表并输出 JSON。")
                addImagePart("data:${input.mimeType};base64,${input.base64}")
            }
            is AiScheduleInput.Images -> buildJsonArray {
                addTextPart(aiSchedulePrompt() + "\n\n下面图片来自同一份课表文件，按页码或视口位置有序发送。相邻图片可能保留少量重叠区域用于校对，请不要重复生成课程。请综合所有图片还原完整课程信息。")
                input.images.forEach { image -> addImagePart(image.dataUrl) }
            }
            is AiScheduleInput.CapturedPage -> buildJsonArray {
                addTextPart(
                    aiSchedulePrompt() +
                        "\n\n这是从教务 WebView 分层抓取的页面内容。DOM 文本可能包含导航、版权、重复表格或缺失字段；截图为当前页面可见渲染结果。" +
                        "\n输入文本开头会提供当前教务页面地址。请根据该网址识别学校；如果模型具备联网或内置检索能力，可以参考该学校公开的校历、作息时间、节次时间或排课安排来校对时间。" +
                        "\n截图来自同一张课表页面，按滚动位置以原始视口比例分段发送；相邻图片可能有少量重叠，只用于校对上下文，不要把同一课程重复输出。请尽力读取并还原完整课程信息。" +
                        "\n请先分析截图/表格结构：识别星期列、节次行、时间轴、课程块跨度、周次标注、地点和教师，再生成 JSON。请优先以截图中的真实课表为准，DOM 文本作为辅助。若课程待定或未安排，输出占位节次/周次并在备注说明需要用户手动修改。" +
                        "\n\n来源：${input.sourceName}" +
                        "\n诊断：\n${input.warnings.joinToString("\n").ifBlank { "无" }}" +
                        "\n\n页面文本：\n${input.text}"
                )
                input.images.forEach { image -> addImagePart(image.dataUrl) }
            }
            is AiScheduleInput.RawFile -> error("当前兼容接口不支持直接上传原始文件，请改用文本、图片或 OpenAI 原生文件模式。")
        }
        val body = buildJsonObject {
            put("model", JsonPrimitive(config.model))
            val messages = buildJsonArray {
                scheduleParserSystemMessage()
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", userContent)
                })
            }
            put("messages", messages)
            put("tools", JsonArray(listOf(scheduleImportChatTool())))
            put("tool_choice", buildJsonObject {
                put("type", JsonPrimitive("function"))
                put("function", buildJsonObject { put("name", JsonPrimitive(ScheduleImportToolName)) })
            })
            putChatSamplingAndReasoning(config)
            putChatOutputBudget(config)
        }
        val response = postJson(config.baseUrl.trimEnd('/') + "/chat/completions", config.apiKey, body.toString(), config.authType, config.providerId)
        val result = runCatching {
            parseScheduleToolResult(response) ?: parseChatCompletionTextResult(response)
        }.getOrElse {
            if (it is AiServiceResponseException) throw it
            throw AiServiceResponseException("AI 响应结构无法解析：${it.message.orEmpty()}", response, it)
        }
        return if (result.finishReason == "length") {
            continueTruncatedScheduleJson(config, body["messages"] ?: JsonArray(emptyList()), result)
        } else {
            result
        }
    }

    fun reviseSchedule(
        config: AiProviderConfig,
        request: String,
        history: AiEduImportProgress
    ): AiProviderTextResult {
        val initialMessages = buildJsonArray {
            scheduleParserSystemMessage()
            add(buildJsonObject {
                put("role", JsonPrimitive("user"))
                put("content", JsonPrimitive(request))
            })
        }
        val firstBody = buildJsonObject {
            put("model", JsonPrimitive(config.model))
            put("messages", initialMessages)
            put("tools", JsonArray(listOf(readOriginalSourceChatTool(), schedulePatchChatTool())))
            put("tool_choice", JsonPrimitive("auto"))
            putChatSamplingAndReasoning(config)
            putChatOutputBudget(config)
        }
        val firstResponse = postJson(
            config.baseUrl.trimEnd('/') + "/chat/completions",
            config.apiKey,
            firstBody.toString(),
            config.authType,
            config.providerId
        )
        parseSchedulePatchToolResult(firstResponse)?.let { return it }
        val root = Json.parseToJsonElement(firstResponse).jsonObject
        val assistantMessage = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?: throw AiServiceResponseException("模型未返回可识别的工具调用", firstResponse)
        val readCall = assistantMessage["tool_calls"]?.jsonArray
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull {
                it["function"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull == ReadOriginalSourceToolName
            }
            ?: throw AiServiceResponseException("模型未提交课表，也未请求读取原始材料", firstResponse)
        val callId = readCall["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val sourceContent = revisionSourceChatContent(history)
        val secondMessages = buildJsonArray {
            initialMessages.forEach(::add)
            add(assistantMessage)
            add(buildJsonObject {
                put("role", JsonPrimitive("tool"))
                put("tool_call_id", JsonPrimitive(callId))
                put("content", JsonPrimitive("原始导入材料已加载，请结合随后提供的内容复核。"))
            })
            add(buildJsonObject {
                put("role", JsonPrimitive("user"))
                put("content", sourceContent)
            })
        }
        val secondBody = buildJsonObject {
            put("model", JsonPrimitive(config.model))
            put("messages", secondMessages)
            put("tools", JsonArray(listOf(schedulePatchChatTool())))
            put("tool_choice", buildJsonObject {
                put("type", JsonPrimitive("function"))
                put("function", buildJsonObject { put("name", JsonPrimitive(SchedulePatchToolName)) })
            })
            putChatSamplingAndReasoning(config)
            putChatOutputBudget(config)
        }
        val secondResponse = postJson(
            config.baseUrl.trimEnd('/') + "/chat/completions",
            config.apiKey,
            secondBody.toString(),
            config.authType,
            config.providerId
        )
        return parseSchedulePatchToolResult(secondResponse)
            ?: throw AiServiceResponseException("模型读取原始材料后未提交课表", secondResponse)
    }

    private fun revisionSourceChatContent(history: AiEduImportProgress): JsonElement =
        if (history.screenshotPreviews.isEmpty()) {
            JsonPrimitive(buildAiOriginalSourceContext(history))
        } else {
            buildJsonArray {
                addTextPart(buildAiOriginalSourceContext(history))
                history.screenshotPreviews.forEach { addImagePart(it.dataUrl) }
            }
        }

    private fun JsonArrayBuilder.addTextPart(text: String) {
        add(buildJsonObject {
            put("type", JsonPrimitive("text"))
            put("text", JsonPrimitive(text))
        })
    }

    private fun JsonArrayBuilder.addImagePart(dataUrl: String) {
        add(buildJsonObject {
            put("type", JsonPrimitive("image_url"))
            put("image_url", buildJsonObject { put("url", JsonPrimitive(dataUrl)) })
        })
    }

    private fun JsonArrayBuilder.scheduleParserSystemMessage() {
        add(buildJsonObject {
            put("role", JsonPrimitive("system"))
            put("content", JsonPrimitive("你是 SleepDown Schedule 的课表解析器，只能输出完整 JSON，不要输出解释文字。若输出被截断，后续请求只续写剩余 JSON。"))
        })
    }

    private fun JsonObjectBuilder.putChatOutputBudget(config: AiProviderConfig) {
        if (config.providerId == AiProviderPresets.deepSeek.id) {
            put("max_tokens", JsonPrimitive(393216))
        } else if (config.providerId == AiProviderPresets.mimo.id || config.providerId == AiProviderPresets.mimoTokenPlan.id) {
            put("max_completion_tokens", JsonPrimitive(131072))
        } else {
            put("max_tokens", JsonPrimitive(32768))
        }
    }

    private fun continueTruncatedScheduleJson(
        config: AiProviderConfig,
        originalMessages: JsonElement,
        firstResult: AiProviderTextResult
    ): AiProviderTextResult {
        var combinedContent = firstResult.content
        var combinedReasoning = firstResult.reasoning
        var finishReason = firstResult.finishReason
        repeat(2) { attempt ->
            if (finishReason != "length") return AiProviderTextResult(
                content = combinedContent,
                reasoning = combinedReasoning,
                finishReason = finishReason
            )
            val messages = buildJsonArray {
                originalMessages.jsonArray.forEach { add(it) }
                add(buildJsonObject {
                    put("role", JsonPrimitive("assistant"))
                    put("content", JsonPrimitive(combinedContent))
                })
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put(
                        "content",
                        JsonPrimitive(
                            "上一条 JSON 因输出长度限制被截断。请从截断位置继续输出剩余 JSON，直到 JSON 完整闭合。" +
                                "不要重复已经输出的内容，不要输出解释、Markdown、代码块或思考过程。续写必须能直接拼接到上一条末尾。"
                        )
                    )
                })
            }
            val body = buildJsonObject {
                put("model", JsonPrimitive(config.model))
                put("messages", messages)
                putChatSamplingAndReasoning(config)
                putChatOutputBudget(config)
            }
            val response = postJson(config.baseUrl.trimEnd('/') + "/chat/completions", config.apiKey, body.toString(), config.authType, config.providerId)
            val next = runCatching {
                parseChatCompletionTextResult(response)
            }.getOrElse {
                if (it is AiServiceResponseException) throw it
                throw AiServiceResponseException("AI 续写响应结构无法解析：${it.message.orEmpty()}", response, it)
            }
            val continuation = next.content
            combinedContent += continuation
            if (next.reasoning.isNotBlank()) {
                combinedReasoning = listOf(combinedReasoning, "续写 ${attempt + 1}：\n${next.reasoning}")
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")
            }
            finishReason = next.finishReason
        }
        return AiProviderTextResult(content = combinedContent, reasoning = combinedReasoning, finishReason = finishReason)
    }
}

private class OpenAiResponsesProvider : AiScheduleImportProvider {
    override fun parseSchedule(config: AiProviderConfig, input: AiScheduleInput): AiProviderTextResult {
        val content = buildJsonArray {
            add(buildJsonObject {
                put("type", JsonPrimitive("input_text"))
                put("text", JsonPrimitive(aiSchedulePrompt()))
            })
            when (input) {
                is AiScheduleInput.RawFile -> {
                    add(buildJsonObject {
                        put("type", JsonPrimitive("input_file"))
                        put("filename", JsonPrimitive(input.fileName))
                        put("file_data", JsonPrimitive("data:${input.mimeType};base64," + Base64.encodeToString(input.bytes, Base64.NO_WRAP)))
                    })
                }
                is AiScheduleInput.ExtractedText -> add(buildJsonObject {
                    put("type", JsonPrimitive("input_text"))
                    put("text", JsonPrimitive(input.text))
                })
                is AiScheduleInput.Images -> input.images.forEach { image ->
                    add(buildJsonObject {
                        put("type", JsonPrimitive("input_image"))
                        put("image_url", JsonPrimitive(image.dataUrl))
                    })
                }
                is AiScheduleInput.CapturedPage -> {
                    add(buildJsonObject {
                        put("type", JsonPrimitive("input_text"))
                        put(
                            "text",
                            JsonPrimitive(
                                "这是从教务 WebView 分层抓取的页面内容。请优先以截图中的真实课表为准，DOM 文本作为辅助。\n" +
                                    "输入文本开头会提供当前教务页面地址。请根据该网址识别学校；如果模型具备联网或内置检索能力，可以参考该学校公开的校历、作息时间、节次时间或排课安排来校对时间。\n" +
                                    "请先分析截图/表格结构：识别星期列、节次行、时间轴、课程块跨度、周次标注、地点和教师，再生成 JSON。\n" +
                                    "截图来自同一张课表页面，按滚动位置以原始视口比例分段发送；相邻图片可能有少量重叠，只用于校对上下文，不要重复生成课程。\n" +
                                    "来源：${input.sourceName}\n" +
                                    "诊断：\n${input.warnings.joinToString("\n").ifBlank { "无" }}\n\n" +
                                    "页面文本：\n${input.text}"
                            )
                        )
                    })
                    input.images.forEach { image ->
                        add(buildJsonObject {
                            put("type", JsonPrimitive("input_image"))
                            put("image_url", JsonPrimitive(image.dataUrl))
                        })
                    }
                }
                is AiScheduleInput.ImageBase64 -> add(buildJsonObject {
                    put("type", JsonPrimitive("input_image"))
                    put("image_url", JsonPrimitive("data:${input.mimeType};base64,${input.base64}"))
                })
            }
        }
        val body = buildJsonObject {
            put("model", JsonPrimitive(config.model))
            // Course files can contain names, locations and school identifiers. Keep the
            // request stateless so an official OpenAI Responses call is not retained remotely
            // by default.
            put("store", JsonPrimitive(false))
            put("input", buildJsonArray {
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", content)
                })
            })
            putResponsesReasoning(config)
            putResponsesOutputBudget(config)
            put("tools", JsonArray(listOf(scheduleImportResponsesTool())))
            put("tool_choice", buildJsonObject {
                put("type", JsonPrimitive("function"))
                put("name", JsonPrimitive(ScheduleImportToolName))
            })
        }
        val response = postJson(
            config.baseUrl.trimEnd('/') + "/responses",
            config.apiKey,
            body.toString(),
            config.authType,
            config.providerId
        )
        return parseScheduleToolResult(response) ?: parseResponsesTextResult(response)
    }

    fun reviseSchedule(
        config: AiProviderConfig,
        request: String,
        history: AiEduImportProgress
    ): AiProviderTextResult {
        val initialInput = buildJsonObject {
            put("role", JsonPrimitive("user"))
            put("content", buildJsonArray {
                add(buildJsonObject {
                    put("type", JsonPrimitive("input_text"))
                    put("text", JsonPrimitive(request))
                })
            })
        }
        val firstBody = buildJsonObject {
            put("model", JsonPrimitive(config.model))
            put("store", JsonPrimitive(false))
            put("input", JsonArray(listOf(initialInput)))
            putResponsesReasoning(config)
            putResponsesOutputBudget(config)
            put("tools", JsonArray(listOf(readOriginalSourceResponsesTool(), schedulePatchResponsesTool())))
            put("tool_choice", JsonPrimitive("auto"))
        }
        val firstResponse = postJson(
            config.baseUrl.trimEnd('/') + "/responses",
            config.apiKey,
            firstBody.toString(),
            config.authType,
            config.providerId
        )
        parseSchedulePatchToolResult(firstResponse)?.let { return it }
        val root = Json.parseToJsonElement(firstResponse).jsonObject
        val outputItems = root["output"]?.jsonArray?.mapNotNull { it as? JsonObject }.orEmpty()
        val readCall = outputItems.firstOrNull {
            it["type"]?.jsonPrimitive?.contentOrNull == "function_call" &&
                it["name"]?.jsonPrimitive?.contentOrNull == ReadOriginalSourceToolName
        } ?: throw AiServiceResponseException("模型未提交课表，也未请求读取原始材料", firstResponse)
        val callId = readCall["call_id"]?.jsonPrimitive?.contentOrNull
            ?: readCall["id"]?.jsonPrimitive?.contentOrNull
            ?: throw AiServiceResponseException("读取原始材料的工具调用缺少 call_id", firstResponse)
        val sourceInput = buildJsonObject {
            put("role", JsonPrimitive("user"))
            put("content", buildJsonArray {
                add(buildJsonObject {
                    put("type", JsonPrimitive("input_text"))
                    put("text", JsonPrimitive(buildAiOriginalSourceContext(history)))
                })
                history.screenshotPreviews.forEach { image ->
                    add(buildJsonObject {
                        put("type", JsonPrimitive("input_image"))
                        put("image_url", JsonPrimitive(image.dataUrl))
                    })
                }
            })
        }
        val secondInput = buildJsonArray {
            add(initialInput)
            outputItems.forEach(::add)
            add(buildJsonObject {
                put("type", JsonPrimitive("function_call_output"))
                put("call_id", JsonPrimitive(callId))
                put("output", JsonPrimitive("原始导入材料已加载，请结合随后提供的内容复核。"))
            })
            add(sourceInput)
        }
        val secondBody = buildJsonObject {
            put("model", JsonPrimitive(config.model))
            put("store", JsonPrimitive(false))
            put("input", secondInput)
            putResponsesReasoning(config)
            putResponsesOutputBudget(config)
            put("tools", JsonArray(listOf(schedulePatchResponsesTool())))
            put("tool_choice", buildJsonObject {
                put("type", JsonPrimitive("function"))
                put("name", JsonPrimitive(SchedulePatchToolName))
            })
        }
        val secondResponse = postJson(
            config.baseUrl.trimEnd('/') + "/responses",
            config.apiKey,
            secondBody.toString(),
            config.authType,
            config.providerId
        )
        return parseSchedulePatchToolResult(secondResponse)
            ?: throw AiServiceResponseException("模型读取原始材料后未提交课表", secondResponse)
    }
}

private fun JsonObjectBuilder.putResponsesReasoning(config: AiProviderConfig) {
    put("reasoning", buildJsonObject {
        val effort = if (config.providerId == AiProviderPresets.deepSeek.id) {
            when (config.reasoningEffort) {
                AiReasoningEffort.NONE -> "none"
                AiReasoningEffort.MINIMAL, AiReasoningEffort.LOW -> "low"
                AiReasoningEffort.MEDIUM, AiReasoningEffort.HIGH -> "high"
                AiReasoningEffort.XHIGH, AiReasoningEffort.MAX -> "max"
            }
        } else {
            config.reasoningEffort.apiValue
        }
        put("effort", JsonPrimitive(effort))
        if (config.providerId == AiProviderPresets.openAI.id && isOfficialOpenAIBaseUrl(config.baseUrl)) {
            put("summary", JsonPrimitive("auto"))
        }
    })
}

private fun JsonObjectBuilder.putResponsesOutputBudget(config: AiProviderConfig) {
    when (config.providerId) {
        AiProviderPresets.deepSeek.id -> put("max_output_tokens", JsonPrimitive(393216))
        AiProviderPresets.mimo.id,
        AiProviderPresets.mimoTokenPlan.id,
        AiProviderPresets.dailyFree.id -> put("max_output_tokens", JsonPrimitive(131072))
    }
}

suspend fun loadAiImportFile(context: Context, uri: Uri): Result<AiImportFile> =
    withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val reportedMime = resolver.getType(uri)
            val metadata = runCatching {
                resolver.query(
                    uri,
                    arrayOf(
                        android.provider.OpenableColumns.DISPLAY_NAME,
                        android.provider.OpenableColumns.SIZE
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) {
                        null
                    } else {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        val displayName = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                            cursor.getString(nameIndex)
                        } else {
                            null
                        }
                        val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                            cursor.getLong(sizeIndex)
                        } else {
                            null
                        }
                        displayName to size
                    }
                }
            }.getOrNull()
            val name = metadata?.first
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: "schedule-file"
            val mime = normalizeAiImportMimeType(name, reportedMime)
            metadata?.second
                ?.takeIf { it >= 0 }
                ?.let { size ->
                    require(size <= MaxAiImportFileBytes) { "文件不能超过 20MB" }
                }
            val rawBytes = resolver.openInputStream(uri)
                ?.use { it.readBytesWithLimit(MaxAiImportFileBytes) }
                ?: error("无法读取文件")
            val bytes = if (classifyAiImportDocument(name, mime) == AiImportDocumentKind.IMAGE) {
                compressAiImportImage(rawBytes)
            } else {
                rawBytes
            }
            AiImportFile(uri, name, mime, bytes)
        }
    }

internal fun InputStream.readBytesWithLimit(maxBytes: Int): ByteArray {
    require(maxBytes > 0) { "读取上限必须大于 0" }
    val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (count == 0) continue
        require(total <= maxBytes - count) { "文件不能超过 20MB" }
        output.write(buffer, 0, count)
        total += count
    }
    return output.toByteArray()
}

private fun compressAiImportImage(bytes: ByteArray): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return bytes
    val longSide = maxOf(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (longSide / sample > 1800) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return bytes
    return ByteArrayOutputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
        bitmap.recycle()
        output.toByteArray()
    }
}

private fun AiImportFile.toRenderedImage(pageIndex: Int): RenderedPageImage {
    return RenderedPageImage(
        pageIndex = pageIndex,
        mimeType = "image/jpeg",
        base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
    )
}

internal fun renderAiImportPreviewImages(
    context: Context,
    file: AiImportFile,
    maxPages: Int = 6
): List<RenderedPageImage> = when {
    file.isImage -> listOf(file.toRenderedImage(0))
    file.isPdf && !isUsefulExtractedScheduleText(extractPdfTextBestEffort(file.bytes)) ->
        renderPdfPageImages(context, file, maxPages)
    else -> emptyList()
}

private fun renderPdfPageImages(context: Context, file: AiImportFile, maxPages: Int = 6): List<RenderedPageImage> {
    val temp = File.createTempFile("sleepdown_ai_pdf_", ".pdf", context.cacheDir)
    return try {
        temp.writeBytes(file.bytes)
        ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                require(renderer.pageCount > 0) { "PDF 文件没有可解析页面" }
                val count = minOf(renderer.pageCount, maxPages)
                (0 until count).map { pageIndex ->
                    renderer.openPage(pageIndex).use { page ->
                        val scale = 1400f / maxOf(page.width, page.height).coerceAtLeast(1)
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = createBitmap(width, height)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val bytes = ByteArrayOutputStream().use { output ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 86, output)
                            bitmap.recycle()
                            output.toByteArray()
                        }
                        RenderedPageImage(
                            pageIndex = pageIndex,
                            mimeType = "image/jpeg",
                            base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        )
                    }
                }
            }
        }
    } finally {
        temp.delete()
    }
}

internal fun extractPdfTextBestEffort(bytes: ByteArray): String {
    val chunks = mutableListOf<String>()
    val raw = bytes.toString(Charsets.ISO_8859_1)
    chunks += extractPdfTextFromStreamText(raw)
    Regex("stream\\r?\\n(.*?)\\r?\\nendstream", RegexOption.DOT_MATCHES_ALL)
        .findAll(raw)
        .forEach { match ->
            val streamText = match.groupValues[1]
            chunks += extractPdfTextFromStreamText(streamText)
            val streamBytes = streamText.toByteArray(Charsets.ISO_8859_1)
            runCatching {
                InflaterInputStream(ByteArrayInputStream(streamBytes)).bufferedReader(Charsets.ISO_8859_1).use { it.readText() }
            }.getOrNull()?.let { inflated ->
                chunks += extractPdfTextFromStreamText(inflated)
            }
        }
    return chunks.joinToString("\n")
        .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]+"), " ")
        .replace(Regex("[ \\t]{2,}"), " ")
        .lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString("\n")
        .take(18_000)
}

private fun extractPdfTextFromStreamText(text: String): String {
    val values = mutableListOf<String>()
    Regex("\\((?:\\\\.|[^\\\\)])*\\)\\s*Tj").findAll(text).forEach {
        values += decodePdfLiteralString(it.value.substringBeforeLast(")").removePrefix("("))
    }
    Regex("\\[(.*?)]\\s*TJ", RegexOption.DOT_MATCHES_ALL).findAll(text).forEach { array ->
        Regex("\\((?:\\\\.|[^\\\\)])*\\)").findAll(array.groupValues[1]).forEach {
            values += decodePdfLiteralString(it.value.removePrefix("(").removeSuffix(")"))
        }
    }
    return values.joinToString(" ")
}

private fun decodePdfLiteralString(value: String): String {
    val builder = StringBuilder()
    var index = 0
    while (index < value.length) {
        val ch = value[index]
        if (ch == '\\' && index + 1 < value.length) {
            val next = value[index + 1]
            builder.append(
                when (next) {
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    'b' -> '\b'
                    'f' -> '\u000C'
                    '(', ')', '\\' -> next
                    else -> next
                }
            )
            index += 2
        } else {
            builder.append(ch)
            index++
        }
    }
    return builder.toString()
}

private fun request(url: String, apiKey: String, method: String, body: ByteArray, contentType: String): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = 30_000
        readTimeout = 600_000
        doOutput = true
        setRequestProperty("Authorization", "Bearer $apiKey")
        setRequestProperty("Content-Type", contentType)
        setRequestProperty("Accept", "application/json")
    }
    return try {
        connection.outputStream.use { it.write(body) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) throw AiServiceResponseException("AI 请求失败 ($status)：${text.take(300)}", text)
        text
    } finally {
        connection.disconnect()
    }
}

private fun safeRequest(
    url: String,
    apiKey: String,
    method: String,
    body: ByteArray,
    contentType: String,
    authType: AiAuthType = AiAuthType.ApiKeyBearer,
    providerId: String? = null
): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = 30_000
        readTimeout = 600_000
        doOutput = true
        setAiAuthHeader(apiKey, authType)
        setRequestProperty("Content-Type", contentType)
        setRequestProperty("Accept", "application/json")
    }
    return try {
        connection.outputStream.use { it.write(body) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) {
            Log.d(AiImportLogTag, "AI HTTP $status url=${redactAiUrl(url)} response=${sanitizeAiOutputForDisplay(text).replace(Regex("\\s+"), " ").take(500)}")
            throw AiServiceResponseException(formatAiRequestError(status, text, providerId), text)
        }
        text
    } catch (throwable: Throwable) {
        if (throwable is AiServiceResponseException) {
            throw throwable
        }
        Log.d(AiImportLogTag, "AI network failure url=${redactAiUrl(url)} message=${throwable.message}", throwable)
        throw IllegalStateException(formatAiNetworkError(url, throwable), throwable)
    } finally {
        connection.disconnect()
    }
}

internal fun HttpURLConnection.setAiAuthHeader(apiKey: String, authType: AiAuthType) {
    when (authType) {
        AiAuthType.ApiKeyBearer,
        AiAuthType.OpenAIProjectKey -> setRequestProperty("Authorization", "Bearer $apiKey")
        AiAuthType.CustomHeader -> setRequestProperty("api-key", apiKey)
    }
}

private fun redactAiUrl(value: String): String {
    return runCatching {
        val url = URL(value)
        "${url.protocol}://${url.host}${url.path}"
    }.getOrDefault(value.substringBefore('?'))
}

private fun formatAiNetworkError(url: String, throwable: Throwable): String {
    val host = runCatching { URL(url).host }.getOrDefault(url)
    val message = throwable.message.orEmpty()
    val hint = when {
        message.contains("Unacceptable certificate", ignoreCase = true) ||
            message.contains("SSLHandshakeException", ignoreCase = true) ||
            message.contains("Trust anchor", ignoreCase = true) ||
            message.contains("certificate", ignoreCase = true) ->
            buildString {
                append("$host 的 HTTPS 证书链没有被 Android 信任。")
                if (host.contains("xiaomimimo.com", ignoreCase = true)) {
                    append("小米 MiMo 普通按量接口应使用 https://api.xiaomimimo.com/v1；Token Plan 应改选“小米 MiMo Token Plan”。")
                }
                append("如果正在使用代理/VPN/抓包工具，请关闭 HTTPS 检查，或确认代理证书已被系统信任；不要在 App 内跳过证书校验。")
            }
        message.contains("failed to connect", ignoreCase = true) ||
            message.contains("connect timed out", ignoreCase = true) ||
            message.contains("Connection refused", ignoreCase = true) ->
            "手机当前网络无法连接到 $host。请尝试切换蜂窝/其他 Wi-Fi，或给手机配置能访问该 API 的代理/VPN。"
        message.contains("Unable to resolve host", ignoreCase = true) ->
            "手机当前网络无法解析 $host。请检查 DNS、网络或代理设置。"
        message.contains("timeout", ignoreCase = true) ->
            "连接 $host 超时。请检查网络可达性，或稍后重试。"
        else -> "无法连接到 $host。请检查手机网络、代理/VPN、接口地址和服务商状态。"
    }
    return "$hint 原始错误：$message"
}

internal fun formatAiRequestError(status: Int, text: String, providerId: String? = null): String {
    if (providerId == AiProviderPresets.dailyFree.id && isManagedFreeLimitError(status, text)) {
        return "今日免费 AI 共享额度已用完，请明天再试，或在 AI 设置中配置自己的 AI 服务。"
    }
    val compact = sanitizeAiOutputForDisplay(text).replace(Regex("\\s+"), " ").take(240)
    val hint = if (
        text.contains("404 page not found", ignoreCase = true) ||
        text.contains("\"code\":\"service_unavailable_error\"", ignoreCase = true)
    ) {
        "接口路径不匹配。若使用第三方兼容站，请确认接口地址包含它要求的版本路径（通常是 /v1），并优先关闭“严格 JSON”。"
    } else {
        null
    }
    return buildString {
        append("AI 请求失败 ($status)")
        hint?.let { append("：").append(it) }
        if (compact.isNotBlank()) append(" 服务返回：").append(compact)
    }
}

private fun isManagedFreeLimitError(status: Int, text: String): Boolean {
    if (status == 429) return true
    val normalized = text.lowercase()
    return listOf(
        "模型超限",
        "额度已用完",
        "额度不足",
        "quota",
        "rate_limit",
        "rate limit",
        "limit exceeded",
        "too many requests"
    ).any(normalized::contains)
}

private fun postJson(
    url: String,
    apiKey: String,
    body: String,
    authType: AiAuthType = AiAuthType.ApiKeyBearer,
    providerId: String? = null
): String {
    return safeRequest(
        url,
        apiKey,
        "POST",
        body.toByteArray(Charsets.UTF_8),
        "application/json",
        authType,
        providerId
    )
}

private fun parseChatCompletionTextResult(response: String, requireContent: Boolean = true): AiProviderTextResult {
    val choice = Json.parseToJsonElement(response)
        .jsonObject["choices"]?.jsonArray?.firstOrNull()
        ?.jsonObject
        ?: throw AiServiceResponseException("AI 响应里没有找到 choices[0]。", response)
    val message = choice["message"]?.jsonObject
        ?: throw AiServiceResponseException("AI 响应里没有找到 choices[0].message。", response)
    val content = message["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val finishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val reasoning = buildList {
        listOf("reasoning_content", "reasoning", "thoughts", "chain_of_thought").forEach { key ->
            message[key]?.let { addAll(collectTextLeaves(it)) }
        }
        addAll(collectInlineReasoningBlocks(content))
    }.map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString("\n\n")
    val finalContent = content.stripInlineReasoningBlocks().trim()
    if (requireContent && finalContent.isBlank()) {
        val detail = if (finishReason == "length") {
            "思考过程耗尽了输出额度，最终正文被截断。"
        } else {
            "模型没有返回最终正文。"
        }
        throw AiServiceResponseException("AI 只返回了思考过程，$detail 当前已保留 DeepSeek thinking；请重试，或提高输出额度后再试。", response)
    }
    return AiProviderTextResult(content = finalContent, reasoning = reasoning, finishReason = finishReason)
}

internal fun parseResponsesTextResult(response: String): AiProviderTextResult {
    val root = Json.parseToJsonElement(response).jsonObject
    val rootText = root["output_text"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val contentParts = mutableListOf<String>()
    val reasoningParts = collectResponsesReasoning(root).toMutableList()
    val output = root["output"]?.jsonArray.orEmpty()
    output.forEach { item ->
        val itemObject = item.jsonObject
        if (itemObject["type"]?.jsonPrimitive?.contentOrNull == "reasoning") return@forEach
        itemObject["content"]?.jsonArray.orEmpty().forEach { responseContent ->
            val contentObject = responseContent.jsonObject
            contentObject["text"]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotBlank)
                ?.let(contentParts::add)
        }
    }
    val finalContent = rootText.ifBlank { contentParts.joinToString("\n") }.trim()
    if (finalContent.isBlank()) {
        throw AiServiceResponseException("AI 没有返回课程表内容。", response)
    }
    return AiProviderTextResult(
        content = finalContent,
        reasoning = reasoningParts.distinct().joinToString("\n\n"),
        finishReason = root["status"]?.jsonPrimitive?.contentOrNull.orEmpty()
    )
}

private fun collectResponsesReasoning(root: JsonObject): List<String> = buildList {
    root["output"]?.jsonArray.orEmpty().forEach { item ->
        val itemObject = item.jsonObject
        if (itemObject["type"]?.jsonPrimitive?.contentOrNull != "reasoning") return@forEach
        itemObject["summary"]?.jsonArray.orEmpty().forEach { summary ->
            summary.jsonObject["text"]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotBlank)
                ?.let(::add)
        }
        itemObject["content"]?.jsonArray.orEmpty().forEach { content ->
            content.jsonObject["text"]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotBlank)
                ?.let(::add)
        }
    }
}.map(String::trim).filter(String::isNotBlank).distinct()

private fun redactReasoningFields(output: String): String {
    return runCatching {
        redactReasoningFields(Json.parseToJsonElement(output)).toString()
    }.getOrElse { output }
}

private fun redactReasoningFields(element: JsonElement): JsonElement {
    return when (element) {
        is JsonObject -> JsonObject(element.mapValues { (key, value) ->
            if (isReasoningKey(key)) JsonPrimitive("【思考过程已拆分到单独区域】") else redactReasoningFields(value)
        })
        is JsonArray -> JsonArray(element.map { redactReasoningFields(it) })
        else -> element
    }
}

private fun collectReasoningText(output: String): List<String> {
    val texts = mutableListOf<String>()
    texts += collectInlineReasoningBlocks(output)
    runCatching { Json.parseToJsonElement(output) }
        .onSuccess { texts += collectReasoningFields(it) }
    return texts.map { it.trim() }.filter { it.isNotBlank() }.distinct()
}

private fun collectReasoningFields(element: JsonElement): List<String> {
    return when (element) {
        is JsonObject -> element.flatMap { (key, value) ->
            if (isReasoningKey(key)) collectTextLeaves(value) else collectReasoningFields(value)
        }
        is JsonArray -> element.flatMap { collectReasoningFields(it) }
        else -> emptyList()
    }
}

private fun collectTextLeaves(element: JsonElement): List<String> {
    return when (element) {
        is JsonPrimitive -> listOfNotNull(element.contentOrNull)
        is JsonObject -> element.values.flatMap { collectTextLeaves(it) }
        is JsonArray -> element.flatMap { collectTextLeaves(it) }
    }
}

private fun isReasoningKey(key: String): Boolean {
    val normalized = key.lowercase()
    return normalized == "reasoning_content" ||
        normalized == "reasoning" ||
        normalized == "thoughts" ||
        normalized == "chain_of_thought" ||
        normalized.contains("reasoning_content")
}

private val ThinkBlockRegexes = listOf(
    Regex("<think[^>]*>.*?</think>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
    Regex("<thinking[^>]*>.*?</thinking>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
)

private fun String.stripInlineReasoningBlocks(): String {
    return ThinkBlockRegexes.fold(this) { current, regex -> regex.replace(current, "") }
}

private fun collectInlineReasoningBlocks(text: String): List<String> {
    return ThinkBlockRegexes.flatMap { regex ->
        regex.findAll(text).map { match ->
            match.value
                .replace(Regex("</?thinking?[^>]*>", RegexOption.IGNORE_CASE), "")
                .trim()
        }.toList()
    }.filter { it.isNotBlank() }
}

private fun AiImportFile.toDataUrl(): String {
    return "data:$mimeType;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
}

fun aiSchedulePrompt(): String = """
识别输入中的真实课表，并调用 IMPORT_SCHEDULE 工具提交结果。
以可见的表头、星期、节次、课程块和周次为准；不要重复长图接缝处的课程。
不确定的信息保留在 note 中，不要猜测。每门课的 periods 和 weeks 均不能为空。
changeSummary 必须用简洁中文说明本次识别或修改了哪些课程字段，不能只写“已完成”。
""".trimIndent()

private const val ScheduleImportToolName = "IMPORT_SCHEDULE"
private const val SchedulePatchToolName = "PATCH_SCHEDULE"
private const val ReadOriginalSourceToolName = "READ_ORIGINAL_IMPORT_SOURCE"

private fun scheduleJsonSchemaFormat(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("json_schema"))
    put("name", JsonPrimitive("sleepdown_schedule_import"))
    put("strict", JsonPrimitive(true))
    put("schema", scheduleJsonSchemaBody())
}

private fun scheduleJsonSchema(): JsonObject = buildJsonObject {
    put("name", JsonPrimitive("sleepdown_schedule_import"))
    put("strict", JsonPrimitive(true))
    put("schema", scheduleJsonSchemaBody())
}

private fun scheduleJsonSchemaBody(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("object"))
    put("additionalProperties", JsonPrimitive(false))
    put("required", JsonArray(listOf(JsonPrimitive("schemaVersion"), JsonPrimitive("scheduleConfig"), JsonPrimitive("courses"), JsonPrimitive("changeSummary"))))
    put("properties", buildJsonObject {
        put("schemaVersion", buildJsonObject {
            put("type", JsonPrimitive("integer"))
            put("const", JsonPrimitive(1))
        })
        put("scheduleConfig", buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("additionalProperties", JsonPrimitive(false))
            put("required", JsonArray(listOf(JsonPrimitive("totalWeeks"), JsonPrimitive("periods"))))
            put("properties", buildJsonObject {
                put("totalWeeks", buildJsonObject {
                    put("type", JsonPrimitive("integer"))
                    put("minimum", JsonPrimitive(1))
                    put("maximum", JsonPrimitive(60))
                })
                put("periods", buildJsonObject {
                    put("type", JsonPrimitive("array"))
                    put("items", buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put("additionalProperties", JsonPrimitive(false))
                        put("required", JsonArray(listOf(JsonPrimitive("index"), JsonPrimitive("startTime"), JsonPrimitive("endTime"))))
                        put("properties", buildJsonObject {
                            put("index", buildJsonObject { put("type", JsonPrimitive("integer")) })
                            put("startTime", buildJsonObject { put("type", JsonPrimitive("string")) })
                            put("endTime", buildJsonObject { put("type", JsonPrimitive("string")) })
                        })
                    })
                })
            })
        })
        put("courses", buildJsonObject {
            put("type", JsonPrimitive("array"))
            put("items", buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("additionalProperties", JsonPrimitive(false))
                put(
                    "required",
                    JsonArray(
                        listOf(
                            "name",
                            "teacher",
                            "location",
                            "weekday",
                            "periods",
                            "weeks",
                            "weekParity",
                            "note"
                        ).map(::JsonPrimitive)
                    )
                )
                put("properties", buildJsonObject {
                    put("name", buildJsonObject { put("type", JsonPrimitive("string")) })
                    put("teacher", nullableStringSchema())
                    put("location", nullableStringSchema())
                    put("weekday", buildJsonObject {
                        put("type", JsonPrimitive("integer"))
                        put("minimum", JsonPrimitive(1))
                        put("maximum", JsonPrimitive(7))
                    })
                    put("periods", integerArraySchema())
                    put("weeks", integerArraySchema())
                    put("weekParity", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("enum", JsonArray(listOf("ALL", "ODD", "EVEN").map(::JsonPrimitive)))
                    })
                    put("note", nullableStringSchema())
                })
            })
        })
        put("changeSummary", buildJsonObject {
            put("type", JsonPrimitive("string"))
            put("description", JsonPrimitive("本轮实际变更摘要。列出课程名称及新增、删除或改动的字段；禁止只写已完成。"))
        })
    })
}

private fun nullableStringSchema(): JsonObject = buildJsonObject {
    put("type", JsonArray(listOf(JsonPrimitive("string"), JsonPrimitive("null"))))
}

private fun integerArraySchema(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("array"))
    put("items", buildJsonObject { put("type", JsonPrimitive("integer")) })
}

private fun scheduleImportChatTool(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("function"))
    put("function", buildJsonObject {
        put("name", JsonPrimitive(ScheduleImportToolName))
        put("description", JsonPrimitive("提交识别完成并可由 SleepDown 本地校验的课程表"))
        put("strict", JsonPrimitive(true))
        put("parameters", scheduleJsonSchemaBody())
    })
}

private fun readOriginalSourceChatTool(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("function"))
    put("function", buildJsonObject {
        put("name", JsonPrimitive(ReadOriginalSourceToolName))
        put("description", JsonPrimitive("仅在需要复核、重新识别或核对原网页/附件时读取原始导入材料；普通字段修改不要调用"))
        put("strict", JsonPrimitive(true))
        put("parameters", emptyObjectSchema())
    })
}

private fun scheduleImportResponsesTool(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("function"))
    put("name", JsonPrimitive(ScheduleImportToolName))
    put("description", JsonPrimitive("提交识别完成并可由 SleepDown 本地校验的课程表"))
    put("strict", JsonPrimitive(true))
    put("parameters", scheduleJsonSchemaBody())
}

/** A compact, provider-neutral edit protocol for existing imported schedules. */
private fun schedulePatchSchemaBody(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("object"))
    put("additionalProperties", JsonPrimitive(false))
    put("required", JsonArray(listOf(JsonPrimitive("changeSummary"), JsonPrimitive("operations"))))
    put("properties", buildJsonObject {
        put("changeSummary", buildJsonObject {
            put("type", JsonPrimitive("string"))
            put("description", JsonPrimitive("逐项说明实际更改的课程和字段，禁止只写已完成。"))
        })
        put("operations", buildJsonObject {
            put("type", JsonPrimitive("array"))
            put("maxItems", JsonPrimitive(16))
            put("items", buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("additionalProperties", JsonPrimitive(false))
                put("required", JsonArray(listOf(
                    JsonPrimitive("type"), JsonPrimitive("index"), JsonPrimitive("course"),
                    JsonPrimitive("periods"), JsonPrimitive("totalWeeks")
                )))
                put("properties", buildJsonObject {
                    put("type", buildJsonObject {
                        put("type", JsonPrimitive("string"))
                        put("enum", JsonArray(listOf("replace_course", "add_course", "remove_course", "replace_periods", "set_total_weeks").map(::JsonPrimitive)))
                    })
                    put("index", buildJsonObject { put("type", JsonPrimitive("integer")); put("minimum", JsonPrimitive(0)) })
                    put("course", nullableRevisionCourseSchema())
                    put("periods", buildJsonObject {
                        put("type", JsonPrimitive("array"))
                        put("items", buildJsonObject {
                            put("type", JsonPrimitive("object")); put("additionalProperties", JsonPrimitive(false))
                            put("required", JsonArray(listOf(JsonPrimitive("index"), JsonPrimitive("startTime"), JsonPrimitive("endTime"))))
                            put("properties", buildJsonObject {
                                put("index", buildJsonObject { put("type", JsonPrimitive("integer")) })
                                put("startTime", buildJsonObject { put("type", JsonPrimitive("string")) })
                                put("endTime", buildJsonObject { put("type", JsonPrimitive("string")) })
                            })
                        })
                    })
                    put("totalWeeks", buildJsonObject { put("type", JsonPrimitive("integer")); put("minimum", JsonPrimitive(0)); put("maximum", JsonPrimitive(60)) })
                })
            })
        })
    })
}

private fun nullableRevisionCourseSchema(): JsonObject = buildJsonObject {
    put("type", JsonArray(listOf(JsonPrimitive("object"), JsonPrimitive("null"))))
    put("additionalProperties", JsonPrimitive(false))
    put("required", JsonArray(listOf("name", "teacher", "location", "weekday", "periods", "weeks", "weekParity", "note").map(::JsonPrimitive)))
    put("properties", scheduleJsonSchemaBody().jsonObject["properties"]!!.jsonObject["courses"]!!.jsonObject["items"]!!.jsonObject["properties"]!!)
}

private fun schedulePatchChatTool(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("function"))
    put("function", buildJsonObject {
        put("name", JsonPrimitive(SchedulePatchToolName))
        put("description", JsonPrimitive("对已有课表应用局部操作；只提交需要改变的课程或配置，不要回传整份课表。"))
        put("strict", JsonPrimitive(true))
        put("parameters", schedulePatchSchemaBody())
    })
}

private fun schedulePatchResponsesTool(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("function"))
    put("name", JsonPrimitive(SchedulePatchToolName))
    put("description", JsonPrimitive("对已有课表应用局部操作；只提交需要改变的课程或配置，不要回传整份课表。"))
    put("strict", JsonPrimitive(true))
    put("parameters", schedulePatchSchemaBody())
}

private fun readOriginalSourceResponsesTool(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("function"))
    put("name", JsonPrimitive(ReadOriginalSourceToolName))
    put("description", JsonPrimitive("仅在需要复核、重新识别或核对原网页/附件时读取原始导入材料；普通字段修改不要调用"))
    put("strict", JsonPrimitive(true))
    put("parameters", emptyObjectSchema())
}

private fun emptyObjectSchema(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("object"))
    put("additionalProperties", JsonPrimitive(false))
    put("properties", buildJsonObject {})
    put("required", JsonArray(emptyList()))
}

private fun parseScheduleToolResult(response: String): AiProviderTextResult? {
    val root = runCatching { Json.parseToJsonElement(response).jsonObject }.getOrNull() ?: return null
    val chatCall = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        ?.get("message")?.jsonObject
        ?.get("tool_calls")?.jsonArray?.firstOrNull()?.jsonObject
        ?.get("function")?.jsonObject
    val responseCall = root["output"]?.jsonArray
        ?.mapNotNull { it as? JsonObject }
        ?.firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "function_call" }
    val call = chatCall ?: responseCall ?: return null
    if (call["name"]?.jsonPrimitive?.contentOrNull != ScheduleImportToolName) return null
    val arguments = call["arguments"]?.let { value ->
        if (value is JsonPrimitive) value.contentOrNull else value.toString()
    }?.trim().orEmpty()
    if (arguments.isBlank()) return null
    val modelReasoning = runCatching {
        if (root.containsKey("choices")) {
            parseChatCompletionTextResult(response, requireContent = false).reasoning
        } else {
            collectResponsesReasoning(root).joinToString("\n\n")
        }
    }.getOrDefault("")
    val changeSummary = runCatching {
        Json.parseToJsonElement(arguments).jsonObject["changeSummary"]
            ?.jsonPrimitive?.contentOrNull.orEmpty()
    }.getOrDefault("")
    val reasoning = listOf(changeSummary, modelReasoning)
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
    return AiProviderTextResult(arguments, reasoning, "tool_call")
}

private fun parseSchedulePatchToolResult(response: String): AiProviderTextResult? {
    val root = runCatching { Json.parseToJsonElement(response).jsonObject }.getOrNull() ?: return null
    val chatCall = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        ?.get("message")?.jsonObject?.get("tool_calls")?.jsonArray?.firstOrNull()?.jsonObject
        ?.get("function")?.jsonObject
    val responseCall = root["output"]?.jsonArray?.mapNotNull { it as? JsonObject }
        ?.firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "function_call" }
    val call = chatCall ?: responseCall ?: return null
    if (call["name"]?.jsonPrimitive?.contentOrNull != SchedulePatchToolName) return null
    val arguments = call["arguments"]?.let { value ->
        if (value is JsonPrimitive) value.contentOrNull else value.toString()
    }?.trim().orEmpty()
    if (arguments.isBlank()) return null
    val changeSummary = runCatching {
        Json.parseToJsonElement(arguments).jsonObject["changeSummary"]?.jsonPrimitive?.contentOrNull.orEmpty()
    }.getOrDefault("")
    require(changeSummary.isNotBlank()) { "模型没有提供本轮修改摘要" }
    return AiProviderTextResult(arguments, changeSummary, "tool_call")
}

private fun applyAiSchedulePatch(base: ImportDraft, patchText: String): ImportDraft {
    val root = Json.parseToJsonElement(patchText).jsonObject
    val operations = root["operations"]?.jsonArray ?: error("PATCH_SCHEDULE 缺少 operations")
    var courses = base.courses.toMutableList()
    var periods = base.periods.toMutableList()
    var totalWeeks = base.config.totalWeeks
    fun courseAt(index: Int): CourseEntity = courses.getOrNull(index - 1)
        ?: error("PATCH_SCHEDULE 引用了不存在的课程索引 #$index")
    operations.forEach { raw ->
        val operation = raw.jsonObject
        val type = operation["type"]?.jsonPrimitive?.contentOrNull ?: error("PATCH_SCHEDULE 缺少操作类型")
        val index = operation["index"]?.jsonPrimitive?.intOrNull ?: 0
        when (type) {
            "replace_course" -> {
                val previous = courseAt(index)
                courses[index - 1] = revisionCourseFromJson(
                    operation["course"]?.jsonObject ?: error("replace_course 缺少 course"),
                    previous
                )
            }
            "add_course" -> courses += revisionCourseFromJson(
                operation["course"]?.jsonObject ?: error("add_course 缺少 course"),
                CourseEntity(scheduleId = base.config.id, name = "", teacher = null, location = null, weekday = 1, periods = listOf(1), weeks = listOf(1), weekParity = WeekParity.ALL, note = null)
            ).copy(id = 0)
            "remove_course" -> {
                courseAt(index)
                courses.removeAt(index - 1)
            }
            "replace_periods" -> {
                val values = operation["periods"]?.jsonArray ?: error("replace_periods 缺少 periods")
                periods = values.map { value ->
                    val item = value.jsonObject
                    PeriodEntity(
                        periodIndex = item["index"]?.jsonPrimitive?.intOrNull ?: error("节次缺少 index"),
                        startTime = item["startTime"]?.jsonPrimitive?.contentOrNull ?: error("节次缺少 startTime"),
                        endTime = item["endTime"]?.jsonPrimitive?.contentOrNull ?: error("节次缺少 endTime"),
                        scheduleId = base.config.id
                    )
                }.sortedBy { it.periodIndex }.toMutableList()
            }
            "set_total_weeks" -> totalWeeks = (operation["totalWeeks"]?.jsonPrimitive?.intOrNull ?: 0).also {
                require(it in 1..60) { "总周数必须在 1 到 60 之间" }
            }
            else -> error("不支持的 PATCH_SCHEDULE 操作：$type")
        }
    }
    val candidate = base.copy(
        config = base.config.copy(totalWeeks = totalWeeks),
        periods = periods,
        courses = courses
    )
    return ScheduleImportParser.parse(draftToPayload(candidate).toString(), base.config)
        .getOrThrow()
        .copy(source = ImportDraftSource.AI_EDU)
}

private fun revisionCourseFromJson(value: JsonObject, previous: CourseEntity): CourseEntity = previous.copy(
    name = value["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { error("课程名称不能为空") },
    teacher = value["teacher"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null },
    location = value["location"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null },
    weekday = value["weekday"]?.jsonPrimitive?.intOrNull?.also { require(it in 1..7) } ?: error("课程缺少 weekday"),
    periods = value["periods"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull }?.distinct()?.sorted()
        ?.takeIf { it.isNotEmpty() } ?: error("课程 periods 不能为空"),
    weeks = value["weeks"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull }?.distinct()?.sorted()
        ?.takeIf { it.isNotEmpty() } ?: error("课程 weeks 不能为空"),
    weekParity = value["weekParity"]?.jsonPrimitive?.contentOrNull?.let { WeekParity.valueOf(it) }
        ?: error("课程缺少 weekParity"),
    note = value["note"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }
)
