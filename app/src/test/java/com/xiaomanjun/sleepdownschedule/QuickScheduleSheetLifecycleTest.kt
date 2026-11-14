package com.xiaomanjun.sleepdownschedule

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.itos.coui.menu.ProvideCouiMenuBackdrop
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects
import com.xiaomanjun.sleepdownschedule.core.ui.material.VisualEffectsState
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassMiuixSettingsTheme
import com.xiaomanjun.sleepdownschedule.feature.schedule.picker.QuickScheduleDraft
import com.xiaomanjun.sleepdownschedule.feature.schedule.picker.QuickScheduleSettingsSheets
import com.xiaomanjun.sleepdownschedule.model.VisualEffectMode
import com.xiaomanjun.sleepdownschedule.platform.coloros.ProvideCouiMaterials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuickScheduleSheetLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun glowSheetKeepsDraftThroughSaveAndReopens() = verify(glow = true)
    @Test fun liquidSheetKeepsDraftThroughSaveAndReopens() = verify(glow = false)

    private fun verify(glow: Boolean) {
        val draft = mutableStateOf<QuickScheduleDraft?>(null)
        var completeSave: (() -> Unit)? = null
        var saves = 0
        var dismissals = 0
        compose.setContent {
            val mode = if (glow) VisualEffectMode.ColorOsGlow else VisualEffectMode.LiquidGlass
            CompositionLocalProvider(LocalVisualEffects provides VisualEffectsState(mode, mode, true)) {
                MaterialTheme {
                    GlassMiuixSettingsTheme(defaultConfig()) {
                        ProvideCouiMaterials(glow) {
                            ProvideCouiMenuBackdrop(isolateContent = false, recordWhenIdle = false) { source ->
                                Box(Modifier.fillMaxSize().then(source))
                                QuickScheduleSettingsSheets(draft.value, defaultConfig(), null,
                                    onDraftChange = { draft.value = it },
                                    onDismiss = { draft.value = null },
                                    onDismissFinished = { dismissals++ },
                                    onSave = { value, done ->
                                        assertEquals(20, value.totalWeeks)
                                        saves++
                                        completeSave = done
                                    }, onDetailedSettings = { _, _, _, _, _ -> })
                            }
                        }
                    }
                }
            }
        }
        var originalPanel: Rect? = null
        repeat(2) { cycle ->
            compose.runOnIdle { draft.value = QuickScheduleDraft(1, 20, 1, true, false, "2026-09-01") }
            compose.onNodeWithText("总周数").assertExists()
            val panel = compose.onNodeWithTag("quick_schedule_sheet").fetchSemanticsNode().boundsInRoot
            assertTrue(panel.width > 0 && panel.height > 0)
            if (cycle == 0) originalPanel = panel else assertEquals(originalPanel, panel)
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithText("保存中").assertIsNotEnabled()
            compose.runOnIdle {
                assertEquals(cycle + 1, saves)
                assertEquals(cycle, dismissals)
                assertTrue(draft.value != null)
            }
            compose.mainClock.autoAdvance = false
            compose.runOnIdle { completeSave!!() }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithText("总周数").assertExists()
            compose.runOnIdle { assertEquals(cycle, dismissals) }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            compose.onNodeWithText("课表设置").assertDoesNotExist()
            compose.runOnIdle { assertEquals(cycle + 1, dismissals) }
        }
    }
}
