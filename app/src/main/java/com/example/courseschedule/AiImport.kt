package com.example.courseschedule

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.security.KeyStore
import java.util.zip.InflaterInputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val AiImportLogTag = "AiImport"

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
    val supportsStreaming: Boolean = false
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
    val supportsPdfDirect: Boolean = capabilities.supportsPdfFileInput
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
    val inputMode: AiInputMode,
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

private data class AiChatTurn(
    val role: String,
    val content: String
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
    val model: String
)

data class AiImportFile(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val bytes: ByteArray
) {
    val sizeBytes: Int get() = bytes.size
    val isPdf: Boolean get() = mimeType.equals("application/pdf", ignoreCase = true) || displayName.endsWith(".pdf", ignoreCase = true)
    val isImage: Boolean get() = mimeType.startsWith("image/", ignoreCase = true)
    val isText: Boolean get() = mimeType.startsWith("text/", ignoreCase = true) ||
        displayName.endsWith(".txt", ignoreCase = true) ||
        displayName.endsWith(".csv", ignoreCase = true)
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
        defaultModel = "gpt-5.5",
        capabilities = AiProviderCapabilities(
            supportsPdfFileInput = true,
            supportsImageInput = true,
            supportsTextInput = true,
            supportsJsonSchema = true,
            supportsJsonMode = true,
            supportsFileUpload = true
        ),
        endpointStyle = AiEndpointStyle.RESPONSES,
        structuredOutputMode = StructuredOutputMode.JSON_SCHEMA,
        supportsVision = true,
        supportsFileUpload = true,
        supportsPdfDirect = true
    )

    val deepSeek = AiProviderProfile(
        id = "deepseek",
        displayName = "DeepSeek",
        providerType = AiProviderType.OpenAIChatCompatible,
        baseUrl = "https://api.deepseek.com/v1",
        defaultModel = "deepseek-v4-flash",
        capabilities = AiProviderCapabilities(
            supportsTextInput = true,
            supportsJsonMode = true
        ),
        endpointStyle = AiEndpointStyle.CHAT_COMPLETIONS,
        structuredOutputMode = StructuredOutputMode.PROMPT_ONLY
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
            supportsJsonMode = false
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
        defaultModel = "",
        capabilities = AiProviderCapabilities(),
        endpointStyle = AiEndpointStyle.CHAT_COMPLETIONS,
        structuredOutputMode = StructuredOutputMode.PROMPT_ONLY
    )

    val selectable = listOf(none, openAI, deepSeek, mimo, custom)

    val all = listOf(none, openAI, deepSeek, dashScope, kimi, zhipu, qianfan, doubao, hunyuan, siliconFlow, miniMax, mimo, mimoTokenPlan, custom)

    fun byId(id: String): AiProviderProfile = all.firstOrNull { it.id == id } ?: openAI

    fun modelOptions(providerId: String): List<AiModelOption> = when (providerId) {
        openAI.id -> listOf(
            AiModelOption("GPT-5.5", "gpt-5.5"),
            AiModelOption("GPT-5.4", "gpt-5.4"),
            AiModelOption("5.4 mini", "gpt-5.4-mini"),
            AiModelOption("5.4 nano", "gpt-5.4-nano")
        )
        deepSeek.id -> listOf(
            AiModelOption("V4 Flash", "deepseek-v4-flash"),
            AiModelOption("V4 Pro", "deepseek-v4-pro")
        )
        dashScope.id -> listOf(
            AiModelOption("Qwen Plus", "qwen-plus"),
            AiModelOption("Qwen VL Plus", "qwen-vl-plus"),
            AiModelOption("Qwen VL Max", "qwen-vl-max")
        )
        kimi.id -> listOf(
            AiModelOption("K2.6", "kimi-k2.6"),
            AiModelOption("K2.5", "kimi-k2.5"),
            AiModelOption("Vision 32K", "moonshot-v1-32k-vision-preview")
        )
        zhipu.id -> listOf(
            AiModelOption("GLM 4 Flash", "glm-4-flash"),
            AiModelOption("GLM 4V Flash", "glm-4v-flash"),
            AiModelOption("GLM 4 Plus", "glm-4-plus")
        )
        qianfan.id -> listOf(
            AiModelOption("ERNIE 4 Turbo", "ernie-4.0-turbo-8k"),
            AiModelOption("ERNIE X1", "ernie-x1-turbo-32k")
        )
        doubao.id -> listOf(
            AiModelOption("Doubao Seed", "doubao-seed-1-6"),
            AiModelOption("Doubao Vision", "doubao-1-5-vision-pro")
        )
        hunyuan.id -> listOf(
            AiModelOption("Hunyuan Turbo", "hunyuan-turbos-latest"),
            AiModelOption("Hunyuan Vision", "hunyuan-vision")
        )
        siliconFlow.id -> listOf(
            AiModelOption("Qwen 72B", "Qwen/Qwen2.5-72B-Instruct"),
            AiModelOption("Qwen VL", "Qwen/Qwen2.5-VL-72B-Instruct"),
            AiModelOption("DeepSeek V3", "deepseek-ai/DeepSeek-V3")
        )
        miniMax.id -> listOf(
            AiModelOption("MiniMax M1", "MiniMax-M1"),
            AiModelOption("MiniMax Text", "abab6.5s-chat")
        )
        mimo.id, mimoTokenPlan.id -> listOf(
            AiModelOption("MiMo V2.5 Pro", "mimo-v2.5-pro"),
            AiModelOption("MiMo V2.5", "mimo-v2.5")
        )
        else -> emptyList()
    }
}

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
    private const val KeyEndpointStyle = "endpoint_style"
    private const val KeyStructuredOutputMode = "structured_output_mode"
    private const val KeyInputMode = "input_mode"
    private const val KeyVision = "supports_vision"
    private const val KeyPdfDirect = "supports_pdf_direct"
    private const val KeyEncryptedApiKey = "encrypted_api_key"
    private fun apiKeyKey(providerId: String): String = "${KeyEncryptedApiKey}_${providerId}"
    private fun providerKey(key: String, providerId: String): String = "${key}_${providerId}"

    fun load(context: Context): AiImportSettings {
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        val savedProviderId = prefs.getString(KeyProviderId, AiProviderPresets.none.id).orEmpty()
        val preset = AiProviderPresets.selectable.firstOrNull { it.id == savedProviderId } ?: AiProviderPresets.none
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
            supportsFileUpload = prefs.getBoolean(KeyFileUpload, preset.capabilities.supportsFileUpload)
        )
        val profile = preset.copy(
            providerType = providerType,
            baseUrl = normalizeAiBaseUrlForProvider(preset.id, prefs.getString(KeyBaseUrl, preset.baseUrl).orEmpty()),
            defaultModel = prefs.getString(KeyModel, preset.defaultModel).orEmpty(),
            capabilities = capabilities,
            endpointStyle = endpointStyle,
            structuredOutputMode = structuredOutputMode,
            inputMode = inputMode,
            supportsVision = prefs.getBoolean(KeyVision, preset.supportsVision || capabilities.supportsImageInput),
            supportsFileUpload = prefs.getBoolean(KeyFileUpload, preset.supportsFileUpload || capabilities.supportsFileUpload),
            supportsPdfDirect = prefs.getBoolean(KeyPdfDirect, preset.supportsPdfDirect || capabilities.supportsPdfFileInput)
        )
        val scopedEncryptedApiKey = prefs.getString(apiKeyKey(profile.id), null)
        val legacyEncryptedApiKey = prefs.getString(KeyEncryptedApiKey, null)
        val apiKey = scopedEncryptedApiKey?.let { decrypt(context, it) }
            ?: legacyEncryptedApiKey?.let { decrypt(context, it) }.orEmpty()
        if (scopedEncryptedApiKey == null && legacyEncryptedApiKey != null) {
            prefs.edit()
                .putString(apiKeyKey(profile.id), legacyEncryptedApiKey)
                .remove(KeyEncryptedApiKey)
                .apply()
        }
        return AiImportSettings(profile, apiKey)
    }

    fun loadProvider(context: Context, providerId: String): AiImportSettings {
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        val current = load(context)
        val preset = AiProviderPresets.byId(providerId)
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
                    )
                )
                preset.copy(
                    providerType = providerType,
                    baseUrl = normalizeAiBaseUrlForProvider(
                        preset.id,
                        prefs.getString(providerKey(KeyBaseUrl, preset.id), preset.baseUrl).orEmpty()
                    ),
                    defaultModel = prefs.getString(
                        providerKey(KeyModel, preset.id),
                        preset.defaultModel
                    ).orEmpty(),
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
                    )
                )
            }
        }
        val apiKey = prefs.getString(apiKeyKey(profile.id), null)?.let { decrypt(context, it) }.orEmpty()
        return AiImportSettings(profile, apiKey)
    }

    fun save(context: Context, settings: AiImportSettings) {
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putString(KeyProviderId, settings.profile.id)
            .putString(KeyBaseUrl, normalizeAiBaseUrlForProvider(settings.profile.id, settings.profile.baseUrl))
            .putString(KeyModel, settings.profile.defaultModel)
            .putString(KeyProviderType, settings.profile.providerType.name)
            .putString(KeyEndpointStyle, settings.profile.endpointStyle.name)
            .putString(KeyStructuredOutputMode, settings.profile.structuredOutputMode.name)
            .putString(KeyInputMode, settings.profile.inputMode.name)
            .putBoolean(KeyImage, settings.profile.capabilities.supportsImageInput)
            .putBoolean(KeyPdf, settings.profile.capabilities.supportsPdfFileInput)
            .putBoolean(KeyJsonSchema, settings.profile.capabilities.supportsJsonSchema)
            .putBoolean(KeyJsonMode, settings.profile.capabilities.supportsJsonMode)
            .putBoolean(KeyFileUpload, settings.profile.capabilities.supportsFileUpload)
            .putBoolean(KeyVision, settings.profile.supportsVision)
            .putBoolean(KeyPdfDirect, settings.profile.supportsPdfDirect)
        writeProviderSettings(editor, settings)
        if (settings.apiKey.isBlank()) {
            editor.remove(apiKeyKey(settings.profile.id))
        } else {
            editor.putString(apiKeyKey(settings.profile.id), encrypt(context, settings.apiKey))
        }
        editor
            .apply()
    }

    fun saveProvider(context: Context, settings: AiImportSettings) {
        val editor = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE).edit()
        writeProviderSettings(editor, settings)
        if (settings.apiKey.isNotBlank()) {
            editor.putString(apiKeyKey(settings.profile.id), encrypt(context, settings.apiKey))
        }
        editor.apply()
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
            .putBoolean(providerKey(KeyVision, id), profile.supportsVision)
            .putBoolean(providerKey(KeyPdfDirect, id), profile.supportsPdfDirect)
    }

    fun clearApiKey(context: Context, providerId: String? = null) {
        val prefs = context.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        val currentProviderId = providerId ?: prefs.getString(KeyProviderId, AiProviderPresets.none.id).orEmpty()
        context.getSharedPreferences(PrefName, Context.MODE_PRIVATE).edit()
            .remove(apiKeyKey(currentProviderId))
            .apply()
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
            if (url.equals("https://api.deepseek.com", ignoreCase = true)) "$url/v1" else url
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
    suspend fun parseScheduleFile(file: AiImportFile, settings: AiImportSettings): Result<AiScheduleImportResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                require(settings.apiKey.isNotBlank()) { "请先在设置中配置 AI API Key" }
                require(settings.profile.baseUrl.isNotBlank()) { "请先配置接口地址" }
                require(settings.profile.defaultModel.isNotBlank()) { "请先配置模型名称" }
                require(file.bytes.size <= 20 * 1024 * 1024) { "文件不能超过 20MB" }
                val config = settings.toProviderConfig().normalizedForRequest()
                val preprocess = DefaultScheduleFilePreprocessor(context).preprocess(file, config)
                val input = preprocess.toScheduleInput(file)
                val result = when {
                    config.endpointStyle == AiEndpointStyle.RESPONSES &&
                        config.providerId == AiProviderPresets.openAI.id -> OpenAiResponsesProvider().parseSchedule(config, input)
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

    suspend fun parseScheduleText(text: String, sourceName: String, settings: AiImportSettings): Result<AiScheduleImportResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                require(settings.apiKey.isNotBlank()) { "请先在设置中配置 AI API Key" }
                require(settings.profile.baseUrl.isNotBlank()) { "请先配置接口地址" }
                require(settings.profile.defaultModel.isNotBlank()) { "请先配置模型名称" }
                val cleaned = text.trim().take(60_000)
                require(cleaned.count { !it.isWhitespace() } >= 40) { "当前页面可提取文本太少，请确认已经进入课表页面" }
                val config = settings.toProviderConfig().normalizedForRequest()
                val input = AiScheduleInput.ExtractedText(cleaned, sourceName)
                val result = when {
                    config.endpointStyle == AiEndpointStyle.RESPONSES &&
                        config.providerId == AiProviderPresets.openAI.id -> OpenAiResponsesProvider().parseSchedule(config, input)
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
        settings: AiImportSettings
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
                val result = when {
                    config.endpointStyle == AiEndpointStyle.RESPONSES &&
                        config.providerId == AiProviderPresets.openAI.id -> OpenAiResponsesProvider().parseSchedule(config, input)
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
}

suspend fun testAiProviderConnection(settings: AiImportSettings): Result<String> {
    return withContext(Dispatchers.IO) {
        runCatching {
            require(settings.apiKey.isNotBlank()) { "请先配置 AI API Key" }
            require(settings.profile.baseUrl.isNotBlank()) { "请先配置接口地址" }
            require(settings.profile.defaultModel.isNotBlank()) { "请先配置模型名称" }
            val config = settings.toProviderConfig().normalizedForRequest().copy(endpointStyle = AiEndpointStyle.CHAT_COMPLETIONS)
            val body = buildJsonObject {
                put("model", JsonPrimitive(config.model))
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("content", JsonPrimitive("请只回复 OK"))
                    })
                })
                put("temperature", JsonPrimitive(0.1))
                if (config.providerId == AiProviderPresets.mimo.id || config.providerId == AiProviderPresets.mimoTokenPlan.id) {
                    put("max_completion_tokens", JsonPrimitive(32))
                } else {
                    put("max_tokens", JsonPrimitive(32))
                }
            }
            val response = postJson(config.baseUrl.trimEnd('/') + "/chat/completions", config.apiKey, body.toString(), config.authType)
            "连接测试成功\n" + response.compactForSettingsResult()
        }
    }
}

suspend fun diagnoseAiProviderNetwork(settings: AiImportSettings): Result<String> {
    return withContext(Dispatchers.IO) {
        runCatching {
            require(settings.profile.baseUrl.isNotBlank()) { "请先配置接口地址" }
            require(settings.profile.defaultModel.isNotBlank()) { "请先配置模型名称" }
            val config = settings.toProviderConfig().normalizedForRequest().copy(endpointStyle = AiEndpointStyle.CHAT_COMPLETIONS)
            val endpoint = config.baseUrl.trimEnd('/') + "/chat/completions"
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
                result.appendLine("Chat 测试：跳过，未配置 API Key")
            } else {
                testAiProviderConnection(settings)
                    .onSuccess { result.appendLine("Chat 测试：成功") }
                    .onFailure { result.appendLine("Chat 测试：失败，${it.message.orEmpty()}") }
            }
            result.toString().trim()
        }
    }
}

private suspend fun sendDeepSeekChatMessage(
    context: Context,
    messages: List<AiChatTurn>,
    thinkingEnabled: Boolean
): Result<AiProviderTextResult> {
    return withContext(Dispatchers.IO) {
        runCatching {
            val settings = AiImportSettingsStore.loadProvider(context, AiProviderPresets.deepSeek.id)
            require(settings.apiKey.isNotBlank()) { "请先在 AI 设置中配置 DeepSeek API Key" }
            val config = settings.toProviderConfig().normalizedForRequest().copy(
                providerId = AiProviderPresets.deepSeek.id,
                endpointStyle = AiEndpointStyle.CHAT_COMPLETIONS
            )
            require(config.model.isNotBlank()) { "请先配置 DeepSeek 模型名称" }
            val body = buildJsonObject {
                put("model", JsonPrimitive(config.model))
                put("messages", buildJsonArray {
                    messages.forEach { message ->
                        add(buildJsonObject {
                            put("role", JsonPrimitive(message.role))
                            put("content", JsonPrimitive(message.content))
                        })
                    }
                })
                put("thinking", buildJsonObject {
                    put("type", JsonPrimitive(if (thinkingEnabled) "enabled" else "disabled"))
                    if (thinkingEnabled) put("reasoning_effort", JsonPrimitive("high"))
                })
                put("max_tokens", JsonPrimitive(if (thinkingEnabled) 4096 else 2048))
            }
            val response = postJson(config.baseUrl.trimEnd('/') + "/chat/completions", config.apiKey, body.toString(), config.authType)
            parseChatCompletionTextResult(response, requireContent = false)
        }
    }
}

private fun currentDeepSeekChatLabel(context: Context): String {
    val settings = AiImportSettingsStore.loadProvider(context, AiProviderPresets.deepSeek.id)
    val baseUrl = normalizeAiBaseUrlForProvider(AiProviderPresets.deepSeek.id, settings.profile.baseUrl)
    return "${settings.profile.defaultModel} · $baseUrl"
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
        supportsVision = profile.supportsVision || profile.capabilities.supportsImageInput,
        supportsFileUpload = profile.supportsFileUpload || profile.capabilities.supportsFileUpload,
        supportsPdfDirect = profile.supportsPdfDirect || profile.capabilities.supportsPdfFileInput,
        inputMode = profile.inputMode,
        authType = profile.authType
    )
}

private fun AiProviderConfig.normalizedForRequest(): AiProviderConfig {
    val normalizedBaseUrl = normalizeAiBaseUrlForProvider(providerId, baseUrl)
    val officialOpenAI = providerId == AiProviderPresets.openAI.id && isOfficialOpenAIBaseUrl(normalizedBaseUrl)
    val useResponses = officialOpenAI &&
        endpointStyle == AiEndpointStyle.RESPONSES &&
        inputMode != AiInputMode.TEXT_ONLY &&
        inputMode != AiInputMode.IMAGE_URL_BASE64
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
        supportsFileUpload = useResponses && supportsFileUpload,
        authType = if (isMimo) AiAuthType.CustomHeader else authType
    )
}

private fun isOfficialOpenAIBaseUrl(value: String): Boolean {
    val url = value.trim().trimEnd('/')
    return url.equals("https://api.openai.com/v1", ignoreCase = true)
}

interface ScheduleFilePreprocessor {
    suspend fun preprocess(file: AiImportFile, config: AiProviderConfig): PreprocessResult
}

private class DefaultScheduleFilePreprocessor(private val context: Context) : ScheduleFilePreprocessor {
    override suspend fun preprocess(file: AiImportFile, config: AiProviderConfig): PreprocessResult {
        return when {
            file.isText -> PreprocessResult.Text(
                text = file.bytes.toString(Charsets.UTF_8),
                routeMessage = "已读取文本文件，使用文本模型解析。"
            )
            file.isPdf -> preprocessPdf(file, config)
            file.isImage -> {
                require(config.inputMode != AiInputMode.TEXT_ONLY) { "当前输入模式为仅本地文本，无法解析图片课表。" }
                require(config.supportsVision || config.inputMode == AiInputMode.FILE_UPLOAD_EXTRACT) {
                    "当前模型不支持视觉输入，请换视觉模型，或上传可复制文字的 TXT/CSV。"
                }
                PreprocessResult.Images(
                    images = listOf(file.toRenderedImage(0)),
                    routeMessage = "图片课表将交给视觉模型解析。"
                )
            }
            else -> error("暂不支持该文件类型，请使用 PDF、图片、TXT 或 CSV。")
        }
    }

    private fun preprocessPdf(file: AiImportFile, config: AiProviderConfig): PreprocessResult {
        if (
            config.supportsPdfDirect &&
            config.supportsFileUpload &&
            config.inputMode != AiInputMode.TEXT_ONLY &&
            config.inputMode != AiInputMode.IMAGE_URL_BASE64
        ) {
            return PreprocessResult.Raw(file, file.bytes, "使用原生 PDF 文件输入解析。")
        }
        val extracted = extractPdfTextBestEffort(file.bytes)
        if (
            config.inputMode != AiInputMode.IMAGE_URL_BASE64 &&
            extracted.length >= 80 &&
            extracted.count { !it.isWhitespace() } >= 40
        ) {
            return PreprocessResult.Text(extracted, "已从 PDF 提取文本，使用文本模型解析。")
        }
        require(config.inputMode != AiInputMode.TEXT_ONLY) {
            "PDF 文本提取结果不足，当前输入模式禁止视觉解析。"
        }
        require(config.supportsVision) {
            "PDF 文本提取结果不足，且当前模型不支持视觉输入，请换视觉模型或上传文本版课表。"
        }
        return PreprocessResult.Images(
            images = renderPdfPageImages(context, file),
            routeMessage = "PDF 文本不足，已转为图片交给视觉模型解析。"
        )
    }
}

private fun PreprocessResult.toScheduleInput(file: AiImportFile): AiScheduleInput {
    return when (this) {
        is PreprocessResult.Text -> AiScheduleInput.ExtractedText(text, file.displayName)
        is PreprocessResult.Images -> AiScheduleInput.Images(images, file.displayName)
        is PreprocessResult.Raw -> AiScheduleInput.RawFile(file.mimeType, file.displayName, bytes)
    }
}

private interface AiScheduleImportProvider {
    fun parseSchedule(config: AiProviderConfig, input: AiScheduleInput): AiProviderTextResult
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
                addTextPart(aiSchedulePrompt() + "\n\n下面图片来自同一份课表文件。若图片是长图或拼接图，请把接缝和重复区域当作上下文校对，不要重复生成课程。请综合整张图尽力还原完整课程信息。")
                input.images.forEach { image -> addImagePart(image.dataUrl) }
            }
            is AiScheduleInput.CapturedPage -> buildJsonArray {
                addTextPart(
                    aiSchedulePrompt() +
                        "\n\n这是从教务 WebView 分层抓取的页面内容。DOM 文本可能包含导航、版权、重复表格或缺失字段；截图为当前页面可见渲染结果。" +
                        "\n输入文本开头会提供当前教务页面地址。请根据该网址识别学校；如果模型具备联网或内置检索能力，可以参考该学校公开的校历、作息时间、节次时间或排课安排来校对时间。" +
                        "\n截图来自同一张课表页面，可能是由连续重叠截图拼成的长图；接缝和重复区域只用于校对上下文，不要把同一课程重复输出。请尽力读取并还原完整课程信息。" +
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
            when (config.structuredOutputMode) {
                StructuredOutputMode.JSON_SCHEMA -> put("response_format", buildJsonObject {
                    put("type", JsonPrimitive("json_schema"))
                    put("json_schema", scheduleJsonSchema())
                })
                StructuredOutputMode.JSON_OBJECT -> put("response_format", buildJsonObject {
                    put("type", JsonPrimitive("json_object"))
                })
                StructuredOutputMode.PROMPT_ONLY -> Unit
            }
            put("temperature", JsonPrimitive(0.1))
            if (config.providerId == AiProviderPresets.deepSeek.id) {
                put("thinking", buildJsonObject {
                    put("type", JsonPrimitive("enabled"))
                    put("reasoning_effort", JsonPrimitive("high"))
                })
            }
            putChatOutputBudget(config)
        }
        val response = postJson(config.baseUrl.trimEnd('/') + "/chat/completions", config.apiKey, body.toString(), config.authType)
        val result = runCatching {
            parseChatCompletionTextResult(response)
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
                put("temperature", JsonPrimitive(0.1))
                if (config.providerId == AiProviderPresets.deepSeek.id) {
                    put("thinking", buildJsonObject {
                        put("type", JsonPrimitive("enabled"))
                        put("reasoning_effort", JsonPrimitive("high"))
                    })
                }
                putChatOutputBudget(config)
            }
            val response = postJson(config.baseUrl.trimEnd('/') + "/chat/completions", config.apiKey, body.toString(), config.authType)
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
                                    "截图来自同一张课表页面，可能是连续重叠截图拼成的长图；接缝和重复区域只用于校对上下文，不要重复生成课程。\n" +
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
            put("input", buildJsonArray {
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", content)
                })
            })
            if (config.structuredOutputMode == StructuredOutputMode.JSON_SCHEMA) {
                put("text", buildJsonObject { put("format", scheduleJsonSchemaFormat()) })
            }
        }
        return AiProviderTextResult(
            content = extractResponsesText(postJson(config.baseUrl.trimEnd('/') + "/responses", config.apiKey, body.toString(), config.authType))
        )
    }
}

private interface AiProviderAdapter {
    fun parse(context: Context, file: AiImportFile, settings: AiImportSettings): String
}

private class OpenAIResponsesAdapter : AiProviderAdapter {
    override fun parse(context: Context, file: AiImportFile, settings: AiImportSettings): String {
        val profile = settings.profile
        val inputContent = buildJsonArray {
            add(buildJsonObject {
                put("type", JsonPrimitive("input_text"))
                put("text", JsonPrimitive(aiSchedulePrompt()))
            })
            when {
                file.isPdf && !(profile.capabilities.supportsPdfFileInput && profile.capabilities.supportsFileUpload) && profile.capabilities.supportsImageInput -> {
                    renderPdfPagesForAi(context, file).forEach { dataUrl ->
                        add(buildJsonObject {
                            put("type", JsonPrimitive("input_image"))
                            put("image_url", JsonPrimitive(dataUrl))
                        })
                    }
                }
                file.isPdf -> {
                    require(profile.capabilities.supportsPdfFileInput && profile.capabilities.supportsFileUpload) {
                        "当前模型配置不支持 PDF 文件解析，请换支持文件输入的模型，或导出图片后使用图片导入"
                    }
                    val fileId = uploadFile(profile, settings.apiKey, file)
                    add(buildJsonObject {
                        put("type", JsonPrimitive("input_file"))
                        put("file_id", JsonPrimitive(fileId))
                    })
                }
                file.isImage -> {
                    require(profile.capabilities.supportsImageInput) { "当前模型配置不支持图片解析，请换多模态模型" }
                    add(buildJsonObject {
                        put("type", JsonPrimitive("input_image"))
                        put("image_url", JsonPrimitive(file.toDataUrl()))
                    })
                }
                file.isText -> {
                    add(buildJsonObject {
                        put("type", JsonPrimitive("input_text"))
                        put("text", JsonPrimitive(file.bytes.toString(Charsets.UTF_8)))
                    })
                }
                else -> error("暂不支持该文件类型，请使用 PDF、图片、TXT 或 CSV")
            }
        }
        val body = buildJsonObject {
            put("model", JsonPrimitive(profile.defaultModel))
            put("input", buildJsonArray {
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", inputContent)
                })
            })
            if (profile.capabilities.supportsJsonSchema) {
                put("text", buildJsonObject {
                    put("format", scheduleJsonSchemaFormat())
                })
            }
        }
        return extractResponsesText(postJson(profile.baseUrl.trimEnd('/') + profile.responsesPath, settings.apiKey, body.toString(), profile.authType))
    }

    private fun uploadFile(profile: AiProviderProfile, apiKey: String, file: AiImportFile): String {
        val boundary = "SleepDownBoundary${System.currentTimeMillis()}"
        val lineEnd = "\r\n"
        val body = ByteArrayOutputStream()
        fun write(value: String) = body.write(value.toByteArray(Charsets.UTF_8))
        write("--$boundary$lineEnd")
        write("Content-Disposition: form-data; name=\"purpose\"$lineEnd$lineEnd")
        write("user_data$lineEnd")
        write("--$boundary$lineEnd")
        write("Content-Disposition: form-data; name=\"file\"; filename=\"${file.displayName}\"$lineEnd")
        write("Content-Type: ${file.mimeType}$lineEnd$lineEnd")
        body.write(file.bytes)
        write(lineEnd)
        write("--$boundary--$lineEnd")
        val response = safeRequest(
            url = profile.baseUrl.trimEnd('/') + profile.filesPath,
            apiKey = apiKey,
            method = "POST",
            body = body.toByteArray(),
            contentType = "multipart/form-data; boundary=$boundary"
        )
        return Json.parseToJsonElement(response).jsonObject["id"]?.jsonPrimitive?.contentOrNull
            ?: error("文件上传成功但没有返回 file id")
    }
}

private class OpenAIChatCompletionsAdapter : AiProviderAdapter {
    override fun parse(context: Context, file: AiImportFile, settings: AiImportSettings): String {
        val profile = settings.profile
        if (file.isPdf && !profile.capabilities.supportsImageInput) {
            error("当前模型配置不支持 PDF 文件解析，请换 OpenAI/支持文件输入的模型，或导出图片后使用图片导入")
        }
        val userContent: JsonElement = when {
            file.isPdf -> {
                buildJsonArray {
                    add(buildJsonObject {
                        put("type", JsonPrimitive("text"))
                        put("text", JsonPrimitive(aiSchedulePrompt() + "\n\n下面是 PDF 渲染得到的页面图片，请按页面顺序识别课表。"))
                    })
                    renderPdfPagesForAi(context, file).forEach { dataUrl ->
                        add(buildJsonObject {
                            put("type", JsonPrimitive("image_url"))
                            put("image_url", buildJsonObject {
                                put("url", JsonPrimitive(dataUrl))
                            })
                        })
                    }
                }
            }
            file.isImage -> {
                require(profile.capabilities.supportsImageInput) { "当前模型配置不支持图片解析，请换多模态模型" }
                buildJsonArray {
                    add(buildJsonObject {
                        put("type", JsonPrimitive("text"))
                        put("text", JsonPrimitive(aiSchedulePrompt()))
                    })
                    add(buildJsonObject {
                        put("type", JsonPrimitive("image_url"))
                        put("image_url", buildJsonObject {
                            put("url", JsonPrimitive(file.toDataUrl()))
                        })
                    })
                }
            }
            file.isText -> JsonPrimitive(aiSchedulePrompt() + "\n\n课表文件内容：\n" + file.bytes.toString(Charsets.UTF_8))
            else -> error("暂不支持该文件类型，请使用 PDF、图片、TXT 或 CSV")
        }
        val body = buildJsonObject {
            put("model", JsonPrimitive(profile.defaultModel))
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", JsonPrimitive("system"))
                    put("content", JsonPrimitive("你是课程表结构化助手，只输出可解析的 JSON，不要输出解释文字。"))
                })
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", userContent)
                })
            })
            if (profile.capabilities.supportsJsonSchema) {
                put("response_format", buildJsonObject {
                    put("type", JsonPrimitive("json_schema"))
                    put("json_schema", scheduleJsonSchema())
                })
            } else if (profile.capabilities.supportsJsonMode) {
                put("response_format", buildJsonObject {
                    put("type", JsonPrimitive("json_object"))
                })
            }
            put("temperature", JsonPrimitive(0.1))
        }
        val response = postJson(profile.baseUrl.trimEnd('/') + profile.chatCompletionsPath, settings.apiKey, body.toString(), profile.authType)
        return Json.parseToJsonElement(response)
            .jsonObject["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            ?: error("AI 没有返回课程表内容")
    }
}

suspend fun loadAiImportFile(context: Context, uri: Uri): Result<AiImportFile> =
    withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val name = resolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "schedule-file"
            val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("无法读取文件")
            val bytes = if (mime.startsWith("image/", ignoreCase = true)) {
                compressAiImportImage(rawBytes)
            } else {
                rawBytes
            }
            AiImportFile(uri, name, mime, bytes)
        }
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
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
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

private fun extractPdfTextBestEffort(bytes: ByteArray): String {
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

private fun renderPdfPagesForAi(context: Context, file: AiImportFile, maxPages: Int = 6): List<String> {
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
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val bytes = ByteArrayOutputStream().use { output ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 86, output)
                            bitmap.recycle()
                            output.toByteArray()
                        }
                        "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                    }
                }
            }
        }
    } finally {
        temp.delete()
    }
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
    authType: AiAuthType = AiAuthType.ApiKeyBearer
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
            throw AiServiceResponseException(formatAiRequestError(status, text), text)
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

private fun HttpURLConnection.setAiAuthHeader(apiKey: String, authType: AiAuthType) {
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

private fun formatAiRequestError(status: Int, text: String): String {
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

private fun postJson(url: String, apiKey: String, body: String, authType: AiAuthType = AiAuthType.ApiKeyBearer): String {
    return safeRequest(url, apiKey, "POST", body.toByteArray(Charsets.UTF_8), "application/json", authType)
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

private fun extractResponsesText(response: String): String {
    val root = Json.parseToJsonElement(response).jsonObject
    root["output_text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
    val output = root["output"]?.jsonArray.orEmpty()
    output.forEach { item ->
        item.jsonObject["content"]?.jsonArray.orEmpty().forEach { content ->
            val obj = content.jsonObject
            obj["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
        }
    }
    error("AI 没有返回课程表内容")
}

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
        else -> emptyList()
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
${SchedulePromptBuilder.build()}

补充要求：
1. 如果页面中没有明确给出节次时间，请根据可见节次生成合理的 periods，并保持 index 连续。
2. 如果同一课程有多个不连续周次，请把 weeks 展开成整数数组，不要写范围字符串。
3. 只能返回最外层 JSON 对象，不要返回数组、代码块、解释文字或 Markdown。
3.1 JSON 可以使用正常的缩进和换行，但不要在课程名、教师、地点、备注、时间等字符串字段中为了排版插入额外换行；同一字段必须保持为一个完整字符串。
4. 在生成 periods 前，请先分析课表表格结构，识别星期列、节次行、时间轴、课程块跨行/跨节范围和午晚间分区。优先使用图片或表格中真实可见的时间；如果页面包含学校网址、学校名或教务系统域名，并且模型具备联网或内置检索能力，可以参考该学校公开作息时间来校对节次时间。
5. 所有节次时间都应理解为同一天内的时间轴。不要因为晚课、页面换行、长图接缝或表格分段，就把课程时间推到“下一天”。如果时间无法确认，请保持节次 index 连续，并在 note 中说明“时间需手动核对”，不要编造跨日时间。
6. 如果课程信息写着“未安排讲课”“待定”“其它课程”，或者没有明确教室时间、星期、节次、周次，不要丢弃该课程；请输出占位课程：weekday 使用 7，periods 使用 [1]，weeks 使用 [1]，weekParity 使用 "ALL"，并在 note 中写入“AI 占位课程：原始数据没有明确上课星期/节次/周次，请手动修改。”。绝对不要输出空的 periods 或空的 weeks。
7. 如果来源名称包含“AI 兜底扒页”，说明输入来自 WebView 整页强制抓取，可能包含导航、版权、重复表格、表单状态、iframe 诊断或无关页面控件。只提取真实课表和课程信息；不确定的字段写入 note，不要编造精确值。
8. scheduleConfig.periods 必须完整列出实际使用到的每一个节次边界。即使相邻两节课程连续，也不能合并成一个大时间段；每个 index 只能对应一节课的开始和结束时间。
""".trimIndent()

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
    put("required", JsonArray(listOf(JsonPrimitive("schemaVersion"), JsonPrimitive("scheduleConfig"), JsonPrimitive("courses"))))
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
    })
}

private fun nullableStringSchema(): JsonObject = buildJsonObject {
    put("type", JsonArray(listOf(JsonPrimitive("string"), JsonPrimitive("null"))))
}

private fun integerArraySchema(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("array"))
    put("items", buildJsonObject { put("type", JsonPrimitive("integer")) })
}
