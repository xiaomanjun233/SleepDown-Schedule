package com.xiaomanjun.sleepdownschedule.feature.importing

import java.io.BufferedReader
import java.io.StringReader
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic provider fixtures only: no credentials, uploaded files or paid model calls. */
class AiImportStreamActivityTest {
    @Test fun continuationBudgetRejectsStillTruncatedEvenWhenJsonCloses() {
        var requests = 0
        val error = runCatching {
            continueTruncatedImportText(AiProviderTextResult("{\"courses\":", finishReason = "length")) {
                requests++
                AiProviderTextResult(if (requests == 1) "[]" else "}", finishReason = "length")
            }
        }.exceptionOrNull()
        assertEquals(2, requests)
        assertTrue(error is AiServiceResponseException)
        assertEquals("", (error as AiServiceResponseException).rawBody)
    }

    @Test fun continuationStopsImmediatelyAfterExplicitSuccessAndPreservesTheWholeResult() {
        var requests = 0
        val result = continueTruncatedImportText(AiProviderTextResult("{\"courses\":", "first", "length")) { previous ->
            requests++
            assertEquals("{\"courses\":", previous)
            AiProviderTextResult("[]}", "continued", "stop")
        }
        assertEquals(1, requests)
        assertEquals("{\"courses\":[]}", result.content)
        assertEquals("stop", result.finishReason)
        assertTrue(result.reasoning.contains("continued"))
    }

    @Test fun nonstreamLengthToolCallIsRejectedBeforeAnyToolArgumentsAreParsed() {
        val response = """{"choices":[{"finish_reason":"length","message":{"tool_calls":[{"function":{"name":"IMPORT_SCHEDULE","arguments":"{\"courses\":[]}"}}]}}]}"""
        assertTrue(runCatching {
            requireUsableChatCompletionStatus(Json.parseToJsonElement(response).jsonObject, response)
        }.exceptionOrNull() is AiServiceResponseException)
        assertTrue(runCatching { parseChatCompletionTextResult(response, false) }.exceptionOrNull() is AiServiceResponseException)
    }

    @Test fun responseSizeLimitFailsInsteadOfTruncatingToolOrReasoningBuffers() {
        val oversized = "x".repeat(AiStreamResponseLimitChars + 1)
        for (consume in listOf<(String) -> Unit>(ChatCompletionSseAccumulator()::consume, ResponsesSseAccumulator()::consume)) {
            val failure = runCatching { consume(oversized) }.exceptionOrNull()
            assertTrue(failure is AiServiceResponseException)
            assertEquals("", (failure as AiServiceResponseException).rawBody)
            assertFalse(failure.message.orEmpty().contains(oversized.take(20)))
        }
        val budget = AiStreamResponseBudget()
        budget.record("x".repeat(AiStreamResponseLimitChars))
        assertTrue(runCatching { budget.record("x") }.exceptionOrNull() is AiServiceResponseException)
        assertTrue(runCatching {
            BufferedReader(StringReader("data: $oversized\n\n")).forEachAiSsePayload { fail("Oversized event must never reach parser") }
        }.exceptionOrNull() is AiServiceResponseException)
    }

    @Test fun burstCoalescesLatestActivityAndFlushesEvenWhenProviderPauses() {
        var now = 0L
        val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
        val updates = mutableListOf<AiImportActivity>()
        val publisher = AiImportActivityPublisher(
            nanoTime = { now }, schedule = { delay, action -> scheduled += delay to action }, onUpdate = updates::add
        )
        publisher.publish(AiImportActivity("已连接", AiImportActivitySource.STATUS))
        now = 1_000_000L
        publisher.publish(AiImportActivity("第一段", AiImportActivitySource.PROVIDER_SUMMARY))
        publisher.publish(AiImportActivity("最新摘要", AiImportActivitySource.PROVIDER_SUMMARY))
        assertEquals(1, scheduled.size)
        assertEquals(99L, scheduled.single().first)
        assertEquals(1, updates.size)
        now = 100_000_000L
        scheduled.single().second.invoke()
        assertEquals(listOf("已连接", "最新摘要"), updates.map { it.text })
    }

    @Test fun activityIsSingleLineBoundedAndStrictlyThrottledAcrossSources() {
        var now = 0L
        val updates = mutableListOf<AiImportActivity>()
        val publisher = AiImportActivityPublisher(nanoTime = { now }, onUpdate = updates::add)
        publisher.publish(AiImportActivity("已连接\n等待输出", AiImportActivitySource.STATUS))
        assertEquals("已连接 等待输出", updates.single().text)
        now = 99_999_999L
        publisher.publish(AiImportActivity("模型摘要", AiImportActivitySource.PROVIDER_SUMMARY))
        assertEquals(1, updates.size)
        now = 100_000_000L
        publisher.publish(AiImportActivity("甲\n".repeat(10_000), AiImportActivitySource.PROVIDER_SUMMARY))
        assertEquals(2, updates.size)
        assertTrue(updates.last().text.length <= AiImportActivityMaxChars)
        assertFalse(updates.last().text.contains('\n'))
        now += 100_000_000L
        publisher.publish(AiImportActivity("甲\n".repeat(10_000), AiImportActivitySource.PROVIDER_SUMMARY))
        assertEquals(2, updates.size)
    }

    @Test fun cancelledInteractionRejectsEveryLateActivity() {
        val interaction = AiImportInteraction("")
        val updates = mutableListOf<AiImportActivity>()
        interaction.onActivity = updates::add
        interaction.publishActivity("已连接")
        interaction.markCancelled()
        assertTrue(runCatching { interaction.publishActivity("迟到结果") }.exceptionOrNull() is CancellationException)
        assertEquals(1, updates.size)
    }

    @Test fun customGatewayRequestShapeSelectsRealStreamingWithoutChangingUrl() {
        assertEquals(AiEndpointStyle.CHAT_COMPLETIONS, aiRequestEndpointStyle("https://gateway.example/v1", """{"messages":[]}"""))
        assertEquals(AiEndpointStyle.RESPONSES, aiRequestEndpointStyle("https://gateway.example/ai", """{"input":[]}"""))
        assertEquals(AiEndpointStyle.KIMI_FILE_EXTRACT, aiRequestEndpointStyle("https://gateway.example/files", "{}"))
        assertEquals(AiEndpointStyle.CHAT_COMPLETIONS, aiRequestEndpointStyle("https://gateway.example/chat/completions", "{}"))
    }

    @Test fun framedSsePreservesMultilineJsonSkipsMetadataAndStopsAtDone() {
        val payloads = mutableListOf<String>()
        val body = "\uFEFF: keep alive\r\nevent: message\r\nid: 7\r\ndata: {\r\ndata: \"choices\": []\r\ndata: }\r\n\r\ndata: [DONE]\r\n\r\ndata: must not be read\n\n"
        BufferedReader(StringReader(body)).forEachAiSsePayload(onPayload = payloads::add)
        assertEquals(1, payloads.size)
        assertEquals(JsonArray(emptyList()), Json.parseToJsonElement(payloads.single()).jsonObject["choices"])
    }

    @Test fun compatibleSseWithoutBlankSeparatorsAndFinalNewlineIsRead() {
        val payloads = mutableListOf<String>()
        BufferedReader(StringReader("data: {\"a\":1}\ndata: {\"a\":2}")).forEachAiSsePayload(onPayload = payloads::add)
        assertEquals(listOf("{\"a\":1}", "{\"a\":2}"), payloads)
    }

    @Test fun cancellationIsCheckedEvenDuringKeepAliveFrames() {
        var checks = 0
        val error = runCatching {
            BufferedReader(StringReader(": ping\n: ping\ndata: {}\n\n"))
                .forEachAiSsePayload(checkActive = { if (++checks == 2) throw CancellationException("stopped") }) {
                    fail("Cancelled streams must not deliver model events")
                }
        }.exceptionOrNull()
        assertTrue(error is CancellationException)
    }

    @Test fun chatNativeChannelIsBoundedAndToolJsonNeverBecomesActivity() {
        val stream = ChatCompletionSseAccumulator()
        stream.consume("""{"choices":[{"delta":{"reasoning_content":"核对周次\n核对节次"}}]}""")
        assertEquals(AiImportActivitySource.PROVIDER_SUMMARY, stream.activity!!.source)
        assertEquals("核对周次 核对节次", stream.activity!!.text)
        stream.consume("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1","function":{"name":"IMPORT_SCHEDULE","arguments":"{\"private_note\":\"DO_NOT_DISPLAY\"}"}}]}}]}""")
        assertEquals(AiImportActivitySource.STATUS, stream.activity!!.source)
        assertFalse(stream.activity!!.text.contains("DO_NOT_DISPLAY"))
        assertEquals("{\"private_note\":\"DO_NOT_DISPLAY\"}", stream.courseOutput)
    }

    @Test fun publicProgressOnlyPublishesCompletedLatestBlocks() {
        val stream = ChatCompletionSseAccumulator()
        stream.consume("""{"choices":[{"delta":{"content":"<import_progress>核对周次"}}]}""")
        assertEquals(AiImportActivitySource.STATUS, stream.activity!!.source)
        stream.consume("""{"choices":[{"delta":{"content":"</import_progress>\n<import_progress>已核对节次</import_progress>"}}]}""")
        assertEquals(AiImportActivitySource.MODEL_PROGRESS, stream.activity!!.source)
        assertEquals("已核对节次", stream.activity!!.text)
        assertEquals("", latestImportProgressSummary("<think>\n<import_progress>秘密</import_progress>\n</think>"))
    }

    @Test fun arbitraryThoughtFieldsAndInlineThinkingAreNotTickerText() {
        val stream = ChatCompletionSseAccumulator()
        stream.consume("""{"choices":[{"delta":{"thoughts":"SECRET","chain_of_thought":"SECRET","content":"<think>SECRET</think>"}}]}""")
        assertEquals(AiImportActivitySource.STATUS, stream.activity!!.source)
        assertFalse(stream.activity!!.text.contains("SECRET"))
    }

    @Test fun responsesSummaryDeltasAndCompletedOnlySummaryUsePublicChannel() {
        val stream = ResponsesSseAccumulator()
        stream.consume("""{"type":"response.reasoning_summary_text.delta","delta":"检查周次\n检查节次"}""")
        assertEquals("检查周次 检查节次", stream.activity!!.text)
        assertEquals(AiImportActivitySource.PROVIDER_SUMMARY, stream.activity!!.source)
        stream.consume("""{"type":"response.completed","response":{"output":[{"type":"reasoning","summary":[{"type":"summary_text","text":"已核对所有页"}]}]}}""")
        assertEquals("已核对所有页", stream.activity!!.text)
    }

    @Test fun responsesRawReasoningAndEncryptedItemsStayOutOfTicker() {
        val stream = ResponsesSseAccumulator()
        stream.consume("""{"type":"response.reasoning_text.delta","delta":"SECRET"}""")
        assertEquals(AiImportActivitySource.STATUS, stream.activity!!.source)
        assertFalse(stream.activity!!.text.contains("SECRET"))
        stream.consume("""{"type":"response.completed","response":{"output":[{"type":"reasoning","encrypted_content":"OPAQUE","content":[{"type":"reasoning_text","text":"SECRET"}]}]}}""")
        assertEquals(AiImportActivitySource.STATUS, stream.activity!!.source)
        assertFalse(stream.activity!!.text.contains("SECRET"))
        assertTrue(stream.toResponseJson().contains("OPAQUE"))
    }

    @Test fun responseItemIndexAliasKeepsToolArgumentsAndOpaqueReasoningIntact() {
        val stream = ResponsesSseAccumulator()
        stream.consume("""{"type":"response.output_item.added","output_index":0,"item":{"id":"fc1","type":"function_call","name":"IMPORT_SCHEDULE","call_id":"call1","arguments":""}}""")
        stream.consume("""{"type":"response.function_call_arguments.delta","output_index":0,"delta":"{\"courses\":"}""")
        stream.consume("""{"type":"response.function_call_arguments.delta","item_id":"fc1","delta":"[]}"}""")
        stream.consume("""{"type":"response.output_item.done","output_index":1,"item":{"id":"r1","type":"reasoning","encrypted_content":"opaque"}}""")
        stream.consume("""{"type":"response.completed"}""")
        val output = Json.parseToJsonElement(stream.toResponseJson()).jsonObject["output"]!!.jsonArray
        val call = output.map { it.jsonObject }.single { it["type"] == JsonPrimitive("function_call") }
        assertEquals(JsonPrimitive("{\"courses\":[]}"), call["arguments"])
        assertEquals(JsonPrimitive("call1"), call["call_id"])
        assertTrue(output.any { it.jsonObject["encrypted_content"] == JsonPrimitive("opaque") })
    }

    @Test fun repeatedCompatibleToolNamesDoNotCorruptCallAndUsageSurvives() {
        val stream = ChatCompletionSseAccumulator()
        stream.consume("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"IMPORT_SCHEDULE","arguments":"{"}}]}}]}""")
        stream.consume("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"IMPORT_SCHEDULE","arguments":"}"}}]}}]}""")
        stream.consume("""{"choices":null,"usage":{"total_tokens":42}}""")
        stream.consume("""{"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""")
        assertEquals("{}", stream.courseOutput)
        assertEquals(JsonPrimitive(42), Json.parseToJsonElement(stream.toCompletionJson()).jsonObject["usage"]!!.jsonObject["total_tokens"])
    }

    @Test fun streamFailuresDoNotBecomeFabricatedSuccessActivity() {
        val stream = ResponsesSseAccumulator()
        val error = runCatching {
            stream.consume("""{"type":"response.failed","response":{"error":{"message":"synthetic failure"}}}""")
        }.exceptionOrNull()
        assertTrue(error is AiServiceResponseException)
        assertEquals("synthetic failure", error!!.message)
        assertNull(stream.activity)
    }

    @Test fun closedChatToolJsonWithoutSuccessfulTerminalIsNeverAccepted() {
        val stream = ChatCompletionSseAccumulator()
        stream.consume("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"IMPORT_SCHEDULE","arguments":"{\"courses\":[]}"}}]}}]}""")
        assertEquals("{\"courses\":[]}", stream.courseOutput)
        assertTrue(runCatching { stream.toCompletionJson() }.exceptionOrNull() is AiServiceResponseException)
        stream.consume("""{"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""")
        assertTrue(stream.toCompletionJson().contains("tool_calls"))
    }

    @Test fun responsesToolDataBeforeCompletionAndIncompleteAreNeverAccepted() {
        val stream = ResponsesSseAccumulator()
        stream.consume("""{"type":"response.output_item.done","item":{"id":"fc1","type":"function_call","name":"PATCH_IMPORT_JSON","arguments":"{}"}}""")
        assertTrue(runCatching { stream.toResponseJson() }.exceptionOrNull() is AiServiceResponseException)
        assertTrue(runCatching {
            stream.consume("""{"type":"response.incomplete","response":{"status":"incomplete","output":[]}}""")
        }.exceptionOrNull() is AiServiceResponseException)
        assertTrue(runCatching {
            parseResponsesTextResult("""{"status":"incomplete","output_text":"{\"courses\":[]}"}""")
        }.exceptionOrNull() is AiServiceResponseException)
    }

    @Test fun explicitLengthCannotPromoteClosedToolArgumentsButRetainsTextContinuation() {
        val stream = ChatCompletionSseAccumulator()
        stream.consume("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"IMPORT_SCHEDULE","arguments":"{\"courses\":[]}"}}]},"finish_reason":"length"}]}""")
        assertTrue(runCatching { stream.toCompletionJson() }.exceptionOrNull() is AiServiceResponseException)
        val text = ChatCompletionSseAccumulator()
        text.consume("""{"choices":[{"delta":{"content":"{\"courses\":["},"finish_reason":"length"}]}""")
        assertEquals("length", parseChatCompletionTextResult(text.toCompletionJson()).finishReason)
    }
}
