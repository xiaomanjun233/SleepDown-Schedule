package com.xiaomanjun.sleepdownschedule.feature.agent

import android.app.Application
import com.sun.net.httpserver.HttpServer
import com.xiaomanjun.sleepdownschedule.feature.importing.*
import com.xiaomanjun.sleepdownschedule.model.*
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AgentImportSourceImagesTest {
    private val images = listOf(RenderedPageImage(0, "image/jpeg", "cGFnZTE="),
        RenderedPageImage(1, "image/png", "cGFnZTI="))
    private val original = ImportDraft(defaultConfig(), defaultPeriods(), listOf(
        CourseEntity(name = "数学", teacher = null, location = "A101", weekday = 1,
            periods = listOf(1), weeks = listOf(1), weekParity = WeekParity.ALL, note = null)))

    @Test fun chatCarriesAllOriginalImagesInEachUserTurnAndRetainsThemThroughToolCalls() = verify(false)
    @Test fun responsesCarriesAllOriginalImagesInEachUserTurnAndRetainsThemThroughToolCalls() = verify(true)

    @Test fun textOnlyModelsAndRestoredHistoryWithoutImagesKeepTextInput() {
        val settings = AiImportSettings(profile = AiProviderPresets.custom.copy(supportsVision = false,
            capabilities = AiProviderCapabilities(supportsImageInput = false)))
        val history = AiEduImportProgress(screenshotPreviews = images)
        assertTrue(AgentImportRuntime.sourceImages(history, settings).isEmpty())
        val restored = progressFromJson(progressToJson(history))
        assertTrue(AgentImportRuntime.sourceImages(restored, settings.copy(profile = settings.profile.copy(supportsVision = true))).isEmpty())
        assertTrue(agentUserMessage("继续修改", emptyList())["content"] is JsonPrimitive)
    }

    private fun verify(responses: Boolean) = runBlocking {
        val requests = mutableListOf<JsonObject>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        fun patch(location: String) = buildJsonObject {
            put("revision", 0)
            put("operationsJson", """[{"op":"replace","path":"/courses/0/location","value":"$location"}]""")
            put("summary", "修改地点")
        }.toString()
        server.createContext("/") { exchange ->
            val index = requests.size
            requests += Json.parseToJsonElement(exchange.requestBody.bufferedReader().use { it.readText() }).jsonObject
            val editing = index % 2 == 0
            val patch = patch(if (index < 2) "A202" else "A203")
            val body = if (responses) buildJsonObject {
                put("status", "completed")
                put("output", buildJsonArray { add(if (editing) buildJsonObject {
                    put("type", "function_call"); put("call_id", "edit-$index")
                    put("name", "PATCH_IMPORT_JSON"); put("arguments", patch)
                } else buildJsonObject {
                    put("type", "message"); put("role", "assistant")
                    put("content", buildJsonArray { add(buildJsonObject { put("type", "output_text"); put("text", "已更新地点，请确认预览。") }) })
                }) })
            } else buildJsonObject {
                put("choices", buildJsonArray { add(buildJsonObject {
                    put("finish_reason", if (editing) "tool_calls" else "stop")
                    put("message", buildJsonObject {
                        put("role", "assistant"); put("content", if (editing) "" else "已更新地点，请确认预览。")
                        if (editing) put("tool_calls", buildJsonArray { add(buildJsonObject {
                            put("id", "edit-$index"); put("type", "function")
                            put("function", buildJsonObject { put("name", "PATCH_IMPORT_JSON"); put("arguments", patch) })
                        }) })
                    })
                }) })
            }
            val bytes = body.toString().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        val context = RuntimeEnvironment.getApplication()
        AiImportHistoryStore.clear(context)
        try {
            val settings = AiImportSettings(profile = AiProviderPresets.custom.copy(
                baseUrl = "http://127.0.0.1:${server.address.port}", defaultModel = "test-vision",
                endpointStyle = if (responses) AiEndpointStyle.RESPONSES else AiEndpointStyle.CHAT_COMPLETIONS,
                supportsVision = true), apiKey = "test-only")
            var draft = original
            var history = AiEduImportProgress(taskId = "test", userPrompt = "导入原图", screenshotPreviews = images,
                pageText = "原始课程文字")
            repeat(2) { turn ->
                AiImportHistoryStore.record(context, draft, history)
                history = AiImportHistoryStore.loadEntry(context, AiImportHistoryStore.load(context).single().id)!!.context!!
                val result = AgentImportRuntime.revise(context, draft,
                    "第${turn + 1}轮修改地点", history, settings, null, {})
                draft = ScheduleImportParser.parseStoredDraft(result.output, draft.config).getOrThrow()
                history = history.copy(userPrompt = "第${turn + 1}轮修改地点", assistantMessage = result.routeMessage,
                    conversationTurns = archiveImportTurn(history, draft))
            }
            assertEquals("A203", draft.courses.single().location)
            assertEquals(4, requests.size)
            requests.forEach { request ->
                val messages = request.getValue(if (responses) "input" else "messages").jsonArray
                val imageParts = messages.flatMap { (it.jsonObject["content"] as? JsonArray).orEmpty() }
                    .filter { it.jsonObject["type"]?.jsonPrimitive?.content == if (responses) "input_image" else "image_url" }
                assertEquals("One copy of each source page per request", 2, imageParts.size)
                assertEquals(images.map { it.dataUrl }, imageParts.map {
                    if (responses) it.jsonObject.getValue("image_url").jsonPrimitive.content
                    else it.jsonObject.getValue("image_url").jsonObject.getValue("url").jsonPrimitive.content
                })
                assertTrue(request.toString().contains("原始课程文字"))
                assertTrue(request.toString().contains("不因原图不同而撤销之前的修改"))
            }
        } finally { server.stop(0); AiImportHistoryStore.clear(context) }
    }
}
