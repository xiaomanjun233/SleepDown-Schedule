package com.xiaomanjun.sleepdownschedule.feature.importing

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class ChatGptResponsesWireTest {
    @Test fun logoutDisconnectsActiveStreamsAndRejectsLateRegistration() {
        val connection = object : HttpURLConnection(URL("https://api.openai.com/v1/responses")) {
            var disconnected = false
            override fun connect() = Unit
            override fun usingProxy() = false
            override fun disconnect() { disconnected = true }
        }
        val generation = ChatGptInferenceSessions.currentGeneration()
        ChatGptInferenceSessions.register(connection, generation)
        ChatGptInferenceSessions.checkActive(connection)
        ChatGptInferenceSessions.invalidate()
        assertTrue(connection.disconnected)
        assertThrows(IllegalStateException::class.java) { ChatGptInferenceSessions.checkActive(connection) }
        assertThrows(IllegalStateException::class.java) { ChatGptInferenceSessions.register(connection, generation) }
    }
    @Test fun subscriptionRequestIsStreamingStatelessAndHasArrayInput() {
        val request = chatGptResponsesBody(Json.parseToJsonElement("""{
          "model":"account-model","input":"Hello","store":true,"stream":false,
          "temperature":0.5,"max_output_tokens":30,"previous_response_id":"old",
          "reasoning":{"effort":"max"},"metadata":{"private":"value"}
        }""").jsonObject)
        assertEquals(JsonPrimitive(false), request["store"])
        assertEquals(JsonPrimitive(true), request["stream"])
        assertEquals("Hello", request["input"]!!.jsonArray.single().jsonObject["content"]!!.jsonPrimitive.content)
        listOf("temperature", "max_output_tokens", "previous_response_id", "reasoning", "metadata").forEach {
            assertFalse(request.containsKey(it))
        }
    }

    @Test fun localToolsAreNamespacedAndSystemMessagesBecomeDeveloper() {
        val request = chatGptResponsesBody(Json.parseToJsonElement("""{
          "model":"account-model","input":[{"type":"message","role":"system","content":"safe rules"}],
          "tools":[{"type":"function","name":"IMPORT_SCHEDULE","parameters":{"type":"object"}}],
          "tool_choice":{"type":"function","name":"IMPORT_SCHEDULE"}
        }""").jsonObject)
        val namespace = request["tools"]!!.jsonArray.single().jsonObject
        assertEquals("namespace", namespace["type"]!!.jsonPrimitive.content)
        assertEquals("sleepdown", namespace["name"]!!.jsonPrimitive.content)
        assertEquals("IMPORT_SCHEDULE", namespace["tools"]!!.jsonArray.single().jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("required", request["tool_choice"]!!.jsonPrimitive.content)
        assertEquals("developer", request["input"]!!.jsonArray.single().jsonObject["role"]!!.jsonPrimitive.content)
    }

    @Test fun unsupportedHostedToolsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            chatGptResponsesBody(Json.parseToJsonElement("""{"input":[],"tools":[{"type":"tool_search"}]}""").jsonObject)
        }
    }

    @Test fun replayRetainsFunctionNamespaceAndOpaqueReasoning() {
        val input = Json.parseToJsonElement("""[
            {"type":"reasoning","id":"r1","encrypted_content":"opaque"},
            {"type":"function_call","namespace":"sleepdown","name":"IMPORT_SCHEDULE","call_id":"c1","arguments":"{}"},
            {"type":"function_call_output","call_id":"c1","output":"done"}
        ]""")
        val normalized = chatGptResponsesBody(buildJsonObject { put("input", input) })
        assertEquals(input, normalized["input"])
    }

    @Test fun bearerEndpointCannotBeOverriddenOrRedirected() {
        requireChatGptInferenceEndpoint("https://api.openai.com/v1/responses")
        listOf("http://api.openai.com/v1/responses", "https://evil.test/responses",
            "https://api.openai.com/v1/chat/completions", "https://api.openai.com/v1/responses?secret=1",
            "https://api.openai.com.evil.test/v1/responses").forEach {
            assertThrows(IllegalArgumentException::class.java) { requireChatGptInferenceEndpoint(it) }
        }
    }

    @Test fun deltaThenEofIsNeverSuccessful() {
        val accumulator = ResponsesSseAccumulator(requireCompleted = true)
        accumulator.consume("""{"type":"response.output_text.delta","delta":"partial plan"}""")
        assertThrows(AiServiceResponseException::class.java) { accumulator.toResponseJson() }
    }

    @Test fun incompleteAndFailedNeverExecutePartialToolCalls() {
        listOf("response.incomplete", "response.failed").forEach { type ->
            val accumulator = ResponsesSseAccumulator(requireCompleted = true)
            assertThrows(AiServiceResponseException::class.java) {
                accumulator.consume("""{"type":"$type","response":{"status":"incomplete","output":[]}}""")
            }
        }
    }

    @Test fun onlyCompletedResponseIsAccepted() {
        val accumulator = ResponsesSseAccumulator(requireCompleted = true)
        accumulator.consume("""{"type":"response.completed","response":{"status":"completed","output":[]}}""")
        assertEquals("completed", Json.parseToJsonElement(accumulator.toResponseJson()).jsonObject["status"]!!.jsonPrimitive.content)
        assertEquals("最近调用成功", ChatGptUsageStatus.state.value.status)
    }

    @Test fun quotaAndRateLimitRemainDistinctAndDoNotEchoRemoteSecrets() {
        val quota = chatGptInferenceError(429, """{"error":{"code":"subscription_sharing_usage_limit_exceeded","message":"secret-token"}}""")
        assertTrue(ChatGptUsageStatus.state.value.limitReached)
        assertFalse(quota.contains("secret-token"))
        chatGptInferenceError(429, """{"error":{"code":"rate_limit_exceeded"}}""")
        assertFalse(ChatGptUsageStatus.state.value.limitReached)
        val status = chatGptInferenceError(503, """{"detail":{"code":"subscription_sharing_usage_unavailable"}}""")
        assertTrue(status.contains("保留登录"))
    }

    @Test fun nestedFailedEventExposesOnlySafeCode() {
        val accumulator = ResponsesSseAccumulator(requireCompleted = true)
        val failure = assertThrows(AiServiceResponseException::class.java) {
            accumulator.consume("""{"type":"response.failed","response":{"error":{"code":"subscription_sharing_usage_limit_exceeded","message":"secret-token"}}}""")
        }
        assertTrue(failure.message!!.contains("使用受限"))
        assertFalse(failure.message!!.contains("secret-token"))
        assertEquals("", failure.rawBody)
    }
}
