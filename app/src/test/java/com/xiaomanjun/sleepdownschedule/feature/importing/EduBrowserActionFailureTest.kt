package com.xiaomanjun.sleepdownschedule.feature.importing

import org.junit.Assert.*
import org.junit.Test

class EduBrowserActionFailureTest {
    @Test fun authoredStageMessageIsShownInsteadOfAnUnrelatedNetworkError() {
        val message = "登录态未能保存到本机，请重试"
        assertEquals(message, eduBrowserActionFailureMessage(EduBrowserActionFailure(message)))
    }

    @Test fun unknownFailuresCannotExposeTheirRawSessionData() {
        val result = eduBrowserActionFailureMessage(IllegalStateException("fixture-cookie=private-fixture"))
        assertFalse(result.contains("fixture"))
        assertFalse(result.contains("网络"))
    }
}
