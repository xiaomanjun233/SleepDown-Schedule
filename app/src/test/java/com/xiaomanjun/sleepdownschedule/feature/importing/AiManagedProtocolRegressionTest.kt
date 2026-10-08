package com.xiaomanjun.sleepdownschedule.feature.importing

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class AiManagedProtocolRegressionTest {
    private fun config(provider: String = AiProviderPresets.dailyFree.id) = AiProviderConfig(
        provider, "test", "placeholder", "https://token-plan-cn.xiaomimimo.com/v1",
        "mimo-v2.5-pro", AiEndpointStyle.CHAT_COMPLETIONS, StructuredOutputMode.JSON_SCHEMA,
        true, false, false, true, AiInputMode.AUTO, AiReasoningEffort.HIGH,
        responsesPath = "", chatCompletionsPath = ""
    )

    @Test fun managedRegionsKeepBackendEndpointAndProtocol() {
        for (region in listOf("cn", "sgp", "ams")) {
            val root = "https://token-plan-$region.xiaomimimo.com/v1"
            val c = config().copy(baseUrl = root).normalizedForRequest()
            assertEquals(root, c.resolveRequestEndpoint())
            assertEquals(root, c.copy(endpointStyle = AiEndpointStyle.RESPONSES).resolveRequestEndpoint())
            assertEquals(AiEndpointStyle.CHAT_COMPLETIONS, c.endpointStyle)
            assertEquals(AiAuthType.CustomHeader, c.authType)
            assertEquals(StructuredOutputMode.PROMPT_ONLY, c.structuredOutputMode)
        }
    }

    @Test fun fullManagedEndpointsAndProxiesArePreserved() {
        for (url in listOf("https://token-plan-cn.xiaomimimo.com/v1/responses",
            "https://gateway.example/v1", "https://token-plan-cn.xiaomimimo.com.evil.example/v1")) {
            assertEquals(url, config().copy(baseUrl = url).resolveRequestEndpoint())
        }
        assertFalse(config().copy(baseUrl = "https://token-plan-cn.xiaomimimo.com.evil.example/v1").usesMimoProtocol())
    }

    @Test fun managedProviderSwitchDropsCachedMimoAuthentication() {
        for (endpoint in listOf(AiEndpointStyle.CHAT_COMPLETIONS, AiEndpointStyle.RESPONSES)) {
            val path = if (endpoint == AiEndpointStyle.RESPONSES) "responses" else "chat/completions"
            val url = "https://gateway.example/v1/$path"
            val c = config().copy(
                baseUrl = url, model = "managed-model", endpointStyle = endpoint,
                authType = AiAuthType.CustomHeader
            ).normalizedForRequest()
            assertEquals(AiAuthType.ApiKeyBearer, managedAiAuthType(url))
            assertEquals(AiAuthType.ApiKeyBearer, c.authType)
            assertEquals(endpoint, c.endpointStyle)
            assertEquals(url, c.resolveRequestEndpoint())
        }
    }

    @Test fun managedMimoAuthRequiresAnExactOfficialHttpsHost() {
        for (host in listOf("api", "token-plan-cn", "token-plan-sgp", "token-plan-ams")) {
            assertEquals(AiAuthType.CustomHeader, managedAiAuthType("https://$host.xiaomimimo.com/v1/responses"))
        }
        for (url in listOf("https://api.xiaomimimo.com.evil.example/v1/responses",
            "https://gateway.example/api.xiaomimimo.com", "http://api.xiaomimimo.com/v1", "")) {
            assertEquals(AiAuthType.ApiKeyBearer, managedAiAuthType(url))
        }
    }

    @Test fun customProviderAuthenticationRemainsExplicit() {
        val c = config("custom:own-gateway").copy(baseUrl = "https://gateway.example/endpoint",
            authType = AiAuthType.CustomHeader).normalizedForRequest()
        assertEquals(AiAuthType.CustomHeader, c.authType)
        assertEquals("https://gateway.example/endpoint", c.resolveRequestEndpoint())
    }

    @Test fun thinkingImportAndPatchUseAutoForBothProtocols() {
        for (endpoint in listOf(AiEndpointStyle.CHAT_COMPLETIONS, AiEndpointStyle.RESPONSES)) {
            val c = config(AiProviderPresets.deepSeek.id).copy(endpointStyle = endpoint)
            for (tool in listOf(ScheduleImportToolName, SchedulePatchToolName)) {
                assertEquals(if (endpoint == AiEndpointStyle.CHAT_COMPLETIONS) null else JsonPrimitive("auto"), scheduleToolChoice(c, tool))
                val forced = scheduleToolChoice(c.copy(reasoningEffort = AiReasoningEffort.NONE), tool)!!.jsonObject
                assertEquals(JsonPrimitive("function"), forced["type"])
                val name = if (endpoint == AiEndpointStyle.RESPONSES) forced["name"] else forced["function"]!!.jsonObject["name"]
                assertEquals(JsonPrimitive(tool), name)
            }
        }
    }

    @Test fun otherProvidersStillForceRequestedTool() {
        assertEquals(JsonPrimitive("function"), scheduleToolChoice(config("custom"), ScheduleImportToolName)!!.jsonObject["type"])
    }

    @Test fun mimoNullableStreamingArraysPreserveTextAndReasoning() {
        val stream = ChatCompletionSseAccumulator()
        stream.consume("""{"choices":null,"usage":{"total_tokens":1}}""")
        stream.consume("""{"choices":[{"delta":{"content":null,"reasoning_content":"thinking","tool_calls":null,"function_call":null}}]}""")
        stream.consume("""{"choices":[{"delta":{"content":"OK","tool_calls":null},"finish_reason":"stop"}]}""")
        stream.consume("""{"choices":[],"usage":{"total_tokens":4}}""")
        val parsed = parseChatCompletionTextResult(stream.toCompletionJson())
        assertEquals("OK", parsed.content)
        assertEquals("thinking", parsed.reasoning)
    }

    @Test fun malformedStreamingArrayIsResponseErrorNotIgnored() {
        val error = runCatching {
            ChatCompletionSseAccumulator().consume("""{"choices":[{"delta":{"tool_calls":{}}}]}""")
        }.exceptionOrNull()
        assertTrue(error is AiServiceResponseException)
        assertTrue(error!!.message.orEmpty().contains("tool_calls"))
        assertFalse(error.message.orEmpty().contains("无法连接"))
    }

    @Test fun streamingToolFragmentsRemainIntact() {
        val stream = ChatCompletionSseAccumulator()
        stream.consume("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"submit","arguments":"{"}}]}}]}""")
        stream.consume("""{"choices":[{"delta":{"tool_calls":null}}]}""")
        stream.consume("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"}"}}]},"finish_reason":"tool_calls"}]}""")
        val root = kotlinx.serialization.json.Json.parseToJsonElement(stream.toCompletionJson()).jsonObject
        val choices = root["choices"] as kotlinx.serialization.json.JsonArray
        val calls = choices[0].jsonObject["message"]!!.jsonObject["tool_calls"] as kotlinx.serialization.json.JsonArray
        assertEquals(JsonPrimitive("{}"), calls[0].jsonObject["function"]!!.jsonObject["arguments"])
    }

    @Test fun managedMimoResponsesUsesDocumentedParametersWithoutRewritingAddress() {
        val c = config().copy(endpointStyle = AiEndpointStyle.RESPONSES, reasoningEffort = AiReasoningEffort.MAX)
        assertEquals(JsonPrimitive("auto"), scheduleToolChoice(c, ScheduleImportToolName))
        val body = kotlinx.serialization.json.buildJsonObject { putResponsesReasoning(c) }
        assertEquals(JsonPrimitive("high"), body["reasoning"]!!.jsonObject["effort"])
        val url = "https://token-plan-cn.xiaomimimo.com/v1/chat/completions"
        assertEquals(url, c.copy(baseUrl = url).normalizedForRequest().resolveRequestEndpoint())
    }

    @Test fun responsesReasoningWithNullArraysDoesNotHideFinalText() {
        val stream = ResponsesSseAccumulator()
        stream.consume("""{"type":"response.completed","response":{"status":"completed","output":[{"type":"reasoning","summary":null,"content":null},{"type":"message","content":[{"type":"output_text","text":"OK"}]}]}}""")
        assertEquals("OK", parseResponsesTextResult(stream.toResponseJson()).content)
        assertNull(parseScheduleToolResult(stream.toResponseJson()))
    }

    @Test fun responsesIncompleteKeepsItsStatus() {
        val stream = ResponsesSseAccumulator()
        stream.consume("""{"type":"response.incomplete","response":{"status":"incomplete","output":[{"type":"message","content":[{"type":"output_text","text":"partial"}]}]}}""")
        assertEquals("incomplete", parseResponsesTextResult(stream.toResponseJson()).finishReason)
    }
}
