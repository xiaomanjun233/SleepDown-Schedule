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
}
