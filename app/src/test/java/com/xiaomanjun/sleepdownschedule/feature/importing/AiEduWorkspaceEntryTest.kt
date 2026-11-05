package com.xiaomanjun.sleepdownschedule.feature.importing

import android.app.Application
import android.webkit.WebView
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
