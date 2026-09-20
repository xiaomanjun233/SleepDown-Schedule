package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.core.remoteconfig.*
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.backup.*
import android.net.Uri
import android.util.Base64
import kotlinx.serialization.Serializable
import java.io.File



internal const val AiImportLogTag = "AiImport"
internal const val MaxAiImportFileBytes = 20 * 1024 * 1024

enum class AiEndpointStyle {
    CHAT_COMPLETIONS,
    RESPONSES,
    KIMI_FILE_EXTRACT
}

enum class AiImportHttpPhase {
    REQUEST_CREATED,
    BODY_WRITE_START,
    BODY_WRITE_END,
    HEADERS_RECEIVED,
    FIRST_EVENT,
    BODY_READ_START,
    STREAM_END
}

internal data class AiImportNetworkContext(
    val inputType: String,
    val imageCount: Int = 0,
    val screenshotCount: Int = 0,
    val onPhase: (AiImportHttpPhase) -> Unit = {},
    val processImportanceProvider: () -> Int? = { null },
    val onReasoningUpdate: ((String) -> Unit)? = null
)

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
    val authType: AiAuthType = AiAuthType.ApiKeyBearer,
    val responsesPath: String = "/responses",
    val chatCompletionsPath: String = "/chat/completions"
) {
    /**
     * 解析实际请求端点。仅当显式配置了非空路径时才追加；路径为空时直接使用 baseUrl，
     * 以便后端下发的完整地址按原样使用，而不是被强制补上 /responses 等后缀。
     */
    fun resolveRequestEndpoint(): String {
        val base = baseUrl.trim().trimEnd('/')
        val path = (if (endpointStyle == AiEndpointStyle.RESPONSES) responsesPath else chatCompletionsPath).trim('/')
        return if (path.isEmpty()) base else "$base/$path"
    }
}

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

