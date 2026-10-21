package com.xiaomanjun.sleepdownschedule.feature.agent

import org.junit.Assert.*
import org.junit.Test

class AgentActionAuthorizationTest {
    @Test fun chatDoesNotNeedIntentRequest() {
        val guard = AgentActionAuthorization("爽") { error("query must not call classifier") }
        assertNull(guard.validateAnswer("周末好好休息。"))
    }

    @Test fun deniedIntentBlocksBothToolsAndLegacyActionMarkup() {
        var calls = 0
        val guard = AgentActionAuthorization("直接玩到下周一") {
            calls++
            """{"intent":"CHAT_OR_QUERY","requestQuote":""}"""
        }
        assertNotNull(guard.feedback())
        assertNotNull(guard.validateAnswer("<agent_actions>{}</agent_actions>"))
        assertEquals(1, calls)
    }

    @Test fun explicitRequestNeedsEvidenceFromCurrentUser() {
        val guard = AgentActionAuthorization("帮我把数学课移到周五") {
            """{"intent":"EXPLICIT_ACTION_REQUEST","requestQuote":"把数学课移到周五"}"""
        }
        assertNull(guard.feedback())
        val stale = AgentActionAuthorization("我说的是明天开始") {
            """{"intent":"EXPLICIT_ACTION_REQUEST","requestQuote":"停掉周五的课"}"""
        }
        assertNotNull(stale.feedback())
    }

    @Test fun malformedAndEmptyEvidenceCannotAuthorizeActions() {
        listOf("yes", "{}", """{"intent":"EXPLICIT_ACTION_REQUEST","requestQuote":""}""").forEach {
            assertNotNull(AgentActionAuthorization("明天有什么课") { it }.feedback())
        }
    }

    @Test(expected = java.io.IOException::class)
    fun transportFailureIsNotSilentlyTreatedAsConsent() {
        AgentActionAuthorization("改课") { throw java.io.IOException("offline") }.feedback()
    }
}
