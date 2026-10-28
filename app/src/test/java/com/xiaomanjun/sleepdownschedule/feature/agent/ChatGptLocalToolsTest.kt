package com.xiaomanjun.sleepdownschedule.feature.agent

import com.xiaomanjun.sleepdownschedule.feature.importing.chatGptResponsesBody
import com.xiaomanjun.sleepdownschedule.feature.importing.AiImportSettings
import com.xiaomanjun.sleepdownschedule.feature.importing.AiProviderPresets
import com.xiaomanjun.sleepdownschedule.feature.importing.ChatGptUsageStatus
import com.xiaomanjun.sleepdownschedule.feature.importing.readChatGptResponse
import java.io.StringReader
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ChatGptLocalToolsTest {
    @Test fun recoveryLoadsOnlyEagerLocalReadsAtTheStartOfTheInput() {
        val reads = agentReadRecoveryTools(agentResponsesToolDefinitions(includeMemoryTool = true))
        val body = chatGptResponsesBody(buildJsonObject {
            put("input", "读取课表")
            put("tools", reads)
            put("tool_choice", "required")
        })
        assertEquals("required", body["tool_choice"]!!.jsonPrimitive.content)
        val names = body["input"]!!.jsonArray.first().jsonObject["tools"]!!.jsonArray
            .map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertTrue("GET_WEEK_SCHEDULE" in names)
        assertFalse("PROPOSE_ACTIONS" in names)
        assertFalse("UPDATE_MEMORY" in names)
        assertEquals(AgentToolName.GET_WEEK_SCHEDULE, normalizeAgentToolName("functions.sleepdown.GET_WEEK_SCHEDULE"))
        assertNull(normalizeAgentToolName("other.GET_WEEK_SCHEDULE"))
    }

    @Test fun realAgentToolsAndInlineImageReachTheAccountWireTogether() {
        val input = Json.parseToJsonElement("""[{"role":"user","content":[
            {"type":"input_text","text":"读取我的课表并核对图片"},
            {"type":"input_image","image_url":"data:image/png;base64,AA=="}
        ]}]""")
        val body = chatGptResponsesBody(buildJsonObject {
            put("model", "account-model")
            put("input", input)
            put("instructions", "Keep confirmation before all writes.")
            put("tools", agentResponsesToolDefinitions())
            put("tool_choice", "auto")
        })
        assertEquals(input.jsonArray.toList(), body["input"]!!.jsonArray.drop(1))
        val declaration = body["input"]!!.jsonArray.first().jsonObject
        assertEquals("additional_tools", declaration["type"]!!.jsonPrimitive.content)
        val names = declaration["tools"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertTrue("GET_WEEK_SCHEDULE" in names)
        assertTrue("GET_CURRENT_OVERVIEW" in names)
        assertTrue("PROPOSE_ACTIONS" in names)
        assertTrue(body["instructions"]!!.jsonPrimitive.content.contains("Keep confirmation"))
        assertTrue(body["instructions"]!!.jsonPrimitive.content.contains("additional_tools"))
        assertEquals("auto", body["tool_choice"]!!.jsonPrimitive.content)
    }

    @Test fun namespacedCourseReadRetainsCallIdentityAndReplaysItsLocalResult() {
        val turn = parseAgentResponsesTurn("""{"status":"completed","output":[
            {"type":"function_call","namespace":"sleepdown","name":"GET_WEEK_SCHEDULE","call_id":"local-read","arguments":"{}"}
        ]}""")
        val call = turn.calls.single()
        assertEquals(AgentToolName.GET_WEEK_SCHEDULE, call.name)
        assertNull(call.validationError)
        val session = AgentTurnToolSession { requested ->
            AgentToolResult(requested.id, requested.name, true, "本周课程：测试课程")
        }
        val result = session.run(call)
        assertEquals("local-read", result.callId)
        assertTrue(result.success)
        val replay = chatGptResponsesBody(buildJsonObject {
            put("input", buildJsonArray {
                turn.outputItems.forEach(::add)
                add(buildJsonObject {
                    put("type", "function_call_output")
                    put("call_id", result.callId)
                    put("output", result.content)
                })
            })
        })["input"]!!.jsonArray
        assertEquals("sleepdown", replay[0].jsonObject["namespace"]!!.jsonPrimitive.content)
        assertEquals("local-read", replay[1].jsonObject["call_id"]!!.jsonPrimitive.content)
        assertEquals(result.content, replay[1].jsonObject["output"]!!.jsonPrimitive.content)
    }

    @Test fun unavailableToolClaimGetsPreciseFeedbackWhileSystemPermissionsStayExternal() {
        assertNotNull(agentLocalToolAvailabilityFeedback("我无法直接读取你的课表，请手动提供。"))
        assertNotNull(agentLocalToolAvailabilityFeedback("我无法在当前聊天环境里直接读取你的课表。"))
        assertNotNull(agentLocalToolAvailabilityFeedback("I cannot access your schedule."))
        assertNull(agentLocalToolAvailabilityFeedback("你好，有什么可以帮你？"))
        assertNull(agentLocalToolAvailabilityFeedback("请在系统设置中授权勿扰模式。"))
    }

    @Test fun streamedScheduleReadExecutesAndReplaysEvenWhenTerminalOutputIsEmpty() {
        val requests = mutableListOf<JsonObject>()
        val calls = mutableListOf<AgentToolCall>()
        val responses = listOf(
            listOf(
                """{"type":"response.output_item.done","output_index":0,"item":{"type":"reasoning","id":"r1","encrypted_content":"opaque"}}""",
                """{"type":"response.output_item.added","output_index":1,"item":{"type":"function_call","id":"fc1","name":"GET_WEEK_SCHEDULE","call_id":"read-week","arguments":""}}""",
                """{"type":"response.function_call_arguments.delta","item_id":"fc1","output_index":1,"delta":"{}"}""",
                """{"type":"response.output_item.done","output_index":1,"item":{"type":"function_call","id":"fc1","name":"GET_WEEK_SCHEDULE","call_id":"read-week","arguments":"{}"}}""",
                """{"type":"response.completed","response":{"status":"completed","output":[]}}"""
            ).joinToString("\n\n", postfix = "\n\n") { "data: $it" },
            answerResponse("本周有测试课程。")
        )
        val runner = OpenAiResponsesAgentRunner(request = { _, body ->
            requests += chatGptResponsesBody(body)
            val response = responses[requests.lastIndex]
            if (requests.lastIndex == 0) StringReader(response).buffered().readChatGptResponse() else response
        })
        val answer = runChat(runner, "本周有哪些课程？", calls)
        assertEquals("本周有测试课程。", answer)
        assertEquals(listOf(AgentToolName.GET_WEEK_SCHEDULE), calls.map { it.name })
        val first = requests.first()
        assertEquals("required", first["tool_choice"]!!.jsonPrimitive.content)
        val declaration = first["input"]!!.jsonArray.first().jsonObject
        assertEquals("additional_tools", declaration["type"]!!.jsonPrimitive.content)
        val names = declaration["tools"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertTrue("GET_WEEK_SCHEDULE" in names)
        assertFalse("PROPOSE_ACTIONS" in names)
        assertFalse("UPDATE_MEMORY" in names)
        val replay = requests[1]["input"]!!.jsonArray.map { it.jsonObject }
        assertTrue(replay.any { it["encrypted_content"]?.jsonPrimitive?.content == "opaque" })
        assertEquals("additional_tools", replay.first()["type"]!!.jsonPrimitive.content)
        val result = replay.single { it["type"]?.jsonPrimitive?.content == "function_call_output" }
        assertEquals("read-week", result["call_id"]!!.jsonPrimitive.content)
        assertEquals("本周课程：测试课程", result["output"]!!.jsonPrimitive.content)
        assertEquals("auto", requests[1]["tool_choice"]!!.jsonPrimitive.content)
    }

    @Test fun ignoredRequiredReadsStopAfterOneRetryWithoutPretendingToReadData() {
        var requests = 0
        val calls = mutableListOf<AgentToolCall>()
        val runner = OpenAiResponsesAgentRunner(request = { _, body ->
            requests++
            assertEquals("required", chatGptResponsesBody(body)["tool_choice"]!!.jsonPrimitive.content)
            answerResponse("我无法在这个聊天窗口读取你的课表。")
        })
        val answer = runChat(runner, "读取我的课表", calls)
        assertEquals(2, requests)
        assertTrue(calls.isEmpty())
        assertTrue(answer.contains("未返回可执行的课表工具调用"))
        assertTrue(ChatGptUsageStatus.state.value.diagnostic.orEmpty().contains("output_types=message"))
    }

    @Test fun greetingDoesNotRequireScheduleAccessAndMultimodalRequestsDo() {
        var first: JsonObject? = null
        runChat(OpenAiResponsesAgentRunner(request = { _, body ->
            first = body
            answerResponse("你好")
        }), "你好", mutableListOf())
        assertEquals("auto", first!!["tool_choice"]!!.jsonPrimitive.content)
        assertTrue(agentNeedsScheduleRead(listOf(Json.parseToJsonElement("""
            {"role":"user","content":[{"type":"text","text":"看看我的课表"},{"type":"image_url","image_url":{"url":"fixture"}}]}
        """).jsonObject)))
        assertFalse(agentNeedsScheduleRead(listOf(buildJsonObject { put("role", "user"); put("content", "请解释什么是机器学习") })))
    }

    @Test fun foreignNamespaceCannotInvokeALocalFunctionWithTheSameName() {
        val turn = parseAgentResponsesTurn("""{"output":[
            {"type":"function_call","namespace":"other","name":"GET_WEEK_SCHEDULE","call_id":"c1","arguments":"{}"}
        ]}""")
        assertEquals(AgentToolName.UNKNOWN, turn.calls.single().name)
        assertNotNull(turn.calls.single().validationError)
    }

    private fun answerResponse(text: String) = buildJsonObject {
        put("status", "completed")
        put("output", buildJsonArray { add(buildJsonObject {
            put("type", "message")
            put("role", "assistant")
            put("content", buildJsonArray { add(buildJsonObject { put("type", "output_text"); put("text", text) }) })
        }) })
    }.toString()

    private fun runChat(runner: OpenAiResponsesAgentRunner, text: String, calls: MutableList<AgentToolCall>): String =
        runner.chat(
            settings = AiImportSettings(profile = AiProviderPresets.chatGpt),
            chatMessages = listOf(buildJsonObject { put("role", "user"); put("content", text) }),
            includeMemoryTool = true,
            onStatus = {}, onDelta = {}, onStreamReset = {},
            executeTool = { call ->
                calls += call
                AgentToolResult(call.id, call.name, true, "本周课程：测试课程")
            },
            telemetry = DayAgentTurnTelemetry("unit-test", elapsedRealtime = { 0L })
        )
}
