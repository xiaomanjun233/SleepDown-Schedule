package com.xiaomanjun.sleepdownschedule.feature.agent

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AgentToolProtocolTest {
    @Test fun namesAndObjectArgumentsWorkAcrossBothTransports() {
        for (name in listOf("getPeriods", "get-periods", "functions.GET_PERIODS", "GET_PERIODS")) {
            val chat = parseAgentToolDecision("""{"choices":[{"message":{"tool_calls":[{"id":"a","function":{"name":"$name","arguments":{}}}]}}]}""")
            val responses = parseAgentResponsesTurn("""{"output":[{"type":"function_call","call_id":"a","name":"$name","arguments":{}}]}""")
            assertEquals(AgentToolName.GET_PERIODS, chat.calls.single().name)
            assertNull(chat.calls.single().validationError)
            assertEquals(chat.calls.single(), responses.calls.single())
            assertEquals("{}", responses.outputItems.single().getValue("arguments").jsonPrimitive.content)
        }
    }

    @Test fun malformedAndUnknownCallsReturnAnErrorWithoutExecutingOrClearingMemory() {
        var executions = 0
        val session = AgentTurnToolSession { call -> executions++; AgentToolResult(call.id, call.name, true, "executed") }
        val malformed = decodeAgentToolCall("bad", "UPDATE_MEMORY", JsonPrimitive("{"))
        val missing = decodeAgentToolCall("missing", "UPDATE_MEMORY", buildJsonObject {})
        val unknown = decodeAgentToolCall("unknown", "DELETE_COURSE", buildJsonObject {})
        val unsupported = decodeAgentToolCall("week", "GET_WEEK_SCHEDULE", buildJsonObject { put("week", 5) })
        listOf(malformed, missing, unknown, unsupported).forEach { assertFalse(session.run(it).success) }
        assertEquals(0, executions)
    }

    @Test fun repeatedNormalizedReadsAndMemoryWritesExecuteOnlyOnce() {
        var executions = 0
        val session = AgentTurnToolSession { call -> executions++; AgentToolResult(call.id, call.name, true, "full result") }
        val first = AgentToolCall("one", AgentToolName.SEARCH_COURSES, mapOf("week" to "03", "name" to " English "))
        val second = first.copy(id = "two", arguments = mapOf("name" to "english", "week" to "3", "query" to ""))
        assertEquals("full result", session.run(first).content)
        assertEquals("two", session.run(second).callId)
        assertEquals(1, executions)
        session.run(AgentToolCall("memory1", AgentToolName.UPDATE_MEMORY, mapOf("memory" to "喜欢早课")))
        assertFalse(session.run(AgentToolCall("memory2", AgentToolName.UPDATE_MEMORY, mapOf("memory" to "另一个偏好"))).success)
        assertEquals(2, executions)
    }

    @Test fun missingCallIdsAreDistinctAndLegacyCallsBecomeValidNativePairs() {
        val result = parseAgentToolDecision("""{"choices":[{"message":{"tool_calls":[{"function":{"name":"SEARCH_COURSES","arguments":"{\"query\":\"英语\"}"}},{"function":{"name":"SEARCH_COURSES","arguments":"{\"query\":\"数学\"}"}}]}}]}""")
        assertEquals(2, result.calls.map { it.id }.distinct().size)
        assertEquals(result.calls.map { it.id }, result.assistantMessage.getValue("tool_calls").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content })
        val legacy = parseAgentToolDecision("""{"choices":[{"message":{"function_call":{"name":"GET_PERIODS","arguments":"{}"}}}]}""")
        assertTrue(legacy.assistantMessage.containsKey("tool_calls"))
        assertFalse(legacy.assistantMessage.containsKey("function_call"))
    }

    @Test fun everyWritableActionHasADiscoverableGuideWithoutSendingAllGuidesUpfront() {
        assertEquals(AgentActionType.entries.toSet(), AgentCapabilityArea.entries.flatMap { it.actions }.toSet())
        AgentCapabilityArea.entries.forEach { area ->
            val guide = agentActionGuide(area.name)
            area.actions.forEach { assertTrue(it.name, guide.contains(it.name)) }
        }
        assertTrue(DayAgentPrompts.TaskStage.length < 1500)
        assertTrue(agentActionGuide("COURSES").contains("sourcePeriods"))
    }

    @Test fun largeCachedSnapshotsAreOnlySentWhenRequested() {
        val cached = linkedMapOf("large" to AgentToolResult("a", AgentToolName.GET_SEMESTER_SCHEDULE, true, "x".repeat(30000)),
            "small" to AgentToolResult("b", AgentToolName.GET_PERIODS, true, "periods"))
        assertEquals(setOf("small"), agentPreloadedFacts(cached).keys)
    }
}
