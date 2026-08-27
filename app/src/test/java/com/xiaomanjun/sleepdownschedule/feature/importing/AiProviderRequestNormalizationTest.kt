package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderRequestNormalizationTest {
    @Test
    fun managedFreeQuotaErrorsUseSharedPoolMessage() {
        val message = formatAiRequestError(
            429,
            "{\"error\":{\"code\":\"model_limit_exceeded\"}}",
            AiProviderPresets.dailyFree.id
        )

        assertEquals(
            "今日免费 AI 共享额度已用完，请明天再试，或在 AI 设置中配置自己的 AI 服务。",
            message
        )
    }

    @Test
    fun userProvidersKeepTheirOriginalQuotaDiagnostics() {
        val message = formatAiRequestError(429, "rate_limit_exceeded", AiProviderPresets.openAI.id)

        assertTrue(message.startsWith("AI 请求失败 (429)"))
    }

    @Test
    fun compatibleChatEndpointKeepsFileUploadForPdfImageConversion() {
        val normalized = providerConfig(
            providerId = "custom-compatible",
            baseUrl = "https://example.com/v1",
            endpointStyle = AiEndpointStyle.CHAT_COMPLETIONS
        ).normalizedForRequest()

        assertEquals(AiEndpointStyle.CHAT_COMPLETIONS, normalized.endpointStyle)
        assertTrue(normalized.supportsFileUpload)
        assertFalse(normalized.supportsPdfDirect)
    }

    @Test
    fun officialOpenAiResponsesKeepsNativePdfCapabilities() {
        val normalized = providerConfig(
            providerId = AiProviderPresets.openAI.id,
            baseUrl = "https://api.openai.com/v1",
            endpointStyle = AiEndpointStyle.RESPONSES
        ).normalizedForRequest()

        assertEquals(AiEndpointStyle.RESPONSES, normalized.endpointStyle)
        assertTrue(normalized.supportsFileUpload)
        assertTrue(normalized.supportsPdfDirect)
    }

    @Test
    fun compatibleResponsesEndpointIsNotForcedBackToChat() {
        val normalized = providerConfig(
            providerId = "custom-compatible",
            baseUrl = "https://example.com/v1",
            endpointStyle = AiEndpointStyle.RESPONSES,
            supportsResponses = true
        ).normalizedForRequest()

        assertEquals(AiEndpointStyle.RESPONSES, normalized.endpointStyle)
        assertTrue(normalized.supportsFileUpload)
        assertTrue(normalized.supportsPdfDirect)
    }

    @Test
    fun backendPublishedResponsesUrlIsNormalizedBeforeAppendingEndpoint() {
        assertEquals(
            "https://api.example.com/v1",
            normalizeAiBaseUrlForProvider(
                AiProviderPresets.dailyFree.id,
                "https://api.example.com/v1/responses"
            )
        )
    }

    @Test
    fun deepSeekResponsesReasoningTextDoesNotLeakIntoVisibleResult() {
        val result = parseResponsesTextResult(
            """{
              "status":"completed",
              "output":[
                {"type":"reasoning","content":[{"type":"reasoning_text","text":"内部推理"}]},
                {"type":"message","content":[{"type":"output_text","text":"最终结果"}]}
              ]
            }""".trimIndent()
        )

        assertEquals("最终结果", result.content)
        assertEquals("内部推理", result.reasoning)
    }

    @Test
    fun officialDeepSeekBaseUrlDropsLegacyV1Suffix() {
        assertEquals(
            "https://api.deepseek.com",
            normalizeAiBaseUrlForProvider(AiProviderPresets.deepSeek.id, "https://api.deepseek.com/v1")
        )
    }

    private fun providerConfig(
        providerId: String,
        baseUrl: String,
        endpointStyle: AiEndpointStyle,
        supportsResponses: Boolean = endpointStyle == AiEndpointStyle.RESPONSES
    ) = AiProviderConfig(
        providerId = providerId,
        displayName = "test",
        apiKey = "key",
        baseUrl = baseUrl,
        model = "vision-model",
        endpointStyle = endpointStyle,
        structuredOutputMode = StructuredOutputMode.PROMPT_ONLY,
        supportsVision = true,
        supportsFileUpload = true,
        supportsPdfDirect = true,
        supportsResponses = supportsResponses,
        inputMode = AiInputMode.RESPONSES_FILE,
        reasoningEffort = AiReasoningEffort.MEDIUM
    )
}
