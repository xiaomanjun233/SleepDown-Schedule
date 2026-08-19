package com.xiaomanjun.sleepdownschedule

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolsTest {
    @Test
    fun courseReadToolExposesExactCustomTimeToTheAgent() {
        val course = CourseEntity(
            id = 7L,
            name = "无机非金属材料学",
            teacher = null,
            location = null,
            weekday = 1,
            periods = listOf(1),
            weeks = listOf(1),
            weekParity = WeekParity.ALL,
            note = null,
            customStartTime = "10:10",
            customEndTime = "11:55",
            scheduleId = 1
        )
        val facts = DayAgentFacts(
            date = java.time.LocalDate.of(2026, 8, 18),
            now = java.time.LocalDateTime.of(2026, 8, 18, 9, 0),
            today = emptyList(),
            tomorrow = emptyList(),
            week = emptyList(),
            weather = null,
            sourceHash = "test",
            semesterCourses = listOf(course),
            scheduleId = 1
        )

        val result = executeAgentReadTools(
            listOf(AgentToolCall("semester", AgentToolName.GET_SEMESTER_SCHEDULE)),
            facts
        ).single()

        assertTrue(result.content.contains("自定义时间=10:10-11:55"))
    }

    @Test
    fun persistedExecutionTraceCanBeReopenedWithoutEnteringModelHistory() {
        val statuses = listOf(
            AgentRunStatus(AgentRunStatusIcon.THINKING, "分析请求"),
            AgentRunStatus(AgentRunStatusIcon.SCHEDULE, "读取本周课表"),
            AgentRunStatus(AgentRunStatusIcon.THINKING, "整理结果")
        )
        val stored = agentMessageWithRunTrace("这是最终答复。", statuses)
        val parsed = parseAgentStoredMessage(stored)

        assertEquals("这是最终答复。", parsed.content)
        assertEquals(statuses, parsed.statuses)
        assertFalse(parsed.content.contains("agent_run_trace"))
    }

    @Test
    fun deepSeekFinalRequestExplicitlyDisablesNativeTools() {
        val body = DayAgentChatTransport().agentBody(
            settings = AiImportSettings(
                profile = AiProviderPresets.deepSeek,
                apiKey = "test-key"
            ),
            messages = listOf(buildJsonObject {
                put("role", "system")
                put("content", DayAgentPrompts.FinalAnswerStage)
            }),
            stream = true,
            includeTools = false
        )
        val json = Json.parseToJsonElement(body).jsonObject

        assertFalse(json.containsKey("tools"))
        assertEquals("none", json["tool_choice"]?.jsonPrimitive?.content)
        val finalInstruction = json["messages"]?.jsonArray?.single()?.jsonObject
            ?.get("content")?.jsonPrimitive?.content.orEmpty()
        assertTrue(finalInstruction.contains("DSML"))
        assertTrue(finalInstruction.contains("<agent_actions>"))
    }

    @Test
    fun splitDsmlProtocolNeverReachesStreamingUi() {
        val forwarded = mutableListOf<String>()
        val gate = AgentFinalOutputGate(forwarded::add)

        gate.accept("<｜DSM")
        val rejected = runCatching { gate.accept("L｜tool_calls>") }.exceptionOrNull()

        assertTrue(rejected is AgentProtocolViolationException)
        assertTrue(forwarded.isEmpty())
        assertEquals("", sanitizeAgentToolOutput("<｜DSML｜invoke name=\"OPEN_SETTINGS\">"))
    }

    @Test
    fun normalFinalAnswerStillStreamsAndFlushesHeldTail() {
        val forwarded = mutableListOf<String>()
        val gate = AgentFinalOutputGate(forwarded::add, holdBackCharacters = 8)
        val answer = "可以打开通知设置。<agent_actions>[]</agent_actions>"

        gate.accept(answer.take(14))
        gate.accept(answer.drop(14))
        assertEquals(answer, gate.finish(answer))
        assertEquals(answer, forwarded.joinToString(""))
    }

    @Test
    fun nullToolCallsMeansTheModelHasReturnedItsFinalAnswer() {
        val decision = parseAgentToolDecision(
            """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "content": "已经读取完成，当前课表共有 6 门课。",
                      "tool_calls": null
                    }
                  }]
                }
            """.trimIndent()
        )

        assertTrue(decision.calls.isEmpty())
        assertEquals("已经读取完成，当前课表共有 6 门课。", decision.content)
        assertFalse(decision.webSearchUsed)
    }

    @Test
    fun officialWebSearchIsReportedOnlyFromActualProviderEvidence() {
        val decision = parseAgentToolDecision(
            """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "content": "这是搜索后的结论。",
                      "annotations": [{
                        "type": "url_citation",
                        "url": "https://example.edu/schedule"
                      }],
                      "tool_calls": null
                    }
                  }],
                  "usage": {
                    "web_search_usage": {
                      "tool_usage": 1,
                      "page_usage": 1
                    }
                  }
                }
            """.trimIndent()
        )

        assertTrue(decision.webSearchUsed)
        assertTrue(decision.calls.isEmpty())
    }

    @Test
    fun toolArgumentsAcceptBothObjectAndProviderJsonStringFormats() {
        val objectArguments = parseAgentToolDecision(
            """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "content": null,
                      "tool_calls": [{
                        "id": "object-call",
                        "type": "function",
                        "function": {
                          "name": "SEARCH_COURSES",
                          "arguments": {"query": "材料化学"}
                        }
                      }]
                    }
                  }]
                }
            """.trimIndent()
        )
        val stringArguments = parseAgentToolDecision(
            """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "content": null,
                      "tool_calls": [{
                        "id": "string-call",
                        "type": "function",
                        "function": {
                          "name": "SEARCH_COURSES",
                          "arguments": "{\"query\":\"材料化学\"}"
                        }
                      }]
                    }
                  }]
                }
            """.trimIndent()
        )

        assertEquals("材料化学", objectArguments.calls.single().arguments["query"])
        assertEquals("材料化学", stringArguments.calls.single().arguments["query"])
    }

    @Test
    fun toolRoundPreservesReasoningAndAcceptsNormalizedFunctionNames() {
        val decision = parseAgentToolDecision(
            """
                {
                  "choices": [{
                    "finish_reason": "tool_calls",
                    "message": {
                      "role": "assistant",
                      "content": null,
                      "reasoning_content": "需要读取当前节次。",
                      "tool_calls": [{
                        "id": "period-call",
                        "type": "function",
                        "function": {
                          "name": "get-periods",
                          "arguments": "{}"
                        }
                      }]
                    }
                  }]
                }
            """.trimIndent()
        )

        assertEquals(AgentToolName.GET_PERIODS, decision.calls.single().name)
        assertEquals("tool_calls", decision.finishReason)
        assertEquals("需要读取当前节次。", decision.reasoning)
        assertEquals(
            "需要读取当前节次。",
            decision.assistantMessage["reasoning_content"]?.jsonPrimitive?.content
        )
    }

    @Test
    fun providerContentPartsAreCombinedAsFinalText() {
        val content = buildJsonArray {
            add(buildJsonObject {
                put("type", "text")
                put("text", "已准备修改。")
            })
            add(buildJsonObject {
                put("type", "text")
                put("text", "<agent_actions>[]</agent_actions>")
            })
        }

        assertEquals(
            "已准备修改。<agent_actions>[]</agent_actions>",
            agentTextFromJson(content)
        )
    }

    @Test
    fun everyToolIsHardScopedToCurrentSchedule() {
        val active = course(1, "当前课表课程", scheduleId = 1)
        val inactive = course(2, "其他课表课程", scheduleId = 2)
        val date = LocalDate.of(2026, 7, 27)
        val facts = DayAgentFacts(
            date = date,
            now = LocalDateTime.of(date, LocalTime.of(8, 0)),
            today = listOf(
                AgentCourseSlot(active, date, LocalTime.of(8, 0), LocalTime.of(8, 45)),
                AgentCourseSlot(inactive, date, LocalTime.of(9, 0), LocalTime.of(9, 45))
            ),
            tomorrow = emptyList(),
            week = emptyList(),
            weather = null,
            sourceHash = "test",
            periodDefinitions = listOf(
                PeriodEntity(1, "08:00", "08:45", scheduleId = 1),
                PeriodEntity(9, "19:00", "19:45", scheduleId = 2)
            ),
            scheduleId = 1,
            semesterCourses = listOf(active, inactive)
        )
        val calls = AgentToolName.entries.mapIndexed { index, name ->
            AgentToolCall(index.toString(), name, mapOf("query" to "课程"))
        }

        val rendered = executeAgentReadTools(calls, facts)
            .joinToString("\n") { it.content }

        assertTrue(rendered.contains("当前课表课程"))
        assertFalse(rendered.contains("其他课表课程"))
        assertFalse(rendered.contains("第9节"))
    }

    @Test
    fun populatedTimelineIsNotErasedWhenSemesterSnapshotIsTemporarilyEmpty() {
        val active = course(7, "刚导入的课程", scheduleId = 4)
        val date = LocalDate.of(2026, 7, 27)
        val facts = DayAgentFacts(
            date = date,
            now = LocalDateTime.of(date, LocalTime.of(8, 0)),
            today = listOf(
                AgentCourseSlot(active, date, LocalTime.of(8, 0), LocalTime.of(8, 45))
            ),
            tomorrow = emptyList(),
            week = listOf(
                AgentCourseSlot(active, date, LocalTime.of(8, 0), LocalTime.of(8, 45))
            ),
            weather = null,
            sourceHash = "import-transition",
            periodDefinitions = listOf(PeriodEntity(1, "08:00", "08:45", scheduleId = 4)),
            scheduleId = 4,
            semesterCourses = emptyList()
        )

        val results = executeAgentReadTools(
            listOf(
                AgentToolCall("semester", AgentToolName.GET_SEMESTER_SCHEDULE),
                AgentToolCall(
                    "search",
                    AgentToolName.SEARCH_COURSES,
                    mapOf("query" to "刚导入的课程")
                )
            ),
            facts
        )

        assertTrue(results.all { it.content.contains("刚导入的课程") })
        assertTrue(results.all { it.content.contains("当前课表ID=4") })
    }

    @Test
    fun toolPayloadIsRemovedWithoutEatingFollowingAnswer() {
        val raw = """
            我先读取一下设置。
            <tool_result name="GET_SETTINGS" id="settings">
            DARK_MODE=false
            </tool_result>
            当前应用正在跟随系统深浅模式。
        """.trimIndent()

        assertEquals(
            "当前应用正在跟随系统深浅模式。",
            sanitizeAgentToolOutput(raw)
        )
        assertEquals("我先读取一下设置。", extractAgentToolPrelude(raw))
    }

    @Test
    fun incompleteStreamingToolPayloadStaysHidden() {
        val partial = """
            我先读取一下设置。
            <tool_result name="GET_SETTINGS" id="settings">
            DARK_MODE=false
        """.trimIndent()

        assertEquals("", sanitizeAgentToolOutput(partial))
        assertEquals("我先读取一下设置。", extractAgentToolPrelude(partial))
    }

    @Test
    fun fakeToolCallIsRemovedAndCannotReplaceFinalAnswer() {
        val raw = """
            <tool_call>
            {"name":"GET_COURSES","id":"courses"}
            </tool_call>
            当前课表共有 2 门课。
        """.trimIndent()

        assertEquals("当前课表共有 2 门课。", sanitizeAgentToolOutput(raw))
    }

    @Test
    fun settingsToolReturnsCurrentValuesInsteadOfCatalogOnly() {
        val facts = DayAgentFacts(
            date = LocalDate.of(2026, 7, 27),
            now = LocalDateTime.of(2026, 7, 27, 8, 0),
            today = emptyList(),
            tomorrow = emptyList(),
            week = emptyList(),
            weather = null,
            sourceHash = "settings",
            scheduleId = 3,
            settingSnapshot = mapOf(
                "WALLPAPER_BLUR_PERCENT" to "42",
                "COURSE_CARD_GLASS_ENABLED" to "true",
                "MORNING_PERIOD_COUNT" to "4",
                "NOON_PERIOD_COUNT" to "2",
                "AFTERNOON_PERIOD_COUNT" to "5",
                "EVENING_PERIOD_COUNT" to "3",
                "WALLPAPER_PRESENT" to "true",
                "WALLPAPER_PORTRAIT_SCALE" to "1.25"
            )
        )

        val result = executeAgentReadTools(
            listOf(AgentToolCall("settings", AgentToolName.GET_SETTINGS)),
            facts
        ).single().content

        assertTrue(result.contains("WALLPAPER_BLUR_PERCENT"))
        assertTrue(result.contains("当前=42"))
        assertTrue(result.contains("COURSE_CARD_GLASS_ENABLED"))
        assertTrue(result.contains("当前=true"))
        assertTrue(result.contains("MORNING_PERIOD_COUNT"))
        assertTrue(result.contains("当前=4"))
        assertTrue(result.contains("WALLPAPER_PORTRAIT_SCALE"))
        assertTrue(result.contains("当前=1.25"))
    }

    @Test
    fun overviewAndWeekToolExposeUpcomingTermInsteadOfWeekOne() {
        val facts = DayAgentFacts(
            date = LocalDate.of(2026, 9, 1),
            now = LocalDateTime.of(2026, 9, 1, 8, 0),
            today = emptyList(),
            tomorrow = emptyList(),
            week = emptyList(),
            weather = null,
            sourceHash = "upcoming",
            scheduleId = 3,
            currentWeek = 1,
            termState = ScheduleTermState.UPCOMING,
            termStatus = "暂未开学"
        )

        val results = executeAgentReadTools(
            listOf(
                AgentToolCall("overview", AgentToolName.GET_CURRENT_OVERVIEW),
                AgentToolCall("week", AgentToolName.GET_WEEK_SCHEDULE)
            ),
            facts
        )

        assertTrue(results[0].content.contains("学期状态=UPCOMING（暂未开学）"))
        assertTrue(results[0].content.contains("当前有效教学周=无"))
        assertTrue(results[1].content.contains("当前没有有效教学周"))
    }

    @Test
    fun periodsToolReturnsTopologyAndEveryPersistedScheme() {
        val facts = DayAgentFacts(
            date = LocalDate.of(2026, 7, 27),
            now = LocalDateTime.of(2026, 7, 27, 8, 0),
            today = emptyList(),
            tomorrow = emptyList(),
            week = emptyList(),
            weather = null,
            sourceHash = "period-schemes",
            scheduleId = 3,
            settingSnapshot = mapOf(
                "MORNING_PERIOD_COUNT" to "4",
                "NOON_PERIOD_COUNT" to "2",
                "AFTERNOON_PERIOD_COUNT" to "4",
                "EVENING_PERIOD_COUNT" to "2"
            ),
            periodDefinitions = listOf(PeriodEntity(1, "08:00", "08:45", 3)),
            periodSchemes = listOf(
                AgentPeriodSchemeSnapshot(
                    id = 9,
                    name = "夏令时",
                    mode = PeriodSchemeMode.AUTO_MATCH,
                    isActive = true,
                    classDurationMinutes = 45,
                    breakDurationMinutes = 10,
                    morningStartTime = "08:00",
                    noonStartTime = "12:10",
                    afternoonStartTime = "14:00",
                    eveningStartTime = "19:20",
                    specialBreaks = mapOf(2 to 20),
                    overriddenPeriods = setOf(6),
                    times = listOf(PeriodSchemeTimeEntity(9, 1, "08:00", "08:45"))
                )
            ),
            activePeriodSchemeId = 9
        )

        val result = executeAgentReadTools(
            listOf(AgentToolCall("periods", AgentToolName.GET_PERIODS)),
            facts
        ).single().content

        assertTrue(result.contains("上午=4"))
        assertTrue(result.contains("名称=夏令时"))
        assertTrue(result.contains("模式=AUTO_MATCH"))
        assertTrue(result.contains("特殊课间={2=20}"))
        assertTrue(result.contains("第1节 08:00-08:45"))
    }

    @Test
    fun courseSearchDoesNotLeakWholeScheduleWhenNothingMatches() {
        val active = course(1, "真实课程", scheduleId = 3)
        val facts = DayAgentFacts(
            date = LocalDate.of(2026, 7, 27),
            now = LocalDateTime.of(2026, 7, 27, 8, 0),
            today = emptyList(),
            tomorrow = emptyList(),
            week = emptyList(),
            weather = null,
            sourceHash = "search",
            scheduleId = 3,
            semesterCourses = listOf(active)
        )

        val result = executeAgentReadTools(
            listOf(
                AgentToolCall(
                    "courses",
                    AgentToolName.SEARCH_COURSES,
                    mapOf("query" to "不存在的课程")
                )
            ),
            facts
        ).single().content

        assertTrue(result.contains("读取成功"))
        assertTrue(result.contains("没有匹配课程"))
        assertFalse(result.contains("真实课程"))
    }

    @Test
    fun modelReceivesAllToolsAndSearchSchemaRequiresQuery() {
        val definitions = agentToolDefinitions().toString()

        listOf(
            AgentToolName.GET_CURRENT_OVERVIEW,
            AgentToolName.SEARCH_COURSES,
            AgentToolName.GET_WEEK_SCHEDULE,
            AgentToolName.GET_SEMESTER_SCHEDULE,
            AgentToolName.GET_PERIODS,
            AgentToolName.GET_SETTINGS
        ).forEach { tool -> assertTrue(definitions.contains("\"name\":\"${tool.name}\"")) }
        assertTrue(definitions.contains("\"required\":[\"query\"]"))
        assertTrue(definitions.contains("\"additionalProperties\":false"))
    }

    @Test
    fun officialResponsesToolsUseFlattenedStrictFunctionSchema() {
        val definitions = agentResponsesToolDefinitions(includeMemoryTool = true)

        assertTrue(definitions.isNotEmpty())
        definitions.forEach { element ->
            val definition = element.jsonObject
            assertEquals("function", definition["type"]?.jsonPrimitive?.content)
            assertTrue(definition["name"]?.jsonPrimitive?.content.orEmpty().isNotBlank())
            assertEquals(true, definition["strict"]?.jsonPrimitive?.boolean)
            assertTrue(definition["parameters"]?.jsonObject?.containsKey("additionalProperties") == true)
            assertFalse(definition.containsKey("function"))
        }
    }

    @Test
    fun responsesFunctionCallKeepsCallIdAndDoesNotExposeReasoningSummary() {
        val turn = parseAgentResponsesTurn(
            """
                {
                  "status": "completed",
                  "output": [
                    {
                      "id": "rs_1",
                      "type": "reasoning",
                      "summary": [{"type": "summary_text", "text": "内部处理摘要"}]
                    },
                    {
                      "id": "fc_1",
                      "type": "function_call",
                      "call_id": "call_schedule",
                      "name": "get-week-schedule",
                      "arguments": "{\"unused\":\"value\"}"
                    }
                  ]
                }
            """.trimIndent()
        )

        assertEquals("call_schedule", turn.calls.single().id)
        assertEquals(AgentToolName.GET_WEEK_SCHEDULE, turn.calls.single().name)
        assertEquals("value", turn.calls.single().arguments["unused"])
        assertEquals("", turn.content)
        assertEquals(0, turn.unparsedToolCallCount)
        assertEquals(2, turn.outputItems.size)
    }

    @Test
    fun responsesFinalTextReadsOutputMessageWithoutDuplicatingTopLevelText() {
        val turn = parseAgentResponsesTurn(
            """
                {
                  "status": "completed",
                  "output_text": "课表读取完成。",
                  "output": [{
                    "id": "msg_1",
                    "type": "message",
                    "role": "assistant",
                    "content": [{
                      "type": "output_text",
                      "text": "课表读取完成。"
                    }]
                  }]
                }
            """.trimIndent()
        )

        assertEquals("课表读取完成。", turn.content)
        assertTrue(turn.calls.isEmpty())
    }

    @Test
    fun contextKeepsOnlyTheLastCompletedUserAssistantPair() {
        fun message(id: Long, role: String, content: String, status: String) =
            AgentMessageEntity(
                id = id,
                scheduleId = 1,
                sessionDate = "2026-07-30",
                role = role,
                content = content,
                createdAt = id,
                status = status
            )

        val compact = compactAgentHistory(
            listOf(
                message(1, "user", "上一轮问题", "READY"),
                message(2, "assistant", "上一轮答案", "READY"),
                message(3, "user", "失败问题", "FAILED"),
                message(4, "user", "仍在发送的问题", "PENDING")
            )
        )

        assertEquals(listOf("上一轮问题", "上一轮答案"), compact.map { it.content })
    }

    @Test
    fun officialSupportedMiMoModelReceivesAutonomousWebSearchAlongsideLocalTools() {
        assertTrue(
            supportsMiMoOfficialWebSearch(
                providerId = "mimo",
                baseUrl = "https://api.xiaomimimo.com/v1",
                model = "mimo-v2.5-pro"
            )
        )
        val definitions = agentToolDefinitions(includeMiMoWebSearch = true)
        val webSearch = definitions
            .map { it.jsonObject }
            .single { it["type"]?.jsonPrimitive?.content == "web_search" }

        assertEquals(false, webSearch["force_search"]?.jsonPrimitive?.boolean)
        assertEquals(3, webSearch["max_keyword"]?.jsonPrimitive?.int)
        assertEquals(1, webSearch["limit"]?.jsonPrimitive?.int)
        assertTrue(
            definitions.jsonArray.any {
                it.jsonObject["function"]?.jsonObject
                    ?.get("name")?.jsonPrimitive?.content == "GET_CURRENT_OVERVIEW"
            }
        )
    }

    @Test
    fun webSearchIsNotSentToUnsupportedModelsOrCompatibleThirdPartyEndpoints() {
        assertFalse(
            supportsMiMoOfficialWebSearch(
                providerId = "mimo",
                baseUrl = "https://api.xiaomimimo.com/v1",
                model = "mimo-v2"
            )
        )
        assertTrue(
            supportsMiMoOfficialWebSearch(
                providerId = "custom",
                baseUrl = "https://api.xiaomimimo.com/v1",
                model = "mimo-v2.5"
            )
        )
        assertFalse(
            supportsMiMoOfficialWebSearch(
                providerId = "mimo",
                baseUrl = "https://example.com/v1",
                model = "mimo-v2.5"
            )
        )
    }

    private fun course(id: Long, name: String, scheduleId: Int) = CourseEntity(
        id = id,
        name = name,
        teacher = null,
        location = null,
        weekday = 1,
        periods = listOf(1),
        weeks = listOf(1),
        weekParity = WeekParity.ALL,
        note = null,
        scheduleId = scheduleId
    )
}
