package com.xiaomanjun.sleepdownschedule.feature.agent

import com.sun.net.httpserver.HttpServer
import com.xiaomanjun.sleepdownschedule.feature.importing.*
import com.xiaomanjun.sleepdownschedule.model.*
import java.net.InetSocketAddress
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AgentImportWorkspaceTest {
    private fun source() = ImportDraft(defaultConfig().copy(currentWeek = 3), defaultPeriods(), listOf(
        CourseEntity(name = "数学", teacher = "王老师", location = "A101", weekday = 1,
            periods = listOf(1, 2), weeks = (1..20).toList(), weekParity = WeekParity.ALL, note = " 保留备注 ",
            customStartTime = "08:10", customEndTime = "09:50",
            customPeriodTimes = "1,08:10-08:55;2,09:05-09:50",
            originalPeriodTimes = "1,08:00-08:45;2,08:55-09:40"),
        CourseEntity(name = "英语", teacher = null, location = "B201", weekday = 2,
            periods = listOf(3), weeks = (1..20).toList(), weekParity = WeekParity.ALL, note = null)
    ), ImportDraftSource.AI_EDU)

    private fun patch(workspace: AgentImportWorkspace, operations: String, revision: Int = workspace.revision) =
        workspace.execute(decodeAgentToolCall("edit-$revision", "PATCH_IMPORT_JSON", buildJsonObject {
            put("revision", revision)
            put("operationsJson", operations)
            put("summary", "调整草稿")
        }))

    @Test fun everyValidStageIsImmutableAndOmittedFieldsAndTrustedBellsSurvive() {
        val checkpoints = mutableListOf<ImportDraft>()
        val workspace = AgentImportWorkspace(source()) { draft, _ -> checkpoints += draft }
        val original = workspace.draft
        assertTrue(patch(workspace, """[{"op":"replace","path":"/courses/0/location","value":"A202"}]""").success)
        assertTrue(patch(workspace, """[{"op":"replace","path":"/scheduleConfig/totalWeeks","value":22},{"op":"replace","path":"/scheduleConfig/periods/2/startTime","value":"10:05"}]""").success)
        assertEquals(2, checkpoints.size)
        assertEquals(20, checkpoints[0].config.totalWeeks)
        assertEquals("10:00", checkpoints[0].periods[2].startTime)
        assertEquals(22, checkpoints[1].config.totalWeeks)
        assertEquals("10:05", checkpoints[1].periods[2].startTime)
        assertEquals(original.courses[0].copy(location = "A202"), checkpoints[0].courses[0])
        assertEquals(original.courses[1], workspace.draft.courses[1])
        assertEquals(original.config.copy(totalWeeks = 22), workspace.draft.config)
        assertEquals(original.source, workspace.draft.source)
    }

    @Test fun incompleteInvalidAndStalePatchesNeverPublishOrPartiallyApply() {
        val checkpoints = mutableListOf<ImportDraft>()
        val workspace = AgentImportWorkspace(source()) { draft, _ -> checkpoints += draft }
        val original = workspace.draft
        for (invalid in listOf("[", "{}",
            """[{"op":"replace","path":"/courses/0/location","value":"changed"},{"op":"replace","path":"/courses/1/weekday","value":9}]""",
            """[{"op":"replace","path":"/scheduleConfig/totalWeeks","value":5}]""",
            """[{"op":"replace","path":"/scheduleConfig/periods/0/endTime","value":"07:00"}]""")) {
            assertFalse(invalid, patch(workspace, invalid).success)
            assertEquals(original, workspace.draft)
            assertEquals(0, workspace.revision)
            assertTrue(checkpoints.isEmpty())
        }
        val valid = """[{"op":"replace","path":"/courses/0/location","value":"A202"}]"""
        assertTrue(patch(workspace, valid).success)
        assertFalse(patch(workspace, valid, revision = 0).success)
        assertEquals(1, checkpoints.size)
    }

    @Test fun rejectedCheckpointAndCancellationDoNotAdvanceWorkspace() {
        val operations = """[{"op":"replace","path":"/courses/0/location","value":"A202"}]"""
        val rejected = AgentImportWorkspace(source()) { _, _ -> throw IllegalArgumentException("阶段已达上限") }
        val original = rejected.draft
        assertFalse(patch(rejected, operations).success)
        assertEquals(original, rejected.draft)
        assertEquals(0, rejected.revision)
        val cancelled = AgentImportWorkspace(source()) { _, _ -> throw java.util.concurrent.CancellationException("paused") }
        assertThrows(java.util.concurrent.CancellationException::class.java) { patch(cancelled, operations) }
        assertEquals(original, cancelled.draft)
        assertEquals(0, cancelled.revision)
    }

    @Test fun truncatedChatDecisionCannotPromoteEvenAParseablePatch() {
        for (reason in listOf("length", "content_filter", "error")) {
            assertThrows(IllegalArgumentException::class.java) {
                parseAgentToolDecision("""{"choices":[{"finish_reason":"$reason","message":{"tool_calls":[{"id":"edit","function":{"name":"PATCH_IMPORT_JSON","arguments":"{}"}}]}}]}""")
            }
        }
    }

    @Test fun applicationToolsAndForgedSourceTimesAreBlockedAtExecutionBoundary() {
        val workspace = AgentImportWorkspace(source())
        for (name in AgentToolName.entries.filter { it !in setOf(AgentToolName.READ_IMPORT_JSON, AgentToolName.PATCH_IMPORT_JSON) }) {
            assertFalse(name.name, workspace.execute(AgentToolCall("forbidden", name)).success)
        }
        for (path in listOf("/courses/0/_courseId", "/courses/0/customPeriodTimes", "/courses/0/originalPeriodTimes",
            "/scheduleConfig/notificationsEnabled", "/courses")) {
            assertFalse(path, patch(workspace, """[{"op":"replace","path":"$path","value":null}]""").success)
        }
        assertFalse(patch(workspace, """[{"op":"add","path":"/courses/-","value":{"name":"伪造","customPeriodTimes":"1,10:00-11:00"}}]""").success)
        val expected = setOf("READ_IMPORT_JSON", "PATCH_IMPORT_JSON")
        assertEquals(expected, agentToolDefinitions(includeMiMoWebSearch = true, includeMemoryTool = true, importWorkspace = true)
            .map { it.jsonObject.getValue("function").jsonObject.getValue("name").jsonPrimitive.content }.toSet())
        assertEquals(expected, agentResponsesToolDefinitions(includeMemoryTool = true, importWorkspace = true)
            .map { it.jsonObject.getValue("name").jsonPrimitive.content }.toSet())
    }

    @Test fun currentWeekSplitRetainsOtherWeeksAndTrustedBellsWithoutInventingMetadata() {
        val workspace = AgentImportWorkspace(source(), onlyCurrentWeek = true)
        val original = workspace.draft
        assertFalse(patch(workspace, """[{"op":"replace","path":"/courses/0/location","value":"A202"}]""").success)
        assertFalse(patch(workspace, """[{"op":"replace","path":"/scheduleConfig/totalWeeks","value":22}]""").success)
        val otherWeeks = (1..20).filter { it != 3 }.joinToString(",")
        assertTrue(patch(workspace, """[
            {"op":"copy","from":"/courses/0","path":"/courses/-"},
            {"op":"replace","path":"/courses/0/weeks","value":[$otherWeeks]},
            {"op":"replace","path":"/courses/2/weeks","value":[3]},
            {"op":"replace","path":"/courses/2/location","value":"A202"}
        ]""").success)
        assertEquals(original.courses[0].copy(weeks = (1..20).filter { it != 3 }), workspace.draft.courses[0])
        assertEquals(original.courses[0].copy(id = workspace.draft.courses[2].id, weeks = listOf(3), location = "A202"), workspace.draft.courses[2])
        assertEquals(3, workspace.draft.courses.map { it.id }.distinct().size)
    }

    @Test fun explicitCourseTimesCanChangeWithoutReusingInconsistentTrustedBells() {
        val workspace = AgentImportWorkspace(source())
        assertTrue(patch(workspace, """[
            {"op":"replace","path":"/courses/0/customStartTime","value":"08:20"},
            {"op":"replace","path":"/courses/0/customEndTime","value":"10:00"}
        ]""").success)
        val course = workspace.draft.courses[0]
        assertEquals("08:20", course.customStartTime)
        assertEquals("10:00", course.customEndTime)
        assertNull(course.customPeriodTimes)
        assertEquals(" 保留备注 ", course.note)
    }

    @Test fun workspaceReadsAreFreshAfterPatchEvenWithTheSameToolArguments() {
        val workspace = AgentImportWorkspace(source())
        val session = AgentTurnToolSession(execute = workspace::execute)
        val read = AgentToolCall("read", AgentToolName.READ_IMPORT_JSON)
        assertTrue(session.run(read).content.contains("A101"))
        assertTrue(patch(workspace, """[{"op":"replace","path":"/courses/0/location","value":"A202"}]""").success)
        val second = session.run(read.copy(id = "read-again"))
        assertTrue(second.content.contains("A202"))
        assertTrue(second.content.contains("\"revision\":1"))
    }

    @Test fun realResponsesTaskLoopContinuesAcrossTwoValidatedEditsAndFreshRead() {
        val checkpoints = mutableListOf<ImportDraft>()
        val workspace = AgentImportWorkspace(source()) { draft, _ -> checkpoints += draft }
        val calls = listOf(
            functionCall("read", "READ_IMPORT_JSON", "{}"),
            functionCall("edit-1", "PATCH_IMPORT_JSON", patchArguments(0,
                """[{"op":"replace","path":"/courses/0/location","value":"A202"}]""")),
            functionCall("read-again", "READ_IMPORT_JSON", "{}"),
            functionCall("edit-2", "PATCH_IMPORT_JSON", patchArguments(1,
                """[{"op":"replace","path":"/scheduleConfig/totalWeeks","value":22}]"""))
        )
        val responses = calls.map { response(it) } + response(buildJsonObject {
            put("type", "message"); put("role", "assistant")
            put("content", buildJsonArray { add(buildJsonObject { put("type", "output_text"); put("text", "已完成地点和总周数调整，请选择草稿预览。") }) })
        })
        val requests = mutableListOf<JsonObject>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/responses") { exchange ->
            val index = requests.size
            requests += Json.parseToJsonElement(exchange.requestBody.bufferedReader().use { it.readText() }).jsonObject
            val body = responses.getOrElse(index) { "unexpected extra request" }.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(if (index < responses.size) 200 else 500, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
            exchange.close()
        }
        server.start()
        try {
            val result = OpenAiResponsesAgentRunner().chat(
                settings = AiImportSettings(profile = AiProviderPresets.openAI.copy(
                    baseUrl = "http://127.0.0.1:${server.address.port}", responsesPath = "responses"), apiKey = "unit-test"),
                chatMessages = listOf(buildJsonObject { put("role", "user"); put("content", "修改地点和总周数") }),
                includeMemoryTool = false, onStatus = {}, onDelta = {}, onStreamReset = {},
                executeTool = workspace::execute, validateAnswer = workspace::validateAnswer,
                telemetry = DayAgentTurnTelemetry("unit-test", elapsedRealtime = { 100L }), importWorkspace = true)
            assertEquals("已完成地点和总周数调整，请选择草稿预览。", result)
            assertEquals(5, requests.size)
            assertEquals(2, checkpoints.size)
            assertEquals(20, checkpoints[0].config.totalWeeks)
            assertEquals(22, checkpoints[1].config.totalWeeks)
            assertEquals("A202", checkpoints[1].courses[0].location)
            requests.forEach { request ->
                assertEquals(setOf("READ_IMPORT_JSON", "PATCH_IMPORT_JSON"), request.getValue("tools").jsonArray
                    .map { it.jsonObject.getValue("name").jsonPrimitive.content }.toSet())
            }
            assertTrue(requests[3].toString().contains("A202"))
        } finally {
            server.stop(0)
        }
    }

    private fun patchArguments(revision: Int, operations: String) = buildJsonObject {
        put("revision", revision); put("operationsJson", operations); put("summary", "调整草稿")
    }.toString()

    private fun functionCall(id: String, name: String, arguments: String) = buildJsonObject {
        put("type", "function_call"); put("call_id", id); put("name", name); put("arguments", arguments)
    }

    private fun response(item: JsonObject) = buildJsonObject {
        put("status", "completed"); put("output", JsonArray(listOf(item)))
    }.toString()
}
