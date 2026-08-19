package com.xiaomanjun.sleepdownschedule

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleConfigChangeMergeTest {
    @Test
    fun newSchedulesDefaultToCenteredDock() {
        val converters = ScheduleConverters()
        assertEquals(DockAlignment.CENTER, defaultConfig().dockAlignment)
        assertEquals(
            DockAlignment.CENTER,
            converters.stringToDockAlignment("UNKNOWN")
        )
        assertEquals(
            LiveUpdateChipTextMode.NORMAL,
            converters.stringToLiveUpdateChipTextMode("SHORT")
        )
    }

    @Test
    fun tabletRootSelectionAlwaysClearsNestedDetailRoute() {
        val liquidGlass = TabletSettingsNavigationState()
            .pushDetail(SettingsPage.LiquidGlass)
        assertEquals(SettingsPage.LiquidGlass, liquidGlass.displayedPage)

        val widgets = liquidGlass.selectRoot(SettingsPage.Widgets)
        assertEquals(SettingsPage.Widgets, widgets.rootPage)
        assertEquals(SettingsPage.Widgets, widgets.displayedPage)
        assertEquals(emptyList<SettingsPage>(), widgets.detailPages)

        val general = widgets.selectRoot(SettingsPage.General)
        assertEquals(SettingsPage.General, general.rootPage)
        assertEquals(SettingsPage.General, general.displayedPage)
        assertEquals(emptyList<SettingsPage>(), general.detailPages)
    }

    @Test
    fun persistentCenteredSettingsTitlesMatchRequestedPages() {
        assertEquals(true, SettingsPage.LiquidGlass.usesPersistentCenteredSettingsTitle())
        assertEquals(true, SettingsPage.Widgets.usesPersistentCenteredSettingsTitle())
        assertEquals(true, SettingsPage.About.usesPersistentCenteredSettingsTitle())
        assertEquals(true, SettingsPage.Changelog.usesPersistentCenteredSettingsTitle())
        assertEquals(false, SettingsPage.General.usesPersistentCenteredSettingsTitle())
    }

    @Test
    fun onlyDetailedScheduleCanInterceptSystemBack() {
        assertEquals(true, shouldInterceptSettingsBack(SettingsSection.Schedule, true, false))
        assertEquals(true, shouldInterceptSettingsBack(SettingsSection.Schedule, false, true))
        assertEquals(false, shouldInterceptSettingsBack(SettingsSection.Schedule, false, false))
        assertEquals(false, shouldInterceptSettingsBack(SettingsSection.Notifications, true, true))
    }

    @Test
    fun generalSettingsRebaseKeepsLatestUnrelatedValues() {
        val database = defaultConfig(id = 4).copy(
            notificationLeadMinutes = 35,
            notificationsEnabled = false,
            currentWeek = 8,
            darkMode = false,
            homeChromeBlurScale = 1.8f,
            homeChromeSamplingScale = 0.55f
        )
        val localDraft = defaultConfig(id = 4).copy(
            notificationLeadMinutes = 10,
            notificationsEnabled = true,
            currentWeek = 1,
            followSystemDarkMode = false,
            darkMode = true,
            defaultHomeMode = HomeStartMode.DAY,
            homeChromeBlurScale = 1.4f,
            homeChromeSamplingScale = 0.7f,
            hideFromRecents = true
        )

        val rebased = database.withGeneralSettingsFrom(localDraft)

        assertEquals(35, rebased.notificationLeadMinutes)
        assertEquals(false, rebased.notificationsEnabled)
        assertEquals(8, rebased.currentWeek)
        assertEquals(false, rebased.followSystemDarkMode)
        assertEquals(true, rebased.darkMode)
        assertEquals(HomeStartMode.DAY, rebased.defaultHomeMode)
        assertEquals(1.8f, rebased.homeChromeBlurScale)
        assertEquals(0.55f, rebased.homeChromeSamplingScale)
        assertEquals(true, rebased.hideFromRecents)
    }

    @Test
    fun liquidGlassPatchUpdatesOnlyBlurScale() {
        val database = defaultConfig(id = 4).copy(
            currentWeek = 8,
            darkMode = true,
            dockAlignment = DockAlignment.RIGHT,
            homeChromeBlurScale = 1f,
            homeChromeSamplingScale = 0.55f
        )

        val merged = database.withHomeChromeBlurScale(3.25f)

        assertEquals(8, merged.currentWeek)
        assertEquals(true, merged.darkMode)
        assertEquals(DockAlignment.RIGHT, merged.dockAlignment)
        assertEquals(3.25f, merged.homeChromeBlurScale)
        assertEquals(0.55f, merged.homeChromeSamplingScale)
        assertEquals(MaxHomeChromeBlurScale, database.withHomeChromeBlurScale(99f).homeChromeBlurScale)
    }

    @Test
    fun homeChromeBlurScalePreservesEachComponentsOriginalAnchor() {
        val config = defaultConfig().copy(homeChromeBlurScale = 1.5f)

        assertEquals(3.dp, homeChromeBlur(2.dp, config))
        assertEquals(1.95f, homeChromeBlur(1.3.dp, config).value, 0.0001f)

        val originalHeaderTokens = homeHeaderGlassTokens(lightGlass = true)
        val scaledHeaderTokens = homeHeaderGlassTokens(lightGlass = true, blurScale = 1.5f)
        assertEquals(originalHeaderTokens.blur * 1.5f, scaledHeaderTokens.blur)
        assertEquals(originalHeaderTokens.copy(blur = scaledHeaderTokens.blur), scaledHeaderTokens)
    }

    @Test
    fun notificationSettingsPatchKeepsGeneralAndScheduleValues() {
        val database = defaultConfig(id = 4).copy(
            currentWeek = 8,
            darkMode = true,
            defaultHomeMode = HomeStartMode.DAY,
            notificationLeadMinutes = 10,
            notificationsEnabled = true
        )
        val notificationDraft = defaultConfig(id = 4).copy(
            currentWeek = 1,
            darkMode = false,
            defaultHomeMode = HomeStartMode.WEEK,
            notificationLeadMinutes = 35,
            notificationsEnabled = false,
            notificationMode = NotificationMode.LIVE_UPDATE,
            liveUpdateChipTextMode = LiveUpdateChipTextMode.SHORT
        )

        val merged = database.withNotificationSettingsFrom(notificationDraft)

        assertEquals(8, merged.currentWeek)
        assertEquals(true, merged.darkMode)
        assertEquals(HomeStartMode.DAY, merged.defaultHomeMode)
        assertEquals(35, merged.notificationLeadMinutes)
        assertEquals(false, merged.notificationsEnabled)
        assertEquals(NotificationMode.LIVE_UPDATE, merged.notificationMode)
        assertEquals(LiveUpdateChipTextMode.SHORT, merged.liveUpdateChipTextMode)
    }

    @Test
    fun appliesOnlyFieldsChangedByCaller() {
        val original = defaultConfig(id = 3)
        val latestDatabaseRow = original.copy(
            currentWeek = 9,
            notificationLeadMinutes = 25,
            cardAlpha = 0.8f
        )
        val submitted = original.copy(
            wallpaperBrightness = 0.65f,
            courseCardFontScale = 1.15f
        )

        val merged = latestDatabaseRow.withChangesFrom(original, submitted)

        assertEquals(3, merged.id)
        assertEquals(9, merged.currentWeek)
        assertEquals(25, merged.notificationLeadMinutes)
        assertEquals(0.8f, merged.cardAlpha)
        assertEquals(0.65f, merged.wallpaperBrightness)
        assertEquals(1.15f, merged.courseCardFontScale)
    }

    @Test
    fun neverReplacesTargetScheduleId() {
        val original = defaultConfig(id = 1)
        val submitted = original.copy(id = 2, darkMode = true)
        val target = defaultConfig(id = 7)

        val merged = target.withChangesFrom(original, submitted)

        assertEquals(7, merged.id)
        assertEquals(true, merged.darkMode)
    }

    @Test
    fun rapidSliderCandidatesPreserveEarlierLocalChanges() {
        val presented = defaultConfig(id = 3)
        val afterBlur = mergePersonalizationCandidate(
            current = presented,
            candidate = presented.copy(wallpaperBlur = 7f),
            changeKey = "wallpaper-blur"
        )
        val afterBrightness = mergePersonalizationCandidate(
            current = afterBlur,
            candidate = presented.copy(wallpaperBrightness = 0.55f),
            changeKey = "wallpaper-brightness"
        )

        assertEquals(7f, afterBrightness.wallpaperBlur)
        assertEquals(0.55f, afterBrightness.wallpaperBrightness)
    }

    @Test
    fun sliderCanReturnToPresentedValueWithoutKeepingIntermediatePreview() {
        val presented = defaultConfig(id = 3)
        val intermediate = presented.copy(cardAlpha = 0.35f)

        val restored = mergePersonalizationCandidate(
            current = intermediate,
            candidate = presented,
            changeKey = "card-alpha"
        )

        assertEquals(presented.cardAlpha, restored.cardAlpha)
    }

    @Test
    fun latestPersonalizationSnapshotCanReturnToggleToDatabaseOriginalValue() {
        val databaseOriginal = defaultConfig(id = 3).copy(
            currentWeek = 9,
            courseCardGlassEnabled = false
        )
        val databaseAfterFirstTap = databaseOriginal.copy(courseCardGlassEnabled = true)
        val latestDraft = databaseOriginal.copy(
            wallpaperBlur = 7f,
            courseCardGlassEnabled = false
        )

        val merged = databaseAfterFirstTap.withPersonalizationFrom(latestDraft)

        assertEquals(false, merged.courseCardGlassEnabled)
        assertEquals(7f, merged.wallpaperBlur)
        assertEquals(9, merged.currentWeek)
    }

    @Test
    fun toggleCandidateDoesNotReplaceAConcurrentSliderDraft() {
        val presented = defaultConfig(id = 3)
        val sliderDraft = presented.copy(cardAlpha = 0.35f)

        val merged = mergePersonalizationCandidate(
            current = sliderDraft,
            candidate = presented.copy(courseCardGlassEnabled = false),
            changeKey = "card-glass"
        )

        assertEquals(presented.alternateCardAlpha, merged.cardAlpha)
        assertEquals(0.35f, merged.alternateCardAlpha)
        assertEquals(false, merged.courseCardGlassEnabled)
    }

    @Test
    fun glassAndSimpleCourseCardParametersRoundTripIndependently() {
        val glass = defaultConfig(id = 3).copy(
            cardColorArgb = 0xFF112233,
            cardAlpha = 0.42f,
            courseCardBlur = 7f,
            courseCardFontScale = 1.12f,
            alternateCardColorArgb = 0xFFABCDEF,
            alternateCardAlpha = 0.88f,
            alternateCourseCardBlur = 22f,
            alternateCourseCardFontScale = 0.94f
        )

        val simple = glass.switchCourseCardGlassMode(false)
        assertEquals(0xFFABCDEF, simple.cardColorArgb)
        assertEquals(0.88f, simple.cardAlpha)
        assertEquals(22f, simple.courseCardBlur)
        assertEquals(0.94f, simple.courseCardFontScale)

        val editedSimple = simple.copy(
            cardAlpha = 0.76f,
            courseCardBlur = 24f,
            courseCardFontScale = 1.03f
        )
        val restoredGlass = editedSimple.switchCourseCardGlassMode(true)

        assertEquals(0xFF112233, restoredGlass.cardColorArgb)
        assertEquals(0.42f, restoredGlass.cardAlpha)
        assertEquals(7f, restoredGlass.courseCardBlur)
        assertEquals(1.12f, restoredGlass.courseCardFontScale)
        assertEquals(0.76f, restoredGlass.alternateCardAlpha)
        assertEquals(24f, restoredGlass.alternateCourseCardBlur)
        assertEquals(1.03f, restoredGlass.alternateCourseCardFontScale)
    }

    @Test
    fun staleSliderReleaseCannotClearTheNewTouchOwner() {
        val afterBrightnessStarts = resolveActivePersonalizationSlider(
            currentKey = "wallpaper-blur",
            eventKey = "wallpaper-brightness",
            active = true
        )
        val afterOldBlurRelease = resolveActivePersonalizationSlider(
            currentKey = afterBrightnessStarts,
            eventKey = "wallpaper-blur",
            active = false
        )

        assertEquals("wallpaper-brightness", afterOldBlurRelease)
    }
}
