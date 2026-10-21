package com.xiaomanjun.sleepdownschedule.feature.importing

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class AiEduRoutingTest {
    private val candidate = AiEduCandidate(AiEduTool.ZHENGFANG, listOf(0), "fixture-document", true)
    private val fingerprint = AiEduFingerprint(listOf(candidate))

    @Test fun onlyOneStrongCandidateBypassesTheModel() {
        assertEquals(candidate, fingerprint.localMatch)
        assertNull(AiEduFingerprint(listOf(candidate.copy(strong = false))).localMatch)
        assertNull(AiEduFingerprint(listOf(candidate, candidate.copy(framePath = listOf(1)))).localMatch)
    }

    @Test fun modelCannotInventAToolOrJavascript() {
        assertEquals(candidate, parseAiEduRoutingDecision(
            """{"action":"adapter","candidate":0,"confidence":0.96}""", fingerprint))
        listOf(
            """{"action":"adapter","candidate":99,"confidence":1}""",
            """{"action":"adapter","candidate":-1,"confidence":1}""",
            """{"action":"adapter","candidate":0,"confidence":0.89}""",
            """{"action":"adapter","candidate":0,"confidence":2}""",
            """{"action":"adapter","candidate":0,"confidence":1,"script":"fetch('/private')"}""",
            """{"action":"text","candidate":0,"confidence":1}""",
            """{"action":"adapter","candidate":0.5,"confidence":1}""",
            "not json"
        ).forEach { assertNull(it, parseAiEduRoutingDecision(it, fingerprint)) }
    }

    @Test fun modelInputContainsOnlyBoundedStructuralMetadata() {
        val input = fingerprint.modelInput()
        assertFalse(input.contains(candidate.documentKey))
        assertFalse(input.contains("http"))
        assertTrue(input.contains("zhengfang_01"))
        assertTrue(input.length < 300)
    }

    @Test fun fingerprintRejectsUnboundedOrUnknownFrameTargets() {
        fun encoded(tool: String, path: String) = JsonPrimitive(
            """{"candidates":[{"tool":"$tool","framePath":$path,"documentKey":"fixture","strong":true}]}"""
        ).toString()
        assertEquals(listOf(0), parseAiEduFingerprint(encoded("URP", "[0]")).candidates.single().framePath)
        listOf(encoded("CUSTOM", "[]"), encoded("URP", "[12]"), encoded("URP", "[0,0,0,0]")).forEach {
            assertTrue(runCatching { parseAiEduFingerprint(it) }.isFailure)
        }
    }

    @Test fun schoolSpecificAdapterCannotMasqueradeAsGeneric() {
        val adapter = EduAdapter(EduSchool("test", "test", "zhengfang_jiaowu"), "zhengfang_01",
            "test", "BACHELOR_AND_ASSOCIATE", "tool.js", "", "", "")
        assertNull(AiEduTool.ZHENGFANG.findAdapter(listOf(adapter)))
        assertNotNull(AiEduTool.ZHENGFANG.findAdapter(listOf(adapter.copy(category = "GENERAL_TOOL"))))
    }
}
