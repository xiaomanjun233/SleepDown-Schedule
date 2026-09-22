package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwuAuthRoutesTest {
    @Test
    fun coursePageIsRecognizedWithoutTreatingLoginAsAuthenticated() {
        assertTrue(SwuAuthRoutes.isCoursePage(SwuAuthRoutes.CoursePageUrl))
        assertTrue(
            SwuAuthRoutes.isTeachingLoginPage(
                "https://jw.swu.edu.cn/jwglxt/xtgl/login_slogin.html"
            )
        )
        assertFalse(
            SwuAuthRoutes.isAuthenticatedTeachingPage(
                "https://jw.swu.edu.cn/jwglxt/xtgl/login_slogin.html"
            )
        )
    }

    @Test
    fun authenticatedTeachingLandingCanAdvanceToCoursePage() {
        assertTrue(
            SwuAuthRoutes.isAuthenticatedTeachingPage(
                "https://jw.swu.edu.cn/jwglxt/xtgl/index_initMenu.html"
            )
        )
        assertTrue(SwuAuthRoutes.isAuthenticatedTeachingPage(SwuAuthRoutes.CoursePageUrl))
    }

    @Test
    fun ssoAndIdentityPagesAreDistinguishedFromTeachingPages() {
        assertTrue(SwuAuthRoutes.isSsoEntry(SwuAuthRoutes.SsoUrl))
        assertTrue(
            SwuAuthRoutes.isUnifiedAuthPage(
                "https://uaaap.swu.edu.cn/cas/login?service=https%3A%2F%2Fjw.swu.edu.cn%2Fsso%2Fzllogin"
            )
        )
        assertTrue(
            SwuAuthRoutes.isUnifiedAuthPage(
                "https://idm.swu.edu.cn/am/UI/Login?realm=%2F"
            )
        )
        assertFalse(SwuAuthRoutes.isCoursePage(SwuAuthRoutes.SsoUrl))
    }

    @Test
    fun lookalikeHostsAreRejected() {
        assertFalse(
            SwuAuthRoutes.isCoursePage(
                "https://jw.swu.edu.cn.example.com/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html"
            )
        )
        assertFalse(
            SwuAuthRoutes.isUnifiedAuthPage(
                "https://uaaap.swu.edu.cn.example.com/cas/login"
            )
        )
    }
}
