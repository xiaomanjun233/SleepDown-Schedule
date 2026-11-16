package com.xiaomanjun.sleepdownschedule.feature.settings

import android.app.Application
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.itos.coui.menu.ProvideCouiMenuBackdrop
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects
import com.xiaomanjun.sleepdownschedule.core.ui.material.VisualEffectsState
import com.xiaomanjun.sleepdownschedule.core.ui.settings.CouiSettingsPageScaffold
import com.xiaomanjun.sleepdownschedule.domain.schedule.PeriodSchemeDraft
import com.xiaomanjun.sleepdownschedule.domain.schedule.PeriodTimelineSession
import com.xiaomanjun.sleepdownschedule.domain.schedule.SchedulePeriodSchemesDraft
import com.xiaomanjun.sleepdownschedule.model.PeriodSchemeEntity
import com.xiaomanjun.sleepdownschedule.model.PeriodSchemeMode
import com.xiaomanjun.sleepdownschedule.model.PeriodSchemeTimeEntity
import com.xiaomanjun.sleepdownschedule.model.VisualEffectMode
import com.xiaomanjun.sleepdownschedule.model.defaultConfig
import com.xiaomanjun.sleepdownschedule.platform.coloros.ProvideCouiMaterials
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PeriodSchemeEditorLiquidGlassUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun periodSchemeEditorOpensAndAddsPeriodInLiquidGlassMode() {
        verifyEditorOpensAndAddsPeriod(VisualEffectMode.LiquidGlass)
    }

    @Test
    @Config(sdk = [35])
    fun periodSchemeEditorOpensAndAddsPeriodInColorOsGlowMode() {
        verifyEditorOpensAndAddsPeriod(VisualEffectMode.ColorOsGlow)
    }

    private fun verifyEditorOpensAndAddsPeriod(mode: VisualEffectMode) {
        val config = defaultConfig().copy(
            activePeriodSchemeId = 7,
            morningPeriodCount = 2,
            noonPeriodCount = 0,
            afternoonPeriodCount = 1,
            eveningPeriodCount = 0,
            darkMode = false,
            followSystemDarkMode = false,
        )
        val scheme = PeriodSchemeEntity(
            id = 7,
            scheduleId = 0,
            name = "常规作息",
            publicId = "public-7",
            mode = PeriodSchemeMode.MANUAL,
            morningPeriodCount = 2,
            afternoonPeriodCount = 1,
        )
        val item = PeriodSchemeDraft(
            scheme = scheme,
            times = listOf(
                PeriodSchemeTimeEntity(7, 1, "08:00", "08:45"),
                PeriodSchemeTimeEntity(7, 2, "08:55", "09:40"),
                PeriodSchemeTimeEntity(7, 3, "14:00", "14:45"),
            ),
        )
        val session = PeriodTimelineSession(
            config = config,
            draft = SchedulePeriodSchemesDraft(
                schemes = listOf(item),
                activeSchemeId = 7,
                originalSchemes = listOf(item),
                originalActiveSchemeId = 7,
            ),
        )
        val effects = VisualEffectsState(
            selectedMode = mode,
            effectiveMode = mode,
            couiKitAvailable = Build.VERSION.SDK_INT >= 34,
        )
        compose.setContent {
            CompositionLocalProvider(LocalVisualEffects provides effects) {
                ProvideCouiMaterials(couiControlsEnabled = effects.usesCouiControls) {
                    ProvideCouiMenuBackdrop(isolateContent = false, recordWhenIdle = false, enabled = true) { source ->
                        Box(Modifier.fillMaxSize().then(source)) {
                            CouiSettingsPageScaffold(title = "作息管理", config = config, showBack = true) { _, _ ->
                                PeriodSchemeEditor(
                                    config = config,
                                    request = session,
                                    onSaveSession = {},
                                    onEditorFinished = {},
                                    exitCommitRequest = 0,
                                    hasAdditionalChanges = false,
                                    saveMessage = null,
                                    canChangeStructure = true,
                                    metadataContent = { _, _ -> },
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("编辑作息").assertIsDisplayed()
        assertTrue(ShadowDialog.getLatestDialog() != null)
        compose.onAllNodesWithText("添加节次").onFirst().performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("添加节次").onFirst().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("第 4 节").performScrollTo().assertIsDisplayed()
        if (Build.VERSION.SDK_INT >= 28) {
            val rendered = compose.onNode(isDialog()).captureToImage()
            assertTrue(rendered.width > 0 && rendered.height > 0)
        }
    }
}
