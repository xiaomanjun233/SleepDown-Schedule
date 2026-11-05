package com.xiaomanjun.sleepdownschedule.feature.importing

import org.junit.Assert.*
import org.junit.Test

class LocalEduAdaptersTest {
    private fun adapter(url: String, id: String = "a") = EduAdapter(
        EduSchool(id, "测试学校", id), id, "教务", "BACHELOR_AND_ASSOCIATE", "a.js", url, "作者", "")

    @Test fun exactOriginIgnoresPathButNeverGuessesParentDomainsOrPorts() {
        val a = adapter("https://edu.example.edu/login?service=portal")
        assertEquals(listOf(a), matchingLocalEduAdapters("https://EDU.example.edu:443/student/course", listOf(a)))
        listOf("https://other.example.edu/course", "https://edu.example.edu.evil.test/", "http://edu.example.edu/",
            "https://edu.example.edu:8443/", "https://user@edu.example.edu/").forEach {
            assertTrue(it, matchingLocalEduAdapters(it, listOf(a)).isEmpty())
        }
    }

    @Test fun allCandidatesStayLocalAndGenericToolsAreExcluded() {
        val a = adapter("https://edu.example.edu/", "a")
        val b = adapter("https://edu.example.edu/other", "b")
        val generic = a.copy(category = "GENERAL_TOOL")
        assertEquals(listOf(a, b), matchingLocalEduAdapters("https://edu.example.edu/current", listOf(a, b, generic)))
    }
}
