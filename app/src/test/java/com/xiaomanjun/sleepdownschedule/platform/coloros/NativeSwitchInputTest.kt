package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.app.Application
import android.widget.Switch
import androidx.compose.ui.Modifier
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeSwitchRequest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NativeSwitchInputTest {
    @Test fun standalonePreferenceStyleSwitchCanToggleAndUsesLatestCallback() {
        val view = Switch(RuntimeEnvironment.getApplication()).apply { isClickable = false; isFocusable = false }
        val changes = mutableListOf<Boolean>()
        var request = NativeSwitchRequest(false, { changes += it }, Modifier)
        bindStandaloneSwitch(view) { request }
        assertTrue(view.isClickable)
        assertTrue(view.isFocusable)
        view.performClick()
        assertEquals(listOf(true), changes)
        request = request.copy(checked = true)
        view.performClick()
        assertEquals(listOf(true, false), changes)
        request = request.copy(enabled = false)
        view.performClick()
        assertEquals(2, changes.size)
    }
}
