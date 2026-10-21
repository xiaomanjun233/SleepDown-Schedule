package com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt

import android.app.Application
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ChatGptBrowserTest {
    @Test fun activityLaunchStaysInTheAppsTaskAndUsesTheBrowsersCustomTab() {
        val intent = ChatGptBrowser.createIntent("test.browser", false).intent
        assertEquals("test.browser", intent.`package`)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertTrue(intent.hasExtra(CustomTabsIntent.EXTRA_SESSION))
        assertEquals(0, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertEquals(CustomTabsIntent.SHARE_STATE_OFF, intent.getIntExtra(CustomTabsIntent.EXTRA_SHARE_STATE, -1))
    }

    @Test fun applicationContextLaunchAddsTheRequiredTaskFlag() {
        assertTrue(ChatGptBrowser.createIntent("test.browser", true).intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
