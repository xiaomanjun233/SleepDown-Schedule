package com.xiaomanjun.sleepdownschedule.feature.agent

import com.sun.net.httpserver.HttpServer
import com.xiaomanjun.sleepdownschedule.feature.importing.*
import com.xiaomanjun.sleepdownschedule.*
import java.net.InetSocketAddress
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Exercise real request/response continuation without a live model or user credentials. */
class AgentTaskLoopTest {
    @Test fun coldStartCanAnswerWithoutBeingForcedIntoAnotherRequest() {
        val answer = "可按课程、日期或周次描述目标，我会准备可确认的修改。"
        runConversation(listOf(response(message(answer)))) { result, requests, calls ->
            assertEquals(answer, result)
            assertEquals(1, requests.size)
            assertTrue(calls.isEmpty())
            assertTaskCapabilities(requests.single())
        }
    }

    @Test fun independentReadsAndDependentReadKeepPlanningAvailableWithoutCachedFacts() {
        val reasoning = buildJsonObject {
            put("type", "reasoning")
            put("id", "reasoning_1")
            put("encrypted_content", "opaque-state")
            put("summary", buildJsonArray {})
        }
        val answer = "请确认调整。<agent_actions>[" +
            "{\"type\":\"UPDATE_COURSE\",\"courseId\":42,\"scope\":\"SELECTED_WEEKS\",\"sourceWeeks\":[3],\"course\":{\"weeks\":[5],\"weekday\":2,\"note\":\"实验准备\"}}," +
            "{\"type\":\"SET_SETTING\",\"settingKey\":\"DARK_MODE\",\"settingValue\":\"true\"}]</agent_actions>"
        runConversation(listOf(
            response(reasoning, call("semester", "GET_SEMESTER_SCHEDULE"), call("settings", "GET_SETTINGS")),
            response(call("periods", "GET_PERIODS")),
            response(message(answer))
        )) { result, requests, calls ->
            assertEquals(answer, result)
            assertEquals(listOf(AgentToolName.GET_SEMESTER_SCHEDULE, AgentToolName.GET_SETTINGS, AgentToolName.GET_PERIODS), calls.map { it.name })
            assertEquals(3, requests.size)
            requests.forEach(::assertTaskCapabilities)
            val second = requests[1]
            val toolNames = second.getValue("tools").jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content }
            assertFalse("GET_SEMESTER_SCHEDULE" in toolNames)
            assertFalse("GET_SETTINGS" in toolNames)
            assertTrue("GET_PERIODS" in toolNames)
            val replay = second.getValue("input").jsonArray
            assertTrue(reasoning in replay)
            val outputs = replay.filter { it.jsonObject["type"]?.jsonPrimitive?.content == "function_call_output" }
            assertEquals(setOf("semester", "settings"), outputs.map { it.jsonObject.getValue("call_id").jsonPrimitive.content }.toSet())
        }
    }

    @Test fun emptyOrLegacyOutputCanRecoverByReadingInsteadOfLosingTools() {
        for (invalid in listOf("", "FINAL_ANSWER_READY", "<｜DSML｜tool_calls>")) {
            runConversation(listOf(
                response(message(invalid)),
                response(call("lookup", "SEARCH_COURSES", """{"query":"实验"}""")),
                response(message("找到实验课程，下一步请确认作用周次。"))
            )) { result, requests, calls ->
                assertEquals("找到实验课程，下一步请确认作用周次。", result)
                assertEquals(3, requests.size)
                requests.forEach(::assertTaskCapabilities)
                assertTrue(requests[1].getValue("instructions").jsonPrimitive.content.contains(DayAgentPrompts.TaskOutputRetry))
                assertEquals("实验", calls.single().arguments["query"])
            }
        }
    }

    private fun assertTaskCapabilities(request: JsonObject) {
        assertEquals("auto", request.getValue("tool_choice").jsonPrimitive.content)
        assertFalse(request.getValue("store").jsonPrimitive.boolean)
        assertTrue(request.getValue("instructions").jsonPrimitive.content.contains("<agent_actions>"))
        assertTrue(request.getValue("tools").jsonArray.isNotEmpty())
        assertEquals("high", request.getValue("reasoning").jsonObject.getValue("effort").jsonPrimitive.content)
    }

    private fun runConversation(
        responses: List<String>,
        verify: (String, List<JsonObject>, List<AgentToolCall>) -> Unit
    ) {
        val requests = mutableListOf<JsonObject>()
        val calls = mutableListOf<AgentToolCall>()
        val deltas = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/responses") { exchange ->
            val index = requests.size
            requests += Json.parseToJsonElement(exchange.requestBody.bufferedReader().use { it.readText() }).jsonObject
            val body = responses.getOrNull(index).orEmpty().toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(if (index < responses.size) 200 else 500, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
            exchange.close()
        }
        server.start()
        try {
            val result = OpenAiResponsesAgentRunner().chat(
                settings = AiImportSettings(
                    profile = AiProviderPresets.openAI.copy(
                        baseUrl = "http://127.0.0.1:${server.address.port}",
                        responsesPath = "responses",
                        reasoningEffort = AiReasoningEffort.HIGH
                    ),
                    apiKey = "unit-test"
                ),
                chatMessages = listOf(buildJsonObject { put("role", "user"); put("content", "处理当前任务") }),
                includeMemoryTool = false,
                onStatus = {},
                onDelta = deltas::add,
                onStreamReset = { fail("Valid task rounds must not reset output") },
                executeTool = { call ->
                    calls += call
                    AgentToolResult(call.id, call.name, true, "fixture:${call.name}")
                },
                telemetry = DayAgentTurnTelemetry("unit-test")
            )
            assertEquals(result, deltas.joinToString(""))
            verify(result, requests, calls)
        } finally {
            server.stop(0)
        }
    }

    private fun response(vararg items: JsonObject) = buildJsonObject {
        put("status", "completed")
        put("output", JsonArray(items.toList()))
    }.toString()

    private fun message(text: String) = buildJsonObject {
        put("type", "message")
        put("role", "assistant")
        put("content", buildJsonArray { add(buildJsonObject { put("type", "output_text"); put("text", text) }) })
    }

    private fun call(id: String, name: String, arguments: String = "{}") = buildJsonObject {
        put("type", "function_call")
        put("call_id", id)
        put("name", name)
        put("arguments", arguments)
    }
}
