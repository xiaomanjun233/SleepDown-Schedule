package com.xiaomanjun.sleepdownschedule.core.ai

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AgentContextPolicyTest {
    @Test fun chatCompactionKeepsToolPairsAndEveryUserSupplement() {
        val original = buildJsonObject { put("role", "user"); put("content", "保留其他课程") }
        val steering = buildJsonObject { put("role", "user"); put("content", "只改实验课地点") }
        val messages = mutableListOf(original)
        repeat(6) { index ->
            messages += buildJsonObject { put("role", "assistant"); put("tool_calls", buildJsonArray { add(buildJsonObject {
                put("id", "call$index"); put("type", "function"); put("function", buildJsonObject { put("name", "READ"); put("arguments", "{}") })
            }) }) }
            messages += buildJsonObject { put("role", "tool"); put("tool_call_id", "call$index"); put("content", "事实".repeat(10_000)) }
            if (index == 2) messages += steering
        }
        val result = AgentContextPolicy(8_192).compactChat(messages).messages
        assertTrue(original in result && steering in result)
        val callIds = result.flatMap { (it["tool_calls"] as? JsonArray).orEmpty().map { call -> call.jsonObject["id"] } }
        val receiptIds = result.filter { it["role"]?.jsonPrimitive?.contentOrNull == "tool" }.map { it["tool_call_id"] }
        assertEquals(callIds, receiptIds)
        assertEquals(4, callIds.size)
        assertEquals(14, messages.size)
    }
    @Test fun compactionKeepsUserRequirementsAndRecentToolPairsIntact() {
        val instruction = buildJsonObject { put("role", "user"); put("content", "保留其他周次") }
        val input = mutableListOf(instruction)
        repeat(6) { index ->
            input += buildJsonObject { put("type", "function_call"); put("call_id", "c$index"); put("name", "READ"); put("arguments", "{}") }
            input += buildJsonObject { put("type", "function_call_output"); put("call_id", "c$index"); put("output", "结果".repeat(10_000)) }
        }
        val result = AgentContextPolicy(8_192).compactResponses(input)
        assertTrue(instruction in result)
        val calls = result.filter { it["type"]?.jsonPrimitive?.contentOrNull == "function_call" }.map { it["call_id"] }
        val outputs = result.filter { it["type"]?.jsonPrimitive?.contentOrNull == "function_call_output" }.map { it["call_id"] }
        assertEquals(calls, outputs)
        assertEquals(4, calls.size)
        assertTrue(result.any { it["content"]?.toString()?.contains("completed_tool_context") == true })
        assertEquals(13, input.size)
    }
}
