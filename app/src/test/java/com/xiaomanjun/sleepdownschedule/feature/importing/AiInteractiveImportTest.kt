package com.xiaomanjun.sleepdownschedule.feature.importing

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class AiInteractiveImportTest {
    private fun config(style: AiEndpointStyle = AiEndpointStyle.RESPONSES) = AiProviderConfig(
        "custom", "test", "placeholder", "https://gateway.example/v1", "gpt-5.6-luna",
        style, StructuredOutputMode.PROMPT_ONLY, true, false, false, true,
        AiInputMode.AUTO, AiReasoningEffort.MEDIUM
    )

    private fun body(style: AiEndpointStyle) = buildJsonObject {
        put(if (style == AiEndpointStyle.RESPONSES) "input" else "messages", buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", "synthetic schedule source")
            })
        })
    }

    private fun response(style: AiEndpointStyle, text: String = "", vararg calls: Pair<String, String>): String {
        return buildJsonObject {
            if (style == AiEndpointStyle.RESPONSES) {
                put("status", "completed")
                put("output", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "message")
                        put("content", buildJsonArray {
                            add(buildJsonObject { put("type", "output_text"); put("text", text) })
                        })
                    })
                    calls.forEach { (name, arguments) -> add(buildJsonObject {
                        put("type", "function_call"); put("name", name); put("arguments", arguments)
                    }) }
                })
            } else {
                put("choices", buildJsonArray {
                    add(buildJsonObject {
                        put("message", buildJsonObject {
                            put("content", text)
                            put("tool_calls", buildJsonArray {
                                calls.forEach { (name, arguments) -> add(buildJsonObject {
                                    put("type", "function")
                                    put("function", buildJsonObject { put("name", name); put("arguments", arguments) })
                                }) }
                            })
                        })
                    })
                })
            }
        }.toString()
    }

    @After fun resetSession() = AiEduImportProgressSession.update(null)

    @Test fun enabledMemorySurvivesProgressContinuationInBothProtocols() {
        for (style in listOf(AiEndpointStyle.CHAT_COMPLETIONS, AiEndpointStyle.RESPONSES)) {
            val requests = mutableListOf<JsonObject>()
            requestScheduleImport(config(style), body(style), AiImportNetworkContext("TEXT", assistantMemory = "课程名称保留英文")) { request, _ ->
                requests += request
                if (requests.size == 1) response(style, "<import_progress>正在核对</import_progress>")
                else response(style, "", ScheduleImportToolName to """{"courses":[]}""")
            }
            val key = if (style == AiEndpointStyle.RESPONSES) "input" else "messages"
            requests.forEach { request ->
                val contents = request[key]!!.jsonArray.map { it.jsonObject["content"]!!.jsonPrimitive.content }
                val memory = contents.single { it.startsWith("{\"kind\":\"user_memory_context\"") }
                assertEquals("课程名称保留英文", Json.parseToJsonElement(memory).jsonObject["content"]!!.jsonPrimitive.content)
                assertTrue(contents.contains("synthetic schedule source"))
            }
        }
    }

    @Test fun formatRepairNeverReceivesAssistantMemory() {
        requestScheduleImport(config(), body(AiEndpointStyle.RESPONSES), AiImportNetworkContext("REPAIR", assistantMemory = "私有偏好")) { request, _ ->
            assertFalse(request.toString().contains("私有偏好"))
            response(AiEndpointStyle.RESPONSES, "", ScheduleImportToolName to """{"courses":[]}""")
        }
    }

    @Test fun partialSummaryIsNotShownAndMarkedSummaryDoesNotCorruptFinalJson() {
        val partial = "<import_progress>已识别课程"
        assertEquals("", importProgressSummary(partial))
        val complete = "$partial</import_progress>\n<import_progress>正在核对周次</import_progress>\n"
        val json = """{"note":"<import_progress>保留原文</import_progress>"}"""
        assertEquals("已识别课程\n\n正在核对周次", importProgressSummary(complete + json))
        assertEquals(json, (complete + json).stripImportProgress())
    }

    @Test fun chatStreamsPublicProgressWithoutExposingToolArgumentsAndNativeSummaryTakesPriority() {
        val stream = ChatCompletionSseAccumulator()
        stream.consume("""{"choices":[{"delta":{"content":"<import_progress>已识别"}}]}""")
        assertEquals("", stream.displayReasoning.toString())
        stream.consume("""{"choices":[{"delta":{"content":"三门课程</import_progress>"}}]}""")
        assertEquals("已识别三门课程", stream.displayReasoning.toString())
        stream.consume("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"IMPORT_SCHEDULE","arguments":"{\"courses\":[]}"}}]}}]}""")
        assertEquals("已识别三门课程", stream.displayReasoning.toString())
        stream.consume("""{"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""")
        assertEquals("{\"courses\":[]}", parseScheduleToolResult(stream.toCompletionJson())!!.content)
        stream.consume("""{"choices":[{"delta":{"reasoning_content":"正在核对课时"}}]}""")
        assertEquals("正在核对课时", stream.displayReasoning.toString())
    }

    @Test fun responsesCompletedOnlySummaryIsVisibleAndProgressIsSeparateFromFinalData() {
        val stream = ResponsesSseAccumulator()
        val raw = response(AiEndpointStyle.RESPONSES, "<import_progress>已核对周次</import_progress>")
        stream.consume("""{"type":"response.completed","response":$raw}""")
        assertEquals("已核对周次", stream.displayReasoning.toString())
        val parsed = parseResponsesTextResult(stream.toResponseJson(), requireContent = false)
        assertEquals("", parsed.content)
        assertEquals("已核对周次", parsed.reasoning)
    }

    @Test fun nativeAndFallbackReplacementWithEqualLengthStillPublishes() {
        val updates = mutableListOf<String>()
        val publisher = AiReasoningStreamPublisher(updates::add)
        publisher.publish("核对节次", force = true)
        publisher.publish("核对周次", force = true)
        assertEquals(listOf("核对节次", "核对周次"), updates)
    }

    @Test fun summaryOnlyResponseContinuesOnceWithSourceAndNewRequirementsInBothProtocols() {
        for (style in listOf(AiEndpointStyle.CHAT_COMPLETIONS, AiEndpointStyle.RESPONSES)) {
            val sent = mutableListOf<JsonObject>()
            val args = """{"changeSummary":"识别完成","courses":[]}"""
            val result = requestScheduleImport(config(style), body(style),
                AiImportNetworkContext("TEXT", interaction = AiImportInteraction("周次是 1 至 16 周"))) { request, _ ->
                sent += request
                if (sent.size == 1) response(style, "<import_progress>已识别课程</import_progress>")
                else response(style, "", ScheduleImportToolName to args)
            }
            assertEquals(2, sent.size)
            val key = if (style == AiEndpointStyle.RESPONSES) "input" else "messages"
            assertEquals(sent[0][key]!!.jsonArray, JsonArray(sent[1][key]!!.jsonArray.take(2)))
            assertTrue(sent[1].toString().contains("周次是 1 至 16 周"))
            assertEquals(JsonPrimitive("auto"), sent[1]["tool_choice"])
            assertEquals(args, result.content)
            assertTrue(result.reasoning.contains("已识别课程"))
        }
    }

    @Test fun repeatedProgressDoesNotLoopOrReturnAFalseFinalResult() {
        var count = 0
        val error = runCatching {
            requestScheduleImport(config(), body(AiEndpointStyle.RESPONSES), AiImportNetworkContext("TEXT")) { _, _ ->
                count++
                response(AiEndpointStyle.RESPONSES, "<import_progress>仍在整理</import_progress>")
            }
        }.exceptionOrNull()
        assertEquals(2, count)
        assertTrue(error is AiServiceResponseException)
    }

    @Test fun questionsPreemptSpeculativeDataWithoutRetryingOrEnteringJsonRepair() {
        for (style in listOf(AiEndpointStyle.CHAT_COMPLETIONS, AiEndpointStyle.RESPONSES)) {
            var count = 0
            val error = runCatching {
                requestScheduleImport(config(style), body(style), AiImportNetworkContext("TEXT")) { _, _ ->
                    count++
                    response(style, "", ScheduleImportToolName to "{}",
                        AskImportDetailsToolName to """{"questions":[" 课程在哪几周上？ ","课程在哪几周上？"]}""")
                }
            }.exceptionOrNull()
            assertEquals(1, count)
            assertEquals(listOf("课程在哪几周上？"), (error as AiImportClarificationRequired).questions)
        }
    }

    @Test fun emptyQuestionsAreAVisibleResponseError() {
        val error = runCatching { importClarificationQuestions(response(AiEndpointStyle.RESPONSES,
            "", AskImportDetailsToolName to """{"questions":[""]}""")) }.exceptionOrNull()
        assertTrue(error is AiServiceResponseException)
    }

    @Test fun cancellationDisconnectsAndPreventsSendingOrAcceptingLateData() {
        val interaction = AiImportInteraction("")
        var disconnected = false
        val connection = object : HttpURLConnection(URL("https://example.invalid")) {
            override fun connect() = Unit
            override fun usingProxy() = false
            override fun disconnect() { disconnected = true }
        }
        interaction.attach(connection)
        val error = runCatching {
            requestScheduleImport(config(), body(AiEndpointStyle.RESPONSES),
                AiImportNetworkContext("TEXT", interaction = interaction)) { _, _ ->
                interaction.markCancelled()
                interaction.disconnect()
                response(AiEndpointStyle.RESPONSES, "", ScheduleImportToolName to "{}")
            }
        }.exceptionOrNull()
        assertTrue(disconnected)
        assertTrue(error is CancellationException)
        val beforeSend = runCatching {
            requestScheduleImport(config(), body(AiEndpointStyle.RESPONSES),
                AiImportNetworkContext("TEXT", interaction = interaction)) { _, _ ->
                fail("Cancelled attempt must not send another request"); ""
            }
        }.exceptionOrNull()
        assertTrue(beforeSend is CancellationException)
    }

    @Test fun stoppedOrOldTasksCannotPublishResultsOrResetANewerStream() {
        val session = AiEduImportProgressSession
        session.update(AiEduImportProgress(taskId = "first"))
        session.updateActiveTask("first") { it.copy(finished = true, awaitingUserInput = true) }
        assertNull(session.updateActiveTask("first") { it.copy(aiOutput = "late result") })
        session.update(AiEduImportProgress(taskId = "second"))
        val current = session.beginReasoning("second")
        current("新请求输出")
        session.beginReasoning("first")("旧请求输出")
        assertNull(session.updateActiveTask("first") { it.copy(error = "late failure") })
        assertEquals("新请求输出", session.liveReasoning.value.text)
        assertEquals("second", session.progress.value!!.taskId)
    }

    @Test fun summaryCapabilityAndToolPolicyPreserveOtherProviderProtocols() {
        assertTrue(config().requestsResponsesReasoningSummary())
        assertFalse(config().copy(reasoningEffort = AiReasoningEffort.NONE).requestsResponsesReasoningSummary())
        assertFalse(config().copy(model = "qwen3").requestsResponsesReasoningSummary())
        assertFalse(config().copy(providerId = AiProviderPresets.deepSeek.id).requestsResponsesReasoningSummary())
        assertFalse(config().copy(providerId = AiProviderPresets.mimo.id).requestsResponsesReasoningSummary())
        assertEquals(JsonPrimitive("auto"), scheduleToolChoice(config(), ScheduleImportToolName, allowProgressUpdates = true))
        assertEquals(JsonPrimitive("function"), scheduleToolChoice(config(), ScheduleImportToolName)!!.jsonObject["type"])
    }
}
