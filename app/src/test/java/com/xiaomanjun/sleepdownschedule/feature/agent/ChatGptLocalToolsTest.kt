package com.xiaomanjun.sleepdownschedule.feature.agent

import com.xiaomanjun.sleepdownschedule.feature.importing.chatGptResponsesBody
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ChatGptLocalToolsTest {
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
        assertEquals(input, body["input"])
        val namespace = body["tools"]!!.jsonArray.single().jsonObject
        assertEquals("sleepdown", namespace["name"]!!.jsonPrimitive.content)
        val names = namespace["tools"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertTrue("GET_WEEK_SCHEDULE" in names)
        assertTrue("GET_CURRENT_OVERVIEW" in names)
        assertTrue("PROPOSE_ACTIONS" in names)
        assertTrue(body["instructions"]!!.jsonPrimitive.content.contains("Keep confirmation"))
        assertTrue(body["instructions"]!!.jsonPrimitive.content.contains("sleepdown"))
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
        assertNotNull(agentLocalToolAvailabilityFeedback("I cannot access your schedule."))
        assertNull(agentLocalToolAvailabilityFeedback("你好，有什么可以帮你？"))
        assertNull(agentLocalToolAvailabilityFeedback("请在系统设置中授权勿扰模式。"))
    }
}
