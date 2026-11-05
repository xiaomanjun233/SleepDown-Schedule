package com.xiaomanjun.sleepdownschedule.feature.importing

import android.app.Application
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class AiEduBrowserSessionTest {
    @Test fun floatingHostReturnsTheSameDocumentWithoutReloading() = withSession { session, home, browser ->
        val floating = FrameLayout(browser.context)
        assertTrue(session.attach(floating))
        assertSame(browser, floating.getChildAt(0))
        assertEquals(0, home.childCount)
        session.restore(floating)
        assertSame(home, browser.parent)
        assertEquals("https://fixture.invalid/portal", browser.url)
        assertEquals(1, browser.loads)
    }

    @Test fun staleHostReleaseCannotDetachTheNewHost() = withSession { session, _, browser ->
        val old = FrameLayout(browser.context)
        val next = FrameLayout(browser.context)
        assertTrue(session.attach(old))
        session.restore(old)
        assertTrue(session.attach(next))
        session.restore(old)
        assertSame(next, browser.parent)
    }

    @Test fun aSecondVisibleWindowCannotStealTheBorrowedBrowser() = withSession { session, _, browser ->
        val first = FrameLayout(browser.context)
        val second = FrameLayout(browser.context)
        assertTrue(session.attach(first))
        assertFalse(session.attach(second))
        assertSame(first, browser.parent)
        assertEquals(0, second.childCount)
    }

    @Test fun releasingTheSourceInvalidatesTheWindowAndCannotResurrectTheView() = withSession { session, home, browser ->
        val floating = FrameLayout(browser.context)
        assertTrue(session.attach(floating))
        AiEduBrowserSessions.release(browser)
        assertNull(AiEduBrowserSessions.current.value)
        assertFalse(session.available)
        assertEquals(0, floating.childCount)
        session.restore(floating)
        assertEquals(0, home.childCount)
        assertFalse(session.attach(FrameLayout(browser.context)))
        AiEduBrowserSessions.bind("stale-task", session)
        assertNull(AiEduBrowserSessions.current.value)
    }

    @Test fun releasingAnOlderSourceStillRevokesItsLeaseWithoutClearingTheNewTask() = withSession { session, _, browser ->
        val floating = FrameLayout(browser.context)
        assertTrue(session.attach(floating))
        withSession { next, _, _ ->
            AiEduBrowserSessions.bind("next-task", next)
            AiEduBrowserSessions.release(browser)
            assertFalse(session.available)
            assertEquals(0, floating.childCount)
            assertSame(next, AiEduBrowserSessions.current.value?.session)
            assertEquals("next-task", AiEduBrowserSessions.current.value?.taskId)
            session.restore(floating)
            assertNull(browser.parent)
        }
    }

    private fun withSession(test: (AiEduBrowserSession, EduBrowserWebViewHost, TrackingWebView) -> Unit) {
        val browser = TrackingWebView(RuntimeEnvironment.getApplication()).apply { loadUrl("https://fixture.invalid/portal") }
        val home = EduBrowserWebViewHost(browser.context, browser)
        val session = checkNotNull(AiEduBrowserSessions.prepare(browser))
        AiEduBrowserSessions.bind("fixture", session)
        try { test(session, home, browser) } finally {
            AiEduBrowserSessions.release(browser)
            (browser.parent as? ViewGroup)?.removeView(browser)
            browser.destroy()
        }
    }

    private class TrackingWebView(app: Application) : WebView(app) {
        var loads = 0
        override fun loadUrl(url: String) { loads++; super.loadUrl(url) }
    }
}
