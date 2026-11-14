package com.xiaomanjun.sleepdownschedule.feature.importing

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AiMaterialLoopTest {
    @Test fun steeringArrivingWithATerminalCandidateIsAppliedBeforeCompletionInBothProtocols() {
        for (responses in listOf(false, true)) {
            val interaction = AiImportInteraction("导入课表")
            val accepted = mutableListOf<String>()
            interaction.onAcceptedInput = { accepted += it.text }
            var requests = 0
            try {
                requestScheduleImport(config(responses), buildJsonObject {
                    put(if (responses) "input" else "messages", JsonArray(emptyList()))
                }, AiImportNetworkContext("FILE", interaction = interaction)) { request, _ ->
                    requests++
                    if (requests == 1) {
                        interaction.mailbox.submit(com.xiaomanjun.sleepdownschedule.feature.agent.AgentTaskInput("地点改成实验楼"),
                            com.xiaomanjun.sleepdownschedule.core.ai.AgentMailbox.Mode.STEER)
                    } else {
                        assertTrue(request.toString().contains("地点改成实验楼"))
                        assertTrue(request.toString().contains("尚未保存"))
                    }
                    response(responses, "submit-$requests", ScheduleImportToolName, """{"courses":[]}""")
                }
                assertEquals(2, requests)
                assertEquals(listOf("地点改成实验楼"), accepted)
            } finally { interaction.markCancelled() }
        }
    }
    private fun config(responses: Boolean) = AiProviderConfig("custom", "fixture", "placeholder", "https://fixture.invalid/v1", "vision",
        if (responses) AiEndpointStyle.RESPONSES else AiEndpointStyle.CHAT_COMPLETIONS,
        StructuredOutputMode.PROMPT_ONLY, true, false, false, responses, AiInputMode.AUTO, AiReasoningEffort.MEDIUM)

    private fun response(responses: Boolean, id: String, name: String, args: String) = buildJsonObject {
        if (responses) {
            put("status", "completed")
            put("output", buildJsonArray {
                add(buildJsonObject { put("type", "reasoning"); put("encrypted_content", "opaque-fixture") })
                add(buildJsonObject { put("type", "function_call"); put("call_id", id); put("name", name); put("arguments", args) })
            })
        } else put("choices", buildJsonArray { add(buildJsonObject {
            put("finish_reason", "tool_calls")
            put("message", buildJsonObject {
                put("role", "assistant"); put("content", "")
                put("tool_calls", buildJsonArray { add(buildJsonObject {
                    put("id", id); put("type", "function")
                    put("function", buildJsonObject { put("name", name); put("arguments", args) })
                }) })
            })
        }) })
    }.toString()

    @Test fun bothProtocolsCanReadThenZoomAndSubmitWithPairedToolResults() {
        for (responses in listOf(false, true)) {
            var views = 0
            val source = AiMaterialWorkspace("fixture.pdf", "周一课程 第1–8周", 2,
                view = { index, region ->
                    assertEquals(1, index)
                    assertEquals(MaterialRegion(100, 200, 500, 800), region)
                    views++
                    RenderedPageImage(index, "image/jpeg", "AA==")
                })
            val key = if (responses) "input" else "messages"
            val initial = buildJsonObject { put(key, buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", "导入模拟课表") }) }) }
            val requests = mutableListOf<JsonObject>()
            val result = requestScheduleImport(config(responses), initial, AiImportNetworkContext("FILE", materials = source)) { request, _ ->
                requests += request
                when (requests.size) {
                    1 -> response(responses, "read", ReadImportMaterialTool, """{"offset":0,"page":0}""")
                    2 -> response(responses, "zoom", ViewImportDetailTool, """{"page":2,"left":100,"top":200,"right":500,"bottom":800}""")
                    else -> response(responses, "submit", ScheduleImportToolName, """{"courses":[]}""")
                }
            }
            assertEquals(3, requests.size)
            assertEquals(1, views)
            assertTrue(result.content.contains("courses"))
            val replay = requests.last()[key]!!.jsonArray.map { it.jsonObject }
            assertTrue(replay.any { it[if (responses) "call_id" else "tool_call_id"]?.jsonPrimitive?.contentOrNull == "read" })
            assertTrue(replay.any { it[if (responses) "call_id" else "tool_call_id"]?.jsonPrimitive?.contentOrNull == "zoom" })
            assertTrue(replay.any { it["role"]?.jsonPrimitive?.contentOrNull == "user" && it.toString().contains("data:image/jpeg;base64,AA==") })
            if (responses) assertTrue(replay.any { it["encrypted_content"]?.jsonPrimitive?.contentOrNull == "opaque-fixture" })
        }
    }

    @Test fun replayedMaterialCallsDoNotReadAgainAndIdCollisionGetsFeedback() {
        var reads = 0
        val source = AiMaterialWorkspace("web", "", 0, refresh = { reads++; "课程文本" })
        val requests = mutableListOf<JsonObject>()
        requestScheduleImport(config(true), buildJsonObject { put("input", JsonArray(emptyList())) },
            AiImportNetworkContext("CAPTURED_PAGE", materials = source)) { body, _ ->
            requests += body
            when (requests.size) {
                1, 2 -> response(true, "read", ReadImportMaterialTool, """{"offset":0}""")
                3 -> response(true, "read", ReadImportMaterialTool, """{"offset":1}""")
                else -> response(true, "submit", ScheduleImportToolName, """{"courses":[]}""")
            }
        }
        assertEquals(1, reads)
        assertTrue(requests.last().toString().contains("工具调用 ID 已用于其他参数"))
    }

    @Test fun invalidRegionsCannotRenderOrEscapeTheDocument() {
        var views = 0
        val source = AiMaterialWorkspace("fixture", "", 1, view = { _, _ -> views++; RenderedPageImage(0, "image/jpeg", "") })
        for (args in listOf("""{"page":2}""", """{"page":1,"left":600,"right":100}""", """{"page":1,"top":-1}""")) {
            try { source.execute(ViewImportDetailTool, Json.parseToJsonElement(args).jsonObject); fail("Expected invalid region") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, views)
    }
}
