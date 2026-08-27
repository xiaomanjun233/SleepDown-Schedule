package com.xiaomanjun.sleepdownschedule.feature.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePersonalizationGeometryTest {
    @Test
    fun adaptiveRowHeightExactlyFillsTheAvailableWeekGrid() {
        val row = adaptiveWeekRowHeightDp(
            viewportHeightDp = 800f,
            topSpacerDp = 96f,
            periodCount = 12,
            bottomInsetDp = 24f
        )

        assertEquals(800f, 96f + 44f + 46f + row * 12f + 24f, 0.001f)
    }

    @Test
    fun adaptiveRowHeightRetainsTheCompleteReadableTimelineFloor() {
        val row = adaptiveWeekRowHeightDp(
            viewportHeightDp = 720f,
            topSpacerDp = 100f,
            periodCount = 14,
            bottomInsetDp = 24f,
            fontScale = 1.15f
        )

        assertEquals(minimumWeekTimelineRowHeightDp(1.15f), row, 0.001f)
    }

    @Test
    fun rowHeightSliderMidpointIsAdaptiveAndRoundTrips() {
        assertEquals(1f, weekCardHeightScaleFromSlider(0.5f), 0.0001f)
        listOf(0f, 0.17f, 0.5f, 0.73f, 1f).forEach { progress ->
            assertEquals(
                progress,
                weekCardHeightSliderFromScale(weekCardHeightScaleFromSlider(progress)),
                0.0001f
            )
        }
    }

    @Test
    fun compactSliderNeverCutsOffTheCompleteTimeline() {
        val fontScale = 1.30f
        val adaptive = adaptiveWeekRowHeightDp(
            viewportHeightDp = 960f,
            topSpacerDp = 108f,
            periodCount = 12,
            bottomInsetDp = 28f,
            fontScale = fontScale
        )
        val floor = minimumWeekCardHeightScale(adaptive, fontScale)
        val compactHeight = adaptive * weekCardHeightScaleFromSlider(0f, floor)

        assertTrue(compactHeight + 0.0001f >= minimumWeekTimelineRowHeightDp(fontScale))
        assertEquals(floor, normalizedWeekCardHeightScale(MinimumWeekCardHeightScale, floor), 0.0001f)
    }

    @Test
    fun cornerMidpointPreservesTheExistingPhoneSilhouette() {
        val minimum = adaptiveWeekCardCornerRadius(48.dp, 48.dp, 412.dp, 915.dp, 0f)
        val middle = adaptiveWeekCardCornerRadius(48.dp, 48.dp, 412.dp, 915.dp, 0.5f)
        val maximum = adaptiveWeekCardCornerRadius(48.dp, 48.dp, 412.dp, 915.dp, 1f)

        assertEquals(8f, middle.value, 0.0001f)
        assertTrue(minimum < middle)
        assertTrue(maximum > middle)
        assertTrue(maximum.value <= 48f * 0.32f)
    }
}
