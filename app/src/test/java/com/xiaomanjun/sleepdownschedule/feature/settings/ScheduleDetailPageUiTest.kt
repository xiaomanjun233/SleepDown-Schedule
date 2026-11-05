package com.xiaomanjun.sleepdownschedule.feature.settings

import android.app.Application
import android.os.Build
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalVisualEffects
import com.xiaomanjun.sleepdownschedule.core.ui.material.VisualEffectsState
import com.xiaomanjun.sleepdownschedule.model.VisualEffectMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.defaultConfig
import com.xiaomanjun.sleepdownschedule.core.ui.settings.CouiSettingsPageScaffold
import com.xiaomanjun.sleepdownschedule.domain.schedule.PeriodSchemeDraft
import com.xiaomanjun.sleepdownschedule.domain.schedule.SchedulePeriodSchemesDraft
import com.xiaomanjun.sleepdownschedule.domain.schedule.SavedPeriodScheme
import com.xiaomanjun.sleepdownschedule.domain.schedule.SavedPeriodTime
import com.xiaomanjun.sleepdownschedule.model.PeriodEntity
import com.xiaomanjun.sleepdownschedule.model.PeriodSchemeEntity
import com.xiaomanjun.sleepdownschedule.model.PeriodSchemeTimeEntity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScheduleDetailPageUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun currentScheduleDetailRendersLoadedTimetableAndRemainsScrollable() = checkDetail(false)

    @Test @Config(sdk = [35])
    fun currentScheduleDetailSupportsCouiNativeToolbarAndControls() = checkDetail(true)

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp-560dpi")
    fun notificationSwitchRowsUseTheOriginalRowHeightAndSingleClickOwner() {
        val config = defaultConfig().copy(darkMode = true, followSystemDarkMode = false)
        var reminders by mutableStateOf(true)
        var changes = 0
        compose.setContent {
            CompositionLocalProvider(LocalVisualEffects provides VisualEffectsState(
                effectiveMode = VisualEffectMode.ColorOsGlow)) {
                CouiSettingsPageScaffold(title = "通知设置", config = config, showBack = true) { _, source ->
                    ScheduleSettingsContent(section = SettingsSection.Notifications,
                        state = AppState(config = config), backdrop = source,
                        totalWeeks = "20", onTotalWeeksChange = {}, currentWeek = "1", onCurrentWeekChange = {},
                        leadMinutes = "10", onLeadMinutesChange = {}, notificationsEnabled = reminders,
                        onNotificationsEnabledChange = { reminders = it; changes++ },
                        notificationMode = config.notificationMode, onNotificationModeChange = {},
                        liveUpdateChipTextMode = config.liveUpdateChipTextMode, onLiveUpdateChipTextModeChange = {},
                        liveUpdateActionsEnabled = true, onLiveUpdateActionsEnabledChange = {},
                        autoCurrentWeek = true, hideEmptyWeekends = false, showNonCurrentWeekCourses = true,
                        onHideEmptyWeekendsChange = {}, onShowNonCurrentWeekCoursesChange = {},
                        termStartDate = "2026.09.01", onTermStartDateChange = {},
                        classDurationMinutes = "45", onClassDurationMinutesChange = {},
                        breakDurationMinutes = "15", onBreakDurationMinutesChange = {},
                        morningPeriodCount = 2, noonPeriodCount = 0, afternoonPeriodCount = 0, eveningPeriodCount = 0,
                        onPeriodCountsChange = { _, _, _, _ -> }, schemeDraft = null, onSchemeDraftChange = {},
                        onAutoMatchPeriodEnds = {}, periods = emptyList(), onPeriodsChange = {},
                        detectedWeek = 1, detectedWeekDescription = "第1周", error = null, onPreviewLiveUpdate = {})
                }
            }
        }
        val density = org.robolectric.RuntimeEnvironment.getApplication().resources.displayMetrics.density
        val quiet = compose.onNode(hasText("自动勿扰") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val remindersRow = compose.onNodeWithText("课程提醒")
        org.junit.Assert.assertEquals(48f * density, quiet.height, 1f)
        org.junit.Assert.assertEquals(48f * density, remindersRow.fetchSemanticsNode().boundsInRoot.height, 1f)
        val sound = compose.onNodeWithText("自动静音／震动").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(sound.height > quiet.height)
        writeSettingsVisualArtifact("notification-settings-native.png", compose.onRoot().captureToImage().asAndroidBitmap())
        remindersRow.performScrollTo().performClick()
        compose.runOnIdle { org.junit.Assert.assertFalse(reminders); org.junit.Assert.assertEquals(1, changes) }
        remindersRow.performTouchInput { click(Offset(width - 24f * density, height / 2f)) }
        compose.runOnIdle { org.junit.Assert.assertTrue(reminders); org.junit.Assert.assertEquals(2, changes) }
    }

    private fun checkDetail(native: Boolean) {
        val config = defaultConfig().copy(morningPeriodCount = 2, noonPeriodCount = 0,
            afternoonPeriodCount = 0, eveningPeriodCount = 0,
            darkMode = native, followSystemDarkMode = false)
        val periods = listOf(PeriodEntity(1, "08:00", "08:45"), PeriodEntity(2, "09:00", "09:45"))
        val scheme = PeriodSchemeEntity(id = 7, scheduleId = config.id, name = "测试作息", isActive = true,
            morningPeriodCount = 2, noonPeriodCount = 0, afternoonPeriodCount = 0, eveningPeriodCount = 0)
        val draft = SchedulePeriodSchemesDraft(listOf(PeriodSchemeDraft(scheme,
            periods.map { PeriodSchemeTimeEntity(7, it.periodIndex, it.startTime, it.endTime) })), 7)
        val state = AppState(config = config, periods = periods)
        val saved = SavedPeriodScheme("test-timetable", "另一套作息", 2, 0, 0, 0,
            45, 15, "08:00", "12:00", "14:00", "19:00",
            periods.map { SavedPeriodTime(it.periodIndex, it.startTime, it.endTime) })
        var selections = 0
        var dateCommits = 0
        compose.setContent {
            CompositionLocalProvider(LocalVisualEffects provides VisualEffectsState(
                effectiveMode = if (native) VisualEffectMode.ColorOsGlow else VisualEffectMode.LiquidGlass)) {
            CouiSettingsPageScaffold(title = "当前课表详细设置", config = config, showBack = true) { _, pageBackdrop ->
                ScheduleSettingsContentFixed(
                    state = state, backdrop = pageBackdrop.takeIf { Build.VERSION.SDK_INT >= 31 },
                    totalWeeks = "20", onTotalWeeksChange = {}, currentWeek = "1", onCurrentWeekChange = {},
                    autoCurrentWeek = true, hideEmptyWeekends = false, showNonCurrentWeekCourses = true,
                    onHideEmptyWeekendsChange = {}, onShowNonCurrentWeekCoursesChange = {},
                    termStartDate = "2026.09.01", onTermStartDateChange = { dateCommits++ },
                    classDurationMinutes = "45", onClassDurationMinutesChange = {},
                    breakDurationMinutes = "15", onBreakDurationMinutesChange = {},
                    morningPeriodCount = 2, noonPeriodCount = 0, afternoonPeriodCount = 0, eveningPeriodCount = 0,
                    onPeriodCountsChange = { _, _, _, _ -> }, schemeDraft = draft, onSchemeDraftChange = {},
                    onAutoMatchPeriodEnds = {}, periods = periods, onPeriodsChange = {},
                    detectedWeek = 1, detectedWeekDescription = "第1周", error = null,
                    periodSchemeLibrary = listOf(saved), onSelectPeriodScheme = { selections++ },
                )
            }
            }
        }
        compose.onNodeWithText("总周数").assertIsDisplayed()
        val weeksLabel = compose.onNodeWithText("总周数", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val autoLabel = compose.onNodeWithText("当前周（自动）", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertEquals(weeksLabel.height, autoLabel.height, 1f)
        if (native) writeSettingsVisualArtifact("schedule-detail-native.png", compose.onRoot().captureToImage().asAndroidBitmap())
        compose.onNodeWithText("本课表作息").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("当前作息").performScrollTo().performClick()
        if (native) writeSettingsVisualArtifact("schedule-miuix-popup-native.png", compose.onRoot().captureToImage().asAndroidBitmap())
        compose.onNodeWithText("另一套作息").assertIsDisplayed().performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, selections) }
        compose.onNodeWithText("学期开始日期").performScrollTo().performClick()
        compose.onNodeWithText("选择日期").assertIsDisplayed()
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        org.junit.Assert.assertEquals(android.view.Gravity.BOTTOM,
            dialog.window!!.attributes.gravity and android.view.Gravity.VERTICAL_GRAVITY_MASK)
        compose.onNodeWithText("确定").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, dateCommits) }
    }
}
