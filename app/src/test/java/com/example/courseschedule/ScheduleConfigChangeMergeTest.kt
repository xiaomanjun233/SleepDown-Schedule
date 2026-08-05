package com.example.courseschedule

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleConfigChangeMergeTest {
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

        assertEquals(0.35f, merged.cardAlpha)
        assertEquals(false, merged.courseCardGlassEnabled)
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
