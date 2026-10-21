package com.xiaomanjun.sleepdownschedule.feature.importing

import org.junit.Assert.*
import org.junit.Test

class EduWebResourcePolicyTest {
    private val page = "http://111.117.146.166/eams/loginExt.action"

    @Test fun recoveryRequiresAnObservedGetAndIsScopedToTheExactOriginAndWindow() {
        val policy = EduWebResourcePolicy()
        assertFalse(policy.enable(page))
        policy.recordNavigation(page, "GET")
        assertTrue(policy.enable(page))
        assertFalse(policy.enable(page))
        assertTrue(policy.shouldForward(page, "GET", true, emptyMap()))
        assertFalse(policy.shouldForward(page.replace("http:", "https:"), "GET", true, emptyMap()))
        assertFalse(policy.shouldForward(page.replace("166/", "166:8080/"), "GET", true, emptyMap()))
        assertFalse(EduWebResourcePolicy().shouldForward(page, "GET", true, emptyMap()))
        assertFalse(policy.shouldForward("about:blank", "GET", true, emptyMap()))
        assertFalse(policy.shouldForward("file:///tmp/page.html", "GET", true, emptyMap()))
        policy.close()
        policy.recordNavigation(page, "GET")
        assertFalse(policy.enable(page))
        assertFalse(policy.shouldForward(page, "GET", true, emptyMap()))
    }

    @Test fun requestsWithBodiesFetchMediaAndRangesStayInChromium() {
        val policy = EduWebResourcePolicy()
        policy.recordNavigation(page, "GET")
        policy.enable(page)
        val js = "http://111.117.146.166/eams/static/scripts/jquery/jquery,/scripts/beangle/beangle.js"
        assertTrue(policy.shouldForward(js, "GET", false, emptyMap()))
        assertTrue(policy.shouldForward(js, "HEAD", false, mapOf("sec-fetch-dest" to "script")))
        for (method in listOf("POST", "PUT", "PATCH", "DELETE")) {
            assertFalse(policy.shouldForward(js, method, false, emptyMap()))
        }
        for (dest in listOf("empty", "video", "audio", "iframe", "worker")) {
            assertFalse(policy.shouldForward(js, "GET", false, mapOf("Sec-Fetch-Dest" to dest)))
        }
        assertFalse(policy.shouldForward(js, "GET", false, mapOf("Range" to "bytes=0-")))
        assertFalse(policy.shouldForward(js, "GET", false, mapOf("X-Requested-With" to "XMLHttpRequest")))
        policy.recordNavigation("https://another.example/page", "GET")
        assertFalse(policy.shouldForward(js, "GET", false, emptyMap()))
        assertFalse(policy.canReload(page))
    }

    @Test fun missingBeangleAndJqueryAreRecognizedButUnrelatedErrorsAndImagesDoNotTriggerRecovery() {
        assertTrue(isMissingEduScript("Uncaught ReferenceError: beangle is not defined"))
        assertTrue(isMissingEduScript("Uncaught ReferenceError: $ is not defined"))
        assertTrue(isMissingEduScript("Bootstrap's JavaScript requires jQuery"))
        assertFalse(isMissingEduScript("validateYearInput is not defined"))
        assertTrue(isEduStyleOrScript("http://school.test/theme.css?v=2", emptyMap()))
        assertTrue(isEduStyleOrScript("http://school.test/resource", mapOf("sec-fetch-dest" to "style")))
        assertFalse(isEduStyleOrScript("http://school.test/favicon.ico", emptyMap()))
        assertFalse(isEduStyleOrScript("http://school.test/theme.css", mapOf("Sec-Fetch-Dest" to "empty")))
    }
}
