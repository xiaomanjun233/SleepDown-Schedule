package com.xiaomanjun.sleepdownschedule.feature.importing

import android.webkit.ConsoleMessage
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class WebCompatDelegateTest {
    @Test fun desktopZoomCorrectsTheUnscaledViewportAndDoesNotRepeatAtTheTarget() {
        val expected = desktopViewportScale(1152, 3f, 800)
        assertEquals(0.3f, expected, 0.00001f)
        assertEquals(0.3f, desktopZoomFactor(1f, expected)!!, 0.00001f)
        assertNull(desktopZoomFactor(0.3f, expected))
        assertNull(desktopZoomFactor(-1f, expected))
        assertNull(desktopZoomFactor(Float.NaN, expected))
        assertNull(desktopZoomFactor(Float.POSITIVE_INFINITY, expected))
        assertEquals(1f, desktopViewportScale(3200, 2f, 800), 0f)
        assertEquals(0.3f, desktopViewportScale(0, 0f, 384), 0.00001f)
    }

    @Test fun desktopModeKeepsTheInstalledChromiumVersion() {
        val mobile = "Mozilla/5.0 (Linux; Android 16; TestDevice; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/140.0.7339.12 Mobile Safari/537.36"
        val desktop = desktopWebUserAgent(mobile)
        assertTrue(desktop.contains("Chrome/140.0.7339.12"))
        assertFalse(desktop.contains("Android"))
        assertFalse(desktop.contains("Mobile"))
        assertEquals("CustomEngine/5", desktopWebUserAgent("CustomEngine/5"))
    }

    @Test fun actualWebViewWidthWinsOverScreenConfiguration() {
        assertEquals("width=1280.0000, initial-scale=0.3000", desktopViewportContent(1152, 3f, 800))
        assertEquals("width=1280.0000, initial-scale=0.1500", desktopViewportContent(576, 3f, 800))
    }

    @Test fun beforeLayoutUsesTheWindowConfigurationWidth() {
        assertEquals("width=1280.0000, initial-scale=0.3000", desktopViewportContent(0, 3f, 384))
        assertEquals("width=1280.0000, initial-scale=0.3000", desktopViewportContent(1152, Float.NaN, 384))
    }

    @Test fun largeWindowsNeverShrinkBelowTheirOwnCssWidth() {
        assertEquals("width=1600.0000, initial-scale=1.0000", desktopViewportContent(3200, 2f, 600))
    }

    @Test fun scaleAndLayoutWidthStayConsistentAcrossWindowSizes() {
        for (width in listOf(480, 720, 1080, 1256, 1920, 3200, 3840)) {
            for (density in listOf(1f, 2f, 2.75f, 3.5f)) {
                val values = desktopViewportContent(width, density, 999).split(", ")
                    .map { it.substringAfter('=').toDouble() }
                assertTrue(values[0] >= 1280.0)
                assertEquals(width / density.toDouble() / values[0], values[1], 0.000051)
            }
        }
    }

    @Test fun decimalFormattingDoesNotFollowTheDevicesLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("width=1280.0000, initial-scale=0.3000", desktopViewportContent(1152, 3f, 384))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun missing404AssetsDoNotTriggerReloadRecoveryWhileBlockedStatusesDo() {
        assertFalse(shouldRecoverEduResourceHttpError(200))
        assertFalse(shouldRecoverEduResourceHttpError(404))
        assertTrue(shouldRecoverEduResourceHttpError(403))
        assertTrue(shouldRecoverEduResourceHttpError(412))
        assertTrue(shouldRecoverEduResourceHttpError(500))
    }

    @Test fun nativeRequestedWithAllowListSkipsOkHttpReloadRecovery() {
        val page = "https://portal.example.edu/home"
        val asset = "https://portal.example.edu/static/app.js"
        assertTrue(
            shouldAttemptEduResourceRecovery(
                nativeRequestedWithStripped = false,
                isForMainFrame = false,
                status = 403,
                requestUrl = asset,
                pageUrl = page,
                requestHeaders = emptyMap()
            )
        )
        assertFalse(
            shouldAttemptEduResourceRecovery(
                nativeRequestedWithStripped = true,
                isForMainFrame = false,
                status = 403,
                requestUrl = asset,
                pageUrl = page,
                requestHeaders = emptyMap()
            )
        )
        assertTrue(
            shouldAttemptConsoleResourceRecovery(
                nativeRequestedWithStripped = false,
                level = ConsoleMessage.MessageLevel.ERROR,
                message = "Uncaught ReferenceError: $ is not defined",
                sourceId = asset,
                pageUrl = page
            )
        )
        assertFalse(
            shouldAttemptConsoleResourceRecovery(
                nativeRequestedWithStripped = true,
                level = ConsoleMessage.MessageLevel.ERROR,
                message = "Uncaught ReferenceError: $ is not defined",
                sourceId = asset,
                pageUrl = page
            )
        )
    }

    @Test fun transientSsoAndOAuthUrlsAreExcludedFromLoginHistory() {
        assertFalse(EduLoginHistoryStore.shouldRememberUrl("https://cas.example.edu/cas/login"))
        assertFalse(EduLoginHistoryStore.shouldRememberUrl("https://cas.example.edu/cas/?service=https%3A%2F%2Fportal.example.edu"))
        assertFalse(EduLoginHistoryStore.shouldRememberUrl("https://idm.example.edu/am/UI/Login?service=initService"))
        assertFalse(EduLoginHistoryStore.shouldRememberUrl("https://idm.example.edu/am/oauth2/authorize?client_id=1"))
        assertFalse(EduLoginHistoryStore.shouldRememberUrl("https://webvpn.example.edu/https/777264/lyuapServer/login?service=x"))
        assertFalse(EduLoginHistoryStore.shouldRememberUrl("https://sso.example.edu/tpass/login?service=x"))
        assertFalse(EduLoginHistoryStore.shouldRememberUrl("https://iam.example.edu/esc-sso/login/page"))
        assertFalse(EduLoginHistoryStore.shouldRememberUrl("https://portal.example.edu/PersonalApplications/viewPageV3?ticket=ST-1"))
        assertFalse(EduLoginHistoryStore.shouldRememberUrl("https://jw.example.edu/sso/login"))
        assertFalse(EduLoginHistoryStore.shouldRememberUrl("https://portal.example.edu/login?code=oauth-code"))
        assertFalse(EduLoginHistoryStore.shouldRememberUrl("https://portal.example.edu/callback?code=oauth-code&state=s1"))
        assertFalse(EduLoginHistoryStore.shouldRememberUrl("http://203.0.113.10:9527/#/authserver/login?redirect_uri=abc"))
        assertTrue(EduLoginHistoryStore.shouldRememberUrl("https://portal.example.edu/PersonalApplications/viewPageV3"))
        assertTrue(EduLoginHistoryStore.shouldRememberUrl("https://jw.example.edu/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151"))
        assertTrue(EduLoginHistoryStore.shouldRememberUrl("https://webvpn.example.edu/https/777264/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html"))
        assertFalse(EduLoginHistoryStore.shouldRememberPage("https://portal.example.edu/app", "未经授权的服务"))
        assertFalse(EduLoginHistoryStore.shouldRememberPage("https://portal.example.edu/app", "未认证授权的服务"))
        assertFalse(EduLoginHistoryStore.shouldRememberPage("https://portal.example.edu/app", "Unauthorized"))
        assertTrue(EduLoginHistoryStore.shouldRememberPage("https://portal.example.edu/app", "个人主页"))
    }

    @Test fun oneTimeLoginCallbacksAreRecognisedSoTheyAreNeverReplayed() {
        assertTrue(eduWebIsTransientAuthUrl("https://portal.example.edu/login?ticket=ST-20261118"))
        assertTrue(eduWebIsTransientAuthUrl("https://cas.example.edu/cas/login?service=x"))
        assertTrue(eduWebIsTransientAuthUrl("https://idm.example.edu/am/oauth2/authorize?client_id=1&state=s"))
        assertTrue(eduWebIsTransientAuthUrl("https://sso.example.edu/saml/SSO?SAMLResponse=abc"))
        assertTrue(eduWebIsTransientAuthUrl("https://webvpn.example.edu/users/sign_in"))
        assertFalse(eduWebIsTransientAuthUrl("https://portal.example.edu/"))
        assertFalse(eduWebIsTransientAuthUrl("https://jw.example.edu/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151"))
        assertFalse(eduWebIsTransientAuthUrl("https://jw.example.edu/jwglxt/xtgl/index_initMenu.html?code=N2151"))
        assertFalse(eduWebIsTransientAuthUrl("about:blank"))
        assertTrue(eduWebHasOneTimeToken("https://portal.example.edu/login?ticket=ST-1"))
        assertTrue(eduWebHasOneTimeToken("https://portal.example.edu/callback?code=c1&state=s1"))
        assertFalse(eduWebHasOneTimeToken("https://cas.example.edu/cas/login?service=https%3A%2F%2Fportal.example.edu"))
        assertFalse(eduWebHasOneTimeToken("https://jw.example.edu/jwglxt/xtgl/index_initMenu.html?code=N2151"))
    }

    @Test fun desktopToggleOnlyReturnsToEntryFromTransientAuthOrMobileHosts() {
        val portal = "https://portal.example.edu/"
        assertTrue(shouldReloadEntryOnDesktopToggle("https://auth.example.edu/cas/login?service=x", portal, true))
        assertTrue(shouldReloadEntryOnDesktopToggle("https://m.example.edu/home", portal, true))
        assertFalse(shouldReloadEntryOnDesktopToggle("https://m.example.edu/home", portal, false))
        assertFalse(shouldReloadEntryOnDesktopToggle("https://jw.example.edu/jwglxt/kbcx", portal, true))
        // 同源的一次回跳页也必须退回入口，不能 reload 当前地址。
        assertTrue(shouldReloadEntryOnDesktopToggle(
            "https://portal.example.edu/login?ticket=ST-1", "https://portal.example.edu/", true))
        assertFalse(shouldReloadEntryOnDesktopToggle(
            "https://portal.example.edu/new/index.html", "https://portal.example.edu/", true))
        // 入口自身带有一次性凭据时绝不回退重放。
        assertFalse(shouldReloadEntryOnDesktopToggle(
            "https://portal.example.edu/login?ticket=ST-1", "https://portal.example.edu/login?ticket=ST-1", true))
    }
}
