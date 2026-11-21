package com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RetainedSessionPolicyTest {
    private fun legacy() = JSONObject().put("schoolId", "school").put("schoolName", "测试大学")
        .put("adapterId", "adapter").put("adapterName", "教务").put("scheduleId", 4)
        .put("automatic", true).put("frequencyMinutes", 10080)
        .put("cookies", "old cookie payload").put("authenticatedUrl", "https://school.test/course")

    @Test fun legacyBindingRetainsTheScheduleAndFrequencyButCannotRunUntilReconnected() {
        val restored = parseAutoRefreshProfile(legacy())
        assertEquals(4, restored.scheduleId)
        assertEquals("adapter", restored.adapterId)
        assertTrue(restored.automatic)
        assertEquals(10080L, restored.frequencyMinutes)
        assertEquals(RefreshConnection.Legacy, restored.connection)
        assertNull(restored.browserProfileName)
        assertNull(restored.authenticatedUrl)
        assertFalse(restored.automaticRefreshEnabled)
    }

    @Test fun savedBrowserProfileOnlyAllowsBackgroundWorkWhenConnectionIsVerified() {
        val id = AutoRefreshWebSession.newBindingId()
        val stored = legacy().put("bindingId", id).put("isolatedBrowser", true)
            .put("scriptFingerprint", "a".repeat(64)).put("connection", RefreshConnection.Ready.name)
        val ready = parseAutoRefreshProfile(stored)
        assertEquals(AutoRefreshWebSession.profileName(id), ready.browserProfileName)
        assertTrue(ready.automaticRefreshEnabled)
        assertFalse(ready.copy(connection = RefreshConnection.SignInRequired).automaticRefreshEnabled)
        assertFalse(ready.copy(connection = RefreshConnection.OptionsRequired).automaticRefreshEnabled)
        assertFalse(ready.copy(connection = RefreshConnection.AdapterChanged).automaticRefreshEnabled)
        assertFalse(ready.copy(automatic = false).automaticRefreshEnabled)
        assertFalse(ready.copy(isolatedBrowser = false).automaticRefreshEnabled)
        assertFalse(ready.copy(scriptFingerprint = null).automaticRefreshEnabled)
    }

    @Test fun choosingATargetRestoresAMissingScheduleWithoutReplacingItsLoginSession() {
        val saved = parseAutoRefreshProfile(legacy()
            .put("bindingId", AutoRefreshWebSession.newBindingId()).put("isolatedBrowser", true)
            .put("scriptFingerprint", "a".repeat(64)).put("connection", RefreshConnection.ScheduleMissing.name)
            .put("interactionAnswers", JSONObject().put("term", "2026 春"))
            .put("lastRefreshAt", 123L))

        val updated = saved.withTargetSchedule(7)

        assertEquals(7, updated.scheduleId)
        assertEquals(0L, updated.lastRefreshAt)
        assertNotEquals(saved.lastResult, updated.lastResult)
        assertEquals(saved.authenticatedUrl, updated.authenticatedUrl)
        assertEquals(saved.browserProfileName, updated.browserProfileName)
        assertEquals(saved.interactionAnswers, updated.interactionAnswers)
        assertEquals(saved.frequencyMinutes, updated.frequencyMinutes)
        assertTrue(updated.automaticRefreshEnabled)
    }

    @Test fun choosingATargetPreservesOtherConnectionStates() {
        val saved = parseAutoRefreshProfile(legacy())
        listOf(RefreshConnection.Legacy, RefreshConnection.BrowserOnly, RefreshConnection.SignInRequired,
            RefreshConnection.OptionsRequired, RefreshConnection.AdapterChanged).forEach { connection ->
            val updated = saved.copy(connection = connection).withTargetSchedule(7)
            assertEquals(connection, updated.connection)
            assertFalse(updated.automaticRefreshEnabled)
        }
    }
}
