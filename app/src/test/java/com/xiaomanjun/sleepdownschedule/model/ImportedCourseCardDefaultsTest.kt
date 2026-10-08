package com.xiaomanjun.sleepdownschedule.model

import org.junit.Assert.*
import org.junit.Test

class ImportedCourseCardDefaultsTest {
    @Test fun resetsAppearanceWithoutChangingTeachingDatesOrWallpaper() {
        val source = defaultConfig(7).copy(
            totalWeeks = 23,
            currentWeek = 9,
            termStartDate = "2026-08-03",
            scheduleAdjustmentsJson = "[{\"date\":\"2026-10-08\"}]",
            wallpaperBrightness = 0.42f,
            cardAlpha = 0.3f,
            courseCardBlur = 2f,
            courseCardFontScale = 1.5f,
            courseCardRefractionStrength = 0.9f,
            courseCardOutlineLightEnabled = false,
            courseCardColoredTextEnabled = false,
            weekCardHeightScale = 1.7f
        )
        val result = source.withImportedCourseCardDefaults()
        val defaults = defaultConfig(7)
        assertTrue(result.courseCardColoredTextEnabled)
        assertTrue(result.courseCardOutlineLightEnabled)
        assertEquals(defaults.cardAlpha, result.cardAlpha)
        assertEquals(defaults.courseCardBlur, result.courseCardBlur)
        assertEquals(defaults.courseCardFontScale, result.courseCardFontScale)
        assertEquals(defaults.courseCardRefractionStrength, result.courseCardRefractionStrength)
        assertEquals(defaults.weekCardHeightScale, result.weekCardHeightScale)
        assertEquals(source.id, result.id)
        assertEquals(source.totalWeeks, result.totalWeeks)
        assertEquals(source.currentWeek, result.currentWeek)
        assertEquals(source.termStartDate, result.termStartDate)
        assertEquals(source.scheduleAdjustmentsJson, result.scheduleAdjustmentsJson)
        assertEquals(source.wallpaperBrightness, result.wallpaperBrightness)
        assertEquals(result, result.withImportedCourseCardDefaults())
    }
}
