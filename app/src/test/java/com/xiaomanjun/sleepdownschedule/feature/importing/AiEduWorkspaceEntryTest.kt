package com.xiaomanjun.sleepdownschedule.feature.importing

import android.app.Application
import android.net.Uri
import android.webkit.WebView
import com.xiaomanjun.sleepdownschedule.core.ai.AgentMailbox
import com.xiaomanjun.sleepdownschedule.feature.agent.AgentTaskInput
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.ShiguangBridgeHost
import com.xiaomanjun.sleepdownschedule.model.defaultConfig
import com.xiaomanjun.sleepdownschedule.model.defaultPeriods
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class AiEduWorkspaceEntryTest {
    @Test fun longInvestigationCompactsAndKeepsFilesAddedWhileRunning() {
        val app = RuntimeEnvironment.getApplication()
        val webView = WebView(app).apply { loadUrl("https://fixture.invalid/portal") }
        val bridge = ShiguangBridgeHost({}, {}, {})
        for (responses in listOf(false, true)) {
            val interaction = AiImportInteraction("保留原有周次")
            try {
                val edu = AiEduWorkspace(app, webView, bridge, defaultConfig(), defaultPeriods(), false) { true }
                AiMaterialWorkspace.fromPages("教务网页", "课表入口", emptyList()).use { materials ->
                    materials.taskWorkspace = AiTaskWorkspace(app, edu)
                    checkNotNull(materials.taskWorkspace?.eduEvidence).apply {
                        recordPageRead()
                        recordInterfaceAttempt()
                    }
                    val config = AiProviderConfig("fixture", "fixture", "placeholder", "https://fixture.invalid/v1", "fixture",
                        if (responses) AiEndpointStyle.RESPONSES else AiEndpointStyle.CHAT_COMPLETIONS,
                        StructuredOutputMode.PROMPT_ONLY, false, false, false, responses, AiInputMode.AUTO,
                        AiReasoningEffort.NONE, contextWindowTokens = 32_768)
                    val key = if (responses) "input" else "messages"
                    val attachment = AiImportFile(Uri.parse("content://fixture/schedule.txt"), "补充课表.txt", "text/plain",
                        "第八周周二有实验课。".repeat(800).toByteArray())
                    val initial = buildJsonObject {
                        put(key, buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", "检查当前教务") }) })
                    }
                    var reads = 0
                    var attempts = 0
                    var serverRejected = false
                    var compacted = false
                    val result = requestScheduleImport(config, initial,
                        AiImportNetworkContext("EDU_WORKSPACE", interaction = interaction,
                            materials = materials, supplementalFileContext = app)) { request, _ ->
                        assertTrue("Requests should require a bounded number of retries", ++attempts <= 25)
                        val wire = request.toString()
                        val tools = request.getValue("tools").jsonArray.map {
                            val tool = it.jsonObject
                            (if (responses) tool else tool.getValue("function").jsonObject).getValue("name").jsonPrimitive.content
                        }
                        assertTrue(wire.contains("保留原有周次"))
                        if (reads == 0) {
                            assertFalse(ReadImportMaterialTool in tools)
                            assertTrue(interaction.submitFiles(AgentTaskInput("请核对第八周", files = listOf(attachment))))
                        } else {
                            assertTrue(wire.contains("请核对第八周") && wire.contains("file=1: 补充课表.txt"))
                            assertTrue(ReadImportMaterialTool in tools)
                        }
                        if (reads == 4 && !serverRejected) {
                            serverRejected = true
                            throw AiServiceResponseException("窗口已满", """{"error":{"code":"context_length_exceeded"}}""")
                        }
                        if (wire.contains("completed_tool_context")) compacted = true
                        if (reads == 4) assertTrue(compacted)
                        reads++
                        if (reads <= 15) fixtureTool(responses, reads, ReadImportMaterialTool, """{"file":1,"page":0,"offset":0}""")
                        else fixtureTool(responses, reads, ScheduleImportToolName, """{"courses":[]}""")
                    }
                    assertEquals(16, reads)
                    assertTrue(serverRejected && compacted)
                    assertTrue(result.content.contains("courses"))
                    assertEquals(listOf(attachment), interaction.supplementalFiles)
                }
            } finally { interaction.markCancelled() }
        }
        bridge.close()
        webView.destroy()
    }

    private fun fixtureTool(responses: Boolean, index: Int, name: String, args: String) = buildJsonObject {
        if (responses) {
            put("status", "completed")
            put("output", buildJsonArray { add(buildJsonObject {
                put("type", "function_call"); put("call_id", "call-$index"); put("name", name); put("arguments", args)
            }) })
        } else put("choices", buildJsonArray { add(buildJsonObject {
            put("finish_reason", "tool_calls")
            put("message", buildJsonObject {
                put("role", "assistant")
                put("tool_calls", buildJsonArray { add(buildJsonObject {
                    put("id", "call-$index"); put("type", "function")
                    put("function", buildJsonObject { put("name", name); put("arguments", args) })
                }) })
            })
        }) })
    }.toString()

    @Test fun prematureImportIsReturnedToTheWorkspaceInBothProtocolsWithoutLosingCallPairing() {
        val app = RuntimeEnvironment.getApplication()
        val webView = WebView(app).apply { loadUrl("https://fixture.invalid/portal") }
        val bridge = ShiguangBridgeHost({}, {}, {})
        try {
            for (responses in listOf(false, true)) {
                val edu = AiEduWorkspace(app, webView, bridge, defaultConfig(), defaultPeriods(), false) { true }
                AiMaterialWorkspace.fromPages("教务工作区", eduWorkspaceEntryText("https://fixture.invalid/portal", "教务门户"), emptyList()).use { materials ->
                    materials.taskWorkspace = AiTaskWorkspace(app, edu)
                    val evidence = checkNotNull(materials.taskWorkspace?.eduEvidence)
                    val endpoint = if (responses) AiEndpointStyle.RESPONSES else AiEndpointStyle.CHAT_COMPLETIONS
                    val config = AiProviderConfig("fixture", "fixture", "placeholder", "https://fixture.invalid/v1", "fixture",
                        endpoint, StructuredOutputMode.PROMPT_ONLY, false, false, false, responses, AiInputMode.AUTO, AiReasoningEffort.NONE)
                    val key = if (responses) "input" else "messages"
                    val initial = buildJsonObject {
                        put(key, buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", "检查当前教务") }) })
                        put("tools", buildJsonArray { add(if (responses) scheduleImportResponsesTool() else scheduleImportChatTool()) })
                    }
                    var requests = 0
                    val result = requestScheduleImport(config, initial, AiImportNetworkContext("EDU_WORKSPACE", materials = materials)) { request, _ ->
                        requests++
                        val names = request.getValue("tools").jsonArray.map { raw ->
                            val item = raw.jsonObject
                            (if (responses) item else item.getValue("function").jsonObject).getValue("name").jsonPrimitive.content
                        }
                        assertTrue(EduWorkspaceTool in names && TaskWorkspaceTool in names)
                        assertFalse(ReadImportMaterialTool in names)
                        assertTrue(request.toString().contains("交互式教务调查工作区"))
                        if (requests == 2) {
                            val history = request.getValue(key).jsonArray
                            assertTrue(history.any { item -> item.jsonObject[if (responses) "call_id" else "tool_call_id"]?.jsonPrimitive?.contentOrNull == "submit1" })
                            assertTrue(request.toString().contains("候选课表未被接受"))
                            // Simulate completed host investigation; policy behavior is tested separately.
                            evidence.recordPageRead()
                            evidence.recordInterfaceAttempt()
                        }
                        val callId = "submit$requests"
                        if (responses) """{"status":"completed","output":[{"type":"function_call","call_id":"$callId","name":"IMPORT_SCHEDULE","arguments":"{\"courses\":[]}"}]}"""
                        else """{"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":"","tool_calls":[{"id":"$callId","type":"function","function":{"name":"IMPORT_SCHEDULE","arguments":"{\"courses\":[]}"}}]}}]}"""
                    }
                    assertEquals(2, requests)
                    assertTrue(result.content.contains("courses"))
                }
            }
        } finally { bridge.close(); webView.destroy() }
    }
}
