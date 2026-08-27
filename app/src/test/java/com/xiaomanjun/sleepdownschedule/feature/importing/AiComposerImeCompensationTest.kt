package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.feature.importing.progress.aiComposerBottomInsetPx

import org.junit.Assert.assertEquals
import org.junit.Test

class AiComposerImeCompensationTest {
    @Test
    fun hiddenImeKeepsNavigationInset() {
        assertEquals(
            72,
            inset(ime = 0, navigation = 72, baselineHeight = 2400, currentHeight = 2400)
        )
    }

    @Test
    fun untouchedWindowReceivesOneFullImeInset() {
        assertEquals(
            900,
            inset(ime = 900, navigation = 72, baselineHeight = 2400, currentHeight = 2400)
        )
    }

    @Test
    fun resizedWindowDoesNotReceiveImeTwice() {
        assertEquals(
            0,
            inset(ime = 900, navigation = 72, baselineHeight = 2400, currentHeight = 1500)
        )
    }

    @Test
    fun pannedWindowDoesNotReceiveImeTwice() {
        assertEquals(
            0,
            inset(
                ime = 900,
                navigation = 72,
                baselineHeight = 2400,
                currentHeight = 2400,
                baselineTop = 0,
                currentTop = -900
            )
        )
    }

    @Test
    fun partialSystemAdjustmentOnlyReceivesRemainingInset() {
        assertEquals(
            400,
            inset(ime = 900, navigation = 72, baselineHeight = 2400, currentHeight = 1900)
        )
    }

    @Test
    fun combinedResizeAndPanDoesNotReceiveImeTwice() {
        assertEquals(
            0,
            inset(
                ime = 900,
                navigation = 72,
                baselineHeight = 2400,
                currentHeight = 1900,
                baselineTop = 0,
                currentTop = -400
            )
        )
    }

    @Test
    fun partialCombinedAdjustmentOnlyReceivesPhysicalRemainder() {
        assertEquals(
            400,
            inset(
                ime = 900,
                navigation = 72,
                baselineHeight = 2400,
                currentHeight = 2100,
                baselineTop = 0,
                currentTop = -200
            )
        )
    }

    @Test
    fun missingBaselineSafelyFallsBackToFullImeInset() {
        assertEquals(
            900,
            inset(ime = 900, navigation = 72, baselineHeight = 0, currentHeight = 1500)
        )
    }

    @Test
    fun adjustResizeHostDoesNotApplyImeTwiceWhenMorphRootLooksUnchanged() {
        assertEquals(
            72,
            aiComposerBottomInsetPx(
                imeBottomPx = 900,
                navigationBottomPx = 72,
                baselineRootHeightPx = 2400,
                currentRootHeightPx = 2400,
                baselineRootTopOnScreenPx = 0,
                currentRootTopOnScreenPx = 0,
                tolerancePx = 24,
                hostConsumesImeResize = true
            )
        )
    }

    private fun inset(
        ime: Int,
        navigation: Int,
        baselineHeight: Int,
        currentHeight: Int,
        baselineTop: Int = 0,
        currentTop: Int = 0
    ): Int = aiComposerBottomInsetPx(
        imeBottomPx = ime,
        navigationBottomPx = navigation,
        baselineRootHeightPx = baselineHeight,
        currentRootHeightPx = currentHeight,
        baselineRootTopOnScreenPx = baselineTop,
        currentRootTopOnScreenPx = currentTop,
        tolerancePx = 24
    )
}
